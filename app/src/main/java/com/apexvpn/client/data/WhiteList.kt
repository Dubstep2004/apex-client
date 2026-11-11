package com.apexvpn.client.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asStateFlow
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.ui.MainActivity

/**
 * 491. Белые списки у оператора. Бывает, что мобильный интернет пропускает только «разрешённые»
 * сайты: обычные серверы VPN недоступны, а LTE-обход («обход белых списков») работает.
 * На мобильной сети с автопилотом проверяем это раз в 5 минут и сами переключаем автопилот
 * на LTE-обход, а когда обычные серверы снова доступны (или включился Wi-Fi) — обратно.
 * Отключается в Настройки → Профиль.
 */
object WhiteList {
    /** Сейчас автопилот выбирает только LTE-обход. */
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Для экрана: подпись меняется сразу, когда автопилот переключился. */
    val state = _state.asStateFlow()
    var active: Boolean
        get() = _state.value
        private set(v) { _state.value = v }

    /** Сбросить (Wi-Fi, автопилот выключен, функция отключена); true — состояние изменилось. */
    fun reset(context: Context): Boolean {
        hits = 0
        if (!active) return false
        active = false
        notify(context, false)
        return true
    }

    /**
     * Сколько проверок подряд похоже на белые списки. Включаем обход только со второго раза:
     * на медленном LTE обычные серверы могут разок не ответить — это ещё не белые списки,
     * а лишний переход на LTE-обход только замедлил бы интернет.
     */
    @Volatile private var hits = 0
    val suspected get() = hits in 1 until CONFIRM
    private const val CONFIRM = 2

    /** Проверка; true — состояние изменилось (нужно перезагрузить ядро). */
    suspend fun check(context: Context): Boolean {
        if (Prefs.selectedTag != AUTO_TAG) return reset(context) // сервер выбран вручную — не вмешиваемся
        val nodes = Subscription.state.value.nodes
        val lte = nodes.filter { ServerInfo.isLte(it.name) }
        val normal = nodes.filter { !ServerInfo.isSpecial(it.name) }
        if (lte.isEmpty() || normal.isEmpty()) return reset(context)
        val pings = Pinger.pingAll(nodes, type = "tcp")
        val tcpNormal = normal.filter { (pings[it.tag] ?: -1) > 0 }.sortedBy { pings[it.tag] }
        val tcpLte = lte.filter { (pings[it.tag] ?: -1) > 0 }.sortedBy { pings[it.tag] }
        var normalOk = tcpNormal.isNotEmpty()
        var lteOk = tcpLte.isNotEmpty()
        // 3.16.1: при белых списках оператор часто пропускает само TCP-соединение к любому
        // серверу, а данные потом не идут — TCP-пинг «зелёный», и белые списки не находились.
        // Пока VPN включён, проверяем настоящим запросом в интернет через каждый сервер.
        var sure = false
        if (VpnState.status.value == VpnStatus.Started && lteOk) {
            val lteUrl = urlOk(tcpLte.take(3))
            if (lteUrl) {
                lteOk = true
                normalOk = urlOk(tcpNormal.take(6))
                // Настоящий запрос: LTE-обход открыл сайт, а обычные — нет. Это уже не
                // «медленный LTE», а белые списки — включаем обход сразу, без второй проверки
                sure = !normalOk
            } else if (active) {
                // Обход уже включён, но замер через ядро не прошёл (ядро как раз перезагружается
                // после переключения) — по одному TCP-пингу не выключаем, иначе автопилот «мигает»
                return false
            }
            // Ни один LTE-обход не ответил запросом — ядро без замеров; остаётся TCP-проверка
        }
        val looks = !normalOk && lteOk
        hits = if (looks) hits + 1 else 0
        // Включаем после подтверждения, выключаем сразу, как только обычные серверы ответили
        val now = if (active) looks else (hits >= CONFIRM || (looks && sure))
        if (now == active) return false
        active = now
        notify(context, now)
        return true
    }

    private var manualNotifiedAt = 0L

    /**
     * 3.17.3. LTE-обход выбран вручную, а обычные серверы снова открывают сайты (белые списки
     * сняли или телефон на Wi-Fi) — ручной выбор не трогаем, а предлагаем уведомлением
     * вернуться на автопилот. Не чаще раза в 2 часа.
     */
    suspend fun checkManualLte(context: Context) {
        val tag = Prefs.selectedTag
        if (tag == AUTO_TAG) return
        val nodes = Subscription.state.value.nodes
        val sel = nodes.find { it.tag == tag } ?: return
        if (!ServerInfo.isLte(sel.name)) return
        if (System.currentTimeMillis() - manualNotifiedAt < 2 * 3600_000L) return
        val normal = nodes.filter { !ServerInfo.isSpecial(it.name) }
        if (normal.isEmpty()) return
        val pings = Pinger.pingAll(normal, type = "tcp")
        val alive = normal.filter { (pings[it.tag] ?: -1) > 0 }.sortedBy { pings[it.tag] }
        if (alive.isEmpty() || !urlOk(alive.take(6))) return
        manualNotifiedAt = System.currentTimeMillis()
        runCatching {
            val nm = context.getSystemService<NotificationManager>() ?: return@runCatching
            val open = PendingIntent.getActivity(
                context, 13, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val back = PendingIntent.getBroadcast(
                context, 14, Intent(context, BackToAutoReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                8,
                NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_apex)
                    .setContentTitle("Обычные серверы снова работают")
                    .setContentText("Сейчас выбран LTE-обход (лимит трафика). Вернуться на автопилот?")
                    .setStyle(NotificationCompat.BigTextStyle())
                    .setContentIntent(open)
                    .addAction(0, "На автопилот", back)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    /** Хоть один сервер открыл сайт через себя (запрос внутри ядра, мимо правил). */
    private suspend fun urlOk(list: List<Node>): Boolean = kotlinx.coroutines.coroutineScope {
        if (list.isEmpty()) return@coroutineScope false
        list.map { n ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { io.nekohasekai.apexcore.Apexcore.proxyPing(n.tag, "", "HEAD", "default", 6000) > 0 }.getOrDefault(false)
            }
        }.awaitAll().any { it }
    }

    private fun notify(context: Context, on: Boolean) = runCatching {
        val nm = context.getSystemService<NotificationManager>() ?: return@runCatching
        val pi = PendingIntent.getActivity(
            context, 13, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            7,
            NotificationCompat.Builder(context, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(if (on) "Мобильный интернет по белым спискам" else "Белые списки сняты")
                .setContentText(
                    if (on) "Автопилот включил LTE-обход. Отключить: Настройки → Профиль" else "Автопилот снова выбирает обычные серверы",
                )
                .setStyle(NotificationCompat.BigTextStyle())
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** Кнопка «На автопилот» в уведомлении: выбрать автопилот и перезапустить ядро. */
class BackToAutoReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Subscription.select(AUTO_TAG)
        context.getSystemService<NotificationManager>()?.cancel(8)
        if (VpnState.status.value == VpnStatus.Started) com.apexvpn.client.vpn.ApexVpnService.reload(context)
    }
}
