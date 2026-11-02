package com.apexvpn.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Windows 1.4: интерфейс как в приложении на Android — колонка «как телефон» по центру окна,
 * шапка APEX с плашкой статуса, большой щит, группы строк с цветными плашками-значками
 * и плавающая нижняя панель «VPN · Режим · Настройки».
 */
enum class Tab(val title: String, val icon: ImageVector) {
    Home("VPN", Icons.Rounded.PowerSettingsNew),
    Mode("Режим", Icons.Rounded.Public),
    Settings("Настройки", Icons.Rounded.Settings),
}

enum class Page(val title: String) {
    Servers("Серверы"), Routes("Свои списки и сайты"), Speed("Тест скорости"), Leak("Проверка утечек"),
    Log("Журнал соединений"), Appearance("Оформление"), Dns("DNS"), Core("Ядро"),
}

private val ColumnMax = 560.dp

@Composable
fun AppUi() {
    var tab by remember { mutableStateOf(Tab.Home) }
    val pages = remember { mutableStateListOf<Page>() }
    val sub by Subscription.state.collectAsState()
    val status by Engine.status.collectAsState()
    val open: (Page) -> Unit = { pages.add(it) }

    Box(Modifier.fillMaxSize()) {
        Backdrop(status == VpnStatus.On)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).widthIn(max = ColumnMax).fillMaxWidth()) {
                val key = pages.lastOrNull()?.name ?: tab.name
                AnimatedContent(key, transitionSpec = {
                    val ms = if (C.animations) 200 else 0
                    fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                }) { k ->
                    val page = Page.entries.firstOrNull { it.name == k }
                    if (page != null) {
                        PageScaffold(page.title, onBack = { pages.removeLastOrNull() }) { PageContent(page, open) }
                    } else if (sub.nodes.isEmpty() && tab != Tab.Settings) {
                        AddSubscription()
                    } else when (tab) {
                        Tab.Home -> HomeTab(open)
                        Tab.Mode -> ModeTab(open)
                        Tab.Settings -> SettingsTab(open)
                    }
                }
            }
            Box(Modifier.widthIn(max = ColumnMax).fillMaxWidth()) {
                BottomBar(tab) { tab = it; pages.clear() }
            }
        }
    }
}

@Composable
private fun PageContent(page: Page, open: (Page) -> Unit) {
    when (page) {
        Page.Servers -> ServersScreen()
        Page.Routes -> RoutesScreen()
        Page.Speed -> SpeedTestCard()
        Page.Leak -> LeakTestCard()
        Page.Log -> ConnLogCard()
        Page.Appearance -> AppearancePage()
        Page.Dns -> DnsPage()
        Page.Core -> CorePage()
    }
}

// ─── Каркас ───────────────────────────────────────────────────────────

