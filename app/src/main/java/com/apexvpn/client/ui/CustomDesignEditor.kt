package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.Settings
import com.apexvpn.client.ui.theme.Apex

private val bases = listOf(
    0xFF000000, 0xFF0E0F14, 0xFF101826, 0xFF16122A, 0xFF0F1F17, 0xFF2A1410, 0xFF1F1A12,
    0xFFF4F4F7, 0xFFFFF6F0, 0xFFF0F6FF, 0xFFF3FFF5, 0xFFFFF0F6,
)
private val accents = listOf(
    0xFF7B6CFF, 0xFF3B82F6, 0xFF22D3EE, 0xFF14B8A6, 0xFF22C55E, 0xFF84CC16, 0xFFEAB308,
    0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFFD946EF, 0xFFD4AF37, 0xFFFFFFFF, 0xFF111111,
)

/** Ряд цветных кружков. */
@Composable
private fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { c ->
            val on = c == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) Apex.Accent else Apex.Outline, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (on) Text("✓", color = if (Color(c).red + Color(c).green + Color(c).blue > 1.8f) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 3.13. «Свой дизайн»: фон, акцент, второй цвет, скругление, стекло, рамка и свечение. */
@Composable
internal fun CustomDesignEditor(viewModel: MainViewModel, s: Settings) {
    fun set(block: Settings.() -> Settings) = viewModel.change(reload = false, block)
    SectionLabel("Свой дизайн")
    Group {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Фон", color = Apex.Text, fontWeight = FontWeight.SemiBold)
            Swatches(bases, s.cdBase) { c -> set { copy(cdBase = c) } }
            Spacer(Modifier.height(8.dp))
            Text("Основной цвет", color = Apex.Text, fontWeight = FontWeight.SemiBold)
            Swatches(accents, s.cdAccent) { c -> set { copy(cdAccent = c) } }
            Spacer(Modifier.height(8.dp))
            Text("Второй цвет (градиент щита и пятна стекла)", color = Apex.Text, fontWeight = FontWeight.SemiBold)
            Swatches(accents, s.cdAccent2) { c -> set { copy(cdAccent2 = c) } }
            Spacer(Modifier.height(10.dp))
            Text("Скругление карточек", color = Apex.Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val radii = listOf(4 to "Острые", 14 to "Мягкие", 24 to "Круглые", 34 to "Пилюли")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                radii.forEach { (r, title) ->
                    val on = s.cdRadius == r
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(r.coerceAtMost(20).dp))
                            .background(if (on) Apex.Accent else Apex.SurfaceHigh).clickable { set { copy(cdRadius = r) } },
                        contentAlignment = Alignment.Center,
                    ) { Text(title, color = if (on) Apex.OnAccent else Apex.Text, fontSize = 13.sp) }
                }
            }
        }
        GroupDivider()
        ListRow("Стекло", "Полупрозрачные карточки на цветном фоне") { ApexSwitch(s.cdGlass, { v -> set { copy(cdGlass = v) } }) }
        GroupDivider()
        ListRow("Рамка карточек", null) { ApexSwitch(s.cdBorder, { v -> set { copy(cdBorder = v) } }) }
        GroupDivider()
        ListRow("Неоновое свечение", "Рамки светятся основным цветом") { ApexSwitch(s.cdGlow, { v -> set { copy(cdGlow = v) } }) }
    }
    Caption("Светлый или тёмный — решает цвет фона. Изменения видны сразу.")
}
