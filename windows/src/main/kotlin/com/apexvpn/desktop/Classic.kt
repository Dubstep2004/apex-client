package com.apexvpn.desktop
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Первый дизайн Windows (1.0–1.3): узкая боковая панель с вкладками, щит в центре,
 * карточки сервера и подписки, настройки одной страницей. Вид по умолчанию.
 */
enum class ClassicTab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home), Servers("Серверы", Icons.Rounded.Dns), Routes("Маршруты", Icons.Rounded.Route), Tools("Инструменты", Icons.Rounded.Speed), Settings("Настройки", Icons.Rounded.Settings)
}

@Composable
fun ClassicApp() {
    var tab by remember { mutableStateOf(ClassicTab.Home) }
    val pages = remember { androidx.compose.runtime.mutableStateListOf<Page>() }
    val open: (Page) -> Unit = { pages.add(it) }
    val sub by Subscription.state.collectAsState()
    val status by Engine.status.collectAsState()
    Box(Modifier.fillMaxSize()) {
    Backdrop(status == VpnStatus.On)
    PhotoBackdrop()
    CountryPhotoBackdrop()
    Row(Modifier.fillMaxSize()) {
        // Боковая панель
        Column(
            Modifier.width(92.dp).fillMaxHeight().background(C.Surface.copy(alpha = 0.85f)).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("APEX", color = C.Text, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(26.dp))
            ClassicTab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable { tab = t; pages.clear() }.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) C.Accent.copy(alpha = 0.25f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, t.title, tint = if (on) C.Text else C.Dim) }
                    Spacer(Modifier.height(4.dp))
                    Text(t.title, color = if (on) C.Text else C.Dim, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // Плавная смена вкладок (выключается вместе с анимациями)
            val page = pages.lastOrNull()
            if (page != null) {
                Column(Modifier.fillMaxSize().padding(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton("← Назад") { pages.removeAt(pages.lastIndex) }
                        Spacer(Modifier.width(14.dp))
                        Text(page.title, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(14.dp))
                    ClassicPage(page)
                }
            } else androidx.compose.animation.Crossfade(tab, animationSpec = tween(if (C.animations) 220 else 0)) { cur ->
                if (sub.nodes.isEmpty() && cur != ClassicTab.Settings) AddSubscription()
                else when (cur) {
                    ClassicTab.Home -> ClassicHome { tab = ClassicTab.Servers }
                    ClassicTab.Servers -> Titled("Серверы") { ServersScreen() }
                    ClassicTab.Routes -> Titled("Маршруты") { RoutesScreen() }
                    ClassicTab.Tools -> Titled("Инструменты") { ToolsScreen() }
                    ClassicTab.Settings -> ClassicSettings(open)
                }
            }
        }
    }
    }
}


// ─── Главная ─────────────────────────────────────────────────────────

