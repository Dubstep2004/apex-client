package com.apexvpn.client.data

import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder

/**
 * Замер задержки (раньше у Hysteria и TUIC было «UDP»):
 *  • VPN включён — настоящая задержка через сервер от работающего ядра (Clash API на 127.0.0.1).
 *  • VPN выключен — время соединения с сервером: TCP для обычных протоколов,
 *    QUIC-рукопожатие для Hysteria, Hysteria 2 и TUIC (Apexcore.udpPing).
 *  • WireGuard без VPN не измерить — показываем «—».
 */
object Pinger {
    private const val TIMEOUT_MS = 5000
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    const val UNREACHABLE = -1
    const val UNKNOWN = -2

    /** tag → задержка в мс или UNREACHABLE */
    /** [save] = false — замер части серверов: сохранять будет вызывающий, вместе с остальными. */
    /**
     * [type] — способ замера вместо выбранного в «Пинге»: служебные проверки (белые списки,
     * режимы автопилота, самопроверка) всегда берут "tcp", чтобы ICMP или прокси-пинг
     * не выдали рабочие серверы за «мёртвые».
     */
    suspend fun pingAll(nodes: List<Node>, save: Boolean = true, type: String? = null): Map<String, Int> = withContext(Dispatchers.IO) {
        if (nodes.isEmpty()) return@withContext emptyMap()
        val running = VpnState.status.value == VpnStatus.Started
        val s = AppSettings.value
        val pingType = type ?: s.pingType
        // «Пинг через прокси» (GET/HEAD): HTTP-запрос через каждый сервер — только при включённом VPN
        if (running && (pingType == "get" || pingType == "head")) {
            val limit = Semaphore(8)
            val r = nodes.map { n ->
                async {
                    limit.withPermit {
                        n.tag to runCatching {
                            Apexcore.proxyPing(n.tag, s.pingUrl, pingType.uppercase(), s.pingMode, s.pingTimeout * 1000).let { if (it > 0) it else UNREACHABLE }
                        }.getOrDefault(UNREACHABLE)
                    }
                }
            }.awaitAll().toMap()
            if (r.values.any { it > 0 }) return@withContext r.also { if (save) Prefs.saveLastPings(it) }
            // Ни один не ответил — ядро без замеров (старый конфиг): дальше обычный пинг
        }
        // ICMP — классический ping до сервера, мимо туннеля
        if (pingType == "icmp") {
            val limit = Semaphore(8)
            val r = nodes.map { n -> async { limit.withPermit { n.tag to icmpPing(n.server, 3000) } } }.awaitAll().toMap()
            return@withContext r.also { if (save) Prefs.saveLastPings(it) }
        }
        // Пинг — это время одного «туда-обратно» до самого сервера: TCP-соединение или
        // QUIC-рукопожатие, мимо туннеля (и под VPN тоже — через ядро напрямую).
        // Раньше под VPN показывалась полная проверка через сервер (подключение, шифрование
        // и запрос к сайту) — в 2–3 раза больше настоящего пинга.
        val result = mutableMapOf<String, Int>()
        val udpNodes = nodes.filter { it.udp }
        if (udpNodes.isNotEmpty()) {
            runCatching {
                val json = JSONObject(Apexcore.udpPing(Prefs.nodesJson, 3000))
                json.keys().forEach { k -> result[k] = json.getInt(k) }
            }
            udpNodes.forEach { if (it.tag !in result) result[it.tag] = UNKNOWN }
        }
        // Остальные — время TCP-соединения мимо туннеля
        val rest = nodes.filter { it.tag !in result }
        val limit = Semaphore(8)
        rest.map { node -> async { limit.withPermit { node.tag to tcpPing(node.server, node.port) } } }
            .awaitAll().toMap(result)
        // Не измерить напрямую (сервер закрыт для прямых соединений, белые списки, mKCP) —
        // берём проверку через ядро: она показывает, что сервер работает
        if (running) {
            val missing = nodes.filter { (result[it.tag] ?: UNREACHABLE) < 0 }
            if (missing.isNotEmpty()) {
                runCatching { viaClashApi(missing) }.getOrNull()?.forEach { (tag, ms) -> if (ms > 0) result[tag] = ms }
            }
        }
        if (save) Prefs.saveLastPings(result)
        result
    }

