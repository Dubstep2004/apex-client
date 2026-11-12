package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.MonthStats
import com.apexvpn.client.data.WeekStats
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import com.apexvpn.client.ui.theme.Apex

/** 3.6. Отчёт за месяц — только данные с этого телефона. */
@Composable
internal fun ReportPage(onBack: () -> Unit) {
    val months = remember { MonthStats.flush(); MonthStats.months() }
    var idx by remember { mutableIntStateOf(0) }
    val key = months.getOrElse(idx) { MonthStats.key() }
    val m = remember(key) { MonthStats.get(key) }

    PageScaffold("Отчёт и история", onBack) {
        WeekSection()
        SectionLabel("За месяц")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = if (idx < months.size - 1) Apex.Accent else Apex.TextDim, fontSize = 28.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = idx < months.size - 1) { idx++ }.padding(horizontal = 14.dp))
            Text(MonthStats.title(key).replaceFirstChar { it.uppercase() }, color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("›", color = if (idx > 0) Apex.Accent else Apex.TextDim, fontSize = 28.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = idx > 0) { idx-- }.padding(horizontal = 14.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (m.empty) {
            Caption("За этот месяц пока нет данных: они собираются, пока VPN включён (с версии 3.6).")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Трафик через VPN", formatBytes(m.down + m.up), "↓ ${formatBytes(m.down)}  ↑ ${formatBytes(m.up)}", Modifier.weight(1f))
                Tile("Время с VPN", hours(m.seconds), "${m.connects} подключ.", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Реклама и трекеры", "%,d".format(m.ads).replace(',', ' '), "заблокировано", Modifier.weight(1f))
                Tile("Средний пинг", if (m.avgPing > 0) "${m.avgPing} мс" else "—", "через VPN", Modifier.weight(1f))
            }
            if (m.servers.isNotEmpty()) {
                SectionLabel("Чаще всего")
                Group {
                    val total = m.servers.values.sum().coerceAtLeast(1)
                    m.servers.entries.sortedByDescending { it.value }.take(5).forEachIndexed { i, (name, sec) ->
                        if (i > 0) GroupDivider()
                        ListRow(name, "${hours(sec)} · ${sec * 100 / total}%")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Caption("Трафик — всё, что прошло через VPN на этом телефоне. Отчёт никуда не отправляется.")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 3.19. Последние 7 дней (столбики — время с VPN) и последние подключения. */
@Composable
private fun WeekSection() {
    val days = remember { WeekStats.lastDays() }
    val sessions = remember { WeekStats.sessions() }
    if (days.isEmpty() || days.all { it.seconds == 0L && it.connects == 0 } && sessions.isEmpty()) {
        SectionLabel("Последние 7 дней")
        Caption("История начнёт собираться с этого обновления, пока VPN включён.")
        Spacer(Modifier.height(8.dp))
        return
    }
    val total = days.sumOf { it.seconds }
    SectionLabel("Последние 7 дней", hours(total))
    Group {
        val max = days.maxOf { it.seconds }.coerceAtLeast(1L)
        Row(
            Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val frac = (d.seconds.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height((4 + 64 * frac).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (i == days.lastIndex) Apex.Accent else Apex.Accent.copy(alpha = if (d.seconds > 0) 0.45f else 0.12f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(WeekStats.weekday(d.key), color = if (i == days.lastIndex) Apex.Text else Apex.TextDim, fontSize = 12.sp)
                }
            }
        }
        GroupDivider()
        ListRow("Трафик через VPN", formatBytes(days.sumOf { it.down + it.up }))
        GroupDivider()
        ListRow("Подключений", "${days.sumOf { it.connects }} · реклама и трекеры: ${"%,d".format(days.sumOf { it.ads }).replace(',', ' ')}")
    }
    if (sessions.isNotEmpty()) {
        SectionLabel("Последние подключения")
        Group {
            sessions.take(10).forEachIndexed { i, s ->
                if (i > 0) GroupDivider()
                val dur = if (s.end > 0) hours((s.end - s.start) / 1000) else "сейчас"
                ListRow("${WeekStats.whenText(s.start)} · $dur", "${s.server} · ${formatBytes(s.bytes)}")
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private fun hours(sec: Long): String = when {
    sec >= 3600 -> "${sec / 3600} ч ${sec % 3600 / 60} мин"
    sec >= 60 -> "${sec / 60} мин"
    else -> "$sec с"
}

@Composable
private fun Tile(title: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Apex.Surface).padding(16.dp)) {
        Text(title, color = Apex.TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Apex.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(1.dp))
        Text(sub, color = Apex.TextDim, fontSize = 12.sp)
    }
}