@Composable
fun ClassicHome(openServers: () -> Unit) {
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val on = status == VpnStatus.On
        val connecting = status == VpnStatus.Connecting
        Spacer(Modifier.height(8.dp))
        if (s.homeStyle != "shield") {
            Box(Modifier.width(560.dp)) { HomeGlobe(sub.nodes.find { it.tag == s.selected } ?: sub.nodes.find { it.tag == autoNow }, on, connecting) }
        } else {
            Box(Modifier.clip(CircleShape).clickable(enabled = !connecting) { Engine.toggle() }) {
                Shield(on = on, connecting = connecting, size = 190)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when (status) { VpnStatus.On -> "Защищено"; VpnStatus.Connecting -> "Подключение…"; VpnStatus.Error -> "Ошибка"; else -> "Не защищено" },
            color = when (status) { VpnStatus.On -> C.Text; VpnStatus.Error -> C.Bad; else -> C.Dim }, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (on && connectedAt > 0) "${duration(now - connectedAt)}  ·  ↓ ${rate(speed.second)}  ↑ ${rate(speed.first)}"
            else "Нажмите на щит, чтобы подключиться",
            color = C.Dim, fontSize = 14.sp,
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = C.Bad, fontSize = 13.sp, modifier = Modifier.width(560.dp).clip(RoundedCornerShape(14.dp)).background(C.Bad.copy(alpha = 0.1f)).padding(12.dp))
            if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); kotlin.system.exitProcess(0) } }
            }
        }
        Spacer(Modifier.height(16.dp))
        HomeModes(Modifier.width(560.dp))
        Spacer(Modifier.height(16.dp))

        // Сервер
        val sel = sub.nodes.find { it.tag == s.selected }
        val autoNode = sub.nodes.find { it.tag == autoNow }
        Card(Modifier.width(560.dp).clickable { openServers() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountryBadge((sel ?: autoNode)?.country)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (sel == null) "Автопилот · ${s.autoMode.title}" else sel.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (sel == null) autoNode?.let { "Сейчас: ${it.title}" } ?: "Самый быстрый из ${sub.nodes.size}" else sel.detail.ifBlank { sel.category },
                        color = C.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val p = pings[(sel ?: autoNode)?.tag]
                if (p != null) Text(if (p > 0) "$p мс" else "—", color = pingColor(p), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.width(560.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) {
                Text("Маршрут", color = C.Dim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Segmented(RouteMode.entries.map { it.title }, s.routeMode.ordinal) { i ->
                    Store.update { copy(routeMode = RouteMode.entries[i]) }; Engine.reload()
                }
            }
            Card(Modifier.weight(1f)) {
                Text("Подключение", color = C.Dim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Прокси", "TUN"), s.connectType.ordinal) { i ->
                    Store.update { copy(connectType = ConnectType.entries[i]) }; Engine.reload()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Подписка
        Card(Modifier.width(560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sub.title.ifBlank { "Подписка" }, color = C.Text, fontWeight = FontWeight.Bold)
                    val left = if (sub.expireAt > 0) ((sub.expireAt * 1000 - now) / 86_400_000L).coerceAtLeast(0) else -1
                    Text(
                        listOfNotNull(
                            if (sub.total > 0) "${bytes(sub.used)} из ${bytes(sub.total)}" else "${bytes(sub.used)} · без лимита",
                            if (left >= 0) "осталось $left дн." else null,
                            "автообновление ${s.refreshHours} ч",
                        ).joinToString(" · "),
                        color = C.Dim, fontSize = 13.sp,
                    )
                }
                val scope = rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                Icon(
                    Icons.Rounded.Refresh, "Обновить", tint = if (busy) C.Accent else C.Dim,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = !busy) {
                        scope.launch { busy = true; runCatching { Subscription.refresh() }.onSuccess { Engine.reload() }; busy = false }
                    }.padding(8.dp),
                )
            }
            if (sub.total > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.SurfaceHigh)) {
                    Box(Modifier.fillMaxWidth((sub.used.toFloat() / sub.total).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(C.Accent, C.Cyan))))
                }
            }
            if (sub.announce.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(sub.announce, color = C.Text, fontSize = 13.sp)
            }
        }
    }
}


