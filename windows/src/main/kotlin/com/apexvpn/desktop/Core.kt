package com.apexvpn.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** apexcli.exe — общее ядро разбора подписки и сборки конфигов (то же, что на Android). */
object CoreCli {
    private fun call(req: JSONObject, timeoutSec: Long = 30): String {
        val exe = Paths.exe("apexcli.exe")
        check(exe.exists()) { "Не найден apexcli.exe — переустановите APEX VPN" }
        val p = ProcessBuilder(exe.absolutePath).redirectErrorStream(false).start()
        p.outputStream.use { it.write(req.toString().toByteArray(Charsets.UTF_8)) }
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); error("Ядро не ответило") }
        val o = runCatching { JSONObject(out) }.getOrElse { error("Ядро вернуло непонятный ответ") }
        if (!o.optBoolean("ok")) error(o.optString("error", "ошибка ядра"))
        return o.optString("result")
    }

    fun parse(content: String) = call(JSONObject().put("cmd", "parse").put("content", content))

    fun build(nodes: String, selected: String, options: String): Pair<String, String> {
        val o = JSONObject(call(JSONObject().put("cmd", "build").put("nodes", nodes).put("selected", selected).put("options", options)))
        return o.optString("singbox") to o.optString("xray")
    }

    fun warpKeypair(): String = call(JSONObject().put("cmd", "warpkey"))

    fun warpParse(response: String, key: String): String = call(JSONObject().put("cmd", "warpparse").put("content", response).put("key", key))

    fun udpPing(nodes: String, timeoutMs: Int): Map<String, Int> = runCatching {
        val o = JSONObject(call(JSONObject().put("cmd", "udpping").put("nodes", nodes).put("timeout", timeoutMs), 20))
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())
}

enum class VpnStatus { Off, Connecting, On, Error }

