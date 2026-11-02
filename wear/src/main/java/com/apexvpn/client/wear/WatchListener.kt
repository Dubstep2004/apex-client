package com.apexvpn.client.wear

import android.content.ComponentName
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Состояние с телефона приходит и когда APEX Watch закрыт: обновляем плитку (409)
 * и значок на циферблате (410), а при отключении VPN — коротко вибрируем (411).
 */
class WatchListener : WearableListenerService() {
    // 3.7. Обновление с телефона: телефон открывает канал /apex/watch-apk и передаёт файл
    override fun onChannelOpened(channel: com.google.android.gms.wearable.ChannelClient.Channel) {
        if (channel.path != WatchUpdate.PATH) return
        WatchUpdate.receive(this, channel)
    }

    override fun onInputClosed(channel: com.google.android.gms.wearable.ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        if (channel.path != WatchUpdate.PATH) return
        WatchUpdate.received(this, closeReason)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != "/apex/status") return@forEach
            val m = DataMapItem.fromDataItem(e.dataItem).dataMap
            val now = WatchState.S(m.getString("status", ""), m.getString("server", ""), m.getInt("ping", 0))
            val prev = WatchState.load(this)
            WatchState.save(this, now)
            // 411. VPN был включён и отключился (или упал в блокировку) — сигнал на руку
            if (prev.status == "started" && (now.status == "stopped" || now.status == "blocked")) vibrate()
            if (prev.status != now.status || prev.server != now.server || prev.ping / 20 != now.ping / 20) {
                runCatching { TileService.getUpdater(this).requestUpdate(ApexTile::class.java) }
                runCatching {
                    ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, ApexComplication::class.java)).requestUpdateAll()
                }
            }
        }
    }

    private fun vibrate() = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120, 90, 260), -1))
    }
}
