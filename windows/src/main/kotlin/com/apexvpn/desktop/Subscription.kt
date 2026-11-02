package com.apexvpn.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

data class Node(
    val tag: String,
    val name: String,
    val category: String,
    val detail: String,
    val server: String,
    val port: Int,
    val udp: Boolean,
    val ads: Boolean,
) {
    val title get() = ServerInfo.title(name)
    val country get() = ServerInfo.countryCode(name)
}

data class SubState(
    val nodes: List<Node> = emptyList(),
    /** Узлы в формате ядра (для сборки конфига) */
    val raw: String = "[]",
    val title: String = "",
    val used: Long = 0,
    val total: Long = 0,
    val expireAt: Long = 0,
    val updatedAt: Long = 0,
    val announce: String = "",
)

object ServerInfo {
    private val lteWords = listOf("lte", "бс", "обход", "mobile", "моб", "4g", "5g")

    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF

    fun flag(name: String): String? {
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) if (isRegional(cps[i]) && isRegional(cps[i + 1])) return String(cps, i, 2)
        return null
    }

    /** Windows не рисует эмодзи-флаги, поэтому показываем код страны: «NL», «DE». */
    fun countryCode(name: String): String? {
        val f = flag(name) ?: return null
        val cps = f.codePoints().toArray()
        return cps.joinToString("") { ('A' + (it - 0x1F1E6)).toString() }
    }

    fun title(name: String): String {
        val f = flag(name)
        val s = (if (f != null) name.replace(f, "") else name).trim(' ', '|', '-', '·', '•', '_')
        return s.ifBlank { name }
    }

    fun isLte(name: String): Boolean {
        val low = name.lowercase()
        return lteWords.any { w -> Regex("(^|[^\\p{L}])${Regex.escape(w)}").containsMatchIn(low) }
    }

    fun isRussia(name: String): Boolean {
        val low = name.lowercase()
        return flag(name) == "🇷🇺" || "россия" in low || "russia" in low || "москва" in low
    }

    fun isSpecial(name: String) = isLte(name) || isRussia(name)
}

object Subscription {
    const val PREFIX = "https://sub.knownhosts.ru/"
    private val file get() = File(Paths.data, "subscription.json")

    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    fun validate(raw: String): String {
        val url = raw.trim()
        require(url.startsWith(PREFIX)) { "APEX VPN работает только с подпиской $PREFIX…" }
        return url
    }

    fun needsRefresh(): Boolean {
        val s = Store.value
        if (s.subscriptionUrl.isBlank()) return false
        return System.currentTimeMillis() - _state.value.updatedAt > s.refreshHours * 3_600_000L
    }

    suspend fun refresh(url: String = Store.value.subscriptionUrl) = withContext(Dispatchers.IO) {
        val u = validate(url)
        val conn = (URI(u).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APEX VPN/Windows ${AppInfo.VERSION}")
            setRequestProperty("Accept", "*/*")
            // Заголовки устройства — их ждут панели с ограничением числа устройств (HWID)
            setRequestProperty("x-hwid", Store.hwid)
            setRequestProperty("x-device-os", "Windows")
            setRequestProperty("x-ver-os", System.getProperty("os.version") ?: "")
            setRequestProperty("x-device-model", runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("PC"))
        }
        try {
            val code = conn.responseCode
            if (code == 404) error("Подписка не найдена — проверьте ссылку")
            if (code !in 200..299) error("Сервер подписки ответил $code")
            val body = conn.inputStream.bufferedReader().readText()
            val parsed = JSONObject(CoreCli.parse(body))
            val nodes = parsed.optJSONArray("nodes") ?: JSONArray()
            if (nodes.length() == 0) error("В подписке нет серверов")
            val meta = parsed.optJSONObject("meta")
            fun header(n: String): String? = conn.getHeaderField(n)?.takeIf { it.isNotBlank() } ?: meta?.optString(n)?.takeIf { it.isNotBlank() }
            val info = parseUserInfo(header("subscription-userinfo"))
            val st = SubState(
                nodes = toNodes(nodes),
                raw = nodes.toString(),
                title = decodeTitle(header("profile-title")),
                used = (info["upload"] ?: 0) + (info["download"] ?: 0),
                total = info["total"] ?: 0,
                expireAt = info["expire"] ?: 0,
                updatedAt = System.currentTimeMillis(),
                announce = decodeTitle(header("announce")).takeIf { it != "0" }.orEmpty(),
            )
            _state.value = st
            save(st)
            if (Store.value.subscriptionUrl != u) Store.update { copy(subscriptionUrl = u) }
            if (st.nodes.none { it.tag == Store.value.selected }) Store.update { copy(selected = "auto") }
        } finally {
            conn.disconnect()
        }
    }

    fun remove() {
        _state.value = SubState()
        runCatching { file.delete() }
        Store.update { copy(subscriptionUrl = "", selected = "auto") }
    }

    private fun decodeTitle(v: String?): String {
        if (v.isNullOrBlank()) return ""
        if (v.startsWith("base64:")) return runCatching { String(java.util.Base64.getDecoder().decode(v.removePrefix("base64:"))) }.getOrDefault("")
        return v
    }

    private fun parseUserInfo(v: String?): Map<String, Long> =
        v.orEmpty().split(';').mapNotNull { p ->
            val kv = p.trim().split('=', limit = 2)
            if (kv.size == 2) kv[0].trim() to (kv[1].trim().toDoubleOrNull()?.toLong() ?: return@mapNotNull null) else null
        }.toMap()

    private fun toNodes(a: JSONArray): List<Node> = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        Node(
            tag = o.optString("tag"), name = o.optString("name"), category = o.optString("category", o.optString("type")),
            detail = o.optString("detail"), server = o.optString("server"), port = o.optInt("port"),
            udp = o.optBoolean("udp"), ads = o.optBoolean("ads"),
        )
    }.filter { it.tag.isNotEmpty() }

    private fun save(s: SubState) {
        val o = JSONObject().put("raw", s.raw).put("title", s.title).put("used", s.used).put("total", s.total)
            .put("expireAt", s.expireAt).put("updatedAt", s.updatedAt).put("announce", s.announce)
        runCatching { file.writeText(o.toString()) }
    }

    private fun load(): SubState = runCatching {
        val o = JSONObject(File(Paths.data, "subscription.json").readText())
        val raw = o.getString("raw")
        SubState(
            nodes = toNodes(JSONArray(raw)), raw = raw, title = o.optString("title"), used = o.optLong("used"),
            total = o.optLong("total"), expireAt = o.optLong("expireAt"), updatedAt = o.optLong("updatedAt"),
            announce = o.optString("announce"),
        )
    }.getOrDefault(SubState())
}