    /** Какой сервер сейчас выбрал автопилот (tag) — из работающего ядра; null — неизвестно. */
    suspend fun autopilotNow(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = clashGet("/proxies/${URLEncoder.encode(AUTO_TAG, "UTF-8")}", 2000)
            if (code == 200) JSONObject(body).optString("now").takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    private fun clashGet(path: String, readTimeout: Int): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:${Prefs.clashApiPort}$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = readTimeout
        conn.setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
        return try {
            val code = conn.responseCode
            code to (if (code == 200) conn.inputStream.bufferedReader().readText() else "")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun viaClashApi(nodes: List<Node>): Map<String, Int> = withContext(Dispatchers.IO) {
        // Ядро без Clash API (старый конфиг) — пусть замер сделает временное ядро
        check(clashGet("/version", 2000).first == 200) { "clash api недоступен" }
        val limit = Semaphore(10)
        nodes.map { node ->
            async {
                limit.withPermit {
                    node.tag to runCatching {
                        val (code, body) = clashGet(
                            "/proxies/${URLEncoder.encode(node.tag, "UTF-8")}/delay" +
                                "?timeout=$TIMEOUT_MS&url=${URLEncoder.encode(TEST_URL, "UTF-8")}",
                            TIMEOUT_MS + 2000,
                        )
                        if (code == 200) JSONObject(body).optInt("delay", UNREACHABLE) else UNREACHABLE
                    }.getOrDefault(UNREACHABLE)
                }
            }
        }.awaitAll().toMap()
    }

    /**
     * ICMP echo (обычный ping) без root: «ping-сокет» Android, привязанный к настоящей сети,
     * чтобы под VPN не уйти в туннель. Только IPv4; -1 — не ответил или ICMP закрыт.
     */
    private fun icmpPing(host: String, timeoutMs: Int): Int = runCatching {
        val addr = java.net.InetAddress.getAllByName(host).firstOrNull { it is java.net.Inet4Address } ?: return UNREACHABLE
        val fd = android.system.Os.socket(android.system.OsConstants.AF_INET, android.system.OsConstants.SOCK_DGRAM, android.system.OsConstants.IPPROTO_ICMP)
        try {
            NetworkMonitor.defaultNetwork?.bindSocket(fd)
            // Тип 8 (запрос эха); идентификатор и контрольную сумму ядро Linux ставит само
            val pkt = byteArrayOf(8, 0, 0, 0, 0, 0, 0, 1) + "APEXPING".toByteArray()
            val start = System.nanoTime()
            android.system.Os.sendto(fd, pkt, 0, pkt.size, 0, addr, 0)
            val pfd = android.system.StructPollfd().apply { this.fd = fd; events = android.system.OsConstants.POLLIN.toShort() }
            if (android.system.Os.poll(arrayOf(pfd), timeoutMs) <= 0) return UNREACHABLE
            val buf = ByteArray(128)
            android.system.Os.recvfrom(fd, buf, 0, buf.size, 0, null)
            if (buf[0].toInt() != 0) return UNREACHABLE // ответ эха — тип 0
            ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
        } finally {
            runCatching { android.system.Os.close(fd) }
        }
    }.getOrDefault(UNREACHABLE)

    private fun tcpPing(host: String, port: Int): Int {
        if (port !in 1..65535) return UNREACHABLE
        return runCatching {
            val net = NetworkMonitor.defaultNetwork
            // 3.16.1: адрес сервера узнаём тоже через реальную сеть. Раньше имя искалось через DNS
            // туннеля: когда VPN включён, но не работает (белые списки), не находился ни один
            // сервер с доменом вместо IP — в том числе LTE-обход, и белые списки не определялись
            val addr = net?.let { n -> runCatching { n.getAllByName(host).let { a -> a.firstOrNull { it is java.net.Inet4Address } ?: a.first() } }.getOrNull() }
            Socket().use { socket ->
                // Если VPN включён, привязываем сокет к реальной сети, иначе замер пойдёт через туннель
                net?.bindSocket(socket)
                val start = System.nanoTime()
                socket.connect(if (addr != null) InetSocketAddress(addr, port) else InetSocketAddress(host, port), 3000)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrDefault(UNREACHABLE)
    }
}
