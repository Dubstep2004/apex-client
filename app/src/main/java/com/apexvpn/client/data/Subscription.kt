package com.apexvpn.client.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.apexvpn.client.BuildConfig
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Node(
    val tag: String,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    /** Категория протокола: VLESS, Hysteria 2, ShadowTLS… */
    val category: String = Protocols.category(type),
    /** Описание подключения: «Reality · Vision · TCP» */
    val detail: String = "",
    /** Протокол поверх UDP/QUIC — пинг по TCP не измерить */
    val udp: Boolean = type in Protocols.udpTypes,
    /** Подпись под названием (serverDescription, как в Happ) */
    val description: String = "",
    /** Пометка «реклама на YouTube» — автопилот такие не выбирает */
    val ads: Boolean = false,
    /** Сервер работает только через ядро Xray (XHTTP, mKCP…) */
    val xrayOnly: Boolean = false,
)

/** Категории протоколов (то же, что в core/apexcore/protocols.go). */
object Protocols {
    private val names = mapOf(
        "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan", "shadowsocks" to "Shadowsocks",
        "hysteria2" to "Hysteria 2", "hysteria" to "Hysteria", "tuic" to "TUIC", "wireguard" to "WireGuard",
        "anytls" to "AnyTLS", "naive" to "NaiveProxy", "socks" to "SOCKS", "http" to "HTTP", "ssh" to "SSH",
        "shadowtls" to "ShadowTLS",
    )
    private val short = mapOf("Shadowsocks" to "SS", "Hysteria 2" to "Hy2", "Hysteria" to "Hy", "WireGuard" to "WG", "NaiveProxy" to "Naive")
    val udpTypes = setOf("hysteria2", "hysteria", "tuic", "wireguard")

    fun category(type: String) = names[type] ?: type.uppercase()
    fun shortName(category: String) = short[category] ?: category
}

data class SubscriptionState(
    val url: String = "",
    val title: String = "",
    val nodes: List<Node> = emptyList(),
    val selectedTag: String = AUTO_TAG,
    val used: Long = -1,
    val total: Long = -1,
    val expireAt: Long = -1,
    val lastUpdated: Long = 0,
    /** Объявление от сервиса (заголовок announce) */
    val announce: String = "",
    /** 434. Картинка (https) и кнопка «текст → ссылка» под объявлением */
    val announceImage: String = "",
    val announceButton: String = "",
    val announceUrl: String = "",
    /** Поддержка (support-url) и сайт (profile-web-page-url) */
    val supportUrl: String = "",
    val webPageUrl: String = "",
    val updateHours: Int = 12,
)

const val AUTO_TAG = "auto"

/** Загрузка и хранение подписки. Разрешены только адреса из [SubHosts] (файл на GitHub + вшитый при сборке). */
object Subscription {
    /** Протоколы, которые приложение не показывает (объявлено до _state: load() его использует) */
    private val hiddenTypes = setOf("shadowsocks")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    private fun load(): SubscriptionState = SubscriptionState(
        url = Prefs.subscriptionUrl,
        title = Prefs.profileTitle,
        nodes = parseNodes(Prefs.nodesJson).filter { it.type !in hiddenTypes },
        selectedTag = Prefs.selectedTag,
        used = Prefs.trafficUsed,
        total = Prefs.trafficTotal,
        expireAt = Prefs.expireAt,
        lastUpdated = Prefs.lastUpdated,
        announce = Prefs.announce,
        announceImage = Prefs.announceImage,
        announceButton = Prefs.announceButton,
        announceUrl = Prefs.announceUrl,
        supportUrl = Prefs.supportUrl,
        webPageUrl = Prefs.webPageUrl,
        updateHours = Prefs.updateIntervalHours,
    )

    /**
     * Проверяет ссылку. Возвращает нормализованную ссылку или бросает
     * IllegalArgumentException с понятным текстом.
     */
    fun validate(raw: String): String {
        // Ссылка на старом адресе подписки — сразу на новый (тот же путь и токен)
        val text = SubHosts.rewrite(raw.trim())
        require(text.isNotEmpty()) { "Вставьте ссылку на подписку" }
        val uri = runCatching { Uri.parse(text) }.getOrNull()
        val host = uri?.host?.lowercase()
        require(SubHosts.isAllowed(host)) {
            "Поддерживается только ссылка https://${SubHosts.primary()}/…"
        }
        require(uri!!.scheme.equals("https", ignoreCase = true)) { "Ссылка должна начинаться с https://" }
        return text
    }

    /** Сохраняет новую ссылку и загружает серверы. */
    suspend fun setUrl(raw: String) {
        val url = validate(raw)
        // Новая подписка — старый выбор сервера к ней не относится
        if (url != Prefs.subscriptionUrl) Prefs.selectedTag = AUTO_TAG
        fetch(url)
        Prefs.subscriptionUrl = url
        _state.value = load()
    }

