package com.apexvpn.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ─── 1.5. Своё фото за всем окном ─────────────────────────────────────

@Composable
fun PhotoBackdrop() {
    val s by Store.settings.collectAsState()
    if (s.photoPath.isBlank()) return
    val img = remember(s.photoPath) { Photo.load(s.photoPath) } ?: return
    val d = s.photoDim / 100f
    Box(Modifier.fillMaxSize()) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Base.copy(alpha = (d + 0.1f).coerceAtMost(0.95f)), C.Base.copy(alpha = d * 0.5f), C.Base.copy(alpha = (d + 0.3f).coerceAtMost(0.95f))))))
    }
}

// ─── 1.5. Глобус на главном экране ────────────────────────────────────

@Composable
fun HomeGlobe(server: Node?, connected: Boolean, busy: Boolean) {
    val me = remember { java.util.Locale.getDefault().country.lowercase().takeIf { it in WorldMap.coords } ?: "ru" }
    val to = server?.country?.lowercase()?.let { WorldMap.coords[it] }
    if (Store.value.homeStyle == "map") {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(C.Radius)).clickable(enabled = !busy) { Engine.toggle() }) {
            FlatMapCard(connected, busy, server?.country?.lowercase(), server?.title ?: "Автопилот")
        }
        return
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(C.Radius)).clickable(enabled = !busy) { Engine.toggle() }) {
        Globe(Modifier.fillMaxWidth().height(260.dp), from = WorldMap.coords[me], to = to, connected = connected, busy = busy)
    }
}

// ─── 1.7. Объявление с картинкой и кнопкой ────────────────────────────

@Composable
fun AnnounceCard(raw: String) {
    if (raw.isBlank()) return
    val rich = remember(raw) { Announce.parse(raw) }
    var hidden by remember(raw) { mutableStateOf(false) }
    if (hidden) return
    val img by produceState<ImageBitmap?>(null, rich.image) { value = if (rich.image.isNotBlank()) Announce.image(rich.image) else null }
    Group {
        img?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Crop) }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(rich.text, color = C.Text, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, "Скрыть", tint = C.Dim, modifier = Modifier.size(20.dp).clip(CircleShape).clickable { hidden = true })
            }
            if (rich.button.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton(rich.button) { WinSys.open(rich.url) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ─── 1.6. Сервисы через VPN ───────────────────────────────────────────

@Composable
fun ServicesPage() {
    val s by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            when (s.routeMode) {
                RouteMode.Selected -> "Режим «Только выбранные»: через VPN идут только включённые сервисы и свои списки."
                RouteMode.Smart -> "Умный режим: включённые сервисы всегда идут через VPN, выключенные — напрямую."
                RouteMode.All -> "Режим «Весь трафик»: выключенные сервисы идут напрямую, остальное через VPN."
            },
            color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Включить все") { Store.update { copy(services = ServiceCatalog.all.map { it.id }.toSet()) }; Engine.reload() }
            SecondaryButton("Выключить все") { Store.update { copy(services = emptySet()) }; Engine.reload() }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            ServiceCatalog.all.forEachIndexed { i, sv ->
                if (i > 0) GroupDivider()
                val on = sv.id in s.services
                ListRow(sv.title, sv.subtitle, leading = {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color(sv.color)), contentAlignment = Alignment.Center) {
                        Text(sv.glyph, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }, onClick = { Store.update { copy(services = if (on) services - sv.id else services + sv.id) }; Engine.reload() }) {
                    ApexSwitch(on) { v -> Store.update { copy(services = if (v) services + sv.id else services - sv.id) }; Engine.reload() }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── 1.6. Для опытных: свои правила, FakeDNS, стратегия доменов ───────

@Composable
fun AdvancedPage() {
    val s by Store.settings.collectAsState()
    var value by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(0) }
    val actions = listOf("proxy" to "VPN", "direct" to "Напрямую", "block" to "Блок")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Свои правила")
        Text("Выше всех остальных правил: блок, затем VPN, затем напрямую", color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(Modifier.fillMaxWidth()) {
            Field(value, "example.com, keyword:ads, 1.2.3.0/24, udp:27015", Modifier.fillMaxWidth()) { value = it }
            Spacer(Modifier.height(8.dp))
            Segmented(actions.map { it.second }, action) { action = it }
            Spacer(Modifier.height(8.dp))
            val norm = normalizeRule(value)
            PrimaryButton("Добавить правило", enabled = norm != null) {
                Store.update { copy(userRules = userRules.filter { it.value != norm } + UserRule(norm!!, actions[action].first)) }
                value = ""; Engine.reload()
            }
        }
        if (s.userRules.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Group {
                s.userRules.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    val (label, color) = when (r.action) { "direct" -> "Напрямую" to C.Good; "block" -> "Блок" to C.Bad; else -> "VPN" to C.Accent }
                    ListRow(r.value, onClick = null) {
                        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Close, "Удалить", tint = C.Dim, modifier = Modifier.clip(CircleShape).clickable { Store.update { copy(userRules = userRules - r) }; Engine.reload() }.padding(6.dp))
                    }
                }
            }
        }
        Section("Настройки доменов")
        Group {
            ListRow("FakeDNS", "Поддельные адреса для сайтов через VPN — быстрее открываются", onClick = { Store.update { copy(fakeDns = !fakeDns) }; Engine.reload() }) {
                ApexSwitch(s.fakeDns) { v -> Store.update { copy(fakeDns = v) }; Engine.reload() }
            }
            GroupDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Стратегия доменов", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val opts = listOf("as_is" to "Как есть", "ip_if_non_match" to "IP, если нет совпадения", "ip_on_demand" to "IP сразу")
                Segmented(opts.map { it.second }, opts.indexOfFirst { it.first == s.domainStrategy }.coerceAtLeast(0)) { i -> Store.update { copy(domainStrategy = opts[i].first) }; Engine.reload() }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── 1.7. Самопроверка «Не работает?» ─────────────────────────────────

@Composable
fun SelfCheckPage() {
    val st by SelfCheck.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Проверим по шагам: интернет, подписку, серверы, VPN и сайты — и подскажем, что делать.", color = C.Dim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        if (st.steps.isNotEmpty()) {
            Group {
                st.steps.forEachIndexed { i, step ->
                    if (i > 0) GroupDivider()
                    val (sym, col) = when (step.mark) {
                        SelfCheck.Mark.Ok -> "✓" to C.Good; SelfCheck.Mark.Warn -> "!" to C.Warn; SelfCheck.Mark.Fail -> "✕" to C.Bad
                        SelfCheck.Mark.Run -> "…" to C.Accent; SelfCheck.Mark.Wait -> "·" to C.Dim
                    }
                    ListRow(step.title, step.detail.ifBlank { null }, leading = {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(col.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text(sym, color = col, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        st.verdict?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(it, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (st.fixTag != null) {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Выбрать «${st.fixTitle}»") { Engine.select(st.fixTag!!) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(if (st.running) "Проверяю…" else if (st.steps.isEmpty()) "Начать проверку" else "Проверить ещё раз", enabled = !st.running) { scope.launch { SelfCheck.run() } }
        Spacer(Modifier.height(16.dp))
    }
}

/** Подпись строки сервиса для экрана «Режим». */
fun servicesSummary(s: Settings): String {
    val on = ServiceCatalog.all.filter { it.id in s.services }
    return if (on.isEmpty()) "Ни один не выбран" else on.take(4).joinToString(", ") { it.title } + if (on.size > 4) " и ещё ${on.size - 4}" else ""
}

@Suppress("unused")
private val keepTextOverflow = TextOverflow.Ellipsis
