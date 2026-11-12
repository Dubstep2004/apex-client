package com.apexvpn.client.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.MainActivity

/**
 * 426. Управление из Tasker, MacroDroid и других приложений автоматизации.
 * Невидимая активность: выполняет действие и сразу закрывается.
 *
 *  • com.apexvpn.client.CONNECT    — включить (extra «server»: название сервера или «auto»)
 *  • com.apexvpn.client.DISCONNECT — выключить
 *  • com.apexvpn.client.TOGGLE     — переключить
 *
 * Работает, только если пользователь сам включил «Автоматизацию» в настройках.
 */
class AutomationActivity : Activity() {
    companion object {
        const val CONNECT = "com.apexvpn.client.CONNECT"
        const val DISCONNECT = "com.apexvpn.client.DISCONNECT"
        const val TOGGLE = "com.apexvpn.client.TOGGLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { handle(intent) }.onFailure { toast("APEX CLIENT: ${it.message ?: "ошибка"}") }
        finish()
    }

    private fun handle(i: Intent?) {
        val action = i?.action ?: return
        if (!AppSettings.value.automation) {
            toast("APEX CLIENT: включите «Автоматизацию» в настройках")
            return
        }
        val status = VpnState.status.value
        val on = status == VpnStatus.Started || status == VpnStatus.Starting || status == VpnStatus.Paused || status == VpnStatus.Blocked
        when (action) {
            DISCONNECT -> if (on) ApexVpnService.stop(this)
            TOGGLE -> if (on) ApexVpnService.stop(this) else connect(null)
            CONNECT -> connect(i.getStringExtra("server"))
        }
    }

    private fun connect(server: String?) {
        val nodes = Subscription.state.value.nodes
        if (nodes.isEmpty()) {
            toast("APEX CLIENT: сначала добавьте подписку")
            return
        }
        if (!server.isNullOrBlank()) {
            if (server.equals("auto", true) || server.equals("автопилот", true)) {
                Subscription.select(AUTO_TAG)
            } else {
                val node = nodes.firstOrNull { ServerInfo.title(it.name).equals(server.trim(), true) }
                    ?: nodes.firstOrNull { ServerInfo.title(it.name).contains(server.trim(), true) }
                if (node == null) toast("APEX CLIENT: сервер «$server» не найден") else Subscription.select(node.tag)
            }
        }
        if (VpnService.prepare(this) != null) {
            // Разрешение на VPN ещё не дано — его может выдать только пользователь в приложении
            startActivity(Intent(this, MainActivity::class.java).putExtra(VpnToggle.EXTRA_CONNECT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (VpnState.status.value == VpnStatus.Stopped) ApexVpnService.start(this) else ApexVpnService.reload(this)
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
}
