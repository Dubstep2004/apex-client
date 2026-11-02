package com.apexvpn.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.KidsSchedule
import com.apexvpn.client.data.Settings
import com.apexvpn.client.ui.theme.Apex

private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** «Пн–Пт», «Каждый день», «Пн, Ср, Сб» */
internal fun KidsDaysText(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Каждый день"
    (1..5).toSet() -> "Будни"
    setOf(6, 7) -> "Выходные"
    else -> days.sorted().joinToString(", ") { dayNames[it - 1] }.ifEmpty { "Дни не выбраны" }
}

/** 3.6. Расписание семейного режима: дни недели и время с шагом 30 минут. */
@Composable
internal fun KidsScheduleEditor(viewModel: MainViewModel, s: Settings) {
    Spacer(Modifier.height(10.dp))
    Group {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayNames.forEachIndexed { i, name ->
                val d = i + 1
                val on = d in s.kidsDays
                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable {
                    viewModel.change { copy(kidsDays = if (on) kidsDays - d else kidsDays + d) }
                }, contentAlignment = Alignment.Center) { Tag(name, outlined = !on) }
            }
        }
        GroupDivider()
        TimeStepper("С", s.kidsFrom) { v -> viewModel.change { copy(kidsFrom = v) } }
        GroupDivider()
        TimeStepper("До", s.kidsTo) { v -> viewModel.change { copy(kidsTo = v) } }
    }
    Spacer(Modifier.height(6.dp))
    Caption(
        (if (KidsSchedule.active(s)) "Сейчас семейный режим действует. " else "Сейчас семейный режим не действует. ") +
            "Включается и выключается сам, пока VPN включён. Окно может идти через полночь, например 20:00–07:00.",
    )
}

@Composable
internal fun TimeStepper(title: String, minutes: Int, onChange: (Int) -> Unit) {
    val step = 30
    ListRow(title) {
        Text("−", color = Apex.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onChange((minutes - step + 1440) % 1440) }.padding(horizontal = 14.dp))
        Text(KidsSchedule.hhmm(minutes), color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("+", color = Apex.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onChange((minutes + step) % 1440) }.padding(horizontal = 14.dp))
    }
}
