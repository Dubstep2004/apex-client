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
 * С 3.4 приложение само скачивает apex-vpn-<версия>.apk, сверяет SHA-256 и подпись
 * и ставит через системный установщик (UpdateInstaller). Страница версии — запасной путь.
 */
data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val pageUrl: String,
    val mandatory: Boolean = false,
    /** Прямая ссылка на apex-vpn-<версия>.apk из релиза (3.4: обновление в один клик) */
    val apkUrl: String? = null,
    /** Ссылка на контрольную сумму apex-vpn-<версия>.apk.sha256 */
    val shaUrl: String? = null,
) {
    /** Число для сравнения и «Позже» (1.7.2 → 1007002). */
    val versionCode: Int get() = UpdateManager.versionNumber(versionName)
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
            _state.value = if (info != null && versionNumber(info.versionName) > versionNumber(BuildConfig.VERSION_NAME)) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Ошибка проверки")
        }
    }

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

    /** null — на GitHub ещё нет ни одной версии. */
    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(latestApi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                403, 429 -> error("GitHub временно ограничил проверки — попробуйте через час")
                else -> error("GitHub ответил кодом ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").ifBlank { return@withContext null }
            val name = tag.removePrefix("v")
            val body = json.optString("body")
            // Только файлы из релизов этого репозитория; часы (apex-watch-…) не трогаем
            val prefix = "https://github.com/$repo/releases/download/"
            val assets = json.optJSONArray("assets")
            fun asset(fileName: String): String? = assets?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == fileName }
                    ?.optString("browser_download_url")?.takeIf { it.startsWith(prefix) }
            }
            UpdateInfo(
                apkUrl = asset("apex-vpn-$name.apk"),
                shaUrl = asset("apex-vpn-$name.apk.sha256"),
                versionName = name,
                changelog = body.trim(),
                pageUrl = json.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                    ?: "https://github.com/$repo/releases/tag/$tag",
                // Обязательное обновление: в описании релиза есть слово [обязательно]
                mandatory = body.contains("[обязательно]", ignoreCase = true),
            )
        } finally {
            conn.disconnect()
        }
    }
}
