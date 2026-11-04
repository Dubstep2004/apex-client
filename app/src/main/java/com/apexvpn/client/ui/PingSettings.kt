package com.apexvpn.client.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.launch

/** Пинг серверов: тип, режим «через прокси», тайм-аут, адрес проверки и вид результата. */
@Composable
internal fun PingSettingsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    PageScaffold("Пинг", onBack) {
        SectionLabel("Тип пинга")
        Group {
            val types = listOf(
                Triple("tcp", "TCP — до сервера", "Время соединения с самим сервером (для Hysteria — QUIC). Быстро и честно, как в других клиентах"),
                Triple("get", "Через прокси · GET", "Запрос к сайту через сервер — показывает, что интернет через него правда работает"),
                Triple("head", "Через прокси · HEAD", "То же, но без тела ответа — чуть быстрее и экономнее"),
                Triple("icmp", "ICMP", "Классический ping. Некоторые серверы и операторы его не пропускают"),
            )
            types.forEachIndexed { i, (id, title, sub) ->
                if (i > 0) GroupDivider()
                ListRow(title, sub, leading = { RadioMark(s.pingType == id) }, onClick = {
                    viewModel.change(reload = false) { copy(pingType = id) }
                    viewModel.pingAll()
                })
            }
        }
        if (s.pingType == "get" || s.pingType == "head") {
            Caption("Через прокси меряется только при включённом VPN. Без VPN — пинг до сервера (TCP).")
        }
        PingTester(s.pingType)

        SectionLabel("Режим (через прокси)")
        Group {
            val modes = listOf(
                Triple("default", "Обычный", "Один запрос: подключение, шифрование и ответ"),
                Triple("double", "Double", "Два запроса с новым соединением, берётся лучший — второй быстрее за счёт DNS"),
                Triple("keepalive", "KeepAlive", "Два запроса по одному соединению — второй показывает чистое время ответа через сервер"),
            )
            modes.forEachIndexed { i, (id, title, sub) ->
                if (i > 0) GroupDivider()
                ListRow(title, sub, leading = { RadioMark(s.pingMode == id) }, onClick = {
                    viewModel.change(reload = false) { copy(pingMode = id) }
                })
            }
        }

        SectionLabel("Тайм-аут пинга через прокси", "${s.pingTimeout} сек")
        var t by remember(s.pingTimeout) { mutableFloatStateOf(s.pingTimeout.toFloat()) }
        Column(Modifier.fillMaxWidth().apexCard().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Slider(
                value = t,
                onValueChange = { t = it },
                onValueChangeFinished = { viewModel.change(reload = false) { copy(pingTimeout = t.toInt()) } },
                valueRange = 5f..15f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = Apex.Accent, activeTrackColor = Apex.Accent, inactiveTrackColor = Apex.SurfaceHigh),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("быстрее", color = Apex.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("терпеливее", color = Apex.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        // 185: длинный тайм-аут — список проверяется дольше
        Caption(
            if (t.toInt() > 8) "Сейчас ${t.toInt()} сек: неработающий сервер будет «думать» до ${t.toInt()} секунд, и список проверяется дольше. Рекомендуем 5–8 сек; больше — только для очень медленной сети."
            else "Рекомендуем 5–8 сек. Сервер, который не ответил за это время, считается недоступным.",
        )
        if (t.toInt() > 8) {
            TextButton(onClick = { t = 6f; viewModel.change(reload = false) { copy(pingTimeout = 6) } }) {
                Text("Поставить 6 сек", color = Apex.AccentText)
            }
        }

        SectionLabel("Адрес проверки (URL-тест)")
        var url by remember { mutableStateOf(s.pingUrl) }
        var err by remember { mutableStateOf<String?>(null) }
        PlainField(url, "https://www.gstatic.com/generate_204") { url = it.trim(); err = null }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (url.isNotEmpty() && !url.startsWith("https://") && !url.startsWith("http://")) {
                    err = "Адрес должен начинаться с https:// или http://"
                } else {
                    // Адрес нужен и автопилоту — ядро перезагрузится
                    viewModel.change { copy(pingUrl = url) }
                    viewModel.showMessage(if (url.isEmpty()) "Адрес проверки — по умолчанию" else "Адрес проверки сохранён")
                }
            }) { Text("Сохранить", color = Apex.AccentText) }
            if (s.pingUrl.isNotEmpty()) {
                TextButton(onClick = { url = ""; viewModel.change { copy(pingUrl = "") } }) { Text("По умолчанию", color = Apex.TextDim) }
            }
        }
        err?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp)) }
        Caption("По этому адресу проверяют серверы пинг «через прокси» и автопилот. По умолчанию — https://www.gstatic.com/generate_204. Адрес должен отвечать быстро и надёжно.")

        // 3.14. Свои пороги цвета
        SectionLabel("Цвет пинга", "до ${s.pingGood} мс — зелёный")
        val presets = listOf(Triple("Обычный", 300, 800), Triple("Для игр", 60, 150), Triple("Строгий", 120, 300), Triple("Мягкий", 500, 1500))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (title, g, o) ->
                val sel = s.pingGood == g && s.pingOk == o
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) Apex.Accent else Apex.Surface)
                        .clickable { viewModel.change(reload = false) { copy(pingGood = g, pingOk = o) } },
                    contentAlignment = Alignment.Center,
                ) { Text(title, color = if (sel) Apex.OnAccent else Apex.Text, fontSize = 13.sp) }
            }
        }
        var good by remember(s.pingGood) { mutableFloatStateOf(s.pingGood.toFloat()) }
        var okv by remember(s.pingOk) { mutableFloatStateOf(s.pingOk.toFloat()) }
        Column(Modifier.fillMaxWidth().apexCard().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Зелёный — до ${good.toInt()} мс", color = Apex.Good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = good, onValueChange = { good = it; if (okv < it + 20) okv = it + 20 },
                onValueChangeFinished = { viewModel.change(reload = false) { copy(pingGood = good.toInt(), pingOk = maxOf(okv.toInt(), good.toInt() + 20)) } },
                valueRange = 30f..1000f,
                colors = SliderDefaults.colors(thumbColor = Apex.Good, activeTrackColor = Apex.Good, inactiveTrackColor = Apex.SurfaceHigh),
            )
            Text("Жёлтый — до ${okv.toInt()} мс, дальше красный", color = Apex.Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = okv, onValueChange = { okv = maxOf(it, good + 20) },
                onValueChangeFinished = { viewModel.change(reload = false) { copy(pingOk = okv.toInt()) } },
                valueRange = 60f..3000f,
                colors = SliderDefaults.colors(thumbColor = Apex.Warn, activeTrackColor = Apex.Warn, inactiveTrackColor = Apex.SurfaceHigh),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf((good * 0.5f).toInt(), ((good + okv) / 2).toInt(), (okv * 1.3f).toInt()).forEach { PingResult(it, "both") }
            }
        }
        Caption("Цвет пинга в списке серверов, на главной и в виджетах. «Для игр» — строже: зелёный только до 60 мс.")

        SectionLabel("Результат пинга")
        Group {
            val views = listOf("time" to "Время (мс)", "bars" to "Столбики", "both" to "Время и столбики")
            views.forEachIndexed { i, (id, title) ->
                if (i > 0) GroupDivider()
                ListRow(title, leading = { RadioMark(s.pingView == id) }, onClick = { viewModel.change(reload = false) { copy(pingView = id) } }) {
                    PingResult(84, id)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RadioMark(on: Boolean) {
    Box(
        Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) Apex.Accent else Apex.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Apex.OnAccent))
    }
}