    /** Обновляет текущую подписку. */
    suspend fun refresh() {
        val url = Prefs.subscriptionUrl
        require(url.isNotEmpty()) { "Подписка не добавлена" }
        fetch(validate(url))
        _state.value = load()
        // 195. Серверов, которых больше нет в подписке, в избранном не держим
        // В избранном хранится название группы (ServerInfo.title), у старых версий — полное имя сервера
        val names = _state.value.nodes.flatMap { listOf(it.name, ServerInfo.title(it.name)) }.toSet()
        if (_state.value.nodes.isNotEmpty()) {
            val fav = AppSettings.value.favorites
            if (fav.any { it !in names }) AppSettings.update { copy(favorites = fav.intersect(names)) }
        }
    }

    fun needsRefresh(): Boolean {
        if (Prefs.subscriptionUrl.isEmpty()) return false
        val age = System.currentTimeMillis() - Prefs.lastUpdated
        return age > Prefs.updateIntervalHours * 3_600_000L
    }

    /** При открытии приложения подписку обновляем чаще — чтобы объявления из панели доходили быстро. */
    fun needsRefreshOnLaunch(): Boolean =
        Prefs.subscriptionUrl.isNotEmpty() && System.currentTimeMillis() - Prefs.lastUpdated > 30 * 60_000L

    fun select(tag: String) {
        Prefs.selectedTag = tag
        _state.value = _state.value.copy(selectedTag = tag)
    }

    fun remove() {
        Prefs.clearSubscription()
        _state.value = load()
    }

    /** Конфиги ядер: sing-box (всегда) и Xray (пусто, если не нужен). */
    fun buildConfigs(context: Context): Pair<String, String> {
        val nodes = Prefs.nodesJson
        require(parseNodes(nodes).isNotEmpty()) { "Нет серверов — обновите подписку" }
        val out = JSONObject(Apexcore.buildConfigs(nodes, Prefs.selectedTag, AppSettings.coreOptions(RulesUpdater.activeDir(context))))
        return out.getString("singbox") to out.optString("xray")
    }

    /**
     * Ищет ссылку подписки в тексте: QR-код, «Поделиться», apexvpn://import?url=…
     * Возвращает ссылку только на разрешённый хост.
     */
    fun extractLink(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val decoded = runCatching { Uri.decode(text) }.getOrDefault(text)
        val re = Regex("https://(" + (SubHosts.allowed() + SubHosts.movedFrom()).joinToString("|") { Regex.escape(it) } + ")/[^\\s\"'<>]*", RegexOption.IGNORE_CASE)
        return re.find(decoded)?.value?.trimEnd('.', ',', ')', ']')?.let { SubHosts.rewrite(it) }
    }

    /** Приложение представляется серверу только как «APEX VPN/версия» — по этой метке панель отдаёт ему подписку (название в приложении — APEX CLIENT). */
    private val userAgent = "APEX VPN/${BuildConfig.VERSION_NAME}"

