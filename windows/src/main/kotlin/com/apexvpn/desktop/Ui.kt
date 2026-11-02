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

object C {
    val Base = Color(0xFF08080E)
    val Surface = Color(0xFF13131C)
    val SurfaceHigh = Color(0xFF1C1C28)
    val Outline = Color(0x14FFFFFF)
    val Text = Color(0xFFF4F4F7)
    val Dim = Color(0xFF8E8E9A)
    val Accent = Color(0xFF7B6CFF)
    val Cyan = Color(0xFF4FA8F2)
    val Good = Color(0xFF34D399)
    val Warn = Color(0xFFF2B75B)
    val Bad = Color(0xFFFF6B6B)
}

enum class Tab(val title: String, val icon: ImageVector) {
    Home("Главная", Icons.Rounded.Home), Servers("Серверы", Icons.Rounded.Dns), Routes("Маршруты", Icons.Rounded.Route), Settings("Настройки", Icons.Rounded.Settings)
}

@Composable
fun AppUi() {
    var tab by remember { mutableStateOf(Tab.Home) }
    val sub by Subscription.state.collectAsState()
    Row(Modifier.fillMaxSize().background(C.Base)) {
        // Боковая панель
        Column(
            Modifier.width(92.dp).fillMaxHeight().background(Color(0xFF0C0C14)).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("APEX", color = C.Text, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(26.dp))
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).clickable { tab = t }.padding(horizontal = 10.dp, vertical = 8.dp),
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
            if (sub.nodes.isEmpty() && tab != Tab.Settings) AddSubscription()
            else when (tab) {
                Tab.Home -> HomeScreen { tab = Tab.Servers }
                Tab.Servers -> ServersScreen()
                Tab.Routes -> RoutesScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}

// ─── Подписка ─────────────────────────────────────────────────────────

@Composable
fun AddSubscription() {
    var url by remember { mutableStateOf(Store.value.subscriptionUrl) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Shield(on = false, connecting = busy, size = 120)
        Spacer(Modifier.height(24.dp))
        Text("Добавьте подписку", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Вставьте ссылку https://sub.knownhosts.ru/… — ту же, что в приложении на телефоне", color = C.Dim, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Field(url, "https://sub.knownhosts.ru/…", Modifier.width(520.dp)) { url = it; err = null }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (busy) "Загружаю…" else "Добавить", enabled = !busy && url.isNotBlank()) {
            scope.launch {
                busy = true
                runCatching { Subscription.refresh(url) }.onFailure { err = it.message ?: "Не удалось загрузить подписку" }
                    .onSuccess { launch { Pinger.pingAll() } }
                busy = false
            }
        }
        err?.let { Spacer(Modifier.height(10.dp)); Text(it, color = C.Bad, fontSize = 13.sp) }
    }
}

// ─── Главная ─────────────────────────────────────────────────────────

@Composable
fun HomeScreen(openServers: () -> Unit) {
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
        Box(Modifier.clip(CircleShape).clickable(enabled = !connecting) { Engine.toggle() }) {
            Shield(on = on, connecting = connecting, size = 190)
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
        Spacer(Modifier.height(24.dp))

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

// ─── Серверы ─────────────────────────────────────────────────────────

@Composable
fun ServersScreen() {
    val sub by Subscription.state.collectAsState()
    val s by Store.settings.collectAsState()
    val pings by Pinger.pings.collectAsState()
    val busy by Pinger.busy.collectAsState()
    val autoNow by Engine.autoNow.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (pings.isEmpty()) Pinger.pingAll() }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Серверы", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            SecondaryButton(if (busy) "Проверяю…" else "Проверить пинг") { scope.launch { Pinger.pingAll() } }
        }
        Spacer(Modifier.height(14.dp))
        // Автопилот и его режимы
        Card(Modifier.fillMaxWidth().clickable { Engine.select("auto") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Accent, C.Cyan))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Bolt, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Автопилот", color = C.Text, fontWeight = FontWeight.Bold)
                    Text(sub.nodes.find { it.tag == autoNow }?.let { "Сейчас: ${it.title}" } ?: "Сам выбирает самый быстрый и переключает при сбое", color = C.Dim, fontSize = 13.sp)
                }
                if (s.selected == "auto") Tag("Выбран")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoMode.entries.forEach { m ->
                    Chip("${m.emoji} ${m.title}", m == s.autoMode) {
                        Store.update { copy(autoMode = m, selected = "auto") }; Engine.reload()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(s.autoMode.subtitle, color = C.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        val sorted = sub.nodes.sortedWith(compareBy({ (pings[it.tag] ?: Int.MAX_VALUE).let { p -> if (p <= 0) Int.MAX_VALUE else p } }, { it.title }))
        LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(C.Surface)) {
            items(sorted, key = { it.tag }) { n ->
                val selected = n.tag == s.selected
                Row(
                    Modifier.fillMaxWidth().clickable { Engine.select(n.tag) }
                        .background(if (selected) C.Accent.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountryBadge(n.country)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.title, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (n.tag == autoNow && s.selected == "auto") { Spacer(Modifier.width(8.dp)); Tag("Автопилот") }
                        }
                        Text(listOf(n.category, n.detail).filter { it.isNotBlank() }.distinct().joinToString(" · "), color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val p = pings[n.tag]
                    Text(
                        when { p == null -> "…"; p > 0 -> "$p мс"; else -> "нет ответа" },
                        color = pingColor(p), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { scope.launch { Pinger.pingOne(n) } }.padding(8.dp),
                    )
                }
            }
        }
    }
}

// ─── Настройки ───────────────────────────────────────────────────────

@Composable
fun SettingsScreen() {
    val s by Store.settings.collectAsState()
    val sub by Subscription.state.collectAsState()
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text("Настройки", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
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
            Toggle("Автопилот без России и LTE-обхода", "Особые серверы выбираются только вручную", s.skipSpecial) { Store.update { copy(skipSpecial = it) }; Engine.reload() }
        }

        Section("Программа")
        Card(Modifier.fillMaxWidth()) {
            Toggle("Запускать вместе с Windows", "В трее, без окна", s.autostart) { Store.update { copy(autostart = it) }; WinSys.setAutostart(it) }
            Toggle("Подключаться при запуске", null, s.connectOnStart) { Store.update { copy(connectOnStart = it) } }
            Toggle("Сворачивать в трей при закрытии", "Значок у часов: подключить, сервер, режим", s.minimizeToTray) { Store.update { copy(minimizeToTray = it) } }
            Toggle("Проверять обновления", "Раз в день на GitHub", s.checkUpdates) { Store.update { copy(checkUpdates = it) } }
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
                SecondaryButton("Проверить обновления") {
                    scope.launch { update = Updates.check()?.let { "Доступна версия ${it.tag}" } ?: "У вас последняя версия" }
                }
                SecondaryButton("Поддержка @Apexx_support") { WinSys.open(AppInfo.SUPPORT) }
                SecondaryButton("Папка журналов") { WinSys.openFolder(Paths.data) }
            }
            update?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = C.Accent, fontSize = 13.sp, modifier = Modifier.clickable { WinSys.open("https://github.com/${AppInfo.REPO}/releases/latest") })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Элементы ────────────────────────────────────────────────────────

@Composable
fun Shield(on: Boolean, connecting: Boolean, size: Int) {
    val t = rememberInfiniteTransition()
    val pulse by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1500), RepeatMode.Reverse))
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
    val c1 by animateColorAsState(if (on) C.Accent else Color(0xFF3A3A46))
    val c2 by animateColorAsState(if (on) C.Cyan else Color(0xFF2A2A36))
    Canvas(Modifier.size((size * 1.35f).dp)) {
        val cx = this.size.width / 2; val cy = this.size.height / 2
        val r = size.dp.toPx() / 2
        if (on || connecting) drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.45f), Color.Transparent), Offset(cx, cy), r * 1.3f * pulse), r * 1.3f * pulse, Offset(cx, cy))
        if (connecting) drawArc(C.Accent, spin, 90f, false, Offset(cx - r * 1.1f, cy - r * 1.1f), androidx.compose.ui.geometry.Size(r * 2.2f, r * 2.2f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val w = r * 1.4f; val h = r * 1.65f
        val p = Path().apply {
            moveTo(cx, cy - h / 2)
            lineTo(cx + w / 2, cy - h / 2 + h * 0.18f)
            lineTo(cx + w / 2, cy + h * 0.02f)
            quadraticTo(cx + w / 2, cy + h * 0.36f, cx, cy + h / 2)
            quadraticTo(cx - w / 2, cy + h * 0.36f, cx - w / 2, cy + h * 0.02f)
            lineTo(cx - w / 2, cy - h / 2 + h * 0.18f)
            close()
        }
        drawPath(p, Brush.linearGradient(listOf(c1, c2), Offset(cx - w / 2, cy + h / 2), Offset(cx + w / 2, cy - h / 2)))
        if (on) {
            val ck = Path().apply { moveTo(cx - w * 0.2f, cy); lineTo(cx - w * 0.04f, cy + h * 0.13f); lineTo(cx + w * 0.24f, cy - h * 0.14f) }
            drawPath(ck, Color.White, style = Stroke(r * 0.12f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.16f, Offset(cx, cy), style = Stroke(r * 0.07f))
            drawLine(Color.White.copy(alpha = 0.85f), Offset(cx, cy - r * 0.26f), Offset(cx, cy - r * 0.06f), r * 0.07f, StrokeCap.Round)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(C.Surface).border(1.dp, C.Outline, RoundedCornerShape(20.dp)).padding(16.dp), content = content)
}

@Composable
fun Section(t: String) {
    Text(t.uppercase(), color = C.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
fun CountryBadge(code: String?) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
        Text(code ?: "•", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Tag(t: String) {
    Text(t, color = C.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.Accent.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun Chip(t: String, on: Boolean, onClick: () -> Unit) {
    Text(
        t, color = if (on) Color.White else C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) C.Accent else C.SurfaceHigh).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.SurfaceHigh).padding(3.dp)) {
        items.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) C.Accent else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, color = if (on) Color.White else C.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun Choice(title: String, subtitle: String?, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) C.Accent else C.Dim, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(C.Accent))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = C.Dim, fontSize = 12.sp)
        }
    }
}

