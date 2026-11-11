package com.apexvpn.client.data

import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 3.18. Управление приложением из веб-админки (docs/admin) — файл docs/config/app.json на GitHub.
 * Без своего сервера и без новых версий: баннер для всех, статус серверов, ссылки,
 * обязательное обновление, отзыв версии, экстренное выключение функций.
 * Нет файла или он битый — всё работает как обычно.
 */
object AppConfig {
    private const val PATH = "docs/config/app.json"
    private val sources = listOf(
        "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/$PATH",
        "https://cdn.jsdelivr.net/gh/${BuildConfig.GITHUB_REPO}@main/$PATH",
    )

    /**
     * Баннер. 3.18.1: расписание (from/to — время в мс), устройства (phone / tv / watch),
     * страна оператора (RU…), версии, «предпросмотр» — только для телефонов с кодом.
     */
    data class Banner(
        val id: String, val text: String, val level: String, val url: String,
        val from: Long = 0, val to: Long = 0, val minCode: Int = 0, val maxCode: Int = 0,
        val devices: Set<String> = emptySet(), val countries: Set<String> = emptySet(), val preview: Boolean = false,
    )
    data class ServerState(val state: String, val note: String, val until: Long = 0, val preview: Boolean = false) // maintenance | slow
    /** Большое окно «Что нового» / объявление — показывается один раз. */
    data class Announce(
        val id: String, val title: String, val text: String, val image: String, val url: String, val button: String,
        val minCode: Int = 0, val maxCode: Int = 0, val preview: Boolean = false,
    )
    data class Config(
        val banners: List<Banner> = emptyList(),
        val servers: Map<String, ServerState> = emptyMap(),
        val recommended: String = "",
        val hidden: Set<String> = emptySet(),
        val support: String = "",
        val channel: String = "",
        val site: String = "",
        val requiredCode: Int = 0,
        val revoked: Set<Int> = emptySet(),
        val features: Map<String, Boolean> = emptyMap(),
        val announce: Announce? = null,
        val previewCode: String = "",
        val rev: Int = 0, // меняется, когда ввели код предпросмотра — чтобы экраны пересчитались
    ) {
        /** Функция включена (по умолчанию — да). */
        fun on(feature: String) = features[feature] != false
        /** На этом телефоне введён код предпросмотра — видны черновики из пульта. */
        val previewer get() = previewCode.isNotBlank() && Prefs.previewCode.trim() == previewCode.trim()
        // Сервер ищется по части названия без флага: «Германия» — все немецкие, «Обход №1» — один сервер
        private fun hit(title: String, key: String) = key.isNotBlank() && title.contains(key.trim(), ignoreCase = true)
        fun stateOf(title: String): ServerState? {
            val now = System.currentTimeMillis()
            return servers.entries.firstOrNull { (k, v) -> hit(title, k) && (v.until == 0L || now < v.until) && (!v.preview || previewer) }?.value
        }
        fun isHidden(title: String) = hidden.any { hit(title, it) }
        fun isRecommended(title: String) = hit(title, recommended)
        /** Сервер не берёт автопилот: на обслуживании или скрыт. */
        fun blocked(title: String) = isHidden(title) || stateOf(title)?.state == "maintenance"

        /** Баннер для этого устройства прямо сейчас (первый подходящий). */
        fun bannerFor(device: String, now: Long = System.currentTimeMillis()): Banner? = banners.firstOrNull { b ->
            val code = BuildConfig.VERSION_CODE
            (b.from == 0L || now >= b.from) && (b.to == 0L || now < b.to) &&
                (b.minCode == 0 || code >= b.minCode) && (b.maxCode == 0 || code <= b.maxCode) &&
                (b.devices.isEmpty() || device in b.devices) &&
                (b.countries.isEmpty() || country() in b.countries) &&
                (!b.preview || previewer)
        }

        /** Объявление для этой версии (ещё не показанное — см. Prefs.announceSeen). */
        fun announceNow(): Announce? = announce?.takeIf { a ->
            val code = BuildConfig.VERSION_CODE
            (a.minCode == 0 || code >= a.minCode) && (a.maxCode == 0 || code <= a.maxCode) && (!a.preview || previewer)
        }
    }

    /** Страна оператора связи (по сети, иначе по SIM, иначе по языку телефона): «RU», «KZ»… */
    fun country(): String = runCatching {
        val tm = com.apexvpn.client.ApexApp.instance.getSystemService(android.telephony.TelephonyManager::class.java)
        listOf(tm?.networkCountryIso, tm?.simCountryIso).firstOrNull { !it.isNullOrBlank() }?.uppercase()
    }.getOrNull() ?: Locale.getDefault().country.uppercase()

    /** Это телевизор? (для баннеров «только для Android TV») */
    fun isTv(): Boolean = runCatching {
        com.apexvpn.client.ApexApp.instance.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)

    private val _state = MutableStateFlow(parse(Prefs.appConfigJson) ?: Config())
    val state: StateFlow<Config> = _state.asStateFlow()
    val value get() = _state.value

