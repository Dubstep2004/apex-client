package com.apexvpn.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Windows 1.2: тест скорости, проверка утечек, журнал соединений. */
@Composable
fun ToolsScreen() {
    var section by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Инструменты", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(520.dp)) {
            Segmented(listOf("Тест скорости", "Проверка утечек", "Журнал"), section) { section = it }
        }
        Spacer(Modifier.height(16.dp))
        when (section) {
            0 -> SpeedTestCard()
            1 -> LeakTestCard()
            else -> ConnLogCard()
        }
    }
}

@Composable
private fun SpeedTestCard() {
    val st by SpeedTest.state.collectAsState()
    val status by Engine.status.collectAsState()
    val scope = rememberCoroutineScope()
    var multi by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.width(620.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Провайдер", color = C.Dim, fontSize = 12.sp)
                    Text(st.meta?.provider?.ifBlank { null } ?: if (st.running) "Поиск…" else "—", color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(st.meta?.ip, st.meta?.city?.ifBlank { null }).joinToString(" · "), color = C.Dim, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Сервер теста", color = C.Dim, fontSize = 12.sp)
                    Text(st.meta?.colo?.ifBlank { null }?.let { "Cloudflare · $it" } ?: "Cloudflare", color = C.Text, fontWeight = FontWeight.SemiBold)
                    Text(if (status == VpnStatus.On) "через VPN" else "без VPN", color = if (status == VpnStatus.On) C.Good else C.Warn, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val shown = when {
            st.running && st.live > 0 -> st.live
            st.up > 0 && !st.running -> st.down
            else -> 0.0
        }
        Gauge(shown, st.running, st.phase) { if (!st.running) scope.launch { SpeedTest.run(if (multi) 4 else 1) } }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.width(620.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Пинг", if (st.ping > 0) "${st.ping} мс" else "—", Modifier.weight(1f))
            Stat("Джиттер", if (st.jitter >= 0) "${st.jitter} мс" else "—", Modifier.weight(1f))
            Stat("↓ Загрузка", if (st.down > 0) "%.1f Мбит/с".format(st.down) else "—", Modifier.weight(1f))
            Stat("↑ Отдача", if (st.up > 0) "%.1f Мбит/с".format(st.up) else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.width(620.dp)) {
            Segmented(listOf("Множественное (4 потока)", "Одинарное"), if (multi) 0 else 1) { multi = it == 0 }
        }
        st.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = C.Bad, fontSize = 13.sp) }
        Spacer(Modifier.height(8.dp))
        Text("Тест расходует 100–300 МБ. Если VPN включён — меряется скорость через VPN.", color = C.Dim, fontSize = 12.sp)
    }
}

/** Спидометр со шкалой 0–1000 Мбит/с (нелинейная, как в Speedtest) и кнопкой «Начать». */
@Composable
private fun Gauge(mbps: Double, running: Boolean, phase: String, onStart: () -> Unit) {
    val marks = listOf(0.0, 10.0, 50.0, 100.0, 250.0, 500.0, 1000.0)
    fun frac(v: Double): Float {
        if (v <= 0) return 0f
        for (i in 0 until marks.size - 1) if (v <= marks[i + 1]) return ((i + (v - marks[i]) / (marks[i + 1] - marks[i])) / (marks.size - 1)).toFloat()
        return 1f
    }
    val f by animateFloatAsState(frac(mbps))
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val tl = Offset(center.x - r, center.y - r)
            drawArc(C.SurfaceHigh, 135f, 270f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            if (f > 0f) drawArc(Brush.sweepGradient(listOf(C.Accent, C.Cyan, C.Accent), center), 135f, 270f * f, false, tl, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        marks.forEachIndexed { i, v ->
            val a = Math.toRadians(135.0 + 270.0 * i / (marks.size - 1))
            val rr = 108f
            Text(
                v.toInt().toString(), color = C.Dim, fontSize = 11.sp,
                modifier = Modifier.padding(start = (150 + rr * Math.cos(a) - 12).coerceAtLeast(0.0).dp.value.dp, top = (150 + rr * Math.sin(a) - 8).coerceAtLeast(0.0).dp.value.dp).align(Alignment.TopStart),
            )
        }
        if (!running && mbps <= 0) {
            Box(
                Modifier.size(140.dp).clip(CircleShape).background(Brush.linearGradient(listOf(C.Accent, C.Cyan))).clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) { Text("Начать", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable(enabled = !running, onClick = onStart).padding(20.dp)) {
                Text("%.1f".format(mbps), color = C.Text, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text("Мбит/с", color = C.Dim, fontSize = 14.sp)
                Text(if (running) phase else "Нажмите, чтобы повторить", color = if (running) C.Accent else C.Dim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(12.dp)) {
        Text(title, color = C.Dim, fontSize = 12.sp)
        Text(value, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LeakTestCard() {
    val st by LeakTest.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(Modifier.width(620.dp)) {
            Text(
                st.verdict ?: if (st.running) "Проверяю…" else "Видят ли сайты ваш настоящий IP и куда уходят DNS-запросы",
                color = when { st.verdict == null -> C.Text; st.good -> C.Good; else -> C.Warn }, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            st.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, color = C.Dim, fontSize = 13.sp, modifier = Modifier.width(170.dp))
                    Text(r.value, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (r.ok != null) Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.ok) C.Good else C.Bad))
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (st.running) "Проверяю…" else "Проверить", enabled = !st.running) { scope.launch { LeakTest.run() } }
        }
    }
}

@Composable
private fun ConnLogCard() {
    val on by ConnLog.enabled.collectAsState()
    val entries by ConnLog.entries.collectAsState()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth()) {
            Toggle("Записывать соединения", "Какие сайты шли через VPN, какие напрямую, что заблокировано. Хранится только в памяти программы", on) { ConnLog.setEnabled(it) }
            if (on) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Записей: ${entries.size}", color = C.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                SecondaryButton("Очистить") { ConnLog.clear() }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (on) {
            if (entries.isEmpty()) Text(if (Engine.status.value == VpnStatus.On) "Пока пусто — откройте какой-нибудь сайт" else "Подключите VPN", color = C.Dim, fontSize = 13.sp)
            LazyColumn(Modifier.fillMaxWidth().weight(1f).heightIn(min = 100.dp).clip(RoundedCornerShape(16.dp)).background(C.Surface)) {
                items(entries) { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.format(Date(e.time)), color = C.Dim, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                        Text(e.host, color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(e.app, color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                        Text(
                            if (e.route == ConnLog.Route.Vpn && e.server.isNotBlank()) "VPN · ${e.server}" else e.route.title,
                            color = when (e.route) { ConnLog.Route.Vpn -> C.Accent; ConnLog.Route.Direct -> C.Good; ConnLog.Route.Blocked -> C.Bad },
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp),
                        )
                    }
                }
            }
        }
    }
}
