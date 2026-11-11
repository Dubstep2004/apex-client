package com.apexvpn.client.data

import android.content.Context
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 220. «Видео — максимальная скорость»: через работающее ядро по очереди включаем
 * несколько серверов с лучшим пингом, качаем по несколько мегабайт и закрепляем самый быстрый.
 * Без VPN не работает: скорость меряется именно через сервер.
 */
object FastestServer {
    data class Probe(val tag: String, val title: String, val flag: String?, val pingMs: Int, val mbps: Double = -1.0)

    data class State(
        val running: Boolean = false,
        val step: String = "",
        val probes: List<Probe> = emptyList(),
        val best: Probe? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    @Volatile private var stopRequested = false

    /** Остановить подбор (в том числе запущенный автоматически после подключения). */
    fun cancel() {
        stopRequested = true
    }

    private const val CANDIDATES = 5
    private const val BYTES = 6_000_000
    private const val LIMIT_NS = 5_000_000_000L

    /** Подобрать и закрепить самый быстрый сервер. Возвращает его или null. */
    suspend fun run(context: Context): Probe? {
        if (_state.value.running) return null
        if (VpnState.status.value != VpnStatus.Started) {
            _state.value = State(error = "Подключите VPN — скорость меряется через сервер")
            return null
        }
        val nodes = Subscription.state.value.nodes
        if (nodes.isEmpty()) {
            _state.value = State(error = "В подписке нет серверов")
            return null
        }
        val before = Prefs.selectedTag
        var switched = false
        stopRequested = false
        _state.value = State(running = true, step = "Пинг всех серверов…")
        try {
            val pings = Pinger.pingAll(nodes, type = "tcp")
            // Как и автопилот: без России и обхода LTE (они «быстрые», но сайты через них не открываются)
            val skip = AppSettings.value.autoSkipSpecial
            // Белые списки у оператора: работает только LTE-обход — из него и выбираем
            val wl = WhiteList.active && AppSettings.value.autoWhitelist
            val pool = (if (wl) nodes.filter { ServerInfo.isLte(it.name) } else nodes.filter { !skip || !ServerInfo.isSpecial(it.name) }).ifEmpty { nodes }
            val top = pool
                .mapNotNull { n -> pings[n.tag]?.takeIf { it > 0 }?.let { n to it } }
                .sortedBy { it.second }
                .distinctBy { ServerInfo.title(it.first.name) } // разные страны/серверы, а не протоколы одного
                .take(CANDIDATES)
            if (top.isEmpty()) error("Ни один сервер не ответил")
            val probes = top.map { (n, p) -> Probe(n.tag, ServerInfo.title(n.name), ServerInfo.flag(n.name), p) }.toMutableList()
            _state.value = _state.value.copy(probes = probes.toList())
            for (i in probes.indices) {
                if (stopRequested) error("Подбор остановлен")
                val pr = probes[i]
                _state.value = _state.value.copy(step = "Скорость: ${pr.title} (${i + 1} из ${probes.size})")
                switched = true
                val mbps = runCatching {
                    switchTo(pr.tag)
                    download()
                }.getOrDefault(0.0)
                probes[i] = pr.copy(mbps = mbps)
                _state.value = _state.value.copy(probes = probes.toList())
            }
            if (stopRequested) error("Подбор остановлен")
            val best = probes.filter { it.mbps > 0 }.maxByOrNull { it.mbps } ?: error("Не удалось измерить скорость")
            // Закрепляем: выбор сохраняется и ядро перезагружается с этим сервером
            Subscription.select(best.tag)
            switched = false
            ApexVpnService.reload(context)
            _state.value = State(probes = probes.sortedByDescending { it.mbps }, best = best)
            return best
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = State()
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(running = false, step = "", error = e.message ?: "Ошибка подбора")
            return null
        } finally {
            if (switched) runCatching { switchTo(before) }
        }
    }

    fun reset() {
        if (!_state.value.running) _state.value = State()
    }

    /**
     * Режим автопилота «Скорость»: скорость загрузки через каждый из [candidates] (Мбит/с),
     * без закрепления сервера — после замера ядро возвращается к прежнему выбору.
     */
    suspend fun speeds(candidates: List<Node>, bytes: Int = 3_000_000): Map<String, Double> {
        if (_state.value.running || candidates.isEmpty()) return emptyMap()
        if (VpnState.status.value != VpnStatus.Started) return emptyMap()
        val before = Prefs.selectedTag
        val out = mutableMapOf<String, Double>()
        var switched = false
        try {
            for (n in candidates) {
                switched = true
                val mbps = runCatching {
                    switchTo(n.tag)
                    download(bytes)
                }.getOrDefault(0.0)
                if (mbps > 0) out[n.tag] = mbps
            }
        } finally {
            if (switched) runCatching { switchTo(before) }
        }
        return out
    }

    /** Переключить группу «proxy» в работающем ядре без перезапуска (Clash API). */
    private suspend fun switchTo(tag: String) = withContext(Dispatchers.IO) {
        val c = URL("http://127.0.0.1:${Prefs.clashApiPort}/proxies/proxy").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"
            c.connectTimeout = 2000
            c.readTimeout = 3000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("name", tag).toString().toByteArray()) }
            check(c.responseCode in 200..299) { "ядро не переключило сервер" }
        } finally {
            c.disconnect()
        }
    }

    /** До [BYTES] байт или [LIMIT_NS]; новое соединение каждый раз, чтобы не ехать через старый сервер. */
    private suspend fun download(bytes: Int = BYTES): Double = withContext(Dispatchers.IO) {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.useCaches = false
        c.setRequestProperty("Connection", "close")
        c.setRequestProperty("Cache-Control", "no-cache")
        var bytes = 0L
        val start = System.nanoTime()
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    bytes += n
                    if (System.nanoTime() - start > LIMIT_NS) break
                }
            }
        } finally {
            c.disconnect()
        }
        val sec = (System.nanoTime() - start) / 1e9
        if (sec <= 0) 0.0 else bytes * 8.0 / sec / 1_000_000.0
    }
}

/** 229. История тестов скорости: последние 15 замеров на телефоне. */
object SpeedHistory {
    data class Entry(val time: Long, val pingMs: Int, val down: Double, val up: Double, val server: String)

    private const val MAX = 15
    private fun sp(context: Context) = context.getSharedPreferences("speed_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun load(context: Context) {
        _entries.value = runCatching {
            val a = JSONArray(sp(context).getString("list", "[]"))
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let {
                    Entry(it.optLong("t"), it.optInt("p", -1), it.optDouble("d", -1.0), it.optDouble("u", -1.0), it.optString("s"))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, e: Entry) {
        val list = (listOf(e) + _entries.value).take(MAX)
        _entries.value = list
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("t", it.time).put("p", it.pingMs).put("d", it.down).put("u", it.up).put("s", it.server))
        }
        sp(context).edit().putString("list", a.toString()).apply()
    }

    fun clear(context: Context) {
        _entries.value = emptyList()
        sp(context).edit().remove("list").apply()
    }
}