    /** Пересчитать экраны (сменили код предпросмотра). */
    fun touch() { _state.value = _state.value.copy(rev = _state.value.rev + 1) }

    private fun strings(a: org.json.JSONArray?) = a?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.toSet() }.orEmpty()

    private fun parseBanner(b: JSONObject, i: Int): Banner? {
        val text = b.optString("text").trim().take(300)
        if (text.isEmpty()) return null
        // Старый формат: until = дата «до конца дня»
        val oldUntil = b.optString("until").let { d -> runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!.time + 24 * 3_600_000L }.getOrDefault(0L) }
        return Banner(
            id = b.optString("id").ifBlank { "b$i-" + text.hashCode() },
            text = text, level = b.optString("level", "info"), url = b.optString("url").trim().takeIf { it.startsWith("https://") }.orEmpty(),
            from = b.optLong("from", 0), to = b.optLong("to", 0).takeIf { it > 0 } ?: oldUntil,
            minCode = b.optInt("minCode", 0), maxCode = b.optInt("maxCode", 0),
            devices = strings(b.optJSONArray("devices")), countries = strings(b.optJSONArray("countries")).map { it.uppercase() }.toSet(),
            preview = b.optBoolean("preview", false),
        )
    }

    fun parse(text: String): Config? = runCatching {
        if (text.isBlank()) return null
        val o = JSONObject(text)
        val banners = buildList {
            o.optJSONArray("banners")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { parseBanner(it, i) }?.let(::add) }
            o.optJSONObject("banner")?.let { parseBanner(it, 99) }?.let(::add)
        }
        val srv = o.optJSONObject("servers")
        val status = srv?.optJSONObject("status")
        val servers = status?.keys()?.asSequence()?.mapNotNull { k ->
            status.optJSONObject(k)?.let { s -> k to ServerState(s.optString("state"), s.optString("note").take(120), s.optLong("until", 0), s.optBoolean("preview", false)) }
        }?.toMap().orEmpty()
        val links = o.optJSONObject("links")
        val upd = o.optJSONObject("update")
        val feats = o.optJSONObject("features")
        val an = o.optJSONObject("announce")?.takeIf { it.optString("title").isNotBlank() || it.optString("text").isNotBlank() }?.let { a ->
            Announce(
                id = a.optString("id").ifBlank { (a.optString("title") + a.optString("text")).hashCode().toString() },
                title = a.optString("title").take(80), text = a.optString("text").take(1500),
                image = a.optString("image").trim().takeIf { it.startsWith("https://") }.orEmpty(),
                url = a.optString("url").trim().takeIf { it.startsWith("https://") }.orEmpty(),
                button = a.optString("button").ifBlank { "Подробнее" }.take(30),
                minCode = a.optInt("minCode", 0), maxCode = a.optInt("maxCode", 0), preview = a.optBoolean("preview", false),
            )
        }
        Config(
            banners = banners,
            servers = servers,
            recommended = srv?.optString("recommended").orEmpty(),
            hidden = strings(srv?.optJSONArray("hidden")),
            support = links?.optString("support").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            channel = links?.optString("channel").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            site = links?.optString("site").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            requiredCode = upd?.optInt("required", 0) ?: 0,
            revoked = upd?.optJSONArray("revoked")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() }.orEmpty(),
            features = feats?.keys()?.asSequence()?.associate { it to feats.optBoolean(it, true) }.orEmpty(),
            announce = an,
            previewCode = o.optString("previewCode"),
        )
    }.getOrNull()

    /** Баннер ещё показывать: не просрочен и не закрыт пользователем. */
    fun bannerVisible(b: Banner?): Boolean = b != null && Prefs.bannerDismissed != (b.id + b.text).hashCode()

    /**
     * 3.18.3. Что из настроек влияет на ядро: пометки и скрытие серверов, выключенные функции, код предпросмотра.
     * Баннер, объявление и ссылки ядро не трогают — из-за них VPN больше не перезагружается.
     */
    private fun coreKey(c: Config) = listOf(
        c.servers.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.state}/${it.value.until}/${it.value.preview}" },
        c.hidden.sorted().joinToString(), c.recommended,
        c.features.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" },
        c.previewCode,
    ).joinToString("|")

    /** Скачать свежий файл (не чаще раза в 5 минут, если не force). true — изменилось то, что влияет на ядро (нужно перезагрузить VPN). */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - Prefs.appConfigAt < 5 * 60_000L) return@withContext false
        val text = sources.firstNotNullOfOrNull { src ->
            runCatching {
                val c = URL(src + "?t=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
            }.getOrNull()?.takeIf { parse(it) != null }
        } ?: return@withContext false
        Prefs.appConfigAt = System.currentTimeMillis()
        if (text == Prefs.appConfigJson) return@withContext false
        Prefs.appConfigJson = text
        val before = coreKey(_state.value)
        _state.value = parse(text) ?: Config()
        before != coreKey(_state.value)
    }
}