/** Пинг в выбранном виде: «84 мс», столбики сигнала или вместе. */
@Composable
internal fun PingResult(ms: Int, view: String, text: String? = null) {
    val color = pingColor(ms)
    val bars = when {
        ms < 0 -> 0
        ms < com.apexvpn.client.data.AppSettings.value.pingGood * 2 / 5 -> 3
        ms < com.apexvpn.client.data.AppSettings.value.pingGood -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (view != "time") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..3) {
                    Box(
                        Modifier.width(4.dp).height((5 + i * 4).dp).clip(RoundedCornerShape(1.5.dp))
                            .background(if (i <= bars) color else color.copy(alpha = 0.22f)),
                    )
                }
            }
        }
        if (view != "bars") {
            if (view == "both") Spacer(Modifier.width(6.dp))
            Text(text ?: if (ms >= 0) "$ms мс" else "нет ответа", color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val pingTypes = listOf("tcp" to "TCP — до сервера", "get" to "Через прокси · GET", "head" to "Через прокси · HEAD", "icmp" to "ICMP")

/**
 * 3.14. «Проверить сейчас» — пинг текущего сервера выбранным способом; «Сравнить способы» —
 * тот же сервер всеми четырьмя (через прокси — только при включённом VPN).
 */
@Composable
private fun PingTester(current: String) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sub by com.apexvpn.client.data.Subscription.state.collectAsStateWithLifecycle()
    val status by com.apexvpn.client.vpn.VpnState.status.collectAsStateWithLifecycle()
    val on = status == com.apexvpn.client.vpn.VpnStatus.Started
    var server by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) } // тип → мс; null — идёт замер
    var busy by remember { mutableStateOf(false) }

    suspend fun target(): com.apexvpn.client.data.Node? {
        val nodes = sub.nodes
        val tag = if (sub.selectedTag == com.apexvpn.client.data.AUTO_TAG) {
            (if (on) com.apexvpn.client.data.Pinger.autopilotNow() else null)
                ?: com.apexvpn.client.data.Prefs.lastPings().filter { it.value > 0 }.minByOrNull { it.value }?.key
        } else sub.selectedTag
        return nodes.firstOrNull { it.tag == tag } ?: nodes.firstOrNull { !it.ads } ?: nodes.firstOrNull()
    }

    fun measure(types: List<String>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val n = target()
                if (n == null) { server = "Нет серверов — добавьте подписку"; return@launch }
                server = com.apexvpn.client.data.ServerInfo.title(n.name)
                results = types.associateWith { null }
                for (type in types) {
                    val ms = if ((type == "get" || type == "head") && !on) -3
                    else com.apexvpn.client.data.Pinger.pingAll(listOf(n), save = false, type = type)[n.tag] ?: -1
                    results = results + (type to ms)
                }
            } finally {
                busy = false
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(
                onClick = { measure(listOf(current)) }, enabled = !busy,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                modifier = Modifier.weight(1f),
            ) { Text("Проверить сейчас", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            androidx.compose.material3.OutlinedButton(onClick = { measure(pingTypes.map { it.first }) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Сравнить способы", color = Apex.Text, fontSize = 14.sp)
            }
        }
        server?.let {
            Spacer(Modifier.height(10.dp))
            Text("Сервер: $it", color = Apex.TextDim, fontSize = 13.sp)
        }
        results.forEach { (type, ms) ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pingTypes.firstOrNull { it.first == type }?.second ?: type, color = if (type == current) Apex.Text else Apex.TextDim,
                    fontSize = 14.sp, fontWeight = if (type == current) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                when {
                    ms == null -> Text("…", color = Apex.TextDim, fontSize = 15.sp)
                    ms == -3 -> Text("нужен включённый VPN", color = Apex.TextDim, fontSize = 13.sp)
                    else -> PingResult(ms, "time")
                }
            }
        }
        if (results.size > 1 && results.values.none { it == null }) {
            Spacer(Modifier.height(8.dp))
            Caption("TCP и ICMP — время до самого сервера; «через прокси» — до сайта через сервер, поэтому больше. Для списка серверов честнее TCP, для проверки «работает ли интернет» — GET.")
        }
    }
}