/** Подключение: sing-box (и Xray, если нужен) отдельными процессами, системный прокси или TUN. */
object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _status = MutableStateFlow(VpnStatus.Off)
    val status = _status.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt = _connectedAt.asStateFlow()
    /** Сервер, который сейчас выбрал автопилот */
    private val _autoNow = MutableStateFlow<String?>(null)
    val autoNow = _autoNow.asStateFlow()
    private val _speed = MutableStateFlow(0L to 0L)
    val speed = _speed.asStateFlow()

    private var singbox: Process? = null
    private var xray: Process? = null
    private var proxySet = false
    private var watch: Job? = null
    @Volatile private var failCount = 0

    val running get() = _status.value == VpnStatus.On || _status.value == VpnStatus.Connecting

    fun toggle() {
        if (running) disconnect() else connect()
    }

    fun connect() = scope.launch {
        lock.withLock {
            if (running) return@withLock
            _error.value = null
            _status.value = VpnStatus.Connecting
            try {
                start()
                _status.value = VpnStatus.On
                _connectedAt.value = System.currentTimeMillis()
                startWatch()
                // Режим по замерам без данных: подбираем серверы уже через VPN и применяем
                val mode = Store.value.autoMode
                if (mode.measured && Store.value.selected == "auto" && AutoModes.allowed(mode, Subscription.state.value.nodes).isEmpty()) {
                    scope.launch {
                        AutoModes.measure(mode)
                        if (AutoModes.allowed(mode, Subscription.state.value.nodes).isNotEmpty()) reload()
                    }
                }
            } catch (e: Exception) {
                stopLocked()
                _error.value = e.message ?: "Не удалось подключиться"
                _status.value = VpnStatus.Error
            }
        }
    }

    fun disconnect() = scope.launch {
        lock.withLock {
            stopLocked()
            _status.value = VpnStatus.Off
        }
    }

    /** Перезапуск с новыми настройками (если подключено). */
    fun reload() = scope.launch {
        if (!running) return@launch
        lock.withLock {
            stopLocked()
            _status.value = VpnStatus.Connecting
            try {
                start()
                _status.value = VpnStatus.On
                startWatch()
            } catch (e: Exception) {
                stopLocked()
                _error.value = e.message
                _status.value = VpnStatus.Error
            }
        }
    }

    /** Синхронная остановка при выходе из программы. */
    fun shutdown() {
        runCatching { if (proxySet) WinSys.clearProxy() }
        proxySet = false
        singbox?.destroyForcibly(); xray?.destroyForcibly()
        singbox = null; xray = null
    }

    private fun options(s: Settings, sub: SubState): String {
        val nodes = sub.nodes
        val o = JSONObject()
            .put("mode", s.routeMode.id)
            .put("services", JSONArray(s.services.toList()))
            .put("kids", s.kids)
            .put("userRules", JSONArray(s.userRules.map { JSONObject().put("value", it.value).put("action", it.action) }))
            .put("fakeDns", s.fakeDns)
            .put("domainStrategy", s.domainStrategy)
            .put("rulesDir", Rules.dirForCore().absolutePath)
            .put("dns", DnsAuto.effective(s).id)
            .put("customLists", JSONArray(s.customLists.filter { it.enabled && it.items.isNotEmpty() }.map {
                JSONObject().put("name", it.name).put("items", JSONArray(it.items))
            }))
            .put("customDomains", JSONArray(s.customDomains))
            .put("directDomains", JSONArray(s.directDomains))
            .put("directSuffixes", JSONArray(DirectCountries.suffixes(s.directCountries)))
            .put("adBlock", s.adBlock)
            .put("profile", s.autoMode.profile)
            .put("core", s.core.id)
            .put("clashApi", Store.clashPort)
            .put("xrayPort", Store.xrayPort)
            .put("xrayAuth", Store.xrayAuth)
            .put("inbound", s.connectType.id)
            .put("proxyPort", Store.proxyPort)
            .put("logFile", Paths.log.absolutePath)
            .put("ipv6", false)
            .put("directProcesses", JSONArray((listOf("APEX VPN.exe", "apexcli.exe") + s.excludeApps).distinct()))
            .put("customDns", s.customDns)
        if (s.warp && s.warpConfig.isNotBlank()) runCatching { o.put("warp", JSONObject(s.warpConfig)) }
        o.put("probePort", Store.probePort)
        if (s.pingUrl.isNotBlank()) o.put("urlTest", s.pingUrl.trim())
        // Режим автопилота по замерам: только подходящие серверы, лучшие — первыми
        val allowed = AutoModes.allowed(s.autoMode, nodes)
        if (WhiteList.active && s.autoWhitelist) {
            // Белые списки у провайдера: автопилот только по серверам «LTE-обход»
            o.put("autoExclude", JSONArray(nodes.filter { !ServerInfo.isLte(it.name) }.map { it.tag }))
        } else if (allowed.isNotEmpty()) {
            o.put("autoExclude", JSONArray(nodes.map { it.tag }.filter { it !in allowed }))
            o.put("autoOrder", JSONArray(allowed))
        } else {
            if (s.skipSpecial) o.put("autoExclude", JSONArray(nodes.filter { ServerInfo.isSpecial(it.name) }.map { it.tag }))
            o.put("autoOrder", JSONArray(Store.lastPings().entries.filter { it.value > 0 }.sortedBy { it.value }.map { it.key }))
        }
        return o.toString()
    }

    private suspend fun start() {
        val s = Store.value
        val sub = Subscription.state.value
        check(sub.nodes.isNotEmpty()) { "Сначала добавьте подписку" }
        if (s.connectType == ConnectType.Tun) check(WinSys.isAdmin()) { "Для TUN нужны права администратора — перезапустите APEX VPN от администратора или выберите «Системный прокси»" }
        if (s.autoWhitelist) runCatching { WhiteList.check() }
        val sb = Paths.exe("sing-box.exe")
        check(sb.exists()) { "Не найден sing-box.exe — переустановите APEX VPN" }

        val (sbCfg, xrCfg) = withContext(Dispatchers.IO) { CoreCli.build(sub.raw, s.selected, options(s, sub)) }
        val run = Paths.run
        runCatching { Paths.log.delete() }
        File(run, "singbox.json").writeText(sbCfg)

        if (xrCfg.isNotBlank()) {
            val xr = Paths.exe("xray.exe")
            check(xr.exists()) { "Не найден xray.exe — переустановите APEX VPN" }
            File(run, "xray.json").writeText(xrCfg)
            xray = ProcessBuilder(xr.absolutePath, "run", "-c", File(run, "xray.json").absolutePath)
                .directory(run).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File(run, "xray.log")))
                .apply { environment()["XRAY_LOCATION_ASSET"] = run.absolutePath }
                .start()
        }
        singbox = ProcessBuilder(sb.absolutePath, "run", "-c", File(run, "singbox.json").absolutePath, "-D", run.absolutePath, "--disable-color")
            .directory(run).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.to(File(run, "singbox.log")))
            .start()

        // Ждём, пока ядро поднимет API (значит, запустилось)
        val until = System.currentTimeMillis() + 15_000
        while (true) {
            val p = singbox ?: error("Ядро остановлено")
            if (!p.isAlive) error("Ядро не запустилось: " + lastLog())
            if (Clash.ok()) break
            if (System.currentTimeMillis() > until) error("Ядро не отвечает: " + lastLog())
            delay(250)
        }
        if (s.connectType == ConnectType.Proxy) {
            WinSys.setProxy("127.0.0.1:${Store.proxyPort}")
            proxySet = true
        }
    }

    private fun lastLog(): String {
        val lines = runCatching { File(Paths.run, "singbox.log").readLines() }.getOrDefault(emptyList()) +
            runCatching { Paths.log.readLines() }.getOrDefault(emptyList())
        return lines.filter { "ERROR" in it || "FATAL" in it || "error" in it }.takeLast(2).joinToString(" ").take(300).ifBlank { "нет подробностей" }
    }

    private fun stopLocked() {
        watch?.cancel(); watch = null
        if (proxySet) runCatching { WinSys.clearProxy() }
        proxySet = false
        singbox?.let { it.destroy(); if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly() }
        xray?.let { it.destroy(); if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly() }
        singbox = null; xray = null
        _connectedAt.value = 0
        _autoNow.value = null
        _speed.value = 0L to 0L
    }

    private fun startWatch() {
        watch?.cancel()
        watch = scope.launch {
            var lastUp = -1L; var lastDown = -1L; var tick = 0
            while (isActive) {
                delay(1000); tick++
                val p = singbox
                if (p == null || !p.isAlive) {
                    lock.withLock { stopLocked() }
                    _error.value = "Ядро остановилось: " + lastLog()
                    _status.value = VpnStatus.Error
                    break
                }
                // Скорость: общий счётчик трафика ядра
                Clash.traffic()?.let { (up, down) ->
                    if (lastUp >= 0) _speed.value = (up - lastUp).coerceAtLeast(0) to (down - lastDown).coerceAtLeast(0)
                    lastUp = up; lastDown = down
                }
                if (tick % 5 == 1) _autoNow.value = Clash.autoNow()
                // Таймер отключения
                if (AutoOff.due()) { AutoOff.clear(); disconnect(); break }
                // Автопереключение: выбранный вручную сервер перестал отвечать — на автопилот
                val sel = Store.value.selected
                if (Store.value.failover && sel != "auto" && tick % 20 == 0) {
                    if (Clash.delay(sel, 5000) < 0) failCount++ else failCount = 0
                    if (failCount >= 3) {
                        failCount = 0
                        _error.value = "Сервер перестал отвечать — включён автопилот"
                        select("auto")
                    }
                }
                if (tick % 2 == 0) ConnLog.poll()
                // Автообновление подписки и баз сайтов, пока подключено
                if (tick % 300 == 0 && Subscription.needsRefresh()) {
                    runCatching { Subscription.refresh() }.onSuccess { reload() }
                }
                if (tick % 600 == 0 && Rules.due() && Rules.update()) reload()
            }
        }
    }

    /** Сменить сервер на лету (через API ядра), без переподключения. */
    fun select(tag: String) {
        Store.update { copy(selected = tag) }
        if (_status.value == VpnStatus.On) scope.launch {
            if (!Clash.select(tag)) reload()
            _autoNow.value = Clash.autoNow()
        }
    }
}

