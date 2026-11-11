package com.apexvpn.client.vpn

import android.content.Context
import android.util.Log
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * 11. Связь с часами Wear OS (модуль wear). Телефон публикует состояние в /apex/status,
 * часы присылают /apex/toggle. Без Google Play Services просто ничего не делает.
 */
object WearBridge {
    /** Часы откликались недавно — тогда сервис присылает им скорость и пинг. */
    @Volatile
    var connected = false
        private set
    private var lastHello = 0L
    private var lastPayload = ""

    fun markSeen() {
        connected = true
        lastHello = System.currentTimeMillis()
    }

    private var lastServers = ""

    /** 408. Список серверов для часов: по одному на страну/группу (без рекламных), плюс «Автопилот». */
    fun publishServers(context: Context, force: Boolean = false) {
        runCatching {
            val sub = Subscription.state.value
            val groups = sub.nodes.filter { !it.ads }.distinctBy { ServerInfo.title(it.name) }.take(40)
            val tags = arrayListOf(AUTO_TAG).apply { addAll(groups.map { it.tag }) }
            val titles = arrayListOf("Автопилот").apply { addAll(groups.map { ServerInfo.title(it.name) }) }
            val flags = arrayListOf("⚡").apply { addAll(groups.map { ServerInfo.flag(it.name) ?: "🌐" }) }
            // Выбранная группа: сервер может быть любым из её протоколов
            val selTitle = sub.nodes.find { it.tag == sub.selectedTag }?.let { ServerInfo.title(it.name) }
            val selected = if (sub.selectedTag == AUTO_TAG || selTitle == null) AUTO_TAG else groups.find { ServerInfo.title(it.name) == selTitle }?.tag ?: sub.selectedTag
            val payload = tags.joinToString(",") + "|" + selected
            if (!force && payload == lastServers) return
            lastServers = payload
            val req = PutDataMapRequest.create("/apex/servers").apply {
                dataMap.putStringArrayList("tags", tags)
                dataMap.putStringArrayList("titles", titles)
                dataMap.putStringArrayList("flags", flags)
                dataMap.putString("selected", selected)
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }.onFailure { Log.d("ApexWear", "no wear: ${it.message}") }
    }

    fun publish(context: Context) {
        if (connected && System.currentTimeMillis() - lastHello > 10 * 60_000) connected = false
        runCatching {
            val status = VpnState.status.value.name.lowercase()
            val sub = Subscription.state.value
            val server = if (sub.selectedTag == AUTO_TAG) "Автопилот" else
                sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: ""
            val ping = VpnState.ping.value
            val down = formatSpeed(VpnState.speed.value.first)
            // 3.18.1: баннер из пульта «только для часов»
            val banner = com.apexvpn.client.data.AppConfig.value.bannerFor("watch")?.text.orEmpty()
            val payload = "$status|$server|$ping|$down|$banner"
            if (payload == lastPayload) return
            lastPayload = payload
            val req = PutDataMapRequest.create("/apex/status").apply {
                dataMap.putString("status", status)
                dataMap.putString("server", server)
                dataMap.putInt("ping", ping)
                dataMap.putString("down", down)
                dataMap.putString("banner", banner)
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }.onFailure { Log.d("ApexWear", "no wear: ${it.message}") }
    }
}

/** Сообщения от часов. */
class WearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WearBridge.markSeen()
        when (event.path) {
            "/apex/toggle" -> runCatching { VpnToggle.toggle(applicationContext) }
                .onFailure { Log.w("ApexWear", "toggle failed", it) }
            "/apex/hello" -> {
                // 3.7: часы присылают свою версию — телефон предложит их обновить
                String(event.data, Charsets.UTF_8).toIntOrNull()?.let { com.apexvpn.client.data.Prefs.watchVersionCode = it }
                WearBridge.publishServers(applicationContext, force = true)
            }
            "/apex/watch-update" -> com.apexvpn.client.update.WatchUpdater.onWatchMessage(String(event.data, Charsets.UTF_8))
            // 408. Выбор сервера с часов
            "/apex/select" -> runCatching {
                val tag = String(event.data, Charsets.UTF_8)
                if (tag == AUTO_TAG || Subscription.state.value.nodes.any { it.tag == tag }) {
                    Subscription.select(tag)
                    ApexVpnService.reload(applicationContext)
                    WearBridge.publishServers(applicationContext)
                }
            }.onFailure { Log.w("ApexWear", "select failed", it) }
        }
        WearBridge.publish(applicationContext)
    }
}
