package com.apexvpn.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.getSystemService
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.BackgroundWork
import com.apexvpn.client.data.CrashReporter
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.vpn.ApexTileService
import com.apexvpn.client.vpn.ApexWidget
import com.apexvpn.client.vpn.VpnState
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class ApexApp : Application() {

    /** Область для фоновых дел всего приложения. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)
        com.apexvpn.client.data.AdCounter.init(this)
        Prefs.init(this)
        AppSettings.init(this)
        com.apexvpn.client.update.UpdateManager.init(this)
        com.apexvpn.client.update.UpdateInstaller.init(this)
        com.apexvpn.client.data.MonthStats.init(this)
        com.apexvpn.client.data.WeekStats.init(this)
        // 3.15/3.16: адрес подписки сменился — сохранённая ссылка переносится сама. Сначала встроенный перенос
        // .ru → .xyz (на случай, если файл с GitHub ещё не скачался), потом — по файлу docs/config/subscription.json
        com.apexvpn.client.data.Prefs.subscriptionUrl.let { u ->
            if (u.startsWith("https://sub.knownhosts.ru/", ignoreCase = true))
                com.apexvpn.client.data.Prefs.subscriptionUrl = "https://sub.knownhosts.xyz/" + u.substring("https://sub.knownhosts.ru/".length)
        }
        com.apexvpn.client.data.SubHosts.migrate()
        appScope.launch {
            if (runCatching { com.apexvpn.client.data.SubHosts.refresh() }.getOrDefault(false)) runCatching { com.apexvpn.client.data.Subscription.refresh() }
        }
        // 3.18. Настройки из веб-админки (баннер, статус серверов, ссылки…): изменились — перестроить ядро
        appScope.launch {
            if (runCatching { com.apexvpn.client.data.AppConfig.refresh(force = true) }.getOrDefault(false) &&
                VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started
            ) com.apexvpn.client.vpn.ApexVpnService.reload(this@ApexApp)
            // 3.18: раз в 15 минут (файл крошечный) — и при каждом открытии приложения (MainActivity)
            while (true) {
                kotlinx.coroutines.delay(15 * 60_000L)
                if (runCatching { com.apexvpn.client.data.AppConfig.refresh() }.getOrDefault(false) &&
                    VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started
                ) com.apexvpn.client.vpn.ApexVpnService.reload(this@ApexApp)
            }
        }
        // 3.19. Раз в 5 минут: предупредить о техработах на выбранном сервере и перестроить ядро,
        // когда техработы по расписанию начались или закончились (без скачивания — по уже полученному файлу)
        appScope.launch {
            var active = com.apexvpn.client.data.AppConfig.value.activeKey()
            while (true) {
                com.apexvpn.client.data.Maintenance.check(this@ApexApp)
                val now = com.apexvpn.client.data.AppConfig.value.activeKey()
                if (now != active) {
                    active = now
                    if (VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) com.apexvpn.client.vpn.ApexVpnService.reload(this@ApexApp)
                }
                kotlinx.coroutines.delay(5 * 60_000L)
            }
        }
        // 3.10. Шаблон «Скрыть VPN»: свежий список и новые приложения — при запуске
        appScope.launch {
            runCatching { com.apexvpn.client.data.HideTemplates.refresh() }
            if (runCatching { com.apexvpn.client.data.HideTemplates.autoApply(this@ApexApp) }.getOrDefault(0) > 0)
                com.apexvpn.client.vpn.ApexVpnService.reload(this@ApexApp)
        }

        val workingDir = File(filesDir, "work").apply { mkdirs() }
        val tempDir = File(cacheDir, "box").apply { mkdirs() }
        Libbox.setup(
            SetupOptions().also {
                it.basePath = filesDir.path
                it.workingPath = workingDir.path
                it.tempPath = tempDir.path
                // Обход бага Go на Android: https://github.com/golang/go/issues/68760
                it.fixAndroidStack = BuildConfig.DEBUG ||
                    Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1 ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                it.logMaxLines = 300
                it.debug = BuildConfig.DEBUG
            },
        )
        val stderr = File(workingDir, "stderr.log")
        CrashReporter.collectNativeCrash(this, stderr) // сбой ядра в прошлый раз
        runCatching { Libbox.redirectStderr(stderr.path) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService<NotificationManager>()!!
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_VPN, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, getString(R.string.notification_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        // Виджет и плитка следят за состоянием VPN и выбранным сервером
        appScope.launch {
            combine(VpnState.status, Subscription.state) { s, sub -> s to sub.selectedTag }
                .distinctUntilChanged()
                .collect {
                    ApexWidget.refresh(this@ApexApp)
                    com.apexvpn.client.vpn.NewWidgets.update(this@ApexApp)
                    com.apexvpn.client.vpn.WearBridge.publish(this@ApexApp)
                    com.apexvpn.client.vpn.WearBridge.publishServers(this@ApexApp)
                    ApexTileService.refresh(this@ApexApp)
                }
        }

        // 20. Иконка с зелёной точкой, пока VPN включён
        appScope.launch {
            combine(VpnState.status, AppSettings.state) { st, set ->
                (st == com.apexvpn.client.vpn.VpnStatus.Started || st == com.apexvpn.client.vpn.VpnStatus.Paused) to (set.iconStatus to set.appIcon)
            }
                .distinctUntilChanged()
                .collect { (on, _) -> com.apexvpn.client.data.AppIcons.syncStatus(this@ApexApp, on) }
        }

        // «Ощущения»: звук и вибрация при подключении
        appScope.launch {
            var last = VpnState.status.value
            VpnState.status.collect { st ->
                if (st == com.apexvpn.client.vpn.VpnStatus.Started && last != com.apexvpn.client.vpn.VpnStatus.Started &&
                    last != com.apexvpn.client.vpn.VpnStatus.Paused
                ) {
                    com.apexvpn.client.data.ConnectFeedback.onConnected(this@ApexApp)
                    com.apexvpn.client.data.MonthStats.connected()
                    // 382. Конфетти на первом и юбилейных подключениях
                    com.apexvpn.client.data.Celebrate.onConnected()
                    // Базы сайтов: если давно не проверяли (телефон был без фоновых задач) — проверяем сейчас
                    appScope.launch {
                        kotlinx.coroutines.delay(15_000)
                        if (com.apexvpn.client.data.RulesUpdater.update(this@ApexApp) is com.apexvpn.client.data.RulesUpdater.Result.Updated &&
                            VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started
                        ) com.apexvpn.client.vpn.ApexVpnService.reload(this@ApexApp)
                    }
                    // Задача «Видео» стала режимом автопилота «Скорость» (3.0) — сервер больше не закрепляется
                }
                // 271. Звук отключения
                // Состояния приходят «пачкой», и «Отключение» могло пропасть (Started → Stopped сразу) —
                // тогда звука не было. Теперь — при любом переходе из подключённого состояния
                if (st == com.apexvpn.client.vpn.VpnStatus.Stopped && last != com.apexvpn.client.vpn.VpnStatus.Stopped && last != com.apexvpn.client.vpn.VpnStatus.Starting) {
                    com.apexvpn.client.data.ConnectFeedback.onDisconnected(this@ApexApp)
                    com.apexvpn.client.data.WeekStats.endSession()
                    com.apexvpn.client.data.MonthStats.flush()
                    com.apexvpn.client.data.AutoRulesEngine.reset()
                }
                last = st
            }
        }

        // Ярлыки избранных серверов (долгое нажатие на иконку)
        appScope.launch {
            combine(AppSettings.state, Subscription.state) { st, sub -> st.favorites to sub.nodes.map { it.name } }
                .distinctUntilChanged()
                .collect {
                    com.apexvpn.client.vpn.Shortcuts.refresh(this@ApexApp)
                    com.apexvpn.client.vpn.WearBridge.publishServers(this@ApexApp)
                }
        }

        // 3.17.3: LTE-обход выбран вручную — раз в 5 минут смотрим, не заработали ли обычные серверы
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5 * 60_000L)
                if (VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) {
                    runCatching { com.apexvpn.client.data.WhiteList.checkManualLte(this@ApexApp) }
                }
            }
        }
        Prefs.initConnectCount()
        com.apexvpn.client.data.RulesUpdater.load(this)
        // DNS «Авто» (499/516) и белые списки (491): после подключения и при каждой смене сети
        appScope.launch {
            var job: kotlinx.coroutines.Job? = null
            combine(
                VpnState.status,
                com.apexvpn.client.vpn.NetworkMonitor.transport,
                AppSettings.state.map { it.autoWhitelist to it.dns },
                // 3.17.8: выбрали автопилот при включённом VPN — проверяем белые списки сразу,
                // а не через 5 минут по расписанию
                com.apexvpn.client.data.Subscription.state.map { it.selectedTag == com.apexvpn.client.data.AUTO_TAG },
            ) { st, tr, set, auto -> Triple(st, tr, set to auto) }
                .distinctUntilChanged()
                .collect { (st, tr, _) ->
                    job?.cancel()
                    if (st != com.apexvpn.client.vpn.VpnStatus.Started) return@collect
                    job = appScope.launch {
                        val app = this@ApexApp
                        var reload = false
                        val s = AppSettings.value
                        val mobile = tr.substringBefore(':') == "mobile" && com.apexvpn.client.data.AppConfig.value.on("whitelistAuto")
                        if (!mobile || !s.autoWhitelist) reload = com.apexvpn.client.data.WhiteList.reset(app)
                        // QUIC: на мобильной выключен, на Wi-Fi включён — сеть сменилась, обновляем ядро
                        if (AppSettings.quicApplied != null && AppSettings.quicBlocked() != AppSettings.quicApplied) reload = true
                        // Для этой сети уже известен лучший DNS — применяем сразу
                        if (s.dns == com.apexvpn.client.data.DnsChoice.Auto &&
                            com.apexvpn.client.data.DnsAuto.bestVariant().key != com.apexvpn.client.data.DnsAuto.applied
                        ) reload = true
                        if (reload) com.apexvpn.client.vpn.ApexVpnService.reload(app)
                        kotlinx.coroutines.delay(3000)
                        if (VpnState.status.value != com.apexvpn.client.vpn.VpnStatus.Started) return@launch
                        // 3.17.1: белые списки — первыми. Раньше сначала шёл замер DNS «Авто»
                        // (при белых списках все DNS не отвечают — это десятки секунд), и только
                        // потом проверка, поэтому автопилот «долго думал»
                        if (mobile && AppSettings.value.autoWhitelist &&
                            com.apexvpn.client.data.WhiteList.check(app)
                        ) com.apexvpn.client.vpn.ApexVpnService.reload(app)
                        // 3.17.3: на LTE-обходе (белые списки) замер DNS пропускаем — 11 запросов через медленный
                        // обход с лимитом трафика только задерживают интернет; DNS остаётся прежним
                        if (AppSettings.value.dns == com.apexvpn.client.data.DnsChoice.Auto && !AppSettings.whitelistAll() &&
                            com.apexvpn.client.data.DnsAuto.measure()) {
                            com.apexvpn.client.vpn.ApexVpnService.reload(app)
                        }
                        // Белые списки: на мобильной сети с автопилотом — проверка раз в 5 минут
                        while (mobile && AppSettings.value.autoWhitelist &&
                            VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started
                        ) {
                            // Первая проверка уже была выше — следующая по расписанию
                            kotlinx.coroutines.delay(if (com.apexvpn.client.data.WhiteList.suspected) 15_000L else if (com.apexvpn.client.data.WhiteList.active) 3 * 60_000L else 5 * 60_000L)
                            if (VpnState.status.value != com.apexvpn.client.vpn.VpnStatus.Started) break
                            if (com.apexvpn.client.data.WhiteList.check(app)) com.apexvpn.client.vpn.ApexVpnService.reload(app)
                        }
                    }
                }
        }
        // Режимы автопилота: после подключения и раз в 15 минут подбираем подходящие серверы
        com.apexvpn.client.data.AutoModes.load(this)
        appScope.launch {
            var job: kotlinx.coroutines.Job? = null
            combine(VpnState.status, AppSettings.state.map { it.autoMode }) { st, m -> st to m }
                .distinctUntilChanged()
                .collect { (st, m) ->
                    job?.cancel()
                    val mode = com.apexvpn.client.data.AutoModes.Mode.of(m)
                    if (st != com.apexvpn.client.vpn.VpnStatus.Started || !mode.measured) return@collect
                    job = appScope.launch {
                        val app = this@ApexApp
                        kotlinx.coroutines.delay(6000)
                        while (VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) {
                            if (Prefs.selectedTag == com.apexvpn.client.data.AUTO_TAG) {
                                val am = com.apexvpn.client.data.AutoModes
                                val stale = System.currentTimeMillis() - am.lastMeasured(app) > 15 * 60_000L ||
                                    (mode == com.apexvpn.client.data.AutoModes.Mode.Speed && System.currentTimeMillis() - am.lastSpeed(app) > 30 * 60_000L)
                                if (stale) am.measure(app, mode, force = false)
                                // Подходящие серверы изменились — ядро перезагружается один раз
                                val allowed = am.allowed(mode, Subscription.state.value.nodes)
                                val whitelist = com.apexvpn.client.data.WhiteList.active && AppSettings.value.autoWhitelist
                                if (!whitelist && allowed.isNotEmpty() && allowed.toSet() != AppSettings.autoApplied.toSet()) {
                                    com.apexvpn.client.vpn.ApexVpnService.reload(app)
                                }
                            }
                            kotlinx.coroutines.delay(15 * 60_000L)
                        }
                    }
                }
        }
        // DNS «Авто»: замеры через ядро, пока VPN включён
        appScope.launch {
            VpnState.status.collect { st ->
                runCatching {
                    if (st == com.apexvpn.client.vpn.VpnStatus.Started) io.nekohasekai.apexcore.Apexcore.setProbeProxy(Prefs.probePort, "apex", Prefs.xrayAuth)
                    else io.nekohasekai.apexcore.Apexcore.setProbeProxy(0, "", "")
                }
            }
        }
        // 413. Трафик по приложениям — пока VPN включён
        com.apexvpn.client.data.AppTraffic.init(this)
        com.apexvpn.client.data.ConnLog.init(this)
        appScope.launch {
            VpnState.status.collect { st ->
                if (st == com.apexvpn.client.vpn.VpnStatus.Started) com.apexvpn.client.data.AppTraffic.start(appScope)
                else if (st == com.apexvpn.client.vpn.VpnStatus.Stopped) com.apexvpn.client.data.AppTraffic.stop()
            }
        }
        BackgroundWork.schedule(this)
        // 259. Точные напоминания о конце подписки — при каждой новой дате окончания
        appScope.launch {
            Subscription.state.map { it.expireAt }.distinctUntilChanged().collect {
                runCatching { com.apexvpn.client.data.ExpiryAlerts.schedule(this@ApexApp) }
                runCatching { com.apexvpn.client.data.ExpiryAlerts.check(this@ApexApp) }
            }
        }
    }

    companion object {
        const val CHANNEL_VPN = "vpn_status"
        const val CHANNEL_ALERTS = "alerts"
        lateinit var instance: ApexApp
            private set

        val connectivity: ConnectivityManager
            get() = instance.getSystemService()!!
    }
}
