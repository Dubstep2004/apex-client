package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.LeakTest
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.launch

/** 585. Проверка утечек: IP, DNS, IPv6 и WebRTC. */
@Composable
internal fun LeakTestPage(onBack: () -> Unit) {
    val st by LeakTest.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // 3.17.11: адреса скрыты точками — удобно делать скриншоты; глазик показывает их
    val m: (String) -> String = { IpMask.apply(it) }
    PageScaffold("Проверка утечек", onBack) {
        Caption("Проверяем, не видят ли сайты, звонки и WebRTC ваш настоящий адрес. Запросы — к Cloudflare, Google STUN и ip-api, только по нажатию; результат остаётся на телефоне.")
        Spacer(Modifier.height(12.dp))
        if (st.items.isNotEmpty()) {
            IpToggle()
            Spacer(Modifier.height(6.dp))
            Group {
                st.items.forEachIndexed { i, item ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        item.title,
                        listOf(item.value, item.note).filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }?.let(m),
                        leading = {
                            if (st.running && item.mark == LeakTest.Mark.Wait) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Apex.Accent)
                            } else {
                                val (icon, tint) = when (item.mark) {
                                    LeakTest.Mark.Ok -> Icons.Rounded.CheckCircle to Apex.Good
                                    LeakTest.Mark.Warn -> Icons.Rounded.Warning to Apex.Warn
                                    LeakTest.Mark.Bad -> Icons.Rounded.Error to Apex.Bad
                                    LeakTest.Mark.Wait -> Icons.Rounded.RadioButtonUnchecked to Apex.TextDim
                                }
                                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
                            }
                        },
                    )
                }
            }
        }
        st.verdict?.let { v ->
            Spacer(Modifier.height(12.dp))
            val good = st.items.none { it.mark == LeakTest.Mark.Bad }
            Text(
                m(v), color = Apex.Text, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background((if (good) Apex.Good else Apex.Bad).copy(alpha = 0.12f)).padding(16.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { scope.launch { LeakTest.run() } },
            enabled = !st.running,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) { Text(if (st.running) "Проверяем…" else if (st.items.isEmpty()) "Проверить" else "Проверить ещё раз", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(24.dp))
    }
}

/** IP-адреса в тексте → «185.•••.•••.•••» (IPv4) и «2a02:••••» (IPv6). */
internal fun maskIps(text: String): String =
    text.replace(Regex("""\b(\d{1,3})\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")) { "${it.groupValues[1]}.•••.•••.•••" }
        .replace(Regex("""\b([0-9a-fA-F]{1,4}):(?:[0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4}\b""")) { "${it.groupValues[1]}:••••" }

/**
 * 3.17.11. Скрытие IP-адресов во всём приложении (проверка утечек, тест скорости, журнал
 * соединений): по умолчанию — точками, «глазик» показывает. Действует до закрытия приложения.
 */
object IpMask {
    var shown by androidx.compose.runtime.mutableStateOf(false)
    fun apply(text: String): String = if (shown) text else maskIps(text)
}

/** Кнопка «Показать / Скрыть IP-адреса». */
@Composable
internal fun IpToggle(modifier: Modifier = Modifier) {
    val on = IpMask.shown
    androidx.compose.foundation.layout.Row(
        modifier.clip(RoundedCornerShape(14.dp)).clickable { IpMask.shown = !on }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(if (on) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = Apex.Accent, modifier = Modifier.size(22.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(if (on) "Скрыть IP-адреса" else "Показать IP-адреса", color = Apex.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
