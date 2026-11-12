package com.apexvpn.client.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3.19. Предупреждение о техработах: если в пульте серверу поставили «Обслуживание»
 * (сейчас или по расписанию) и этот сервер выбран вручную — за ~15 минут приходит уведомление
 * с кнопкой «На автопилот». На автопилоте не беспокоим: он сам обойдёт сервер.
 */
object Maintenance {
    private const val NID = 9
    private const val AHEAD = 20 * 60_000L

    fun check(context: Context) = runCatching {
        // 3.19.6: только когда VPN включён — иначе техработы пользователя не касаются
        if (com.apexvpn.client.vpn.VpnState.status.value != com.apexvpn.client.vpn.VpnStatus.Started) return@runCatching
        val tag = Prefs.selectedTag
        if (tag == AUTO_TAG) return@runCatching
        val node = Subscription.state.value.nodes.find { it.tag == tag } ?: return@runCatching
        val title = ServerInfo.title(node.name)
        val (name, st) = AppConfig.value.maintenanceFor(title, AHEAD) ?: return@runCatching
        val key = "$name|${st.from}|${st.until}"
        val seen = Prefs.maintNotified.split('\n').filter { it.isNotBlank() }
        if (key in seen) return@runCatching
        Prefs.maintNotified = (seen.takeLast(19) + key).joinToString("\n")

        val now = System.currentTimeMillis()
        val time = SimpleDateFormat("HH:mm", Locale("ru"))
        val untilText = if (st.until > 0) " до ${time.format(Date(st.until))}" else ""
        val soon = st.from > now
        val titleText = if (soon) "Техработы на «$title» в ${time.format(Date(st.from))}" else "Сервер «$title» на обслуживании"
        val body = buildString {
            append(if (soon) "Сервер будет недоступен$untilText." else "Сейчас он может не работать$untilText.")
            if (st.note.isNotBlank()) append(" ").append(st.note)
            append(" Переключитесь на автопилот — он выберет рабочий сервер сам.")
        }
        val nm = context.getSystemService<NotificationManager>() ?: return@runCatching
        val open = PendingIntent.getActivity(
            context, 15, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val auto = PendingIntent.getBroadcast(
            context, 16, Intent(context, BackToAutoReceiver::class.java).putExtra("nid", NID),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NID,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(titleText)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .addAction(0, "На автопилот", auto)
                .setAutoCancel(true)
                .build(),
        )
    }
}
