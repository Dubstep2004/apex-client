package com.apexvpn.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.R
import com.apexvpn.client.data.KeepAlive
import com.apexvpn.client.data.SelfCheck
import com.apexvpn.client.ui.theme.Apex

// ─── Надёжность ─────────────────────────────────────────────────────────

/** «Все серверы работают» / «Работают 18 из 24» — по последнему замеру; нажатие — проверить заново. */
@Composable
fun ServerHealthCard(viewModel: MainViewModel) {
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val pings by viewModel.pings.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    val nodes = sub.nodes
    if (nodes.isEmpty()) return
    // Свежий замер с экрана серверов, иначе — сохранённый с прошлого раза
    val source = pings.ifEmpty { remember(nodes) { com.apexvpn.client.data.Prefs.lastPings() } }
    val measured = nodes.filter { source.containsKey(it.tag) }
    val ok = nodes.count { (source[it.tag] ?: -1) > 0 }
    val (color, text) = when {
        pinging -> Apex.TextDim to "Проверяем серверы…"
        measured.isEmpty() && ok == 0 -> Apex.TextDim to "Проверить серверы"
        ok == nodes.size -> Apex.Good to "Все серверы работают"
        ok == 0 -> Apex.Bad to "Серверы не отвечают"
        else -> Apex.Warn to "Работают $ok из ${nodes.size}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .apexCard()
            .clickable(enabled = !pinging) { viewModel.pingAll() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinging) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Apex.Accent)
        } else {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Apex.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Chevron()
    }
}