/** Локальный API ядра (Clash API): выбор сервера, задержка, трафик. */
object Clash {
    private fun req(method: String, path: String, body: String? = null, timeout: Int = 1500): Pair<Int, String> = runCatching {
        val c = URI("http://127.0.0.1:${Store.clashPort}$path").toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("Authorization", "Bearer ${Store.xrayAuth}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        try {
            val code = c.responseCode
            code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty())
        } finally { c.disconnect() }
    }.getOrDefault(-1 to "")

    fun ok() = req("GET", "/version", timeout = 700).first == 200

    fun select(tag: String) = req("PUT", "/proxies/proxy", JSONObject().put("name", tag).toString()).first in 200..299

    fun autoNow(): String? = runCatching {
        JSONObject(req("GET", "/proxies/auto").second).optString("now").ifBlank { null }
    }.getOrNull()

    fun connections(): JSONObject? = runCatching { JSONObject(req("GET", "/connections").second) }.getOrNull()

    /** Суммарно отдано/получено, байт. */
    fun traffic(): Pair<Long, Long>? = runCatching {
        val o = JSONObject(req("GET", "/connections").second)
        o.getLong("uploadTotal") to o.getLong("downloadTotal")
    }.getOrNull()

    /** Задержка через сервер (как «Проверить пинг» в Happ), мс или -1. */
    fun delay(tag: String, timeout: Int = 5000): Int = runCatching {
        val url = URLEncoder.encode("https://www.gstatic.com/generate_204", "UTF-8")
        val (code, body) = req("GET", "/proxies/${URLEncoder.encode(tag, "UTF-8")}/delay?timeout=$timeout&url=$url", timeout = timeout + 1000)
        if (code == 200) JSONObject(body).optInt("delay", -1) else -1
    }.getOrDefault(-1)
}