// ─── Настройки ───────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClassicSettings(open: (Page) -> Unit) {
    val s by Store.settings.collectAsState()
    val sub by Subscription.state.collectAsState()
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text("Настройки", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Section("Оформление")
        Card(Modifier.fillMaxWidth()) {
            Text("Дизайн", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Design.entries.forEach { d -> DesignCard(d, s.design == d) { Store.update { copy(design = d) } } }
            }
            Spacer(Modifier.height(6.dp))
            Text(s.design.subtitle, color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("Цвет", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentChoice.entries.forEach { a ->
                    val on = s.accent == a
                    val c1 = a.c1 ?: s.design.palette.accent
                    val c2 = a.c2 ?: s.design.palette.accent2
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) C.SurfaceHigh else Color.Transparent)
                            .border(1.dp, if (on) C.Accent else C.Outline, RoundedCornerShape(14.dp))
                            .clickable { Store.update { copy(accent = a) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))))
                        Spacer(Modifier.width(8.dp))
                        Text(a.title, color = C.Text, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Toggle("Анимации", "Живой фон, плавная смена вкладок. Выключите на слабом компьютере", s.animations) { Store.update { copy(animations = it) } }
        }
        Section("Подключение")
        Card(Modifier.fillMaxWidth()) {
            Text("Способ", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ConnectType.entries.forEach { t ->
                Choice(t.title, t.subtitle, s.connectType == t) { Store.update { copy(connectType = t) }; Engine.reload() }
            }
            if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
                Spacer(Modifier.height(8.dp))
                Text("Сейчас программа запущена без прав администратора — TUN не включится.", color = C.Warn, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); kotlin.system.exitProcess(0) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Text("Маршрут", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            RouteMode.entries.forEach { m -> Choice(m.title, m.subtitle, s.routeMode == m) { Store.update { copy(routeMode = m) }; Engine.reload() } }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Text("Ядро", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CoreChoice.entries.forEach { c -> Choice(c.title, c.subtitle, s.core == c) { Store.update { copy(core = c) }; Engine.reload() } }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Text("DNS для зарубежных сайтов", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DnsChoice.entries.forEach { d ->
                    Chip(d.title, s.dns == d) {
                        Store.update { copy(dns = d) }
                        if (d == DnsChoice.Auto) scope.launch { DnsAuto.measure(); Engine.reload() } else Engine.reload()
                    }
                }
            }
            if (s.dns == DnsChoice.Custom) { Spacer(Modifier.height(10.dp)); CustomDnsField() }
            if (s.dns == DnsChoice.Auto) {
                val results by DnsAuto.results.collectAsState()
                Spacer(Modifier.height(10.dp))
                Text("Сейчас: ${DnsAuto.best.title} · самый быстрый из ${maxOf(results.size, 4)} (DNS-over-HTTPS)", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                results.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(r.choice.title, color = if (r.choice == DnsAuto.best) C.Accent else C.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (r.ms > 0) "${r.ms} мс" else "нет ответа", color = pingColor(r.ms), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                SecondaryButton("Проверить сейчас") {
                    scope.launch { val b = DnsAuto.best; DnsAuto.measure(); if (DnsAuto.best != b) Engine.reload() }
                }
            }
            Spacer(Modifier.height(10.dp))
            Toggle("Блокировка рекламы", "Реклама и трекеры не загружаются", s.adBlock) { Store.update { copy(adBlock = it) }; Engine.reload() }
            Toggle("Автопереключение при сбое", "Выбранный сервер перестал отвечать — включить автопилот", s.failover) { Store.update { copy(failover = it) } }
            Toggle("Белые списки → LTE-обход", "Провайдер пускает только «разрешённые» сайты — автопилот на LTE-обход", s.autoWhitelist) { Store.update { copy(autoWhitelist = it) } }
            Toggle("Автопилот без России и LTE-обхода", "Особые серверы выбираются только вручную", s.skipSpecial) { Store.update { copy(skipSpecial = it) }; Engine.reload() }
        }

        Section("Ещё")
        Card(Modifier.fillMaxWidth()) {
            Text("Вид окна", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val layouts = listOf("classic" to "Первый (классический)", "windows" to "Windows 11", "phone" to "Как на телефоне")
            Segmented(layouts.map { it.second }, layouts.indexOfFirst { it.first == s.layout }.coerceAtLeast(0)) { i -> Store.update { copy(layout = layouts[i].first) } }
            Spacer(Modifier.height(14.dp))
            Text("Главный экран", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Щит", "Карта", "3D-глобус"), listOf("shield", "map", "globe").indexOf(s.homeStyle).coerceAtLeast(0)) { i -> Store.update { copy(homeStyle = listOf("shield", "map", "globe")[i]) } }
            Spacer(Modifier.height(14.dp))
            Text("Живой фон", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LiveBgPicker()
            Spacer(Modifier.height(10.dp))
            Toggle("Звук подключения", if (s.soundOn) Sounds.sets.firstOrNull { it.first == s.sound }?.second else "Выключен", s.soundOn) { Store.update { copy(soundOn = it) } }
            if (s.soundOn) Segmented(Sounds.sets.map { it.second }, Sounds.sets.indexOfFirst { it.first == s.sound }.coerceAtLeast(0)) { i ->
                Store.update { copy(sound = Sounds.sets[i].first) }; Sounds.play(true, force = true)
            }
            Spacer(Modifier.height(6.dp))
            Toggle("Семейный режим", "Закрывает сайты 18+, казино и букмекеров", s.kids) { Store.update { copy(kids = it) }; Engine.reload() }
            Toggle("Напоминать о конце подписки", "За 3 дня, за 1 день и в последний день", s.expiryAlerts) { Store.update { copy(expiryAlerts = it) } }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Сервисы через VPN") { open(Page.Services) }
                SecondaryButton("Для опытных") { open(Page.Advanced) }
                SecondaryButton("Не работает?") { open(Page.SelfCheck) }
                SecondaryButton(if (s.photoPath.isBlank()) "Своё фото на фон" else "Убрать фото") {
                    if (s.photoPath.isBlank()) Photo.pick()?.let { p -> Store.update { copy(photoPath = p) } } else Store.update { copy(photoPath = "") }
                }
                SecondaryButton("Резервная копия") { Backup.export() }
                SecondaryButton("Загрузить копию") { scope.launch { runCatching { Backup.import() }; Engine.reload() } }
                SecondaryButton("Настройки пинга") { open(Page.Ping) }
                SecondaryButton("Программы мимо VPN") { open(Page.Exclude) }
                SecondaryButton("Справка") { open(Page.Help) }
                SecondaryButton("Что нового") { open(Page.WhatsNew) }
                SecondaryButton("Ссылка-приглашение") { Invite.copy() }
                SecondaryButton("Kill Switch") { open(Page.KillSwitch) }
                SecondaryButton("Раздача по дому") { open(Page.Share) }
                SecondaryButton("Ограничение скорости") { open(Page.Limits) }
            }
        }

        Section("Программа")
        Card(Modifier.fillMaxWidth()) {
            Toggle("Запускать вместе с Windows", "В трее, без окна", s.autostart) { Store.update { copy(autostart = it) }; WinSys.setAutostart(it) }
            Toggle("Подключаться при запуске", null, s.connectOnStart) { Store.update { copy(connectOnStart = it) } }
            Toggle("Сворачивать в трей при закрытии", "Значок у часов: подключить, сервер, режим", s.minimizeToTray) { Store.update { copy(minimizeToTray = it) } }
            Toggle("Проверять обновления", if (s.updateMode == "auto") "Раз в 6 часов · ставить само" else "Раз в 6 часов · спросить меня", s.checkUpdates) { Store.update { copy(checkUpdates = it) } }
        }

        Section("Подписка")
        Card(Modifier.fillMaxWidth()) {
            Text(Store.value.subscriptionUrl.let { if (it.length > 34) it.take(34) + "…" else it }.ifBlank { "Не добавлена" }, color = C.Dim, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Автообновление", color = C.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val opts = listOf(1, 6, 12, 24)
            Segmented(opts.map { "$it ч" }, opts.indexOf(s.refreshHours).coerceAtLeast(0)) { i -> Store.update { copy(refreshHours = opts[i]) } }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Обновить сейчас") { scope.launch { runCatching { Subscription.refresh() }.onSuccess { Engine.reload() } } }
                if (sub.nodes.isNotEmpty()) SecondaryButton("Удалить подписку", C.Bad) { Engine.disconnect(); Subscription.remove() }
            }
        }

        Section("О программе")
        Card(Modifier.fillMaxWidth()) {
            Text("APEX VPN для Windows ${AppInfo.VERSION}", color = C.Text, fontWeight = FontWeight.Bold)
            Text("Ядра: sing-box 1.13 и Xray 26 · общий движок с Android", color = C.Dim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Обновление") { open(Page.Update) }
                SecondaryButton("Поддержка @Apexx_support") { WinSys.open(AppInfo.SUPPORT) }
                SecondaryButton("Папка журналов") { WinSys.openFolder(Paths.data) }
            }
            update?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = C.Accent, fontSize = 13.sp, modifier = Modifier.clickable { WinSys.open(Updates.PAGE) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


/** Вкладка с заголовком, как в первой версии. */
@Composable
private fun Titled(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text(title, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ClassicPage(page: Page) {
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
