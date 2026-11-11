package com.apexvpn.client.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.apexvpn.client.R
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.MainActivity

/**
 * 9. Ярлыки: «Подключить Германию» одним касанием.
 *  • долгое нажатие на иконку — избранные серверы и автопилот (динамические ярлыки);
 *  • «На рабочий стол» в списке серверов — закреплённый ярлык.
 * Ярлык открывает приложение с действием CONNECT и названием сервера.
 */
object Shortcuts {
    const val ACTION_CONNECT = "com.apexvpn.client.CONNECT"
    const val ACTION_DISCONNECT = "com.apexvpn.client.DISCONNECT"
    const val ACTION_FEATURE = "com.apexvpn.client.FEATURE"
    const val EXTRA_SERVER = "server"

    fun connectIntent(context: Context, server: String?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_CONNECT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (server != null) putExtra(EXTRA_SERVER, server) }

    private fun shortcut(context: Context, id: String, label: String, server: String?): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label.take(24))
            .setLongLabel("Подключить: $label".take(48))
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_connect))
            .setIntent(connectIntent(context, server))
            .build()

    /** Обновить ярлыки из избранного (до 3) — вызывается при изменении избранного и подписки. */
    fun refresh(context: Context) = runCatching {
        val favorites = AppSettings.value.favorites
        val titles = Subscription.state.value.nodes.map { ServerInfo.title(it.name) }.distinct()
        // 3.17.9: ярлык «LTE-обход» — первый сервер обхода из подписки (для белых списков)
        val lte = Subscription.state.value.nodes.firstOrNull { ServerInfo.isLte(it.name) }?.let { ServerInfo.title(it.name) }
        val list = titles.filter { it in favorites && it != lte }.take(if (lte != null) 2 else 3).mapIndexed { i, t -> shortcut(context, "fav$i", t, t) }
        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            list + listOfNotNull(lte?.let { shortcut(context, "lte", "LTE-обход", it) }) + shortcut(context, "auto", "Автопилот", "auto"),
        )
    }

    fun canPin(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Закрепить ярлык сервера на рабочем столе. */
    fun pin(context: Context, title: String): Boolean = runCatching {
        ShortcutManagerCompat.requestPinShortcut(
            context,
            shortcut(context, "pin-" + title.hashCode(), title, title),
            null,
        )
    }.getOrDefault(false)
}
