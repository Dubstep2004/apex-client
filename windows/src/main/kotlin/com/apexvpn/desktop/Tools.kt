package com.apexvpn.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Сеть для проверок. Само приложение ходит мимо VPN (и в режиме TUN), поэтому «через VPN»
 * — это локальный прокси ядра 127.0.0.1:proxyPort, а «через конкретный сервер» — SOCKS
 * для замеров (probe) с пользователем n:<сервер>.
 */
object Net {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    @Volatile private var socksUser = "apex"

    init {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? =
                if (requestingProtocol?.startsWith("SOCKS") == true && requestingPort == Store.probePort)
                    PasswordAuthentication(socksUser, Store.xrayAuth.toCharArray())
                else null
        })
    }

    fun viaVpn(): Proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", Store.proxyPort))

    /** Соединения через этот сервер (замеры по одному серверу за раз). */
    fun viaNode(tag: String): Proxy {
        socksUser = "n:$tag"
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", Store.probePort))
    }

    fun open(url: String, proxy: Proxy? = null, timeout: Int = 8000): HttpURLConnection {
        val u = URI(url).toURL()
        val c = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout * 2
        c.useCaches = false
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Cache-Control", "no-cache")
        return c
    }

    fun text(url: String, proxy: Proxy? = null, timeout: Int = 8000): String? = runCatching {
        val c = open(url, proxy, timeout)
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }.getOrNull()

    fun trace(proxy: Proxy?): Map<String, String> = text("https://speed.cloudflare.com/cdn-cgi/trace", proxy).orEmpty()
        .lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
}

// ─── Тест скорости (как на Android: серверы Cloudflare) ───────────────

