package com.apexvpn.client.update

import android.content.Context
import android.net.Uri
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.Prefs
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 3.7. Обновление часов вместе с телефоном: телефон скачивает apex-watch-<версия>.apk из того же
 * релиза, сверяет SHA-256 и передаёт на часы; часы ставят его сами (одна кнопка «Установить» на часах).
 */
object WatchUpdater {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    /** Версия приложения на часах (часы сообщают её при запуске), 0 — неизвестно. */
    val watchCode: Int get() = Prefs.watchVersionCode

    /** Часы отстают от телефона (у часов код на 1 меньше, чем у телефона той же версии). */
    val outdated: Boolean get() = watchCode in 1 until BuildConfig.VERSION_CODE - 1

    fun onWatchMessage(text: String) {
        _status.value = when {
            text == "received" -> "Часы получили обновление — нажмите «Установить» на часах"
            text == "installed" -> "Часы обновлены"
            text == "need_permission" -> "На часах разрешите установку из APEX Watch и нажмите «Обновить часы» ещё раз"
            text.startsWith("failed") -> "Часы не установили обновление" + text.removePrefix("failed").let { if (it.isNotBlank()) ": ${it.trimStart(':', ' ')}" else "" }
            else -> text
        }
    }

    suspend fun send(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val ver = BuildConfig.VERSION_NAME
        val base = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/v$ver/apex-watch-$ver.apk"
        _status.value = "Скачиваем обновление для часов…"
        val (file, err) = UpdateInstaller.fetchVerified(app, base, "$base.sha256", "apex-watch-$ver.apk") { pct ->
            _status.value = "Скачиваем обновление для часов: $pct%"
        }
        if (file == null) { _status.value = err ?: "Не удалось скачать обновление для часов"; return@withContext false }
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(app).connectedNodes, 15, TimeUnit.SECONDS)
            if (nodes.isEmpty()) error("Часы не подключены к телефону")
            _status.value = "Передаём на часы…"
            val channels = Wearable.getChannelClient(app)
            nodes.forEach { node ->
                val ch = Tasks.await(channels.openChannel(node.id, "/apex/watch-apk"), 20, TimeUnit.SECONDS)
                Tasks.await(channels.sendFile(ch, Uri.fromFile(file)), 5, TimeUnit.MINUTES)
            }
            _status.value = "Передано — часы предложат установить"
            true
        }.getOrElse {
            _status.value = it.message ?: "Не удалось передать на часы"
            false
        }
    }
}
