package com.apexvpn.client.wear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import java.io.File

/**
 * 3.7. Обновление APEX Watch с телефона: файл приходит по каналу /apex/watch-apk,
 * часы ставят его через PackageInstaller и сообщают телефону, что получилось.
 */
object WatchUpdate {
    const val PATH = "/apex/watch-apk"
    const val ACTION = "com.apexvpn.client.wear.UPDATE_STATUS"

    private fun file(ctx: Context) = File(ctx.cacheDir, "watch-update.apk")

    fun receive(ctx: Context, channel: ChannelClient.Channel) {
        val f = file(ctx).apply { delete() }
        runCatching { Wearable.getChannelClient(ctx).receiveFile(channel, Uri.fromFile(f), false) }
            .onFailure { reply(ctx, "failed: ${it.message}") }
    }

    /** Файл принят целиком — ставим. */
    fun received(ctx: Context, closeReason: Int) {
        val f = file(ctx)
        if (closeReason != ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL || !f.exists() || f.length() == 0L) {
            reply(ctx, "failed: передача прервалась")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            reply(ctx, "need_permission")
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        reply(ctx, "received")
        runCatching {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("apex-watch.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, WatchUpdateReceiver::class.java).setAction(ACTION), flags)
                s.commit(pi.intentSender)
            }
        }.onFailure { reply(ctx, "failed: ${it.message}") }
    }

    fun reply(ctx: Context, text: String) {
        val app = ctx.applicationContext
        Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, "/apex/watch-update", text.toByteArray()) }
        }
    }
}

class WatchUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> WatchUpdate.reply(context, "installed")
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> WatchUpdate.reply(context, "failed: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""))
        }
    }
}