object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"

    data class Meta(val provider: String, val ip: String, val city: String, val colo: String)
    data class State(
        val running: Boolean = false,
        val phase: String = "",
        val live: Double = 0.0,
        val ping: Int = -1,
        val jitter: Int = -1,
        val down: Double = -1.0,
        val up: Double = -1.0,
        val meta: Meta? = null,
        val viaVpn: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val lock = Mutex()

    private fun proxy() = if (Engine.status.value == VpnStatus.On) Net.viaVpn() else null

    suspend fun run(streams: Int = 4) = withContext(Dispatchers.IO) {
        if (!lock.tryLock()) return@withContext
        try {
            val p = proxy()
            _state.value = State(running = true, phase = "Сервер теста…", viaVpn = p != null)
            val meta = meta(p)
            _state.value = _state.value.copy(meta = meta, phase = "Пинг")
            val samples = (1..8).mapNotNull {
                runCatching {
                    val c = Net.open("$BASE/__down?bytes=0", p, 5000)
                    val t = System.nanoTime()
                    try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
                    ((System.nanoTime() - t) / 1_000_000).toInt()
                }.getOrNull()
            }
            val warm = if (samples.size > 2) samples.drop(1) else samples
            if (warm.isEmpty()) error(if (p != null) "Сервер теста не отвечает через VPN" else "Нет связи с сервером теста")
            _state.value = _state.value.copy(ping = warm.min(), jitter = if (warm.size < 2) -1 else warm.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt(), phase = "Загрузка")
            val down = transfer(p, streams, upload = false)
            _state.value = _state.value.copy(down = down, live = 0.0, phase = "Отдача")
            val up = transfer(p, streams, upload = true)
            _state.value = _state.value.copy(up = up, live = 0.0, phase = "Готово", running = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, phase = "", error = e.message ?: "Ошибка теста")
        } finally {
            lock.unlock()
        }
    }

    private fun meta(p: Proxy?): Meta? {
        val m = runCatching { JSONObject(Net.text("$BASE/meta", p) ?: "") }.getOrNull()
        if (m != null && m.optString("asOrganization").isNotBlank())
            return Meta(m.optString("asOrganization"), m.optString("clientIp"), m.optString("city"), m.optString("colo"))
        val tr = Net.trace(p)
        val who = runCatching { JSONObject(Net.text("https://ipwho.is/", p) ?: "") }.getOrNull()
        val isp = who?.optJSONObject("connection")?.let { it.optString("isp").ifBlank { it.optString("org") } }.orEmpty()
        if (tr.isEmpty() && isp.isEmpty()) return null
        return Meta(isp, tr["ip"] ?: who?.optString("ip").orEmpty(), who?.optString("city").orEmpty(), tr["colo"].orEmpty())
    }

    /** Мбит/с за 10 секунд, [streams] соединений одновременно. */
    private suspend fun transfer(p: Proxy?, streams: Int, upload: Boolean): Double = coroutineScope {
        val total = AtomicLong()
        val start = System.nanoTime()
        val limit = 10_000_000_000L
        val chunk = ByteArray(256 * 1024).also { SecureRandom().nextBytes(it) }
        val jobs = (1..streams.coerceIn(1, 8)).map {
            launch(Dispatchers.IO) {
                while (System.nanoTime() - start < limit) {
                    runCatching {
                        if (upload) {
                            val c = Net.open("$BASE/__up", p, 8000)
                            c.requestMethod = "POST"; c.doOutput = true
                            c.setFixedLengthStreamingMode(8L * chunk.size)
                            try {
                                c.outputStream.use { out ->
                                    repeat(8) { ensureActive(); out.write(chunk); total.addAndGet(chunk.size.toLong()) }
                                }
                                c.responseCode
                            } finally { c.disconnect() }
                        } else {
                            val c = Net.open("$BASE/__down?bytes=25000000", p, 8000)
                            try {
                                c.inputStream.use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        ensureActive()
                                        val n = input.read(buf)
                                        if (n < 0 || System.nanoTime() - start > limit) break
                                        total.addAndGet(n.toLong())
                                    }
                                }
                            } finally { c.disconnect() }
                        }
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; delay(300) }
                }
            }
        }
        val ticker = launch {
            while (true) {
                delay(250)
                val sec = (System.nanoTime() - start) / 1e9
                if (sec > 0.5) _state.value = _state.value.copy(live = total.get() * 8 / sec / 1e6)
            }
        }
        jobs.forEach { it.join() }
        ticker.cancel()
        val sec = (System.nanoTime() - start) / 1e9
        if (total.get() == 0L) -1.0 else total.get() * 8 / sec / 1e6
    }
}

// ─── Проверка утечек ─────────────────────────────────────────────────

object LeakTest {
    data class Row(val title: String, val value: String, val ok: Boolean?)
    data class State(val running: Boolean = false, val rows: List<Row> = emptyList(), val verdict: String? = null, val good: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    suspend fun run() = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext
        _state.value = State(running = true)
        val on = Engine.status.value == VpnStatus.On
        val real = Net.trace(null)
        val rows = mutableListOf(Row("IP без VPN", real["ip"]?.let { "$it · ${real["loc"].orEmpty()}" } ?: "не удалось узнать", null))
        if (!on) {
            _state.value = State(rows = rows, verdict = "Подключите VPN и проверьте ещё раз")
            return@withContext
        }
        val vpn = Net.trace(Net.viaVpn())
        val vpnIp = vpn["ip"]
        val ipOk = vpnIp != null && vpnIp != real["ip"]
        rows += Row("IP через VPN", vpnIp?.let { "$it · ${vpn["loc"].orEmpty()}" } ?: "нет ответа", ipOk)
        // DNS: какой DNS-сервер спрашивает адрес сайта, открытого через VPN
        val id = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789"[SecureRandom().nextInt(36)] }.joinToString("")
        val dns = runCatching { JSONObject(Net.text("http://$id.edns.ip-api.com/json", Net.viaVpn()) ?: "").optJSONObject("dns") }.getOrNull()
        val dnsGeo = dns?.optString("geo").orEmpty()
        val dnsOk = dns != null && !dnsGeo.contains("Russia", true) || (dns != null && real["loc"] != "RU")
        rows += Row("DNS", dns?.let { "${it.optString("ip")} · $dnsGeo" } ?: "не удалось проверить", if (dns == null) null else dnsOk)
        if (Store.value.connectType == ConnectType.Proxy)
            rows += Row("Программы без прокси", "идут мимо VPN — для всего компьютера включите TUN", null)
        val good = ipOk && (dns == null || dnsOk)
        _state.value = State(
            rows = rows, good = good,
            verdict = when {
                !ipOk -> "Сайты видят ваш настоящий IP — проверьте сервер"
                dns != null && !dnsOk -> "DNS-запросы уходят российскому DNS — выберите другой DNS"
                else -> "Утечек нет ✓"
            },
        )
    }
}

