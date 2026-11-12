package com.apexvpn.client.data

import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom

/**
 * 585. Проверка утечек: видит ли сайт ваш настоящий IP.
 *  • IP — настоящий (мимо VPN) и через VPN, со страной;
 *  • DNS — чей DNS-сервер на самом деле узнаёт адреса сайтов;
 *  • IPv6 — не уходит ли IPv6 мимо туннеля;
 *  • WebRTC (STUN) — какой адрес видят звонки и браузерный WebRTC.
 * Проверка только по нажатию; результаты никуда не отправляются.
 */
object LeakTest {
    enum class Mark { Wait, Ok, Warn, Bad }

    data class Item(val title: String, val mark: Mark = Mark.Wait, val value: String = "", val note: String = "")

    data class State(val running: Boolean = false, val items: List<Item> = emptyList(), val verdict: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private data class Trace(val ip: String, val country: String)

    /** Cloudflare: ip=… и loc=… — без ключей и учёта. */
    private fun trace(url: String, direct: Boolean): Trace? = runCatching {
        val u = URL(url)
        val net = if (direct) NetworkMonitor.defaultNetwork else null
        val c = (net?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.useCaches = false
        try {
            if (c.responseCode != 200) return null
            val m = c.inputStream.bufferedReader().readText().lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            Trace(m["ip"] ?: return null, m["loc"].orEmpty())
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /**
     * DNS: edns.ip-api.com отвечает, какой DNS-сервер запросил его адрес.
     * 3.18.5: две попытки с паузой (ip-api иногда не отвечает с первого раза).
     */
    private fun dnsResolver(): Pair<String, String>? =
        dnsResolverOnce() ?: run { Thread.sleep(800); dnsResolverOnce() }

    /**
     * 3.18.5. Запасная проверка, если ip-api молчит: спросить адрес у системы телефона.
     * Ответ из 198.18.0.0/15 — это FakeIP ядра: значит, все DNS-запросы телефона уходят в туннель, утечки нет.
     */
    private fun dnsInTunnel(): Boolean? = runCatching {
        val id = (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789"[SecureRandom().nextInt(36)] }.joinToString("")
        val a = InetAddress.getAllByName("$id.apex-check.cloudflare.com").firstOrNull { it is java.net.Inet4Address }
            ?: InetAddress.getAllByName("www.cloudflare.com").first { it is java.net.Inet4Address }
        val b = a.address
        (b[0].toInt() and 0xff) == 198 && ((b[1].toInt() and 0xff) == 18 || (b[1].toInt() and 0xff) == 19)
    }.getOrNull()

    private fun dnsResolverOnce(): Pair<String, String>? = runCatching {
        val id = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789"[SecureRandom().nextInt(36)] }.joinToString("")
        val c = URL("http://$id.edns.ip-api.com/json").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            val o = JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("dns") ?: return null
            o.optString("ip") to o.optString("geo")
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** STUN (как WebRTC): какой адрес и порт видит внешний сервер для UDP. */
    private fun stun(): String? = runCatching {
        DatagramSocket().use { sock ->
            sock.soTimeout = 4000
            val tid = ByteArray(12).also { SecureRandom().nextBytes(it) }
            // Binding Request: тип 0x0001, длина 0, magic cookie 0x2112A442, id
            val req = byteArrayOf(0, 1, 0, 0, 0x21, 0x12, 0xA4.toByte(), 0x42) + tid
            val addr = InetAddress.getAllByName("stun.l.google.com").first { it is java.net.Inet4Address }
            sock.send(DatagramPacket(req, req.size, addr, 19302))
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            sock.receive(p)
            var i = 20
            while (i + 4 <= p.length) {
                val type = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
                val len = ((buf[i + 2].toInt() and 0xFF) shl 8) or (buf[i + 3].toInt() and 0xFF)
                if ((type == 0x0020 || type == 0x0001) && len >= 8 && buf[i + 5].toInt() == 1) {
                    val xor = type == 0x0020
                    val ip = (0 until 4).map { k -> (buf[i + 8 + k].toInt() and 0xFF) xor (if (xor) intArrayOf(0x21, 0x12, 0xA4, 0x42)[k] else 0) }
                    return@use ip.joinToString(".")
                }
                i += 4 + len + ((4 - len % 4) % 4)
            }
            null
        }
    }.getOrNull()

    private fun flag(cc: String) = if (cc.length == 2) cc.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("") else ""

    suspend fun run() = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext
        val titles = listOf("Ваш настоящий IP", "IP через VPN", "DNS", "IPv6", "WebRTC (STUN)")
        _state.value = State(running = true, items = titles.map { Item(it) })
        fun set(i: Int, item: Item) {
            _state.value = _state.value.copy(items = _state.value.items.toMutableList().also { it[i] = item })
        }
        if (VpnState.status.value != VpnStatus.Started) {
            _state.value = State(items = titles.map { Item(it) }, verdict = "Включите VPN — проверка сравнивает адрес с VPN и без него.")
            return@withContext
        }
        val (real, vpn) = coroutineScope {
            val a = async { trace("https://1.1.1.1/cdn-cgi/trace", direct = true) }
            val b = async { trace("https://1.1.1.1/cdn-cgi/trace", direct = false) }
            a.await() to b.await()
        }
        set(0, Item(titles[0], if (real != null) Mark.Ok else Mark.Warn, real?.let { "${flag(it.country)} ${it.ip}" } ?: "не удалось узнать", "Его сайты видеть не должны"))
        val ipLeak = vpn == null || (real != null && vpn.ip == real.ip)
        set(
            1,
            Item(
                titles[1],
                when {
                    vpn == null -> Mark.Warn
                    ipLeak -> Mark.Bad
                    else -> Mark.Ok
                },
                vpn?.let { "${flag(it.country)} ${it.ip}" } ?: "нет ответа",
                if (ipLeak && vpn != null) "Совпадает с настоящим — проверьте режим и приложения" else "Этот адрес видят сайты",
            ),
        )
        val dns = dnsResolver()
        val dnsLeak = dns != null && real != null && real.country.isNotEmpty() && vpn != null && real.country != vpn.country &&
            dns.second.contains(countryName(real.country), ignoreCase = true)
        val tunnel = if (dns == null) dnsInTunnel() else null
        set(
            2,
            Item(
                titles[2],
                when {
                    dnsLeak -> Mark.Bad
                    dns != null || tunnel == true -> Mark.Ok
                    else -> Mark.Warn
                },
                when {
                    dns != null -> "${dns.first} · ${dns.second}"
                    tunnel == true -> "через VPN"
                    else -> "не удалось проверить"
                },
                when {
                    dnsLeak -> "DNS идёт через вашего провайдера — включите DNS «Авто» или свой DNS"
                    dns != null || tunnel == true -> "Адреса сайтов узнаются через защищённый DNS"
                    else -> "Сервис проверки не ответил — попробуйте ещё раз"
                },
            ),
        )
        val v6 = trace("https://[2606:4700:4700::1111]/cdn-cgi/trace", direct = false)
        val v6Real = trace("https://[2606:4700:4700::1111]/cdn-cgi/trace", direct = true)
        val v6Leak = v6 != null && v6Real != null && v6.ip == v6Real.ip
        set(
            3,
            Item(
                titles[3],
                if (v6Leak) Mark.Bad else Mark.Ok,
                when {
                    v6 == null -> "закрыт"
                    else -> "${flag(v6.country)} ${v6.ip}"
                },
                when {
                    v6Leak -> "IPv6 идёт мимо VPN — выключите IPv6 в настройках"
                    v6 == null -> "IPv6 не уходит мимо туннеля"
                    else -> "IPv6 идёт через VPN"
                },
            ),
        )
        val st = stun()
        val stunLeak = st != null && real != null && st == real.ip
        set(
            4,
            Item(
                titles[4],
                when {
                    st == null -> Mark.Ok
                    stunLeak -> Mark.Bad
                    else -> Mark.Ok
                },
                st ?: "UDP закрыт",
                if (stunLeak) "Звонки и WebRTC видят настоящий адрес" else "Звонки и WebRTC не видят настоящий адрес",
            ),
        )
        val bad = _state.value.items.count { it.mark == Mark.Bad }
        _state.value = _state.value.copy(
            running = false,
            verdict = if (bad == 0) "Утечек нет ✓ Сайты видят только адрес VPN." else "Найдено утечек: $bad — подробности выше.",
        )
    }

    /** Английское название страны для сравнения с ответом edns (там «Russia», «Netherlands»…). */
    private fun countryName(cc: String) = java.util.Locale("", cc).getDisplayCountry(java.util.Locale.ENGLISH)

    fun reset() {
        if (!_state.value.running) _state.value = State()
    }
}
