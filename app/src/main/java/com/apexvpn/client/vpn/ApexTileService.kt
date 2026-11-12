package com.apexvpn.client.vpn

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.apexvpn.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Плитка «APEX CLIENT» в шторке быстрых настроек. */
class ApexTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            VpnState.status.collectLatest { render(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    override fun onClick() {
        val action = { VpnToggle.toggle(this) { intent -> openApp(intent) } }
        if (isLocked) unlockAndRun(action) else action()
    }

    @Suppress("DEPRECATION")
    private fun openApp(intent: android.content.Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun render(status: VpnStatus) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_apex)
        tile.label = getString(R.string.app_name)
        tile.state = when (status) {
            VpnStatus.Started, VpnStatus.Paused, VpnStatus.Blocked -> Tile.STATE_ACTIVE
            VpnStatus.Starting, VpnStatus.Stopping -> Tile.STATE_ACTIVE
            VpnStatus.Stopped -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                VpnStatus.Started -> "Включён"
                VpnStatus.Paused -> "Пауза"
                VpnStatus.Starting -> "Подключение…"
                VpnStatus.Stopping -> "Отключение…"
                VpnStatus.Blocked -> "Переподключение…"
                VpnStatus.Stopped -> "Выключен"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ApexTileService::class.java)) }
        }
    }
}
