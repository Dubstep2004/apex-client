package com.apexvpn.client.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Новая версия на GitHub Releases (последний релиз v<версия>, «Latest»).
 * С 3.4 приложение само скачивает apex-client-<версия>.apk, сверяет SHA-256 и подпись
 * и ставит через системный установщик (UpdateInstaller). Страница версии — запасной путь.
 */
data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val pageUrl: String,
    val mandatory: Boolean = false,
    /** Прямая ссылка на apex-client-<версия>.apk из релиза (3.4: обновление в один клик) */
    val apkUrl: String? = null,
    /** Ссылка на контрольную сумму apex-client-<версия>.apk.sha256 */
    val shaUrl: String? = null,
    /** Тестовая версия (Pre-release на GitHub) */
    val beta: Boolean = false,
) {
    /** Число для сравнения и «Позже» (1.7.2 → 1007002). */
    val versionCode: Int get() = UpdateManager.rank(versionName).toInt()
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

object UpdateManager {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    private val repo get() = BuildConfig.GITHUB_REPO
    private val latestApi get() = "https://api.github.com/repos/$repo/releases/latest"

    /** Страница всех версий — если конкретную узнать не удалось. */
    val releasesPage get() = "https://github.com/$repo/releases"

    /** Проверка всегда доступна: GitHub не нужно настраивать. */
    val configured: Boolean get() = repo.contains('/')

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** «1.7.2» → 1007002; «1.10» → 1010000. Нечисловые части отбрасываются. */
    fun versionNumber(name: String): Int {
        val parts = name.trim().removePrefix("v").split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
        return (parts.getOrElse(0) { 0 } * 1_000_000) + (parts.getOrElse(1) { 0 } * 1_000) + parts.getOrElse(2) { 0 }
    }

    suspend fun check(silent: Boolean = false) {
        if (!silent) _state.value = UpdateState.Checking
        try {
            val info = fetchLatest()
            _state.value = if (info != null && rank(info.versionName) > rank(BuildConfig.VERSION_NAME)) {
                // 3.18: из веб-админки — «версии ниже N обязаны обновиться»
                val required = com.apexvpn.client.data.AppConfig.value.requiredCode
                UpdateState.Available(if (required > BuildConfig.VERSION_CODE) info.copy(mandatory = true) else info)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Ошибка проверки")
        }
    }

    /**
     * 3.20.10: «Позже» прячет окно обновления, но на главном экране остаётся плашка
     * «Вышла версия … Обновить» — обновиться можно в одно нажатие, без настроек.
     * Значение — код версии, для которой нажали «Позже» (0 — окно снова показывать).
     */
    private val _later by lazy { MutableStateFlow(runCatching { com.apexvpn.client.data.Prefs.dismissedUpdateCode }.getOrDefault(0)) }
    val laterCode: StateFlow<Int> get() = _later.asStateFlow()
    fun later(info: UpdateInfo) { com.apexvpn.client.data.Prefs.dismissedUpdateCode = info.versionCode; _later.value = info.versionCode }
    fun reopen() { com.apexvpn.client.data.Prefs.dismissedUpdateCode = 0; _later.value = 0 }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateState.Available && s.info.mandatory) return
        _state.value = UpdateState.Idle
    }

    /** Открыть страницу версии на GitHub в браузере. */
    fun openPage(context: Context, info: UpdateInfo?) {
        val url = info?.pageUrl ?: releasesPage
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Порядок версий с учётом тестовых: 3.7.0-beta1 < 3.7.0-beta2 < 3.7.0 < 3.7.1.
     * (Обычная версия — «бета 99».)
     */
    fun rank(name: String): Long {
        val beta = Regex("""beta\.?(\d+)""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull()
        return versionNumber(name).toLong() * 100 + (beta?.coerceIn(1, 98) ?: 99)
    }

    fun isBeta(name: String) = name.contains("beta", ignoreCase = true)

    private fun api(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return ""
                403, 429 -> error("GitHub временно ограничил проверки — попробуйте через час")
                else -> error("GitHub ответил кодом ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun toInfo(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name").ifBlank { return null }
        if (!tag.startsWith("v") || json.optBoolean("draft")) return null
        val name = tag.removePrefix("v")
        val body = json.optString("body")
        // Только файлы из релизов этого репозитория; часы (apex-watch-…) не трогаем
        // 3.19.3: репозиторий переименован apex-vpn → apex-client — принимаем оба адреса
        val prefixes = listOf("https://github.com/$repo/releases/download/", "https://github.com/Dubstep2004/apex-client/releases/download/", "https://github.com/Dubstep2004/apex-vpn/releases/download/")
        val assets = json.optJSONArray("assets")
        fun asset(fileName: String): String? = assets?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .firstOrNull { it.optString("name") == fileName }
                ?.optString("browser_download_url")?.takeIf { u -> prefixes.any { u.startsWith(it) } }
        }
        return UpdateInfo(
            // 3.19.3: файлы называются apex-client-…, у старых релизов — apex-vpn-…
            apkUrl = asset("apex-client-$name.apk") ?: asset("apex-vpn-$name.apk"),
            shaUrl = asset("apex-client-$name.apk.sha256") ?: asset("apex-vpn-$name.apk.sha256"),
            versionName = name,
            changelog = cleanNotes(body),
            pageUrl = json.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                ?: "https://github.com/$repo/releases/tag/$tag",
            // Обязательное обновление: в описании релиза есть слово [обязательно]
            mandatory = body.contains("[обязательно]", ignoreCase = true),
            beta = json.optBoolean("prerelease"),
        )
    }

    /** Последние релизы телефона (с тестовыми), новые первыми. */
    private fun releases(): List<UpdateInfo> {
        val text = api("https://api.github.com/repos/$repo/releases?per_page=30").ifBlank { return emptyList() }
        val a = org.json.JSONArray(text)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::toInfo) }.sortedByDescending { rank(it.versionName) }
    }

    /** null — на GitHub ещё нет ни одной версии. Тестовый канал — самая новая, включая тестовые. */
    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (com.apexvpn.client.data.AppSettings.value.betaChannel) {
            releases().firstOrNull()
        } else {
            api(latestApi).ifBlank { return@withContext null }.let { toInfo(JSONObject(it)) }
        }
    }

    /** 3.7. Прошлые обычные версии — для «Вернуть прошлую версию». */
    suspend fun previousVersions(): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val mine = rank(BuildConfig.VERSION_NAME)
        releases().filter { !it.beta && rank(it.versionName) < mine && it.apkUrl != null }.take(5)
    }

    /** Текст релиза без разметки GitHub (звёздочки, `код`) и без списка файлов после «---». */
    fun cleanNotes(body: String): String = body.replace("\r", "")
        .substringBefore("\n---")
        .replace("**", "").replace("`", "")
        .lines().joinToString("\n") { l -> if (l.startsWith("- ")) "• " + l.removePrefix("- ") else l }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