/** Шапка как на Android: слово APEX и плашка «Защищено». */
@Composable
fun Header() {
    val status by Engine.status.collectAsState()
    val on = status == VpnStatus.On
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("APEX", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
        val c = when (status) { VpnStatus.On -> C.Cyan; VpnStatus.Connecting -> C.Warn; VpnStatus.Error -> C.Bad; else -> C.Dim }
        Row(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(7.dp))
            Text(
                when (status) { VpnStatus.On -> "Защищено"; VpnStatus.Connecting -> "Подключение"; VpnStatus.Error -> "Ошибка"; else -> if (on) "" else "Не защищено" },
                color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronLeft, "Назад", tint = C.Text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(title, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}

/** Нижняя навигация 2.0, как на Android: плавающая пилюля, выделение переезжает к вкладке. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.Surface)
                .border(1.dp, C.Outline, RoundedCornerShape(28.dp)).padding(6.dp),
        ) {
            val itemWidth = maxWidth / Tab.entries.size
            val offset by animateDpAsState(itemWidth * current.ordinal, if (C.animations) spring(dampingRatio = 0.75f, stiffness = 420f) else tween(0))
            Box(Modifier.offset(x = offset).width(itemWidth).height(54.dp).clip(RoundedCornerShape(22.dp)).background(C.SurfaceHigh))
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    val sel = t == current
                    val tint by animateColorAsState(if (sel) C.Accent else C.Dim)
                    Column(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(22.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(t.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(t.title, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = if (sel) C.Text else C.Dim)
                    }
                }
            }
        }
    }
}

// ─── Элементы в стиле Android ─────────────────────────────────────────

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(C.Radius)).background(C.Surface).border(1.dp, C.Outline, RoundedCornerShape(C.Radius)), content = content)
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 1.dp, color = C.Outline)
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = C.Text,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = C.Dim, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Цветная квадратная плашка со значком — как в настройках Android-версии. */
@Composable
fun IconTile(icon: ImageVector, color: Color = C.Accent, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = C.Dim)

@Composable
fun ApexSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(on, onChange, colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White, checkedTrackColor = C.Accent, checkedBorderColor = C.Accent,
        uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.SurfaceHigh, uncheckedBorderColor = C.SurfaceHigh,
    ))
}

@Composable
fun Check(on: Boolean) {
    if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
        Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Вкладка VPN ──────────────────────────────────────────────────────

@Composable
fun HomeTab(open: (Page) -> Unit) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clip(CircleShape).clickable(enabled = !connecting) { Engine.toggle() }) {
                Shield(on = on, connecting = connecting, size = 170)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (status) { VpnStatus.On -> "Защищено"; VpnStatus.Connecting -> "Подключение…"; VpnStatus.Error -> "Ошибка"; else -> "Не защищено" },
                color = when (status) { VpnStatus.Error -> C.Bad; VpnStatus.Off -> C.Dim; else -> C.Text }, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (on && connectedAt > 0) "${duration(now - connectedAt)}  ·  ↓ ${rate(speed.second)}" else "Нажмите на щит, чтобы подключиться",
                color = C.Dim, fontSize = 15.sp,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = C.Bad, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Bad.copy(alpha = 0.1f)).padding(12.dp))
                if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); kotlin.system.exitProcess(0) } }
                }
            }
            Spacer(Modifier.height(20.dp))

            val sel = sub.nodes.find { it.tag == s.selected }
            val autoNode = sub.nodes.find { it.tag == autoNow }
            val shown = sel ?: autoNode
            Group {
                ListRow(
                    if (sel == null) "Автопилот" else sel.title,
                    if (sel == null) (autoNode?.let { "Сейчас: ${it.title}" } ?: "Самый быстрый из ${sub.nodes.size}") + " · ${s.autoMode.emoji} ${s.autoMode.title}"
                    else sel.detail.ifBlank { sel.category },
                    leading = { CountryBadge(shown?.country) },
                    onClick = { open(Page.Servers) },
                ) {
                    val p = pings[shown?.tag]
                    if (p != null) Text(if (p > 0) "$p мс" else "—", color = pingColor(p), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp)); Chevron()
                }
                GroupDivider()
                ListRow(
                    s.routeMode.title, s.routeMode.subtitle,
                    leading = { IconTile(Icons.Rounded.AltRoute, C.Cyan) },
                    onClick = { Store.update { copy(routeMode = if (routeMode == RouteMode.Smart) RouteMode.All else RouteMode.Smart) }; Engine.reload() },
                ) { Tag(if (s.connectType == ConnectType.Tun) "TUN" else "Прокси") }
            }
            Spacer(Modifier.height(12.dp))
            SubscriptionCard(now)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SubscriptionCard(now: Long) {
    val sub by Subscription.state.collectAsState()
    val s by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Group {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sub.title.ifBlank { "Подписка" }, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Refresh, "Обновить", tint = if (busy) C.Accent else C.Dim,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = !busy) {
                        scope.launch { busy = true; runCatching { Subscription.refresh() }.onSuccess { Engine.reload() }; busy = false }
                    }.padding(6.dp),
                )
            }
            val left = if (sub.expireAt > 0) ((sub.expireAt * 1000 - now) / 86_400_000L).coerceAtLeast(0) else -1
            Text(
                listOfNotNull(
                    if (sub.total > 0) "${bytes(sub.used)} из ${bytes(sub.total)}" else "${bytes(sub.used)} · без лимита",
                    if (left >= 0) "осталось $left дн." else null,
                ).joinToString(" · "),
                color = C.Dim, fontSize = 14.sp,
            )
            if (sub.total > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth((sub.used.toFloat() / sub.total).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(C.Accent, C.Cyan))))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Автообновление — ${s.refreshHours} ч.", color = C.Dim, fontSize = 13.sp)
            if (sub.announce.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(sub.announce, color = C.Text, fontSize = 14.sp) }
        }
    }
}

