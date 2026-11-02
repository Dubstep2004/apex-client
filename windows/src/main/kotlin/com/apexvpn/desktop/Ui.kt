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

// ─── Серверы ─────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ServersScreen() {
    val sub by Subscription.state.collectAsState()
    val s by Store.settings.collectAsState()
    val pings by Pinger.pings.collectAsState()
    val busy by Pinger.busy.collectAsState()
    val autoNow by Engine.autoNow.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (pings.isEmpty()) Pinger.pingAll() }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пинг до серверов", color = C.Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoMode.entries.forEach { m ->
                    Chip("${m.emoji} ${m.title}", m == s.autoMode) {
                        Store.update { copy(autoMode = m, selected = "auto") }
                        // Режим по замерам: сначала подбираем серверы, потом переподключаемся
                        if (m.measured) scope.launch { AutoModes.measure(m); Engine.reload() } else Engine.reload()
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val modeStatus by AutoModes.status.collectAsState()
            val quality by AutoModes.quality.collectAsState()
            val fit = remember(quality, s.autoMode, sub) { AutoModes.allowed(s.autoMode, sub.nodes).size }
            Text(
                modeStatus ?: (s.autoMode.subtitle + when {
                    !s.autoMode.measured -> ""
                    fit > 0 -> " · подходящих серверов: $fit"
                    s.autoMode == AutoMode.Speed && Engine.status.value != VpnStatus.On -> " · подключитесь, чтобы замерить скорость"
                    else -> " · нажмите ещё раз, чтобы подобрать"
                }),
                color = if (modeStatus != null) C.Accent else C.Dim, fontSize = 12.sp,
            )
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

// ─── Элементы ────────────────────────────────────────────────────────

@Composable
fun Shield(on: Boolean, connecting: Boolean, size: Int) {
    val t = rememberInfiniteTransition()
    val pulse by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1500), RepeatMode.Reverse))
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
    // Защищено: от щита расходятся волны
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)))
    val anim = C.animations
    val c1 by animateColorAsState(if (on) C.Accent else Color(0xFF3A3A46))
    val c2 by animateColorAsState(if (on) C.Cyan else Color(0xFF2A2A36))
    Canvas(Modifier.size((size * 1.35f).dp)) {
        val cx = this.size.width / 2; val cy = this.size.height / 2
        val r = size.dp.toPx() / 2
        val pl = if (anim) pulse else 1f
        if (on || connecting) drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.45f), Color.Transparent), Offset(cx, cy), r * 1.3f * pl), r * 1.3f * pl, Offset(cx, cy))
        if (on && anim) for (k in 0..1) {
            val ph = (wave + k * 0.5f) % 1f
            drawCircle(c2.copy(alpha = (1f - ph) * 0.35f), r * (0.95f + 0.4f * ph), Offset(cx, cy), style = Stroke(2.dp.toPx()))
        }
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
    Column(modifier.clip(RoundedCornerShape(C.Radius)).background(C.Surface).border(1.dp, C.Outline, RoundedCornerShape(C.Radius)).padding(16.dp), content = content)
}

@Composable
fun Section(t: String) {
    // Как на Android: подпись группы обычным текстом, без капса
    if (C.fluent) Text(t, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 8.dp))
    else Text(t, color = C.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
fun CountryBadge(code: String?) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
        Text(code ?: "•", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Tag(t: String) {
    Text(t, color = C.AccentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(C.AccentSoft).padding(horizontal = 9.dp, vertical = 3.dp))
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


/** Карточка дизайна: мини-окно в его цветах. */
@Composable
fun DesignCard(d: Design, on: Boolean, onClick: () -> Unit) {
    val p = d.palette
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).border(2.dp, if (on) C.Accent else C.Outline, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).background(Brush.linearGradient(listOf(p.base, p.accent.copy(alpha = 0.55f), p.base)))) {
            Box(Modifier.padding(10.dp).size(34.dp, 40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(p.accent, p.accent2))))
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalAlignment = Alignment.End) {
                Box(Modifier.size(56.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(p.surfaceHigh))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.size(38.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(p.surfaceHigh))
            }
        }
        Text(d.title, color = if (on) C.Text else C.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.background(C.Surface).fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp))
    }
}