// ─── Журнал соединений ───────────────────────────────────────────────

object ConnLog {
    enum class Route(val title: String) { Vpn("VPN"), Direct("напрямую"), Blocked("заблокировано") }
    data class Entry(val time: Long, val host: String, val route: Route, val server: String, val app: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val seen = HashSet<String>()
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    fun setEnabled(v: Boolean) { _enabled.value = v; if (!v) clear() }

    fun clear() { synchronized(seen) { seen.clear() }; _entries.value = emptyList() }

    /** Опрос API ядра (раз в 2 секунды, пока подключено и журнал включён). */
    fun poll() {
        if (!_enabled.value) return
        val o = Clash.connections() ?: return
        val arr = o.optJSONArray("connections") ?: JSONArray()
        val sub = Subscription.state.value
        val fresh = mutableListOf<Entry>()
        val ids = HashSet<String>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val id = c.optString("id"); ids += id
            val isNew = synchronized(seen) { seen.add(id) }
            if (!isNew) continue
            val m = c.optJSONObject("metadata") ?: continue
            val type = m.optString("type")
            if ("probe-in" in type || "apex-dial" in type) continue
            val host = m.optString("host").ifEmpty { m.optString("destinationIP") }
            if (host.isEmpty() || host == "127.0.0.1") continue
            val first = c.optJSONArray("chains")?.optString(0).orEmpty()
            val route = when (first) { "direct" -> Route.Direct; "block" -> Route.Blocked; else -> Route.Vpn }
            val server = if (route == Route.Vpn) sub.nodes.find { it.tag == first }?.title.orEmpty() else ""
            val app = m.optString("processPath").substringAfterLast('\\').substringAfterLast('/')
            fresh += Entry(System.currentTimeMillis(), host, route, server, app)
        }
        synchronized(seen) { seen.retainAll(ids) }
        if (fresh.isNotEmpty()) _entries.value = (fresh + _entries.value).take(500)
    }
}

// ─── Режимы автопилота по замерам ─────────────────────────────────────

/** Замеры для режимов «Игры», «Скорость», «Надёжный», «Звонки» — как на Android. */
object AutoModes {
    data class Quality(val avg: Int = -1, val jitter: Int = -1, val loss: Int = 100, val mbps: Double = -1.0, val fails: Int = 0)

    private val _quality = MutableStateFlow(load())
    val quality = _quality.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val lock = Mutex()
    private val file get() = File(Paths.data, "auto-modes.json")

    private fun pool(nodes: List<Node>) = nodes.filter { !ServerInfo.isSpecial(it.name) && !it.ads }.ifEmpty { nodes }

