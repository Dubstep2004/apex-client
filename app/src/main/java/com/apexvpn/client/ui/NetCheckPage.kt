package com.apexvpn.client.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.NetCheck
import com.apexvpn.client.data.NetCheck.Kind
import com.apexvpn.client.data.NetCheck.Mark
import com.apexvpn.client.ui.theme.Apex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun kindColor(k: Kind?) = when (k) {
    null, Kind.Ok -> Apex.Good
    Kind.WhiteList, Kind.JamPartial -> Apex.Warn
    else -> Apex.Bad
}

/** 3.21. «Проверка сети»: вердикт, что приложение уже сделало, этапы с узлами, история, «Поделиться». */
@Composable
internal fun NetCheckPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val st by NetCheck.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!st.running && st.result == null) viewModel.runNetCheck() }
    PageScaffold("Проверка сети", onBack) {
        val r = st.result
        val color = if (st.running) Apex.Accent else kindColor(r?.kind)
        // Вердикт
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.32f), Apex.Surface)))
                .padding(20.dp),
        ) {
            Text(r?.network ?: NetCheck.networkName(context), color = Apex.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (st.running) "Проверяем сеть…" else r?.let { NetCheck.title(it.kind) } ?: "Проверка сети",
                color = Apex.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (st.running) "Смотрим, что открывается напрямую, отвечают ли серверы и не глушат ли VPN. Это 10–20 секунд."
                else r?.let { NetCheck.explain(it.kind) } ?: "",
                color = Apex.Text.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp,
            )
            if (r != null && !st.running) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${r.okNodes}/${r.allNodes}", "серверов", Modifier.weight(1f))
                    Stat(if (r.ping > 0) "${r.ping}" else "—", "пинг, мс", Modifier.weight(1f))
                    Stat(if (r.protoAll > 0) "${r.protoOk}/${r.protoAll}" else "—", "протоколов", Modifier.weight(1f))
                }
            }
        }
        // Что уже сделано само
        if (r != null && !st.running && r.fixed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "✅ Уже исправлено: " + r.fixed, color = Apex.Text, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Apex.Good.copy(alpha = 0.12f)).padding(14.dp),
            )
        }
        // Что можно сделать одной кнопкой
        if (!st.running) {
            st.lteTag?.let { tag ->
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.select(tag); viewModel.showMessage("Выбран «Обход LTE»"); viewModel.runNetCheck() },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                ) { Text("Включить «Обход LTE»", fontWeight = FontWeight.SemiBold) }
            }
            if (st.suggestAuto) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.select(AUTO_TAG); viewModel.showMessage("Включён автопилот"); viewModel.runNetCheck() },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                ) { Text("Протокол вашего сервера глушат — включить автопилот", fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.runNetCheck() }, enabled = !st.running,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (r == null) Apex.Accent else Apex.SurfaceHigh, contentColor = if (r == null) Apex.OnAccent else Apex.Text),
        ) { Text(if (st.running) "Проверяем…" else "Проверить снова", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.History, null, tint = Apex.Text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("История", color = Apex.Text)
            }
            OutlinedButton(
                onClick = {
                    val t = NetCheck.shareText()
                    if (t.isNotEmpty()) runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Поделиться")) }
                },
                enabled = r != null && !st.running, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Rounded.Share, null, tint = Apex.Text, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Поделиться", color = Apex.Text) }
        }
        if (showHistory) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("История проверок")
            val hist = remember(st.result) { NetCheck.history() }
            Group {
                if (hist.isEmpty()) ListRow("Пока пусто", "Здесь появятся прошлые проверки")
                val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
                hist.forEachIndexed { i, h ->
                    if (i > 0) GroupDivider()
                    ListRow(NetCheck.title(h.kind), fmt.format(Date(h.at)) + " · " + h.network, leading = { Dot(kindColor(h.kind)) })
                }
            }
        }
        // Этапы
        st.stages.forEachIndexed { i, stage ->
            Spacer(Modifier.height(14.dp))
            SectionLabel("${i + 1} · ${stage.title}")
            Group {
                if (stage.items.isEmpty()) {
                    ListRow(
                        if (st.running && stage.note.isEmpty()) "Проверяем…" else stage.note.ifEmpty { "—" }, null,
                        leading = { MarkIcon(if (st.running && stage.note.isEmpty()) Mark.Run else Mark.Wait) },
                    )
                }
                stage.items.forEachIndexed { j, it ->
                    if (j > 0) GroupDivider()
                    ListRow(it.title, it.detail, leading = { MarkIcon(it.mark) }) {
                        if (it.ms > 0) Text("${it.ms} мс", color = Apex.TextDim, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption("Проверка идёт только на вашем телефоне — никуда не отправляется. Сайты проверяются напрямую, мимо VPN.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f)).padding(10.dp)) {
        Text(value, color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Apex.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(c))

@Composable
private fun MarkIcon(mark: Mark) {
    if (mark == Mark.Run) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Apex.Accent); return
    }
    val (icon, tint) = when (mark) {
        Mark.Ok -> Icons.Rounded.CheckCircle to Apex.Good
        Mark.Slow -> Icons.Rounded.Warning to Apex.Warn
        Mark.Fail -> Icons.Rounded.Error to Apex.Bad
        else -> Icons.Rounded.RadioButtonUnchecked to Apex.TextDim
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
}