// ─── Вкладка «Режим» ─────────────────────────────────────────────────

@Composable
fun ModeTab(open: (Page) -> Unit) {
    val s by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Режим", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 18.dp))
        Section("Что идёт через VPN")
        Group {
            RouteMode.entries.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                ListRow(m.title, m.subtitle, leading = { IconTile(if (m == RouteMode.Smart) Icons.Rounded.AltRoute else Icons.Rounded.Public, if (m == RouteMode.Smart) C.Cyan else C.Accent) },
                    onClick = { Store.update { copy(routeMode = m) }; Engine.reload() }) { Check(s.routeMode == m) }
            }
        }
        Section("Подключение к Windows")
        Group {
            ConnectType.entries.forEachIndexed { i, t ->
                if (i > 0) GroupDivider()
                ListRow(t.title, t.subtitle, leading = { IconTile(if (t == ConnectType.Tun) Icons.Rounded.Lan else Icons.Rounded.Security, if (t == ConnectType.Tun) Color(0xFFF59E0B) else Color(0xFF10B981)) },
                    onClick = { Store.update { copy(connectType = t) }; Engine.reload() }) { Check(s.connectType == t) }
            }
        }
        if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
            Text("Программа запущена без прав администратора — TUN не включится.", color = C.Warn, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
            Spacer(Modifier.height(6.dp))
            SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); kotlin.system.exitProcess(0) } }
        }
        Section("Свои правила")
        Group {
            val n = s.customLists.count { it.enabled }
            ListRow("Свои списки и сайты", if (n > 0) "Включено списков: $n · сайтов: ${s.customDomains.size + s.directDomains.size}" else "Шаблоны: Нейросети, Игры, Работа, Соцсети",
                leading = { IconTile(Icons.Rounded.ReceiptLong, Color(0xFF8B5CF6)) }, onClick = { open(Page.Routes) }) { Chevron() }
            GroupDivider()
            ListRow("Сайты стран напрямую", if (s.directCountries.isEmpty()) "Казахстан, Беларусь, Армения, Турция…" else DirectCountries.all.filter { it.id in s.directCountries }.joinToString(", ") { it.title },
                leading = { IconTile(Icons.Rounded.Flag, Color(0xFFEF4444)) }, onClick = { open(Page.Routes) }) { Chevron() }
            GroupDivider()
            ListRow("Блокировка рекламы", "Реклама и трекеры не загружаются", leading = { IconTile(Icons.Rounded.Block, Color(0xFFF43F5E)) },
                onClick = { Store.update { copy(adBlock = !adBlock) }; Engine.reload() }) { ApexSwitch(s.adBlock) { v -> Store.update { copy(adBlock = v) }; Engine.reload() } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Вкладка «Настройки» ─────────────────────────────────────────────

@Composable
fun SettingsTab(open: (Page) -> Unit) {
    val s by Store.settings.collectAsState()
    val sub by Subscription.state.collectAsState()
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Настройки", color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 18.dp))

        Section("Подписка")
        Group {
            ListRow(sub.title.ifBlank { "Подписка" }, Store.value.subscriptionUrl.let { if (it.length > 40) it.take(40) + "…" else it }.ifBlank { "Не добавлена" },
                leading = { IconTile(Icons.Rounded.Refresh, C.Accent) },
                onClick = { scope.launch { runCatching { Subscription.refresh() }.onSuccess { Engine.reload() } } }) { Tag("Обновить") }
            GroupDivider()
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Schedule, Color(0xFF0EA5E9)); Spacer(Modifier.width(16.dp))
                    Text("Автообновление", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                val opts = listOf(1, 6, 12, 24)
                Segmented(opts.map { "$it ч" }, opts.indexOf(s.refreshHours).coerceAtLeast(0)) { i -> Store.update { copy(refreshHours = opts[i]) } }
            }
            if (sub.nodes.isNotEmpty()) {
                GroupDivider()
                ListRow(if (confirmRemove) "Нажмите ещё раз, чтобы удалить" else "Удалить подписку", titleColor = C.Bad, leading = { IconTile(Icons.Rounded.Delete, C.Bad) },
                    onClick = { if (confirmRemove) { Engine.disconnect(); Subscription.remove(); confirmRemove = false } else confirmRemove = true })
            }
        }

        Section("Подключение")
        Group {
            ListRow("Режим автопилота", "${s.autoMode.emoji} ${s.autoMode.title} — ${s.autoMode.subtitle}", leading = { IconTile(Icons.Rounded.AutoMode, Color(0xFF6366F1)) }, onClick = { open(Page.Servers) }) { Chevron() }
            GroupDivider()
            ListRow("DNS", if (s.dns == DnsChoice.Auto) "Авто · ${DnsAuto.best.title}" else s.dns.title, leading = { IconTile(Icons.Rounded.Dns, Color(0xFF14B8A6)) }, onClick = { open(Page.Dns) }) { Chevron() }
            GroupDivider()
            ListRow("Ядро", "${s.core.title} — ${s.core.subtitle}", leading = { IconTile(Icons.Rounded.Memory, Color(0xFF64748B)) }, onClick = { open(Page.Core) }) { Chevron() }
            GroupDivider()
            ListRow("Автопилот без России и LTE-обхода", "Особые серверы — только вручную", leading = { IconTile(Icons.Rounded.Public, Color(0xFFF97316)) },
                onClick = { Store.update { copy(skipSpecial = !skipSpecial) }; Engine.reload() }) { ApexSwitch(s.skipSpecial) { v -> Store.update { copy(skipSpecial = v) }; Engine.reload() } }
        }

        Section("Инструменты")
        Group {
            ListRow("Тест скорости", "Пинг, загрузка и отдача, как в Speedtest", leading = { IconTile(Icons.Rounded.Speed, Color(0xFF3B82F6)) }, onClick = { open(Page.Speed) }) { Chevron() }
            GroupDivider()
            ListRow("Проверка утечек", "Видят ли сайты ваш настоящий IP и DNS", leading = { IconTile(Icons.Rounded.Security, Color(0xFF10B981)) }, onClick = { open(Page.Leak) }) { Chevron() }
            GroupDivider()
            ListRow("Журнал соединений", "Какие сайты шли через VPN, а какие напрямую", leading = { IconTile(Icons.Rounded.ReceiptLong, Color(0xFF8B5CF6)) }, onClick = { open(Page.Log) }) { Chevron() }
        }

        Section("Оформление")
        Group {
            ListRow("Дизайн и цвет", "${s.design.title} · ${s.accent.title}", leading = { IconTile(Icons.Rounded.Palette, Color(0xFFEC4899)) }, onClick = { open(Page.Appearance) }) { Chevron() }
        }

        Section("Программа")
        Group {
            ListRow("Запускать вместе с Windows", "В трее, без окна", onClick = { val v = !s.autostart; Store.update { copy(autostart = v) }; WinSys.setAutostart(v) }) {
                ApexSwitch(s.autostart) { v -> Store.update { copy(autostart = v) }; WinSys.setAutostart(v) }
            }
            GroupDivider()
            ListRow("Подключаться при запуске", onClick = { Store.update { copy(connectOnStart = !connectOnStart) } }) { ApexSwitch(s.connectOnStart) { v -> Store.update { copy(connectOnStart = v) } } }
            GroupDivider()
            ListRow("Сворачивать в трей", "Значок у часов: подключить, сервер, режим", onClick = { Store.update { copy(minimizeToTray = !minimizeToTray) } }) { ApexSwitch(s.minimizeToTray) { v -> Store.update { copy(minimizeToTray = v) } } }
            GroupDivider()
            ListRow("Проверять обновления", "На GitHub", onClick = { Store.update { copy(checkUpdates = !checkUpdates) } }) { ApexSwitch(s.checkUpdates) { v -> Store.update { copy(checkUpdates = v) } } }
        }

        Section("О программе")
        Group {
            ListRow("APEX VPN для Windows ${AppInfo.VERSION}", update ?: "sing-box 1.13 · Xray 26 · общий движок с Android", leading = { IconTile(Icons.Rounded.Info, C.Accent) },
                onClick = { WinSys.open("https://github.com/${AppInfo.REPO}/releases/latest") })
            GroupDivider()
            ListRow("Проверить обновления", leading = { IconTile(Icons.Rounded.SystemUpdate, Color(0xFF22C55E)) },
                onClick = { scope.launch { update = Updates.check()?.let { "Доступна версия ${it.tag} — нажмите, чтобы скачать" } ?: "У вас последняя версия" } }) { Chevron() }
            GroupDivider()
            ListRow("Поддержка", "@Apexx_support в Telegram", leading = { IconTile(Icons.Rounded.SupportAgent, Color(0xFF0EA5E9)) }, onClick = { WinSys.open(AppInfo.SUPPORT) }) { Chevron() }
            GroupDivider()
            ListRow("Папка журналов", "Для отчёта в поддержку", leading = { IconTile(Icons.Rounded.FolderOpen, Color(0xFF64748B)) }, onClick = { WinSys.openFolder(Paths.data) }) { Chevron() }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Страницы настроек ───────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppearancePage() {
    val s by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Дизайн")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Design.entries.forEach { d -> DesignCard(d, s.design == d) { Store.update { copy(design = d) } } }
        }
        Text(s.design.subtitle, color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        Section("Цвет")
        Group {
            AccentChoice.entries.forEachIndexed { i, a ->
                if (i > 0) GroupDivider()
                val c1 = a.c1 ?: s.design.palette.accent
                val c2 = a.c2 ?: s.design.palette.accent2
                ListRow(a.title, leading = { Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(c1, c2)))) },
                    onClick = { Store.update { copy(accent = a) } }) { Check(s.accent == a) }
            }
        }
        Section("Плавность")
        Group {
            ListRow("Анимации", "Живой фон, волны от щита, плавная смена экранов", onClick = { Store.update { copy(animations = !animations) } }) {
                ApexSwitch(s.animations) { v -> Store.update { copy(animations = v) } }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun DnsPage() {
    val s by Store.settings.collectAsState()
    val results by DnsAuto.results.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("DNS для зарубежных сайтов")
        Group {
            DnsChoice.entries.forEachIndexed { i, d ->
                if (i > 0) GroupDivider()
                ListRow(d.title, if (d == DnsChoice.Auto) "Самый быстрый из Cloudflare, Google, Quad9 и Mullvad" else null,
                    leading = { IconTile(Icons.Rounded.Dns, if (s.dns == d) C.Accent else C.SurfaceHigh) },
                    onClick = {
                        Store.update { copy(dns = d) }
                        if (d == DnsChoice.Auto) scope.launch { DnsAuto.measure(); Engine.reload() } else Engine.reload()
                    }) { Check(s.dns == d) }
            }
        }
        if (s.dns == DnsChoice.Auto) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Text("Сейчас: ${DnsAuto.best.title}", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                results.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(r.choice.title, color = if (r.choice == DnsAuto.best) C.AccentText else C.Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (r.ms > 0) "${r.ms} мс" else "нет ответа", color = pingColor(r.ms), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Проверить сейчас") { scope.launch { val b = DnsAuto.best; DnsAuto.measure(); if (DnsAuto.best != b) Engine.reload() } }
            }
        }
    }
}

@Composable
fun CorePage() {
    val s by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Какое ядро подключает серверы")
        Group {
            CoreChoice.entries.forEachIndexed { i, c ->
                if (i > 0) GroupDivider()
                ListRow(c.title, c.subtitle, leading = { IconTile(Icons.Rounded.Memory, if (s.core == c) C.Accent else C.SurfaceHigh) },
                    onClick = { Store.update { copy(core = c) }; Engine.reload() }) { Check(s.core == c) }
            }
        }
    }
}