@Composable
fun Toggle(title: String, subtitle: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!on) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = C.Dim, fontSize = 12.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White, uncheckedTrackColor = C.SurfaceHigh, uncheckedThumbColor = C.Dim, uncheckedBorderColor = C.Outline))
    }
}

@Composable
fun PrimaryButton(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (enabled) Brush.horizontalGradient(listOf(C.Accent, C.Cyan)) else SolidColor(C.SurfaceHigh))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 28.dp, vertical = 13.dp),
    ) { Text(t, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(t: String, color: Color = C.Text, onClick: () -> Unit) {
    Text(t, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.SurfaceHigh).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
fun Field(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(C.Surface).border(1.dp, C.Outline, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (value.isEmpty()) Text(hint, color = C.Dim, fontSize = 14.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = C.Text, fontSize = 14.sp), cursorBrush = SolidColor(C.Accent), modifier = Modifier.fillMaxWidth())
    }
}

fun pingColor(ms: Int?) = when {
    ms == null -> C.Dim
    ms <= 0 -> C.Bad
    ms < 120 -> C.Good
    ms < 250 -> C.Warn
    else -> C.Bad
}

fun duration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

fun rate(bytesPerSec: Long): String {
    val bits = bytesPerSec * 8.0
    return when {
        bits >= 1e6 -> "%.1f Мбит/с".format(bits / 1e6)
        bits >= 1e3 -> "%.0f Кбит/с".format(bits / 1e3)
        else -> "0 Кбит/с"
    }
}

fun bytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f ГБ".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f МБ".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} КБ"
}
