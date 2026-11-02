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
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
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
    Runtime.getRuntime().addShutdownHook(Thread { Engine.shutdown() })
    val startHidden = "--tray" in args
    C.apply(Store.value)
    // Галочка «Запускать вместе с Windows» в установщике — показываем её и в настройках
    if (WinSys.autostartEnabled() != Store.value.autostart) Store.update { copy(autostart = WinSys.autostartEnabled()) }

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val status by Engine.status.collectAsState()
        val s by Store.settings.collectAsState()
        val sub by Subscription.state.collectAsState()
        val state = rememberWindowState(size = DpSize(1020.dp, 720.dp), position = WindowPosition.PlatformDefault)

        fun quit() {
            Engine.shutdown()
            exitApplication()
        }

        // Запуск: подписка, подключение, обновления
        LaunchedEffect(Unit) {
            if (Subscription.needsRefresh()) runCatching { Subscription.refresh() }
            if (Store.value.dns == DnsChoice.Auto) runCatching { DnsAuto.measure() }
            if (s.connectOnStart && sub.nodes.isNotEmpty()) Engine.connect()
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
            icon = painterResource(if (status == VpnStatus.On) "tray_on.png" else "tray_off.png"),
            tooltip = "APEX VPN — " + when (status) { VpnStatus.On -> "защищено"; VpnStatus.Connecting -> "подключение…"; else -> "не защищено" },
            onAction = { visible = true },
            menu = {
                Item("Открыть APEX VPN", onClick = { visible = true })
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
            window.minimumSize = java.awt.Dimension(860, 600)
            AppUi()
        }
    }
}
