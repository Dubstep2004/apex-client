package com.apexvpn.client.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 604. Журнал соединений: какие сайты шли через VPN, какие напрямую, что заблокировано.
 * Только на телефоне (файл в памяти приложения), последние 500 записей, старше суток удаляются.
 * Включается вручную в «Журнале соединений»; кнопка «Очистить» стирает всё сразу.
 */
object ConnLog {
    enum class Route { Vpn, Direct, Blocked }

    data class Entry(val time: Long, val host: String, val route: Route, val server: String, val app: String)

    private const val MAX = 500
    private const val DAY = 24 * 3600 * 1000L

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries = _entries.asStateFlow()
    private val known = HashSet<String>()
    private var file: File? = null
    private var dirty = 0

    fun init(context: Context) {
        file = File(context.filesDir, "conn_log.json")
        _entries.value = runCatching {
            val a = JSONArray(file!!.readText())
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let {
                    Entry(it.optLong("t"), it.optString("h"), Route.entries.getOrElse(it.optInt("r")) { Route.Vpn }, it.optString("s"), it.optString("a"))
                }
            }
        }.getOrDefault(emptyList()).filter { System.currentTimeMillis() - it.time < DAY }
    }

    /** Новое соединение из списка ядра (вызывается из опроса трафика). */
    @Synchronized
    fun onConnection(o: JSONObject) {
        if (!AppSettings.value.connLog) return
        val id = o.optString("id")
        if (id.isEmpty() || !known.add(id)) return
        val m = o.optJSONObject("metadata") ?: return
        // Служебные соединения самого ядра: замеры (probe-in) и выход Xray в сеть (apex-dial)
        val inbound = m.optString("type")
        if ("probe-in" in inbound || "apex-dial" in inbound) return
        val host = m.optString("host").ifEmpty { m.optString("destinationIP") }
        if (host.isEmpty() || host == "127.0.0.1") return
        val chains = o.optJSONArray("chains")
        val first = chains?.optString(0).orEmpty()
        val route = when (first) {
            "direct" -> Route.Direct
            "block" -> Route.Blocked
            else -> Route.Vpn
        }
        val server = if (route == Route.Vpn) {
            Subscription.state.value.nodes.find { it.tag == first }?.let { ServerInfo.title(it.name) } ?: ""
        } else ""
        val app = m.optString("processPath").substringBefore(" (").trim().takeIf { it.isNotEmpty() && !it.all(Char::isDigit) }.orEmpty()
        val now = System.currentTimeMillis()
        _entries.value = (listOf(Entry(now, host, route, server, app)) + _entries.value)
            .filter { now - it.time < DAY }
            .take(MAX)
        if (++dirty >= 10) save()
    }

    /** Ядро забыло закрытые соединения — и мы их id, чтобы набор не рос бесконечно. */
    @Synchronized
    fun retain(ids: Set<String>) {
        known.retainAll(ids)
    }

    fun save() {
        dirty = 0
        val f = file ?: return
        if (!AppSettings.value.connLog) return
        runCatching {
            val a = JSONArray()
            _entries.value.forEach { a.put(JSONObject().put("t", it.time).put("h", it.host).put("r", it.route.ordinal).put("s", it.server).put("a", it.app)) }
            f.writeText(a.toString())
        }
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        known.clear()
        runCatching { file?.delete() }
    }
}
