package com.apexvpn.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Windows 2.0: свой дизайн для компьютера в стиле Windows 11 — боковое меню навигации,
 * как в «Параметрах», широкий главный экран-дашборд, плоские карточки и мелкие значки.
 */
enum class NavItem(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home),
    Servers("Серверы", Icons.Rounded.Dns),
    Routes("Маршруты", Icons.Rounded.AltRoute),
    Services("Сервисы", Icons.Rounded.Apps),
    Tools("Инструменты", Icons.Rounded.Speed),
    Look("Оформление", Icons.Rounded.Palette),
    Settings("Настройки", Icons.Rounded.Settings),
}

@Composable
fun FluentApp() {
    var nav by remember { mutableStateOf(NavItem.Home) }
    val pages = remember { mutableStateListOf<Page>() }
    val sub by Subscription.state.collectAsState()
    val status by Engine.status.collectAsState()
    val open: (Page) -> Unit = { pages.add(it) }

    Box(Modifier.fillMaxSize()) {
        Backdrop(status == VpnStatus.On)
        PhotoBackdrop()
        CountryPhotoBackdrop()
        Row(Modifier.fillMaxSize()) {
            NavPane(nav) { nav = it; pages.clear() }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, end = 28.dp)) {
                // Заголовок страницы с «хлебными крошками», как в «Параметрах» Windows
                Row(Modifier.padding(top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(nav.title, color = if (pages.isEmpty()) C.Text else C.Dim, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = pages.isNotEmpty()) { pages.clear() })
                    pages.forEachIndexed { i, p ->
                        Icon(Icons.Rounded.ChevronRight, null, tint = C.Dim, modifier = Modifier.padding(horizontal = 6.dp).size(22.dp))
                        Text(p.title, color = if (i == pages.lastIndex) C.Text else C.Dim, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { while (pages.size > i + 1) pages.removeAt(pages.lastIndex) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val key = pages.lastOrNull()?.name ?: nav.name
                    AnimatedContent(key, transitionSpec = {
                        val ms = if (C.animations) 180 else 0
                        (fadeIn(tween(ms)) + slideInVertically(tween(ms)) { it / 24 }) togetherWith fadeOut(tween(ms / 2))
                    }) { k ->
                        val page = Page.entries.firstOrNull { it.name == k }
                        Box(Modifier.fillMaxSize().widthIn(max = 1100.dp)) {
                            when {
                                page != null -> Column(Modifier.fillMaxSize()) { FluentPage(page, open) }
                                sub.nodes.isEmpty() && nav != NavItem.Settings && nav != NavItem.Look -> AddSubscription()
                                else -> when (nav) {
                                    NavItem.Home -> FluentHome(open)
                                    NavItem.Servers -> ServersScreen()
                                    NavItem.Routes -> ModeTab(open)
                                    NavItem.Services -> ServicesPage()
                                    NavItem.Tools -> ToolsScreen()
                                    NavItem.Look -> AppearancePage()
                                    NavItem.Settings -> SettingsTab(open)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FluentPage(page: Page, open: (Page) -> Unit) {
    when (page) {
        Page.Servers -> ServersScreen()
        Page.Routes -> RoutesScreen()
        Page.Speed -> SpeedTestCard()
        Page.Leak -> LeakTestCard()
        Page.Log -> ConnLogCard()
        Page.Appearance -> AppearancePage()
        Page.Dns -> DnsPage()
        Page.Core -> CorePage()
        Page.Services -> ServicesPage()
        Page.Advanced -> AdvancedPage()
        Page.SelfCheck -> SelfCheckPage()
        Page.Ping -> PingSettingsPage()
        Page.Exclude -> ExcludeAppsPage()
        Page.Help -> HelpPage()
        Page.WhatsNew -> WhatsNewPage()
        Page.Update -> UpdatePage()
        Page.KillSwitch -> KillSwitchPage()
        Page.Share -> SharePage()
        Page.Limits -> LimitsPage()
    }
}

/** Боковое меню: значок + подпись, выделенный пункт — с акцентной чертой слева. */
@Composable
private fun NavPane(current: NavItem, onSelect: (NavItem) -> Unit) {
    val status by Engine.status.collectAsState()
    Column(Modifier.width(250.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 14.dp)) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(C.Accent, C.Cyan))), contentAlignment = Alignment.Center) {
                Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("APEX VPN", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        NavItem.entries.forEach { item ->
            if (item == NavItem.Settings) Spacer(Modifier.weight(1f))
            NavRow(item, item == current) { onSelect(item) }
        }
        Spacer(Modifier.height(8.dp))
        // Внизу: состояние подключения, как значок сети в Windows
        val on = status == VpnStatus.On
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Surface.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(when (status) { VpnStatus.On -> C.Good; VpnStatus.Connecting -> C.Warn; VpnStatus.Error -> C.Bad; else -> C.Dim }))
            Spacer(Modifier.width(10.dp))
            Text(if (on) "Защищено" else if (status == VpnStatus.Connecting) "Подключение…" else "Не защищено", color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(AppInfo.VERSION, color = C.Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavRow(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when { selected -> C.Text.copy(alpha = if (C.Light) 0.06f else 0.08f); hovered -> C.Text.copy(alpha = 0.05f); else -> Color.Transparent },
    )
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .hoverable(hover).clickable(interactionSource = hover, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.Accent else Color.Transparent))
        Spacer(Modifier.width(13.dp))
        Icon(item.icon, null, tint = if (selected) C.Accent else C.Text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(item.title, color = C.Text, fontSize = 14.sp)
    }
}

// ─── Главная: дашборд ─────────────────────────────────────────────────

@Composable
private fun FluentHome(open: (Page) -> Unit) {
    val status by Engine.status.collectAsState()
    val error by Engine.error.collectAsState()
    val connectedAt by Engine.connectedAt.collectAsState()
    val speed by Engine.speed.collectAsState()
    val autoNow by Engine.autoNow.collectAsState()
    val s by Store.settings.collectAsState()
    val sub by Subscription.state.collectAsState()
    val pings by Pinger.pings.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val on = status == VpnStatus.On
    val connecting = status == VpnStatus.Connecting
    val sel = sub.nodes.find { it.tag == s.selected }
    val autoNode = sub.nodes.find { it.tag == autoNow }
    val shown = sel ?: autoNode

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 820.dp
        val left: @Composable ColumnScope.() -> Unit = {
            // Большая карточка подключения
            FCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PowerButton(on, connecting) { Engine.toggle() }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (status) { VpnStatus.On -> "Защищено"; VpnStatus.Connecting -> "Подключение…"; VpnStatus.Error -> "Ошибка"; else -> "Не защищено" },
                            color = when (status) { VpnStatus.Error -> C.Bad; else -> C.Text }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (on) "Трафик идёт через ${shown?.title ?: "сервер"}" else "Нажмите кнопку, чтобы подключиться",
                            color = C.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (on) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Metric(Icons.Rounded.Timer, if (connectedAt > 0) duration(now - connectedAt) else "—")
                                Metric(Icons.Rounded.ArrowDownward, rate(speed.second))
                                Metric(Icons.Rounded.ArrowUpward, rate(speed.first))
                            }
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = C.Bad, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(C.Bad.copy(alpha = 0.1f)).padding(10.dp))
                    if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); kotlin.system.exitProcess(0) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (s.homeStyle != "shield") {
                FCard(Modifier.fillMaxWidth()) { HomeGlobe(shown, on, connecting) }
                Spacer(Modifier.height(12.dp))
            }
            // Быстрые настройки — как плитки в центре уведомлений Windows
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("Умный режим", "Российские сайты напрямую", s.routeMode == RouteMode.Smart, Icons.Rounded.AltRoute, Modifier.weight(1f)) {
                    Store.update { copy(routeMode = if (routeMode == RouteMode.Smart) RouteMode.All else RouteMode.Smart) }; Engine.reload()
                }
                QuickTile("TUN", if (s.connectType == ConnectType.Tun) "Весь компьютер" else "Сейчас: системный прокси", s.connectType == ConnectType.Tun, Icons.Rounded.Dns, Modifier.weight(1f)) {
                    Store.update { copy(connectType = if (connectType == ConnectType.Tun) ConnectType.Proxy else ConnectType.Tun) }; Engine.reload()
                }
                QuickTile("Без рекламы", "Блокировка рекламы", s.adBlock, Icons.Rounded.Apps, Modifier.weight(1f)) {
                    Store.update { copy(adBlock = !adBlock) }; Engine.reload()
                }
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            Group {
                ListRow(
                    if (sel == null) "Автопилот" else sel.title,
                    if (sel == null) (autoNode?.let { "Сейчас: ${it.title}" } ?: "Самый быстрый из ${sub.nodes.size}") + " · ${s.autoMode.emoji} ${s.autoMode.title}" else sel.detail.ifBlank { sel.category },
                    leading = { CountryBadge(shown?.country) },
                    onClick = { open(Page.Servers) },
                ) {
                    val p = pings[shown?.tag]
                    if (p != null) Text(if (p > 0) "$p мс" else "—", color = pingColor(p), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp)); Chevron()
                }
            }
            Spacer(Modifier.height(12.dp))
            HomeModes(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AnnounceCard(sub.announce)
            FCard(Modifier.fillMaxWidth()) {
                Text(sub.title.ifBlank { "Подписка" }, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val leftDays = if (sub.expireAt > 0) ((sub.expireAt * 1000 - now) / 86_400_000L).coerceAtLeast(0) else -1
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(if (sub.total > 0) "${bytes(sub.used)} из ${bytes(sub.total)}" else "${bytes(sub.used)} · без лимита", if (leftDays >= 0) "осталось $leftDays дн." else null).joinToString(" · "),
                    color = C.Dim, fontSize = 12.sp,
                )
                if (sub.total > 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.SurfaceHigh)) {
                        Box(Modifier.fillMaxWidth((sub.used.toFloat() / sub.total).coerceIn(0f, 1f)).fillMaxHeight().background(C.Accent))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Автообновление — ${s.refreshHours} ч.", color = C.Dim, fontSize = 12.sp)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.25f), content = left)
                Column(Modifier.weight(1f), content = right)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                left(); Spacer(Modifier.height(12.dp)); right()
            }
        }
    }
}

/** Плоская карточка Windows 11: скругление 8, тонкая рамка. */
@Composable
fun FCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(C.Surface.copy(alpha = 0.92f)).border(1.dp, C.Outline, RoundedCornerShape(8.dp)).padding(20.dp), content = content)
}

/** Круглая кнопка питания: акцентная заливка, когда подключено. */
@Composable
private fun PowerButton(on: Boolean, busy: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val fill by animateColorAsState(if (on) C.Accent else if (hovered) C.SurfaceHigh else C.Surface)
    val ring by animateColorAsState(if (on) C.Accent else if (busy) C.Warn else C.Outline.copy(alpha = 0.6f))
    Box(
        Modifier.size(112.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape)
            .hoverable(hover).clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, if (on) "Отключить" else "Подключить", tint = if (on) (if (C.Light) Color.White else Color.Black) else C.Text, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun Metric(icon: ImageVector, value: String) {
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(C.SurfaceHigh).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.Dim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, color = C.Text, fontSize = 13.sp)
    }
}

/** Плитка быстрых настроек: включённая — в цвете акцента. */
@Composable
private fun QuickTile(title: String, subtitle: String, on: Boolean, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) C.Accent else C.Surface.copy(alpha = 0.92f))
    val fg = if (on) (if (C.Light) Color.White else Color.Black) else C.Text
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(8.dp)).background(bg).border(1.dp, if (on) Color.Transparent else C.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = fg.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