/** Пинг серверов: TCP до сервера; для UDP-протоколов (Hysteria 2, TUIC) — через ядро. */
object Pinger {
    private val _pings = MutableStateFlow(Store.lastPings())
    val pings = _pings.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    suspend fun pingAll() = withContext(Dispatchers.IO) {
        if (_busy.value) return@withContext
        _busy.value = true
        try {
            val sub = Subscription.state.value
            val res = java.util.concurrent.ConcurrentHashMap<String, Int>()
            kotlinx.coroutines.coroutineScope {
                val gate = kotlinx.coroutines.sync.Semaphore(16)
                sub.nodes.filter { !it.udp }.map { n ->
                    launch { gate.acquire(); try { res[n.tag] = measure(n) } finally { gate.release() } }
                }
            }
            if (sub.nodes.any { it.udp }) res.putAll(CoreCli.udpPing(sub.raw, 3000))
            _pings.value = res.toMap()
            Store.savePings(res)
        } finally {
            _busy.value = false
        }
    }

    suspend fun pingOne(n: Node) = withContext(Dispatchers.IO) {
        val ms = if (n.udp) CoreCli.udpPing(JSONArray(Subscription.state.value.raw).let { a ->
            JSONArray((0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("tag") == n.tag })
        }.toString(), 3000)[n.tag] ?: -1 else tcp(n.server, n.port)
        _pings.value = _pings.value + (n.tag to ms)
    }

    /** Время TCP-соединения с сервером (лучшее из двух), мс или -1. */
    /** Пинг по выбранному в «Настройках пинга» способу (через сервер — только при включённом VPN). */
    private fun measure(n: Node): Int {
        val s = Store.value
        return when {
            s.pingType == "proxy" && Engine.status.value == VpnStatus.On -> Clash.delay(n.tag, s.pingTimeout * 1000)
            s.pingType == "icmp" -> runCatching {
                val a = java.net.InetAddress.getByName(n.server)
                val t = System.nanoTime()
                if (a.isReachable(s.pingTimeout * 1000)) ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1) else -1
            }.getOrDefault(-1)
            else -> tcp(n.server, n.port)
        }
    }

    private fun tcp(host: String, port: Int): Int {
        var best = -1
        repeat(2) {
            runCatching {
                Socket().use { s ->
                    val t = System.nanoTime()
                    s.connect(InetSocketAddress(host, port), (Store.value.pingTimeout * 1000).coerceAtMost(5000))
                    val ms = ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
                    if (best < 0 || ms < best) best = ms
                }
            }
        }
        return best
    }
}