    suspend fun measure(mode: AutoMode) = lock.withLock {
        if (!mode.measured) return@withLock
        val nodes = pool(Subscription.state.value.nodes)
        if (nodes.isEmpty()) return@withLock
        try {
            _status.value = if (mode == AutoMode.Games) "Подбираю серверы для игр…" else "Проверяю стабильность серверов…"
            val rounds = mutableListOf<Map<String, Int>>()
            repeat(4) { i ->
                Pinger.pingAll()
                rounds += Pinger.pings.value
                if (i < 3) delay(400)
            }
            val old = _quality.value
            val next = old.toMutableMap()
            for (n in nodes) {
                val v = rounds.mapNotNull { r -> r[n.tag]?.takeIf { it > 0 } }
                val prev = old[n.tag] ?: Quality()
                next[n.tag] = prev.copy(
                    avg = if (v.isEmpty()) -1 else v.average().toInt(),
                    jitter = if (v.size < 2) -1 else v.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt(),
                    loss = 100 * (rounds.size - v.size) / rounds.size,
                    fails = if (v.isEmpty()) prev.fails + 1 else 0,
                )
            }
            _quality.value = next
            // «Скорость»: загрузка через 3 лучших по пингу (нужен включённый VPN)
            if (mode == AutoMode.Speed && Engine.status.value == VpnStatus.On) {
                _status.value = "Проверяю скорость серверов…"
                val top = nodes.filter { (next[it.tag]?.avg ?: -1) > 0 }.sortedBy { next[it.tag]!!.avg }.distinctBy { it.title }.take(3)
                for (n in top) {
                    val mbps = withContext(Dispatchers.IO) { speedVia(n.tag) }
                    if (mbps > 0) _quality.value = _quality.value + (n.tag to (_quality.value[n.tag] ?: Quality()).copy(mbps = mbps))
                }
            }
            save()
        } finally {
            _status.value = null
        }
    }

    /** Мбит/с: 6 секунд загрузки через этот сервер. */
    private fun speedVia(tag: String): Double = runCatching {
        val p = Net.viaNode(tag)
        val c = Net.open("https://speed.cloudflare.com/__down?bytes=50000000", p, 8000)
        val start = System.nanoTime(); var total = 0L
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (System.nanoTime() - start < 6_000_000_000L) {
                    val n = input.read(buf); if (n < 0) break; total += n
                }
            }
        } finally { c.disconnect() }
        total * 8 / ((System.nanoTime() - start) / 1e9) / 1e6
    }.getOrDefault(-1.0)

    /** Из каких серверов выбирает автопилот (по порядку); пусто — без ограничений. */
    fun allowed(mode: AutoMode, nodes: List<Node>): List<String> {
        val q = _quality.value
        val base = pool(nodes).filter { q[it.tag] != null }
        if (base.isEmpty()) return emptyList()
        fun ok(n: Node) = (q[n.tag]?.avg ?: -1) > 0
        val list = when (mode) {
            AutoMode.Games -> base.filter { ok(it) && q[it.tag]!!.loss == 0 }
                .filter { val s = q[it.tag]!!; s.jitter in 0..maxOf(25, s.avg / 4) }
                .sortedBy { val s = q[it.tag]!!; s.avg + 2 * s.jitter }.take(5)
            AutoMode.Speed -> base.filter { (q[it.tag]?.mbps ?: -1.0) > 0 }.sortedByDescending { q[it.tag]!!.mbps }.take(2)
            AutoMode.Stable, AutoMode.Calls -> base.filter { ok(it) && q[it.tag]!!.loss == 0 && q[it.tag]!!.fails == 0 }
                .sortedBy { val s = q[it.tag]!!; s.jitter.coerceAtLeast(0) * 3 + s.avg }.take(5)
            else -> emptyList()
        }
        return list.map { it.tag }
    }

    private fun save() {
        val o = JSONObject()
        _quality.value.forEach { (k, v) -> o.put(k, JSONObject().put("a", v.avg).put("j", v.jitter).put("l", v.loss).put("m", v.mbps).put("f", v.fails)) }
        runCatching { file.writeText(o.toString()) }
    }

    private fun load(): Map<String, Quality> = runCatching {
        val o = JSONObject(File(Paths.data, "auto-modes.json").readText())
        o.keys().asSequence().associateWith { k ->
            val q = o.getJSONObject(k)
            Quality(q.optInt("a", -1), q.optInt("j", -1), q.optInt("l", 100), q.optDouble("m", -1.0), q.optInt("f", 0))
        }
    }.getOrDefault(emptyMap())
}
