package com.apexvpn.client.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.ui.MainActivity
import com.apexvpn.client.vpn.ApexVpnService
import java.util.concurrent.TimeUnit

/**
 * Фоновая задача раз в час (только при наличии интернета):
 * обновление подписки, предупреждение о конце подписки, новости,
 * обновление баз и отправка отчётов о сбоях.
 */
class BackgroundWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Subscription.needsRefresh()) {
            runCatching {
                Subscription.refresh()
                ApexVpnService.reload(ctx)
            }
        }
        ExpiryAlerts.check(ctx)
        // 3.16. Адрес подписки из файла на GitHub (раз в 6 часов); перенесли ссылку — обновляем подписку
        if (runCatching { SubHosts.refresh() }.getOrDefault(false)) runCatching { Subscription.refresh() }
        // 3.10. Шаблон «Скрыть VPN»: свежий список из интернета и новые приложения — сразу в список
        runCatching { HideTemplates.refresh() }
        if (runCatching { HideTemplates.autoApply(ctx) }.getOrDefault(0) > 0) com.apexvpn.client.vpn.ApexVpnService.reload(ctx)
        // 3.6. 1-го числа — итоги прошлого месяца
        MonthStats.reportDue()?.let { notifyReport(ctx, it) }
        if (RulesUpdater.update(ctx) is RulesUpdater.Result.Updated) ApexVpnService.reload(ctx)
        CrashReporter.cleanup(ctx)
        // Новая версия на GitHub. «Ставить само» — скачиваем заранее и ставим, когда VPN выключен или экран погас;
        // иначе (или пока не вышло) — уведомление, нажатие сразу обновляет
        if (AppSettings.value.autoCheckUpdates) {
            runCatching { com.apexvpn.client.update.UpdateManager.check(silent = true) }
            (com.apexvpn.client.update.UpdateManager.state.value as? com.apexvpn.client.update.UpdateState.Available)?.let {
                val auto = AppSettings.value.updateMode == "auto" &&
                    runCatching { com.apexvpn.client.update.UpdateInstaller.autoUpdate(ctx, it.info) }.getOrDefault(false)
                if (!auto) notifyUpdate(ctx, it.info)
            }
        }
        return Result.success()
    }

    private fun notifyReport(context: Context, key: String) {
        val m = MonthStats.get(key)
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(
            context, 14,
            Intent(context, com.apexvpn.client.ui.MainActivity::class.java)
                .putExtra(com.apexvpn.client.ui.MainActivity.EXTRA_OPEN_PAGE, "Report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            7,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("Ваш ${MonthStats.title(key).substringBefore(' ')} с APEX VPN")
                .setContentText("${com.apexvpn.client.ui.formatBytes(m.down + m.up)} через VPN · ${m.seconds / 3600} ч · ${m.ads} рекламы заблокировано")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notifyUpdate(context: Context, info: com.apexvpn.client.update.UpdateInfo) {
        val version = info.versionName
        val sp = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        if (sp.getString("update_ready", "") == version) return // уже говорили про эту версию
        sp.edit { putString("update_ready", version) }
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(
            context, 12,
            if (info.apkUrl != null && info.shaUrl != null)
                Intent(context, com.apexvpn.client.ui.MainActivity::class.java)
                    .putExtra(com.apexvpn.client.ui.MainActivity.EXTRA_INSTALL_UPDATE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            else Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            5,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle("Доступна версия APEX VPN $version")
                .setContentText(if (info.apkUrl != null) "Нажмите — обновится в один клик" else "Нажмите — откроется страница версии на GitHub")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackgroundWork>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("apex-background", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

// ─── 13/259. Подписка скоро закончится ──────────────────────────────────

/** Срабатывает точно в момент «за 3 дня», «за 1 день» и «закончилась» — даже без интернета. */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ExpiryAlerts.check(applicationContext)
        return Result.success()
    }
}

object ExpiryAlerts {
    private const val DAY = 24L * 3600 * 1000

    /** Этап: 3 — осталось ≤ 3 дней, 1 — ≤ 1 дня, 0 — закончилась, null — всё в порядке. */
    private fun stage(expireAt: Long, nowSec: Long): Int? {
        if (expireAt <= 0) return null
        val left = expireAt - nowSec
        return when {
            left <= 0 -> 0
            left <= 86400 -> 1
            left <= 3 * 86400 -> 3
            else -> null
        }
    }

    /** Текст предупреждения или null, если всё в порядке. */
    fun warning(state: SubscriptionState): String? {
        val now = System.currentTimeMillis() / 1000
        when (stage(state.expireAt, now)) {
            0 -> return "Подписка закончилась — продлите её"
            1 -> {
                val hours = ((state.expireAt - now) / 3600).toInt()
                return if (hours < 1) "Подписка закончится меньше чем через час" else "Подписка закончится через ${hoursWord(hours)}"
            }
            3 -> return "Подписка закончится через ${daysWord((((state.expireAt - now) + 86399) / 86400).toInt().coerceIn(2, 3))}"
        }
        if (state.total > 0 && state.used >= 0) {
            val left = state.total - state.used
            when {
                left <= 0 -> return "Трафик закончился"
                left.toDouble() / state.total < 0.1 -> return "Осталось меньше 10% трафика"
            }
        }
        return null
    }

    private fun daysWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n день"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n дня"
        else -> "$n дней"
    }

    private fun hoursWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "$n час"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n часа"
        else -> "$n часов"
    }

    /**
     * 259. Уведомления о конце подписки: по одному за 3 дня, за 1 день и в момент окончания
     * (каждое — один раз для этой даты). О трафике — не чаще раза в сутки.
     */
    fun check(context: Context) {
        if (!AppSettings.value.expiryAlerts) return
        val state = Subscription.state.value
        val sp = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis() / 1000
        val st = stage(state.expireAt, now)
        if (st != null) {
            val key = "expiry_${state.expireAt}"
            val last = sp.getInt(key, 99)
            if (st < last) {
                sp.edit { putInt(key, st) }
                notify(context, warning(state) ?: return, st)
            }
            return
        }
        val text = warning(state) ?: return
        if (System.currentTimeMillis() - sp.getLong("traffic", 0) < DAY) return
        sp.edit { putLong("traffic", System.currentTimeMillis()) }
        notify(context, text, -1)
    }

    /** Будильники WorkManager точно на «за 3 дня», «за 1 день» и окончание. */
    fun schedule(context: Context) {
        val expireAt = Subscription.state.value.expireAt
        val wm = WorkManager.getInstance(context)
        val nowMs = System.currentTimeMillis()
        listOf(3 * 86400L to "expiry-3", 86400L to "expiry-1", 0L to "expiry-0").forEach { (before, name) ->
            val at = (expireAt - before) * 1000
            if (expireAt <= 0 || at <= nowMs) {
                wm.cancelUniqueWork(name)
            } else {
                wm.enqueueUniqueWork(
                    name,
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work.OneTimeWorkRequestBuilder<ExpiryWorker>()
                        .setInitialDelay(at - nowMs + 60_000, TimeUnit.MILLISECONDS)
                        .build(),
                )
            }
        }
    }

    private fun notify(context: Context, text: String, stage: Int) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(
            context, 11, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (stage) {
            0 -> "Подписка APEX VPN закончилась"
            1 -> "Подписка заканчивается завтра"
            3 -> "Подписка скоро закончится"
            else -> "APEX VPN"
        }
        // 3.17.4: кнопка «Продлить» — личный кабинет из подписки, иначе чат поддержки
        val sub = Subscription.state.value
        val renewUrl = sub.webPageUrl.ifBlank { sub.supportUrl }.ifBlank { Links.SUPPORT }
        val renew = runCatching {
            PendingIntent.getActivity(
                context, 12, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(renewUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }.getOrNull()
        nm.notify(
            4,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .apply { if (stage >= 0 && renew != null) addAction(0, "Продлить", renew) }
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(title)
                .setContentText(if (stage >= 0) "$text. Продлите в личном кабинете, чтобы VPN не отключился." else text)
                .setStyle(NotificationCompat.BigTextStyle())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}
