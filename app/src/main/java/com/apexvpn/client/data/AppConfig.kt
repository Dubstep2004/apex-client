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

    data class Banner(val text: String, val level: String, val url: String, val until: Long)
    data class ServerState(val state: String, val note: String) // state: maintenance | slow
    data class Config(
        val banner: Banner? = null,
        val servers: Map<String, ServerState> = emptyMap(),
        val recommended: String = "",
        val hidden: Set<String> = emptySet(),
        val support: String = "",
        val channel: String = "",
        val site: String = "",
        val requiredCode: Int = 0,
        val revoked: Set<Int> = emptySet(),
        val features: Map<String, Boolean> = emptyMap(),
    ) {
        /** Функция включена (по умолчанию — да). */
        fun on(feature: String) = features[feature] != false
        // Сервер ищется по части названия без флага: «Германия» — все немецкие, «Обход №1» — один сервер
        private fun hit(title: String, key: String) = key.isNotBlank() && title.contains(key.trim(), ignoreCase = true)
        fun stateOf(title: String) = servers.entries.firstOrNull { hit(title, it.key) }?.value
        fun isHidden(title: String) = hidden.any { hit(title, it) }
        fun isRecommended(title: String) = hit(title, recommended)
        /** Сервер не берёт автопилот: на обслуживании или скрыт. */
        fun blocked(title: String) = isHidden(title) || stateOf(title)?.state == "maintenance"
    }

    private val _state = MutableStateFlow(parse(Prefs.appConfigJson) ?: Config())
    val state: StateFlow<Config> = _state.asStateFlow()
    val value get() = _state.value

    fun parse(text: String): Config? = runCatching {
        if (text.isBlank()) return null
        val o = JSONObject(text)
        val b = o.optJSONObject("banner")
        val until = b?.optString("until").orEmpty().let { d ->
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!.time + 24 * 3_600_000L }.getOrDefault(0L)
        }
        val minC = b?.optInt("minCode", 0) ?: 0
        val maxC = b?.optInt("maxCode", 0) ?: 0
        val code = BuildConfig.VERSION_CODE
        val banner = b?.optString("text").orEmpty().trim().takeIf { t ->
            t.isNotEmpty() && (minC == 0 || code >= minC) && (maxC == 0 || code <= maxC)
        }?.let { Banner(it.take(300), b!!.optString("level", "info"), b.optString("url").trim(), until) }
        val srv = o.optJSONObject("servers")
        val status = srv?.optJSONObject("status")
        val servers = status?.keys()?.asSequence()?.mapNotNull { k ->
            status.optJSONObject(k)?.let { s -> k to ServerState(s.optString("state"), s.optString("note").take(120)) }
        }?.toMap().orEmpty()
        val hidden = srv?.optJSONArray("hidden")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
        val links = o.optJSONObject("links")
        val upd = o.optJSONObject("update")
        val feats = o.optJSONObject("features")
        Config(
            banner = banner,
            servers = servers,
            recommended = srv?.optString("recommended").orEmpty(),
            hidden = hidden,
            support = links?.optString("support").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            channel = links?.optString("channel").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            site = links?.optString("site").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            requiredCode = upd?.optInt("required", 0) ?: 0,
            revoked = upd?.optJSONArray("revoked")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() }.orEmpty(),
            features = feats?.keys()?.asSequence()?.associate { it to feats.optBoolean(it, true) }.orEmpty(),
        )
    }.getOrNull()

    /** Баннер ещё показывать: не просрочен и не закрыт пользователем. */
    fun bannerVisible(b: Banner?): Boolean =
        b != null && (b.until == 0L || System.currentTimeMillis() < b.until) && Prefs.bannerDismissed != b.text.hashCode()

    /** Скачать свежий файл (не чаще раза в 5 минут, если не force). true — изменился. */
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
        _state.value = parse(text) ?: Config()
        true
    }
}