    private suspend fun fetch(url: String) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        // До двух попыток: временный сбой сети или сервера
        repeat(2) {
            try {
                fetchWith(url, userAgent)
                return@withContext
            } catch (e: NotFound) {
                throw e // ссылка неверная — другие варианты не помогут
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Не удалось загрузить подписку")
    }

    private class NotFound(message: String) : Exception(message)

    private fun fetchWith(url: String, userAgent: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "*/*")
            // Заголовки устройства: их ждут панели с ограничением числа устройств (HWID)
            setRequestProperty("x-hwid", Prefs.deviceId)
            setRequestProperty("x-device-os", "Android")
            setRequestProperty("x-ver-os", android.os.Build.VERSION.RELEASE ?: "")
            setRequestProperty("x-device-model", android.os.Build.MODEL ?: "")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim()?.take(120)
                }.getOrNull().orEmpty()
                val suffix = if (detail.isNotEmpty() && detail.length < 120) ": $detail" else ""
                when (code) {
                    404 -> throw NotFound("Подписка не найдена — проверьте ссылку (код 404)")
                    401, 403 -> error("Подписка отключена или превышен лимит устройств (код $code)$suffix")
                    in 500..599 -> error("Сервер подписки временно недоступен (код $code). Попробуйте позже$suffix")
                    else -> error("Сервер подписки ответил кодом $code$suffix")
                }
            }
            // Защита от редиректа на чужой домен
            require(SubHosts.isAllowed(conn.url.host)) {
                "Подписка перенаправила на другой домен — отклонено"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            // Запоминаем выбранный сервер по сути (имя, протокол, адрес), а не по номеру:
            // после обновления порядок серверов в подписке может измениться
            val previous = parseNodes(Prefs.nodesJson).find { it.tag == Prefs.selectedTag }
            val parsed = JSONObject(Apexcore.parseSubscription(body))
            val all = parsed.getJSONArray("nodes")
            // Shadowsocks в приложении не используется
            val nodes = JSONArray()
            for (i in 0 until all.length()) {
                val o = all.getJSONObject(i)
                if (o.optString("type") !in hiddenTypes) nodes.put(o)
            }
            require(nodes.length() > 0) { "В подписке нет серверов, кроме Shadowsocks" }

            Prefs.nodesJson = nodes.toString()
            Prefs.lastUpdated = System.currentTimeMillis()
            Prefs.nodesVersion = BuildConfig.VERSION_CODE

            // Заголовки подписки; их же можно передать в теле строками «#имя: значение»
            val meta = parsed.optJSONObject("meta")
            fun header(name: String): String? =
                conn.getHeaderField(name)?.takeIf { it.isNotBlank() } ?: meta?.optString(name)?.takeIf { it.isNotBlank() }

            Prefs.profileTitle = decodeTitle(header("profile-title")) ?: ""
            // Интервал из панели больше не перезаписывает выбор в настройках (раньше из-за этого
            // «1/6 ч» в настройках молча превращались в 12 ч панели) — интервал задаёт пользователь
            Prefs.updateIntervalHours = AppSettings.value.refreshHours
            parseUserInfo(header("subscription-userinfo"))
            // Объявление из Remnawave (заголовок announce, как у Happ); «0» — убрать
            // 434. Картинка и кнопка: заголовки announce-image / announce-button / announce-url
            // или прямо в тексте: ![](https://…картинка) и [Текст кнопки](https://…)
            val rich = AnnounceParser.parse(decodeTitle(header("announce"))?.trim()?.takeIf { it != "0" } ?: "")
            Prefs.announce = rich.text.take(500)
            Prefs.announceImage = (header("announce-image")?.trim() ?: rich.image).takeIf { AnnounceParser.safeImage(it) } ?: ""
            Prefs.announceUrl = (header("announce-url")?.trim() ?: rich.url).takeIf { AnnounceParser.safeLink(it) } ?: ""
            Prefs.announceButton = if (Prefs.announceUrl.isEmpty()) "" else
                (decodeTitle(header("announce-button"))?.trim()?.takeIf { it.isNotEmpty() } ?: rich.button.ifBlank { "Подробнее" }).take(40)
            // Объявление может быть только картинкой или кнопкой — без текста
            if (Prefs.announce.isEmpty() && Prefs.announceImage.isEmpty() && Prefs.announceUrl.isEmpty()) Prefs.announceButton = ""
            Prefs.supportUrl = header("support-url")?.trim()?.takeIf { it.startsWith("http") || it.startsWith("tg:") } ?: ""
            Prefs.webPageUrl = header("profile-web-page-url")?.trim()?.takeIf { it.startsWith("http") } ?: ""

            // Тот же сервер под новым номером; если его больше нет — «Авто»
            if (Prefs.selectedTag != AUTO_TAG) {
                val fresh = parseNodes(Prefs.nodesJson)
                val same = previous?.let { p ->
                    fresh.find { it.name == p.name && it.type == p.type && it.server == p.server && it.port == p.port }
                        ?: fresh.find { it.name == p.name && it.type == p.type }
                        ?: fresh.find { it.name == p.name }
                }
                Prefs.selectedTag = same?.tag ?: AUTO_TAG
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeTitle(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (v.startsWith("base64:")) {
            return runCatching {
                String(Base64.decode(v.removePrefix("base64:"), Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull()
        }
        return v
    }

    private fun parseUserInfo(raw: String?) {
        if (raw.isNullOrBlank()) {
            Prefs.trafficUsed = -1; Prefs.trafficTotal = -1; Prefs.expireAt = -1
            return
        }
        val map = raw.split(";").mapNotNull {
            val (k, v) = it.split("=", limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k.trim().lowercase() to (v.trim().toDoubleOrNull()?.toLong() ?: return@mapNotNull null)
        }.toMap()
        val up = map["upload"] ?: 0
        val down = map["download"] ?: 0
        Prefs.trafficUsed = up + down
        Prefs.trafficTotal = map["total"] ?: -1
        Prefs.expireAt = map["expire"] ?: -1
    }

    private fun parseNodes(json: String): List<Node> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val type = o.optString("type")
            Node(
                tag = o.getString("tag"),
                name = o.optString("name"),
                type = type,
                server = o.optString("server"),
                port = o.optInt("port"),
                category = o.optString("category").ifEmpty { Protocols.category(type) },
                detail = o.optString("detail"),
                udp = o.optBoolean("udp", type in Protocols.udpTypes),
                description = o.optString("description"),
                ads = o.optBoolean("ads"),
                xrayOnly = o.isNull("outbound") || !o.has("outbound"),
            )
        }
    }.getOrDefault(emptyList())
}

/** Сервер в избранном: по названию группы (как в списке серверов) или по полному имени (старые версии). */
fun Node.isFavorite(favorites: Set<String>): Boolean = name in favorites || ServerInfo.title(name) in favorites
