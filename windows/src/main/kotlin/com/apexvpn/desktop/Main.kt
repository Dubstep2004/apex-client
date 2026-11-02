package com.apexvpn.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import kotlin.system.exitProcess

private var instanceLock: FileLock? = null

/** Одна копия программы: вторая просто закрывается (первая уже в трее). */
private fun singleInstance(): Boolean = runCatching {
    val ch = RandomAccessFile(File(Paths.data, "app.lock"), "rw").channel
    instanceLock = ch.tryLock()
    instanceLock != null
}.getOrDefault(true)

fun main(args: Array<String>) {
    if (!singleInstance()) exitProcess(0)
    WinSys.cleanupStaleProxy()
    // Kill Switch остался от прошлого запуска (программу закрыли) — открываем интернет и переподключаемся
    val killSwitchWas = KillSwitch.startupCleanup()
    val reconnectAfterUpdate = Updater.consumeReconnect()
    // «Что нового»: версия сменилась (у тех, кто обновился с 2.2 и раньше, seenVersion ещё нет)
    val showNewsAtStart = Store.value.seenVersion != AppInfo.VERSION &&
        (Store.value.seenVersion.isNotBlank() || Store.value.subscriptionUrl.isNotBlank())
    if (Store.value.seenVersion != AppInfo.VERSION) Store.update { copy(seenVersion = AppInfo.VERSION) }
    Runtime.getRuntime().addShutdownHook(Thread { KillSwitch.release(); Engine.shutdown() })
    val startHidden = "--tray" in args
    C.apply(Store.value)
    // Галочка «Запускать вместе с Windows» в установщике — показываем её и в настройках
    if (WinSys.autostartEnabled() != Store.value.autostart) Store.update { copy(autostart = WinSys.autostartEnabled()) }

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val status by Engine.status.collectAsState()
        val s by Store.settings.collectAsState()
        val sub by Subscription.state.collectAsState()
        val state = rememberWindowState(size = when (Store.value.layout) { "windows" -> DpSize(1120.dp, 740.dp); "phone" -> DpSize(500.dp, 760.dp); else -> DpSize(1020.dp, 720.dp) }, position = WindowPosition.PlatformDefault)

        var showNews by remember { mutableStateOf(showNewsAtStart) }
        val upd by Updater.state.collectAsState()
        val speed by Engine.speed.collectAsState()
        val ksOn by KillSwitch.engaged.collectAsState()
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        fun quit() {
            KillSwitch.release()
            Engine.shutdown()
            exitApplication()
        }

        // Запуск: подписка, подключение, обновления
        val trayState = rememberTrayState()
        // Звуки подключения и уведомление, если VPN неожиданно отключился
        LaunchedEffect(Unit) {
            var prev = Engine.status.value
            Engine.status.collect { st ->
                if (st == VpnStatus.On && prev != VpnStatus.On) Sounds.play(true)
                if (prev == VpnStatus.On && st != VpnStatus.On) {
                    Sounds.play(false)
                    if (st == VpnStatus.Error) trayState.sendNotification(Notification("APEX VPN отключился",
                        if (KillSwitch.engaged.value) "Kill Switch: интернет закрыт, пока VPN не переподключится" else Engine.error.value ?: "Ядро остановилось",
                        Notification.Type.Warning))
                }
                // «Ставить само»: обновление скачано — ставим, когда VPN выключили
                if (st == VpnStatus.Off && Store.value.updateMode == "auto") Updater.state.value.ready?.let { Updater.install(it) }
                prev = st
            }
        }
        // Напоминания о конце подписки — раз в час
        LaunchedEffect(Unit) {
            while (true) {
                ExpiryAlerts.due()?.let { trayState.sendNotification(Notification("APEX VPN", it, Notification.Type.Info)) }
                delay(60 * 60_000L)
            }
        }
        // Обновления программы: через 30 с после запуска, потом раз в 6 часов
        LaunchedEffect(Unit) {
            delay(30_000)
            var told = ""
            while (true) {
                if (Store.value.checkUpdates) {
                    val info = runCatching { Updater.check() }.getOrNull()
                    if (info != null) {
                        if (Store.value.updateMode == "auto") {
                            val f = runCatching { Updater.download(info) }.getOrNull()
                            if (f != null && !Engine.running) Updater.install(f)
                            if (f != null && told != info.tag) trayState.sendNotification(Notification("APEX VPN ${info.tag}", "Обновление скачано — установится, когда вы отключите VPN", Notification.Type.Info))
                        } else if (told != info.tag) {
                            trayState.sendNotification(Notification("Доступна APEX VPN ${info.tag}", "Меню значка у часов → «Обновить до ${info.tag}» — одна кнопка", Notification.Type.Info))
                        }
                        told = info.tag
                    }
                }
                delay(6 * 60 * 60_000L)
            }
        }
        LaunchedEffect(Unit) {
            if (Subscription.needsRefresh()) runCatching { Subscription.refresh() }
            if (Store.value.dns == DnsChoice.Auto) runCatching { DnsAuto.measure() }
            if ((s.connectOnStart || reconnectAfterUpdate || (killSwitchWas && s.killSwitch)) && sub.nodes.isNotEmpty()) Engine.connect()
            runCatching { Pinger.pingAll() }
            if (Rules.due() && runCatching { Rules.update() }.getOrDefault(false)) Engine.reload()
            while (true) {
                delay(5 * 60_000L)
                if (!Engine.running && Subscription.needsRefresh()) runCatching { Subscription.refresh() }
                if (!Engine.running && Rules.due()) runCatching { Rules.update() }
                // DNS «Авто»: новый замер раз в 30 минут; сменился лучший — переподключаемся
                if (Store.value.dns == DnsChoice.Auto && DnsAuto.stale()) {
                    val before = DnsAuto.best
                    runCatching { DnsAuto.measure() }
                    if (DnsAuto.best != before) Engine.reload()
                }
            }
        }

        Tray(
            state = trayState,
            icon = painterResource(if (status == VpnStatus.On) "tray_on.png" else "tray_off.png"),
            tooltip = "APEX VPN — " + when {
                upd.progress >= 0 -> "загрузка обновления ${upd.progress}%"
                ksOn -> "Kill Switch: интернет закрыт"
                status == VpnStatus.On -> "защищено · ↓ ${rate(speed.second)}  ↑ ${rate(speed.first)}"
                status == VpnStatus.Connecting -> "подключение…"
                else -> "не защищено"
            },
            onAction = { visible = true },
            menu = {
                Item("Открыть APEX VPN", onClick = { visible = true })
                if (ksOn) Item("Kill Switch: вернуть интернет без VPN", onClick = { KillSwitch.release() })
                upd.available?.let { a ->
                    Item(if (upd.progress >= 0) "Загрузка обновления ${upd.progress}%" else "Обновить до ${a.tag}",
                        enabled = upd.progress < 0, onClick = { scope.launch { Updater.updateNow() } })
                }
                Separator()
                Item(if (Engine.running) "Отключить" else "Подключить", onClick = { Engine.toggle() })
                Menu("Сервер") {
                    CheckboxItem("Автопилот", checked = s.selected == "auto", onCheckedChange = { Engine.select("auto") })
                    sub.nodes.take(25).forEach { n ->
                        CheckboxItem(listOfNotNull(n.country, n.title).joinToString(" "), checked = s.selected == n.tag, onCheckedChange = { Engine.select(n.tag) })
                    }
                }
                Menu("Маршрут") {
                    RouteMode.entries.forEach { m ->
                        CheckboxItem(m.title, checked = s.routeMode == m, onCheckedChange = { Store.update { copy(routeMode = m) }; Engine.reload() })
                    }
                }
                Menu("Подключение") {
                    ConnectType.entries.forEach { t ->
                        CheckboxItem(t.title, checked = s.connectType == t, onCheckedChange = { Store.update { copy(connectType = t) }; Engine.reload() })
                    }
                }
                Menu("Вид окна") {
                    CheckboxItem("Первый (классический)", checked = s.layout == "classic", onCheckedChange = { Store.update { copy(layout = "classic") } })
                    CheckboxItem("Windows 11", checked = s.layout == "windows", onCheckedChange = { Store.update { copy(layout = "windows") } })
                    CheckboxItem("Как на телефоне", checked = s.layout == "phone", onCheckedChange = { Store.update { copy(layout = "phone") } })
                }
                CheckboxItem("Звук подключения", checked = s.soundOn, onCheckedChange = { v -> Store.update { copy(soundOn = v) } })
                Separator()
                Item("Выход", onClick = { quit() })
            },
        )

        Window(
            onCloseRequest = { if (Store.value.minimizeToTray) visible = false else quit() },
            visible = visible,
            state = state,
            title = "APEX VPN",
            icon = painterResource("icon.png"),
        ) {
            window.minimumSize = java.awt.Dimension(420, 680)
            // Заголовок окна в цвет темы (тёмный у тёмных дизайнов) — Windows 10 20H1+ / 11
            LaunchedEffect(C.Light) { WinSys.darkTitleBar(window.windowHandle, !C.Light) }
            AppUi()
        }

        // После обновления — что изменилось в этой версии
        if (showNews && visible) {
            androidx.compose.ui.window.DialogWindow(
                onCloseRequest = { showNews = false },
                state = androidx.compose.ui.window.rememberDialogState(size = DpSize(520.dp, 560.dp)),
                title = "Что нового в APEX VPN ${AppInfo.VERSION}",
                icon = painterResource("icon.png"),
            ) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(C.Base)) {
                    WhatsNewLatest { showNews = false }
                }
            }
        }
    }
}
