package com.apexvpn.client.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.apexvpn.client.ApexApp
import com.apexvpn.client.R
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.AppsMode
import com.apexvpn.client.data.BankApps
import com.apexvpn.client.data.ConnProfile
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.MainActivity
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL
import io.nekohasekai.libbox.Notification as BoxNotification

/**
 * VPN-сервис APEX VPN: поднимает TUN-интерфейс Android и запускает в нём ядро sing-box.
 * Дополнительно: скорость в уведомлении, автопереключение при сбое сервера,
 * таймер отключения и пауза в доверенных сетях Wi-Fi.
 */
class ApexVpnService : VpnService(), PlatformInterface, CommandServerHandler {

    companion object {
        private const val TAG = "ApexVPN"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_ID = 2
        const val ACTION_START = "com.apexvpn.client.START"
        const val ACTION_STOP = "com.apexvpn.client.STOP"
        const val ACTION_RELOAD = "com.apexvpn.client.RELOAD"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ApexVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            if (VpnState.status.value == VpnStatus.Stopped) return
            context.startService(Intent(context, ApexVpnService::class.java).setAction(ACTION_STOP))
        }

        /** Применить новый сервер/настройки без разрыва VPN. */
        fun reload(context: Context) {
            val s = VpnState.status.value
            if (s != VpnStatus.Started && s != VpnStatus.Paused) return
            context.startService(Intent(context, ApexVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var commandServer: CommandServer? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var monitor: Job? = null

    // ─── Жизненный цикл ──────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch { stopVpn() }
            ACTION_RELOAD -> scope.launch { reloadVpn() }
            else -> {
                showNotification()
                scope.launch { startVpn() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Пользователь включил другой VPN или отозвал разрешение
        scope.launch { stopVpn() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startVpn() = lock.withLock {
        val s = VpnState.status.value
        if (s == VpnStatus.Started || s == VpnStatus.Starting || s == VpnStatus.Paused || s == VpnStatus.Blocked) return@withLock
        VpnState.set(VpnStatus.Starting)
        try {
            if (prepare(this) != null) error("Нет разрешения на VPN")
            NetworkMonitor.start()
            val server = CommandServer(this, this)
            server.start()
            commandServer = server
            if (isTrustedWifi()) {
                VpnState.set(VpnStatus.Paused)
            } else {
                server.startOrReloadService(prepareConfig(), overrideOptions())
                VpnState.set(VpnStatus.Started)
            }
            showNotification()
            startMonitor()
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            VpnState.fail(e.message)
            shutdown()
        }
    }

    private suspend fun reloadVpn() = lock.withLock {
        val server = commandServer ?: return@withLock
        if (VpnState.status.value == VpnStatus.Blocked) {
            // настройки поменялись во время блокировки — сразу пробуем переподключиться
            runCatching { server.startOrReloadService(prepareConfig(), overrideOptions()) }
                .onSuccess { VpnState.set(VpnStatus.Started); showNotification() }
            return@withLock
        }
        if (VpnState.status.value != VpnStatus.Started) return@withLock
        try {
            server.startOrReloadService(prepareConfig(), overrideOptions())
            showNotification()
        } catch (e: Throwable) {
            Log.e(TAG, "reload failed", e)
            VpnState.fail(e.message)
            failOrBlock()
        }
    }

    private suspend fun stopVpn() = lock.withLock {
        if (VpnState.status.value == VpnStatus.Stopped) {
            stopSelf()
            return@withLock
        }
        VpnState.set(VpnStatus.Stopping)
        shutdown()
    }

    /** Пауза: туннель закрыт, сервис остаётся и ждёт выхода из доверенной сети. */
    private suspend fun pause() = lock.withLock {
        if (VpnState.status.value != VpnStatus.Started) return@withLock
        runCatching { commandServer?.closeService() }
        XrayCore.stop()
        com.apexvpn.client.data.AdCounter.stop()
        closeTun()
        VpnState.set(VpnStatus.Paused)
        showNotification()
    }

    private suspend fun resume() = lock.withLock {
        val server = commandServer ?: return@withLock
        if (VpnState.status.value != VpnStatus.Paused) return@withLock
        try {
            server.startOrReloadService(prepareConfig(), overrideOptions())
            VpnState.set(VpnStatus.Started)
            showNotification()
        } catch (e: Throwable) {
            VpnState.fail(e.message)
            failOrBlock()
        }
    }

    // ─── 7. Аварийный выключатель ────────────────────────────────────────

    /** VPN упал после подключения: с аварийным выключателем закрываем интернет, иначе выключаемся. */
    private fun failOrBlock() {
        if (AppSettings.value.killSwitch) enterBlocked() else shutdown()
    }

    /**
     * Интернет закрыт: вместо туннеля ядра поднимаем «пустой» интерфейс, который забирает
     * весь трафик и никуда его не отправляет. Каждые 10 секунд пробуем переподключиться.
     */
    private fun enterBlocked() {
        runCatching { commandServer?.closeService() }
        XrayCore.stop()
        closeTun()
        try {
            val builder = Builder()
                .setSession(getString(R.string.app_name) + " — блокировка")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
            runCatching { builder.addAddress("fdfe:dcba:9876::1", 126).addRoute("::", 0) }
            // Исключения те же, что у VPN: банки и «кроме выбранных» работают как обычно
            val o = overrideOptions()
            val ex = o.excludePackage
            while (ex.hasNext()) runCatching { builder.addDisallowedApplication(ex.next()) }
            val inc = o.includePackage
            while (inc.hasNext()) runCatching { builder.addAllowedApplication(inc.next()) }
            tunFd = builder.establish() ?: error("нет разрешения")
            VpnState.set(VpnStatus.Blocked)
            showNotification()
            alert("VPN упал — интернет закрыт, пока он не переподключится")
            if (monitor?.isActive != true) startMonitor()
        } catch (e: Throwable) {
            Log.e(TAG, "kill switch failed", e)
            shutdown()
        }
    }

    /** Попытка вернуться из блокировки. */
    private suspend fun recover() = lock.withLock {
        val server = commandServer ?: return@withLock
        if (VpnState.status.value != VpnStatus.Blocked) return@withLock
        try {
            server.startOrReloadService(prepareConfig(), overrideOptions())
            VpnState.set(VpnStatus.Started)
            VpnState.clearError()
            showNotification()
            alert("VPN снова работает")
        } catch (e: Throwable) {
            Log.w(TAG, "recover failed: ${e.message}")
        }
    }

    /** Останавливает ядро, закрывает TUN и сервис. Вызывать под lock. */
    private fun shutdown() {
        monitor?.cancel()
        monitor = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        XrayCore.stop()
        closeTun()
        NetworkMonitor.setListener(null)
        NetworkMonitor.stop()
        VpnState.set(VpnStatus.Stopped)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closeTun() {
        runCatching { tunFd?.close() }
        tunFd = null
    }

    /** Собирает конфиги, запускает/перезапускает Xray при необходимости, возвращает конфиг sing-box. */
    private fun prepareConfig(): String {
        com.apexvpn.client.data.Diagnostics.trimCoreLog(this)
        // 197. Счётчик рекламы: локальный DNS до сборки конфига
        if (com.apexvpn.client.data.AppSettings.value.adBlock) {
            com.apexvpn.client.data.AdCounter.ensure(com.apexvpn.client.data.Prefs.adsDnsPort)
        } else {
            com.apexvpn.client.data.AdCounter.stop()
        }
        val (singBox, xray) = Subscription.buildConfigs(this)
        XrayCore.apply(this, xray)
        return singBox
    }

    private fun overrideOptions() = OverrideOptions().apply {
        val s = AppSettings.value
        // 349. Свой список приложений, от которых VPN скрыт, — так же, как банки
        val banks = ((if (s.hideFromBanks) BankApps.packages else emptyList()) + s.hiddenApps).distinct()
        autoRedirect = false
        when (s.appsMode) {
            AppsMode.Include -> {
                // Только выбранные приложения (и само APEX VPN — для теста скорости и пинга)
                includePackage = StringArray((s.apps - banks.toSet() + packageName).toList())
                excludePackage = StringArray(emptyList())
            }
            AppsMode.Exclude -> {
                includePackage = StringArray(emptyList())
                excludePackage = StringArray((s.apps + banks).filter { it != packageName }.distinct())
            }
            AppsMode.AllApps -> {
                includePackage = StringArray(emptyList())
                excludePackage = StringArray(banks.filter { it != packageName })
            }
        }
    }

    // ─── Фоновые проверки: скорость, таймер, сбои, Wi-Fi ─────────────────

    @Volatile private var subRefreshing = false

    private fun startMonitor() {
        monitor?.cancel()
        monitor = scope.launch {
            val uid = Process.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            var tick = 0L
            var failures = 0
            var kidsApplied: Boolean? = null
            while (isActive) {
                delay(1000)
                tick++
                val status = VpnState.status.value
                val settings = AppSettings.value

                // Скорость: весь трафик ядра идёт через сокеты этого приложения
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx >= 0 && tx >= 0) {
                    val dRx = (rx - lastRx).coerceAtLeast(0); val dTx = (tx - lastTx).coerceAtLeast(0)
                    VpnState.setSpeed(dRx, dTx)
                    lastRx = rx; lastTx = tx
                    // 3.6. Отчёт за месяц
                    if (status == VpnStatus.Started) {
                        val sub = Subscription.state.value
                        val server = if (sub.selectedTag == AUTO_TAG) "Автопилот"
                            else sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: "Сервер"
                        com.apexvpn.client.data.MonthStats.tick(dRx, dTx, server)
                    }
                }
                if (status == VpnStatus.Started && tick % 60 == 30L) {
                    scope.launch { com.apexvpn.client.data.MonthStats.ping(com.apexvpn.client.data.LivePing.measure()) }
                }
                // 3.11. Правила «если — то»
                if (tick % 5 == 0L && status == VpnStatus.Started && settings.autoRules.isNotEmpty()) {
                    val todo = com.apexvpn.client.data.AutoRulesEngine.tick(this@ApexVpnService)
                    if ("off" in todo) { scope.launch { stopVpn() } }
                    else {
                        if ("reload" in todo) scope.launch { reloadVpn(); showNotification() }
                        if ("rules" in todo) scope.launch {
                            if (com.apexvpn.client.data.RulesUpdater.update(this@ApexVpnService) is com.apexvpn.client.data.RulesUpdater.Result.Updated) reloadVpn()
                        }
                    }
                }
                // 3.6. Семейный режим по расписанию: окно началось или закончилось — применяем
                if (tick % 30 == 0L && status == VpnStatus.Started) {
                    val kidsNow = com.apexvpn.client.data.KidsSchedule.active(settings)
                    if (kidsApplied != null && kidsApplied != kidsNow) scope.launch { reloadVpn() }
                    kidsApplied = kidsNow
                }
                if (settings.speedInNotification && tick % 2 == 0L && status == VpnStatus.Started) showNotification()

                // 10/11. Виджеты со скоростью и пингом, часы — обновляем, только если они есть
                if (tick % 3 == 0L) {
                    val live = NewWidgets.anyLive(this@ApexVpnService) || WearBridge.connected
                    if (live && status == VpnStatus.Started && tick % 15 == 0L) {
                        VpnState.setPing(com.apexvpn.client.data.LivePing.measure().coerceAtLeast(0))
                    }
                    if (NewWidgets.anyLive(this@ApexVpnService)) NewWidgets.update(this@ApexVpnService)
                    if (WearBridge.connected) WearBridge.publish(this@ApexVpnService)
                }

                // Xray упал — перезапускаем
                if (tick % 5 == 0L && status == VpnStatus.Started && XrayCore.crashed()) {
                    Log.w(TAG, "xray exited: ${XrayCore.lastError()}")
                    reloadVpn()
                }

                // Аварийный выключатель: пробуем переподключиться
                if (status == VpnStatus.Blocked && tick % 10 == 0L && NetworkMonitor.defaultNetwork != null) recover()

                // Таймер отключения
                val offAt = VpnState.autoOffAt.value
                if (offAt > 0 && SystemClock.elapsedRealtime() >= offAt) {
                    VpnState.setTimer(0)
                    alert("VPN выключен по таймеру")
                    stopVpn()
                    return@launch
                }

                // Автообновление подписки, пока VPN включён: фоновую задачу оболочки (Xiaomi, Honor…)
                // часто откладывают на часы, поэтому проверяем срок и здесь — раз в 5 минут
                if (tick % 300 == 0L && status == VpnStatus.Started && Subscription.needsRefresh() && !subRefreshing) {
                    subRefreshing = true
                    scope.launch {
                        try {
                            runCatching { Subscription.refresh() }.onSuccess { reloadVpn() }
                        } finally {
                            subRefreshing = false
                        }
                    }
                }

                // Доверенные сети Wi-Fi
                if (tick % 5 == 0L && settings.wifiRules) {
                    val trusted = isTrustedWifi()
                    if (trusted && status == VpnStatus.Started) pause()
                    if (!trusted && status == VpnStatus.Paused) resume()
                }
                if (!settings.wifiRules && status == VpnStatus.Paused) resume()

                // Автопереключение: выбранный вручную сервер перестал отвечать
                val period = if (settings.profile == ConnProfile.Battery) 60L else 20L
                if (settings.failover && status == VpnStatus.Started && tick % period == 0L &&
                    Subscription.state.value.selectedTag != AUTO_TAG && NetworkMonitor.defaultNetwork != null
                ) {
                    failures = if (probe()) 0 else failures + 1
                    if (failures >= 2) {
                        failures = 0
                        val sub = Subscription.state.value
                        val name = sub.nodes.find { it.tag == sub.selectedTag }?.name?.let { ServerInfo.title(it) } ?: "Сервер"
                        Subscription.select(AUTO_TAG)
                        reloadVpn()
                        alert("$name не отвечает — включён автопилот")
                    }
                }
            }
        }
    }

    /** Проверка связи через туннель: запрос идёт через выбранный сервер. */
    private fun probe(): Boolean = runCatching {
        val c = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.instanceFollowRedirects = false
        try {
            c.responseCode in 200..399
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    private fun isTrustedWifi(): Boolean {
        val s = AppSettings.value
        if (!s.wifiRules || s.trustedWifi.isEmpty()) return false
        val ssid = WifiInfoReader.currentSsid(this) ?: return false
        return ssid in s.trustedWifi
    }

    // ─── Уведомления ─────────────────────────────────────────────────────

    private fun showNotification() {
        val state = Subscription.state.value
        val serverName = if (state.selectedTag == AUTO_TAG) {
            "Автопилот"
        } else {
            state.nodes.find { it.tag == state.selectedTag }?.name?.let { ServerInfo.title(it) } ?: "Сервер"
        }
        val status = VpnState.status.value
        val title = when (status) {
            VpnStatus.Started -> getString(R.string.notification_connected) + " · " + serverName
            VpnStatus.Paused -> "Пауза — доверенная сеть Wi-Fi"
            VpnStatus.Blocked -> "Интернет закрыт — переподключаемся"
            else -> getString(R.string.notification_connecting)
        }
        val text = when {
            // Защита от подделок: копия честно говорит, что она не оригинал
            com.apexvpn.client.data.Integrity.status(this) == com.apexvpn.client.data.Integrity.Status.Unofficial ->
                "⚠ Неофициальная копия APEX VPN — скачайте оригинал с GitHub"
            status == VpnStatus.Paused -> "VPN включится сам, когда вы выйдете из этой сети"
            status == VpnStatus.Blocked -> "Аварийный выключатель: без VPN интернет не работает"
            status == VpnStatus.Started && AppSettings.value.speedInNotification -> {
                val sp = VpnState.speed.value
                "↓ ${formatSpeed(sp.first)}   ↑ ${formatSpeed(sp.second)}"
            }
            else -> serverName
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ApexVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 3.5: «Приложение не работает?» — сразу к выбору приложения, которое пустить мимо VPN
        val appIssue = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PAGE, "AppIssue")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, ApexApp.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_disconnect), stopIntent)
            .apply { if (status == VpnStatus.Started) addAction(0, "Приложение не работает?", appIssue) }
            // 3.9. Android 16: «живое» уведомление — капсула в строке состояния со скоростью
            .apply {
                if (Build.VERSION.SDK_INT >= 36 && status == VpnStatus.Started) addExtras(android.os.Bundle().apply {
                    putBoolean("android.requestPromotedOngoing", true)
                    // Скорость — только если уведомление обновляется каждую секунду, иначе в капсуле застынет старое число
                    putString("android.shortCriticalText",
                        if (AppSettings.value.speedInNotification) formatSpeed(VpnState.speed.value.first).substringBefore('/').take(9) else "VPN")
                })
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun alert(message: String) {
        VpnState.notice(message)
        val nm = getSystemService<NotificationManager>() ?: return
        nm.notify(
            ALERT_ID,
            NotificationCompat.Builder(this, ApexApp.CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_apex)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    // ─── CommandServerHandler (команды от ядра) ──────────────────────────

    override fun serviceStop() {
        scope.launch { stopVpn() }
    }

    override fun serviceReload() {
        scope.launch { reloadVpn() }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().apply {
        available = false
        enabled = false
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {}

    override fun writeDebugMessage(message: String?) {
        Log.d(TAG, message.orEmpty())
    }

    // ─── PlatformInterface (что ядро просит у Android) ───────────────────

    override fun localDNSTransport(): LocalDNSTransport = LocalResolver

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        // Сокеты ядра идут мимо VPN, иначе получится петля
        protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("Нет разрешения на VPN")
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(options.mtu)
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val inet4 = options.inet4Address
        while (inet4.hasNext()) inet4.next().let { builder.addAddress(it.address(), it.prefix()) }
        val inet6 = options.inet6Address
        while (inet6.hasNext()) inet6.next().let { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)
            // Диапазоны маршрутов уже без исключённых адресов — работает на всех версиях Android
            val r4 = options.inet4RouteRange
            if (r4.hasNext()) {
                while (r4.hasNext()) r4.next().let { builder.addRoute(it.address(), it.prefix()) }
            } else if (options.inet4Address.hasNext()) {
                builder.addRoute("0.0.0.0", 0)
            }
            val r6 = options.inet6RouteRange
            if (r6.hasNext()) {
                while (r6.hasNext()) r6.next().let { builder.addRoute(it.address(), it.prefix()) }
            } else if (options.inet6Address.hasNext()) {
                builder.addRoute("::", 0)
            }
        }

        // Android не разрешает смешивать «только эти» и «кроме этих»: ядро присылает что-то одно
        val include = options.includePackage
        while (include.hasNext()) {
            runCatching { builder.addAllowedApplication(include.next()) }
        }
        val exclude = options.excludePackage
        while (exclude.hasNext()) {
            // Неустановленные приложения пропускаем (NameNotFoundException)
            runCatching { builder.addDisallowedApplication(exclude.next()) }
        }

        val pfd = builder.establish() ?: error("Android не разрешил создать VPN-интерфейс")
        tunFd?.let { runCatching { it.close() } }
        tunFd = pfd
        return pfd.fd
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        // 413. Чьё соединение — для «Трафика по приложениям» (Android 10+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("not supported")
        val cm = getSystemService<android.net.ConnectivityManager>() ?: error("no connectivity")
        val uid = cm.getConnectionOwnerUid(
            ipProtocol,
            java.net.InetSocketAddress(sourceAddress, sourcePort),
            java.net.InetSocketAddress(destinationAddress, destinationPort),
        )
        if (uid == android.os.Process.INVALID_UID) error("owner not found")
        val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        return ConnectionOwner().apply {
            userId = uid
            userName = ""
            setAndroidPackageNames(StringArray(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        NetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        NetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator = collectInterfaces()

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun readWIFIState(): WIFIState? = null

    override fun systemCertificates(): StringIterator = loadSystemCertificates()

    override fun clearDNSCache() {}

    override fun sendNotification(notification: BoxNotification) {
        val nm = getSystemService<NotificationManager>() ?: return
        val n = NotificationCompat.Builder(this, ApexApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_apex)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .build()
        nm.notify(notification.typeID + 100, n)
    }
}

/** «1.2 МБ/с». */
fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec < 1024 -> "$bytesPerSec Б/с"
    bytesPerSec < 1024 * 1024 -> String.format(java.util.Locale("ru"), "%.0f КБ/с", bytesPerSec / 1024.0)
    else -> String.format(java.util.Locale("ru"), "%.1f МБ/с", bytesPerSec / 1048576.0)
}
