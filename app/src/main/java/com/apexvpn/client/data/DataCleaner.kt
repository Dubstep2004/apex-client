package com.apexvpn.client.data

import android.content.Context
import java.io.File

/**
 * 379. «Очистить данные»: всё, кроме подписки — настройки, свои списки, правила,
 * избранное, историю тестов, журналы, отчёты о сбоях, фото фона и кэш.
 */
object DataCleaner {
    fun clear(context: Context) {
        AppSettings.reset()
        SpeedHistory.clear(context)
        AppTraffic.clear()
        ConnLog.clear()
        AutoModes.clear(context)
        AdCounter.reset()
        FastestServer.reset()
        Backdrop.clear(context)
        com.apexvpn.client.ui.LiveVideos.clearOwn(context)
        CountryPhoto.clear(context)
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().clear().apply()
        runCatching { Diagnostics.coreLog(context).delete() }
        runCatching { File(context.filesDir, "crashes").deleteRecursively() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        Prefs.connectCount = 0
        Prefs.clearMeasurements()
        Prefs.dismissedAnnounce = ""
    }
}