/** «Чтобы VPN не отключался»: батарея, автозапуск и инструкции для своей марки телефона. */
@Composable
internal fun KeepAlivePage(onBack: () -> Unit) {
    val context = LocalContext.current
    // Статус перечитываем, когда пользователь вернулся из системных настроек
    var unrestricted by remember { mutableStateOf(KeepAlive.unrestricted(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { unrestricted = KeepAlive.unrestricted(context) }
    }
    val mine = remember { KeepAlive.thisVendor() }
    PageScaffold("Чтобы VPN не отключался", onBack) {
        Caption("Многие телефоны закрывают фоновые приложения ради батареи — и VPN выключается сам. Два шага ниже это исправляют.")
        Spacer(Modifier.height(12.dp))
        Group {
            ListRow(
                "1. Батарея без ограничений",
                if (unrestricted) "Готово — Android не будет усыплять VPN" else "Сейчас Android может закрыть VPN в фоне",
                leading = { StatusIcon(if (unrestricted) SelfCheck.Mark.Ok else SelfCheck.Mark.Warn) },
                onClick = if (unrestricted) null else ({ KeepAlive.requestUnrestricted(context) }),
            ) {
                if (!unrestricted) {
                    Text("Разрешить", color = Apex.AccentText, fontWeight = FontWeight.SemiBold)
                }
            }
            GroupDivider()
            ListRow(
                "2. Автозапуск",
                "Откроется экран вашей оболочки — включите APEX VPN",
                leading = { StatusIcon(SelfCheck.Mark.Wait) },
                onClick = { KeepAlive.openAutostart(context) },
            ) { Chevron() }
        }
        SectionLabel("Ваш телефон: ${mine.title}")
        Group(Modifier.padding(bottom = 4.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mine.steps.forEachIndexed { i, step -> StepLine(i + 1, step) }
            }
        }
        SectionLabel("Другие марки")
        KeepAlive.vendors.filter { it.id != mine.id }.forEach { v ->
            var open by remember { mutableStateOf(false) }
            Group(Modifier.padding(bottom = 8.dp)) {
                ListRow(v.title, onClick = { open = !open }) { Text(if (open) "−" else "+", color = Apex.TextDim, fontSize = 20.sp) }
                if (open) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        v.steps.forEachIndexed { i, step -> StepLine(i + 1, step) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { KeepAlive.openAppSettings(context) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Открыть настройки APEX VPN", color = Apex.Text)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Apex.AccentSoft), contentAlignment = Alignment.Center) {
            Text("$n", color = Apex.AccentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Apex.Text, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun StatusIcon(mark: SelfCheck.Mark) {
    when (mark) {
        SelfCheck.Mark.Run -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Apex.Accent)
        else -> {
            val (icon, tint) = when (mark) {
                SelfCheck.Mark.Ok -> Icons.Rounded.CheckCircle to Apex.Good
                SelfCheck.Mark.Warn -> Icons.Rounded.Warning to Apex.Warn
                SelfCheck.Mark.Fail -> Icons.Rounded.Error to Apex.Bad
                else -> Icons.Rounded.RadioButtonUnchecked to Apex.TextDim
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

/** «Не работает?» — самопроверка по шагам и выбор рабочего сервера. */
@Composable
internal fun SelfCheckPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val st by SelfCheck.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!st.running && st.steps.isEmpty()) viewModel.runSelfCheck() }
    PageScaffold("Не работает?", onBack) {
        Caption("Проверим по шагам: интернет, подписку, серверы и VPN — и подскажем, что сделать.")
        Spacer(Modifier.height(12.dp))
        Group {
            st.steps.forEachIndexed { i, step ->
                if (i > 0) GroupDivider()
                ListRow(step.title, step.detail.ifEmpty { null }, leading = { StatusIcon(step.mark) })
            }
        }
        st.verdict?.let { v ->
            Spacer(Modifier.height(14.dp))
            val good = st.steps.none { it.mark == SelfCheck.Mark.Fail }
            Text(
                v,
                color = Apex.Text,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background((if (good) Apex.Good else Apex.Bad).copy(alpha = 0.12f))
                    .padding(16.dp),
            )
        }
        if (st.fixTag != null && !st.running) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.select(st.fixTag!!); viewModel.showMessage("Выбран ${st.fixTitle}"); viewModel.runSelfCheck() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
            ) { Text("Выбрать рабочий: ${st.fixTitle}", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.select(com.apexvpn.client.data.AUTO_TAG); viewModel.showMessage("Включён автопилот"); viewModel.runSelfCheck() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Включить автопилот", color = Apex.Text) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.runSelfCheck() },
            enabled = !st.running,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (st.running) "Проверяем…" else "Проверить ещё раз", color = Apex.Text) }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── О приложении и лицензии ────────────────────────────────────────────

private data class License(val name: String, val license: String, val url: String)

private val licenses = listOf(
    License("sing-box", "GPL-3.0", "https://github.com/SagerNet/sing-box"),
    License("Xray-core", "MPL-2.0", "https://github.com/XTLS/Xray-core"),
    License("Базы sing-geosite и sing-geoip", "GPL-3.0", "https://github.com/SagerNet/sing-geosite"),
    License("domain-list-community", "MIT", "https://github.com/v2fly/domain-list-community"),
    License("Jetpack Compose и AndroidX", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    License("Kotlin и kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    License("ZXing и zxing-android-embedded", "Apache-2.0", "https://github.com/journeyapps/zxing-android-embedded"),
    License("flag-icons", "MIT", "https://github.com/lipis/flag-icons"),
    License("world.geo.json", "Unlicense", "https://github.com/johan/world.geo.json"),
)

@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    var showLicenses by remember { mutableStateOf(false) }
    PageScaffold("О приложении", onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(112.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B1830), Color(0xFF0B0B14)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(160.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("APEX VPN", color = Apex.Text, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Apex.TextDim, fontSize = 15.sp)
            Text("sing-box ${singBoxVersion()} · Xray ${BuildConfig.XRAY_VERSION}", color = Apex.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("© 2026 Dubstep2004", color = Apex.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AboutLine(Icons.Rounded.VisibilityOff, "Без журналов: APEX VPN не хранит и никуда не отправляет, какие сайты вы открываете")
            AboutLine(Icons.Rounded.Public, "Серверы по всему миру; российские сайты и банки идут напрямую")
            AboutLine(Icons.Rounded.Lock, "Только подписки ${BuildConfig.SUBSCRIPTION_HOST} — чужие ссылки приложение не примет")
            AboutLine(Icons.Rounded.SupportAgent, "Поддержка в Telegram: ${com.apexvpn.client.data.Links.SUPPORT_NAME}") {
                open(com.apexvpn.client.data.Links.SUPPORT)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Приложение собрано на открытом коде под лицензией GPL-3.0 и распространяется без гарантий. " +
                "Исходный код этой версии — на GitHub. Название «APEX VPN» и логотип под GPL не передаются.",
            color = Apex.TextDim, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth().apexCard().padding(18.dp),
        )
        Spacer(Modifier.height(12.dp))
        Group {
            ListRow(
                "Исходный код версии ${BuildConfig.VERSION_NAME}",
                "github.com/Dubstep2004/apex-vpn",
                leading = { Icon(Icons.Rounded.Code, null, tint = Apex.Text) },
                onClick = { open("${com.apexvpn.client.data.Links.GITHUB}/tree/v${BuildConfig.VERSION_NAME}") },
            ) { Chevron() }
            GroupDivider()
            ListRow("Лицензии", "Открытые проекты внутри APEX VPN", onClick = { showLicenses = !showLicenses }) {
                Text(if (showLicenses) "−" else "+", color = Apex.TextDim, fontSize = 20.sp)
            }
            if (showLicenses) {
                licenses.forEach { l ->
                    GroupDivider()
                    ListRow(l.name, l.license, onClick = { open(l.url) }) { Chevron() }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "© APEX VPN",
            color = Apex.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutLine(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Apex.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Apex.AccentText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Apex.Text, fontSize = 16.sp, lineHeight = 21.sp)
    }
}
