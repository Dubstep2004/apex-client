package com.apexvpn.client.ui

import android.Manifest
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AccentColor
import com.apexvpn.client.data.Design
import com.apexvpn.client.data.AppsMode
import com.apexvpn.client.data.ConnProfile
import com.apexvpn.client.data.DnsChoice
import com.apexvpn.client.data.ThemeMode
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.WifiInfoReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import kotlinx.coroutines.launch

/** Дополнительные страницы, открываются поверх вкладок. */
enum class Page { Apps, SpeedTest, SiteCheck, Wifi, Share, Dns, Profile, Appearance, Changelog, Advanced, HideApps, AppTraffic, Help, KeepAlive, SelfCheck, About, Ping, ConnLog, LeakTest, AppIssue, ServiceCheck, Report, Rollback, Helper, AutoRules }

@Composable
fun PageHost(page: Page, viewModel: MainViewModel, onOpen: (Page) -> Unit = {}, onBack: () -> Unit) {
    when (page) {
        Page.Apps -> AppsPage(viewModel, onBack)
        Page.SpeedTest -> SpeedTestPage(viewModel, onBack)
        Page.SiteCheck -> SiteCheckPage(viewModel, onBack)
        Page.Wifi -> WifiPage(viewModel, onBack)
        Page.Share -> SharePage(viewModel, onBack)
        Page.Dns -> DnsPage(viewModel, onBack)
        Page.Profile -> ProfilePage(viewModel, onBack)
        Page.Appearance -> AppearancePage(viewModel, onBack)
        Page.Changelog -> ChangelogPage(onBack)
        Page.Advanced -> AdvancedPage(viewModel, onBack)
        Page.HideApps -> HideAppsPage(viewModel, onBack)
        Page.AppTraffic -> AppTrafficPage(viewModel, onBack)
        Page.Help -> HelpPage(onBack)
        Page.KeepAlive -> KeepAlivePage(onBack)
        Page.SelfCheck -> SelfCheckPage(viewModel, onBack)
        Page.About -> AboutPage(onBack)
        Page.Ping -> PingSettingsPage(viewModel, onBack)
        Page.ConnLog -> ConnLogPage(viewModel, onBack)
        Page.LeakTest -> LeakTestPage(onBack)
        Page.AppIssue -> AppIssuePage(viewModel, onBack)
        Page.ServiceCheck -> ServiceCheckPage(viewModel, onBack)
        Page.Report -> ReportPage(onBack)
        Page.Rollback -> RollbackPage(viewModel, onBack)
        Page.Helper -> HelperPage(viewModel, onBack, onOpen)
        Page.AutoRules -> AutoRulesPage(viewModel, onBack)
    }
}

@Composable
internal fun PageScaffold(title: String, onBack: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
    com.apexvpn.client.ui.theme.DesignBackground()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = Apex.Text) }
            Spacer(Modifier.width(4.dp))
            Text(title, color = Apex.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListRow(title, subtitle, leading = { RadioDot(selected) }, onClick = onClick)
}

// ─── 6. Приложения через VPN ────────────────────────────────────────────

@Composable
private fun AppsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val appList by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadApps() }

    PageScaffold("Приложения", onBack, scroll = false) {
        Group {
            AppsMode.entries.forEachIndexed { i, m ->
                if (i > 0) GroupDivider()
                ChoiceRow(m.title, m.subtitle, s.appsMode == m) { viewModel.change { copy(appsMode = m) } }
            }
        }
        if (s.appsMode != AppsMode.AllApps) {
            Spacer(Modifier.height(12.dp))
            SearchField(query, "Поиск приложения") { query = it }
            Spacer(Modifier.height(10.dp))
            val list = appList
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Apex.Accent)
                }
            } else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                    .sortedByDescending { it.packageName in s.apps }
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Apex.Surface),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val on = app.packageName in s.apps
                        val icon = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                        ListRow(
                            title = app.label,
                            subtitle = app.packageName,
                            leading = {
                                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else GlyphTile(app.label.take(1), Apex.Accent)
                            },
                            onClick = { viewModel.change { copy(apps = if (on) apps - app.packageName else apps + app.packageName) } },
                        ) {
                            ApexSwitch(on, { v -> viewModel.change { copy(apps = if (v) apps + app.packageName else apps - app.packageName) } })
                        }
                    }
                }
            }
        } else {
            Caption("Выберите режим выше, чтобы отметить отдельные приложения.")
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ─── 413. Трафик по приложениям ─────────────────────────────────────────

@Composable
private fun AppTrafficPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val today by com.apexvpn.client.data.AppTraffic.today.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var week by rememberSaveable { mutableStateOf(false) }
    val list = if (week) remember(today) { com.apexvpn.client.data.AppTraffic.period(7) } else today
    PageScaffold("Трафик приложений", onBack) {
        Group {
            ListRow("Считать трафик по приложениям", if (android.os.Build.VERSION.SDK_INT >= 29) "Только то, что идёт через VPN. Данные — только на телефоне" else "Нужен Android 10 или новее") {
                ApexSwitch(s.appTraffic, { v -> viewModel.change { copy(appTraffic = v) } }, enabled = android.os.Build.VERSION.SDK_INT >= 29)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChipRow(listOf("today" to "Сегодня", "week" to "7 дней"), if (week) "week" else "today") { week = it == "week" }
        Spacer(Modifier.height(12.dp))
        val total = list.sumOf { it.total }
        Text(formatBytes(total), color = Apex.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (week) "через VPN за 7 дней" else "через VPN сегодня", color = Apex.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Caption("Пока пусто. Включите VPN и пользуйтесь приложениями — здесь появится, кто сколько потратил.")
        } else {
            val top = list.first().total.coerceAtLeast(1)
            Group {
                list.take(40).forEachIndexed { i, u ->
                    if (i > 0) GroupDivider()
                    val info = remember(u.pkg) {
                        runCatching {
                            val ai = context.packageManager.getApplicationInfo(u.pkg, 0)
                            context.packageManager.getApplicationLabel(ai).toString() to
                                runCatching { context.packageManager.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                        }.getOrDefault(u.pkg to null)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = info.second
                        if (icon != null) Image(icon, null, Modifier.size(40.dp)) else GlyphTile(info.first.take(1), Apex.Accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.first, color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("↓ ${formatBytes(u.down)}   ↑ ${formatBytes(u.up)}", color = Apex.TextDim, fontSize = 13.sp)
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Apex.SurfaceHigh)) {
                                Box(Modifier.fillMaxWidth((u.total.toFloat() / top).coerceIn(0.02f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Apex.accent))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(formatBytes(u.total), color = Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Caption("Считается примерно: раз в 3 секунды. Сайты, которые идут напрямую, не учитываются.")
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 424. Справка ───────────────────────────────────────────────────────

@Composable
private fun HelpPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(-1) }
    PageScaffold("Справка", onBack) {
        Caption("Короткие инструкции с картинками — те же, что на странице APEX VPN на GitHub.")
        Spacer(Modifier.height(10.dp))
        com.apexvpn.client.data.HelpTopics.all.forEachIndexed { i, t ->
            val expanded = open == i
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .apexCard()
                    .clickable { open = if (expanded) -1 else i }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(t.title, color = Apex.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (expanded) "−" else "+", color = Apex.TextDim, fontSize = 22.sp)
                }
                androidx.compose.animation.AnimatedVisibility(expanded) {
                    Column {
                        t.image?.let { name ->
                            val img = remember(name) {
                                runCatching { context.assets.open("help/$name.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
                            }
                            if (img != null) {
                                Spacer(Modifier.height(12.dp))
                                Image(img, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        t.steps.forEachIndexed { k, step ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(Apex.AccentSoft), contentAlignment = Alignment.Center) {
                                    Text("${k + 1}", color = Apex.AccentText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(step, color = Apex.Text, fontSize = 15.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 349. Скрыть VPN от приложений ──────────────────────────────────────

@Composable
private fun HideAppsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val appList by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadApps() }
    PageScaffold("Скрыть VPN", onBack, scroll = false) {
        Caption("Отмеченные приложения работают мимо VPN — для них соединение обычное, и они не видят, что VPN включён. Как «Банки не увидят VPN», только список ваш: госуслуги, маркетплейсы, игры с защитой от VPN.")
        Spacer(Modifier.height(12.dp))
        SearchField(query, "Поиск приложения") { query = it }
        Spacer(Modifier.height(10.dp))
        val list = appList
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Apex.Accent)
            }
        } else {
            // Готовый шаблон: российские приложения, которые проверяют VPN
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val tpl = remember(list) { com.apexvpn.client.data.HideTemplates.installed(list, ctx.packageName) }
            if (tpl.isNotEmpty() && query.isBlank()) {
                val added = tpl.count { it in s.hiddenApps }
                val all = added == tpl.size
                Group {
                    ListRow(
                        "📋 Шаблон: ${com.apexvpn.client.data.HideTemplates.TITLE}",
                        if (all) "Добавлены все: ${tpl.size} · нажмите, чтобы убрать"
                        else "Найдено у вас: ${tpl.size}" + (if (added > 0) " · уже скрыто $added" else "") + " · нажмите, чтобы добавить все",
                        onClick = {
                            viewModel.change {
                                if (all) copy(hiddenApps = hiddenApps - tpl.toSet(), hideTemplateAuto = false)
                                else copy(hiddenApps = hiddenApps + tpl, hideTemplateSkip = hideTemplateSkip - tpl.toSet())
                            }
                        },
                    ) { Tag(if (all) "Убрать" else "Добавить") }
                    GroupDivider()
                    // 3.10. Шаблон обновляется сам
                    ListRow(
                        "Обновлять шаблон автоматически",
                        (if (s.hideTemplateAuto) "Новые приложения из шаблона добавляются сами · " else "Список пополняется из интернета · ") +
                            (com.apexvpn.client.data.HideTemplates.remoteInfo() ?: "встроенный список"),
                        onClick = { viewModel.change { copy(hideTemplateAuto = !hideTemplateAuto, hiddenApps = if (!hideTemplateAuto) hiddenApps + tpl.filter { it !in hideTemplateSkip } else hiddenApps) } },
                    ) {
                        ApexSwitch(s.hideTemplateAuto, { v ->
                            viewModel.change { copy(hideTemplateAuto = v, hiddenApps = if (v) hiddenApps + tpl.filter { it !in hideTemplateSkip } else hiddenApps) }
                        })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                .sortedByDescending { it.packageName in s.hiddenApps }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Apex.Surface),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val on = app.packageName in s.hiddenApps
                    val icon = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                    ListRow(
                        title = app.label,
                        subtitle = if (on) "VPN скрыт" else app.packageName,
                        leading = {
                            if (icon != null) Image(icon, null, Modifier.size(40.dp)) else GlyphTile(app.label.take(1), Apex.Accent)
                        },
                        onClick = { viewModel.change { copy(hiddenApps = if (on) hiddenApps - app.packageName else hiddenApps + app.packageName, hideTemplateSkip = if (on) hideTemplateSkip + app.packageName else hideTemplateSkip - app.packageName) } },
                    ) {
                        ApexSwitch(on, { v -> viewModel.change { copy(hiddenApps = if (v) hiddenApps + app.packageName else hiddenApps - app.packageName, hideTemplateSkip = if (v) hideTemplateSkip - app.packageName else hideTemplateSkip + app.packageName) } })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Apex.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Apex.TextDim)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Apex.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Apex.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── 5/229. Тест скорости 2.0 ───────────────────────────────────────────

@Composable
private fun SpeedTestPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val st by viewModel.speed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val history by com.apexvpn.client.data.SpeedHistory.entries.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { com.apexvpn.client.data.SpeedHistory.load(context) }
    val running = st.phase in listOf(SpeedPhase.Ping, SpeedPhase.Download, SpeedPhase.Upload)
    val set by viewModel.settings.collectAsStateWithLifecycle()
    val meta by viewModel.speedMeta.collectAsStateWithLifecycle()
    val metaLoading by viewModel.speedMetaLoading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (meta == null) viewModel.loadSpeedMeta() }
    PageScaffold("Тест скорости", { viewModel.cancelSpeedTest(); onBack() }) {
        Spacer(Modifier.height(8.dp))
        val shown = when (st.phase) {
            SpeedPhase.Download, SpeedPhase.Upload -> st.live
            SpeedPhase.Done -> st.result.downMbps
            else -> 0.0
        }
        Box(contentAlignment = Alignment.Center) {
            SpeedGauge(
                mbps = shown,
                label = when (st.phase) {
                    SpeedPhase.Idle -> ""
                    SpeedPhase.Ping -> "Измеряем пинг…"
                    SpeedPhase.Download -> "↓ Загрузка"
                    SpeedPhase.Upload -> "↑ Отдача"
                    SpeedPhase.Done -> "Готово"
                    SpeedPhase.Error -> st.error ?: "Ошибка"
                },
                upload = st.phase == SpeedPhase.Upload,
                error = st.phase == SpeedPhase.Error,
                scale = set.speedScale,
                unit = set.speedUnit,
            )
            // Большая круглая кнопка «Начать», как в Speedtest
            if (st.phase == SpeedPhase.Idle) {
                val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "start")
                val k by pulse.animateFloat(1f, 1.06f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100), androidx.compose.animation.core.RepeatMode.Reverse), label = "k")
                Box(
                    Modifier
                        .size(150.dp)
                        .graphicsLayer { scaleX = k; scaleY = k }
                        .clip(CircleShape)
                        .background(Apex.Base)
                        .border(4.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Apex.Violet, Apex.Cyan)), CircleShape)
                        .clickable { viewModel.runSpeedTest() },
                    contentAlignment = Alignment.Center,
                ) { Text("Начать", color = Apex.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val r = st.result
            Metric("Пинг", if (r.pingMs >= 0) "${r.pingMs} мс" else "—", Modifier.weight(1f))
            Metric("Джиттер", if (r.jitterMs >= 0) "${r.jitterMs} мс" else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val r = st.result
            val (d, u1) = com.apexvpn.client.data.SpeedTest.format(r.downMbps, set.speedUnit)
            val (up, u2) = com.apexvpn.client.data.SpeedTest.format(r.upMbps, set.speedUnit)
            Metric("↓ Загрузка", if (r.downMbps >= 0) "$d $u1" else "—", Modifier.weight(1f))
            Metric("↑ Отдача", if (r.upMbps >= 0) "$up $u2" else "—", Modifier.weight(1f))
        }
        if (st.phase != SpeedPhase.Idle) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (running) viewModel.cancelSpeedTest() else viewModel.runSpeedTest() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Apex.SurfaceHigh else Apex.Accent, contentColor = if (running) Apex.Text else Apex.OnAccent),
            ) {
                Text(if (running) "Остановить" else "Повторить", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Провайдер и сервер теста
        Spacer(Modifier.height(16.dp))
        Group {
            ListRow(
                meta?.provider?.ifEmpty { null } ?: if (metaLoading) "Поиск провайдера…" else "Провайдер не определён",
                meta?.let { m -> listOf(m.ip, listOf(m.city, m.country).filter { it.isNotEmpty() }.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · ") },
                leading = { if (metaLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Apex.Accent) else IconTile(Apex.SurfaceHigh) { Text("🛰", fontSize = 20.sp) } },
                onClick = { viewModel.loadSpeedMeta() },
            )
            GroupDivider()
            ListRow(
                if (metaLoading && meta == null) "Поиск сервера…" else "Cloudflare" + (meta?.colo?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                "Сервер теста — ближайший узел Cloudflare",
                leading = { if (metaLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Apex.Accent) else IconTile(Apex.SurfaceHigh) { Text("🌐", fontSize = 20.sp) } },
            )
        }
        Caption("Если VPN включён, видно провайдера сервера VPN — так и должно быть. Тест расходует около 50–150 МБ трафика.")

        // Единицы, масштаб и тип соединения
        SectionLabel("Настройки теста")
        Column(Modifier.fillMaxWidth().apexCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedOption("Единицы", listOf("mbps" to "Мбит/с", "MBps" to "МБ/с", "kBps" to "КБ/с"), set.speedUnit) {
                viewModel.change(reload = false) { copy(speedUnit = it) }
            }
            SpeedOption("Масштаб", listOf("100" to "100", "500" to "500", "1000" to "1000"), set.speedScale.toString()) {
                viewModel.change(reload = false) { copy(speedScale = it.toInt()) }
            }
            SpeedOption("Соединение", listOf("multi" to "Множественное", "single" to "Одинарное"), if (set.speedMulti) "multi" else "single") {
                viewModel.change(reload = false) { copy(speedMulti = it == "multi") }
            }
        }
        Caption("Множественное — 4 соединения сразу: показывает всю ширину канала, как Speedtest. Одинарное — одно соединение: так обычно качает браузер или видео.")

        // 220. Самый быстрый сервер
        SectionLabel("Самый быстрый сервер")
        FastestCard(viewModel)

        // 229. История
        if (history.isNotEmpty()) {
            SectionLabel("История", trailing = "${history.size}")
            Group {
                val best = history.maxOf { it.down }
                history.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.server, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                java.text.SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(java.util.Date(e.time)) +
                                    (if (e.pingMs >= 0) " · ${e.pingMs} мс" else "") +
                                    (if (e.up >= 0) " · ↑ " + "%.0f".format(Locale.US, e.up) else ""),
                                color = Apex.TextDim, fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            // Полоска относительно лучшего результата
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Apex.SurfaceHigh)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(if (best > 0) (e.down / best).toFloat().coerceIn(0.03f, 1f) else 0f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Apex.accent),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (e.down >= 0) "%.1f".format(Locale.US, e.down) else "—",
                            color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Text(" Мбит/с", color = Apex.TextDim, fontSize = 12.sp)
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = { com.apexvpn.client.data.SpeedHistory.clear(context) }) {
                Text("Очистить историю", color = Apex.TextDim)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 229. Спидометр: дуга 240°, шкала до 1000 Мбит/с (логарифмическая), стрелка плавно догоняет значение. */
@Composable
private fun SpeedGauge(mbps: Double, label: String, upload: Boolean, error: Boolean, scale: Int = 1000, unit: String = "mbps") {
    // Шкала логарифмическая до [scale] Мбит/с: 100, 500 или 1000
    val top = kotlin.math.log10(1.0 + scale)
    val target = (kotlin.math.log10(1.0 + mbps.coerceAtLeast(0.0)) / top).toFloat().coerceIn(0f, 1f)
    val frac by androidx.compose.animation.core.animateFloatAsState(
        target, androidx.compose.animation.core.tween(if (Apex.feel.smooth) 0 else 450), label = "gauge",
    )
    val c1 = if (upload) Apex.Cyan else Apex.Violet
    val c2 = if (upload) Apex.Violet else Apex.Cyan
    val track = Apex.SurfaceHigh
    val dim = Apex.TextDim
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(250.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 18.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 150f, 240f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (frac > 0.002f) {
                drawArc(
                    androidx.compose.ui.graphics.Brush.sweepGradient(listOf(c1, c2, c1), center),
                    150f, 240f * frac, false, topLeft, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            // Подписи шкалы
            val r = arcSize.width / 2
            listOf(0, scale / 100, scale / 10, scale).forEach { v ->
                val f = (kotlin.math.log10(1.0 + v) / top).toFloat().coerceIn(0f, 1f)
                val a = Math.toRadians((150 + 240 * f).toDouble())
                val rr = r + stroke * 0.5f + 10.dp.toPx()
                val text = measurer.measure(v.toString(), TextStyle(color = dim, fontSize = 11.sp))
                val x = center.x + (rr * kotlin.math.cos(a)).toFloat() - text.size.width / 2
                val y = center.y + (rr * kotlin.math.sin(a)).toFloat() - text.size.height / 2
                drawText(text, topLeft = androidx.compose.ui.geometry.Offset(x, y))
            }
            // Точка-стрелка на конце дуги
            val a = Math.toRadians((150 + 240 * frac).toDouble())
            drawCircle(
                Color.White, stroke * 0.42f,
                androidx.compose.ui.geometry.Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (num, u) = com.apexvpn.client.data.SpeedTest.format(if (mbps > 0) mbps else -1.0, unit)
            Text(num, color = Apex.Text, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            Text(u, color = Apex.TextDim, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = if (error) Apex.Bad else Apex.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(170.dp))
        }
    }
}

/** Строка настройки теста: название слева, варианты справа (как в Speedtest). */
@Composable
private fun SpeedOption(title: String, items: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Apex.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (id, t) ->
                val on = id == selected
                Text(
                    t,
                    color = if (on) Apex.OnAccent else Apex.TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Apex.Accent else Apex.SurfaceHigh)
                        .clickable { onPick(id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 220. Подбор самого быстрого сервера через VPN. */
@Composable
private fun FastestCard(viewModel: MainViewModel) {
    val f by viewModel.fastest.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
        Text(
            when {
                f.running -> f.step
                f.best != null -> "Выбран ${f.best!!.title} — " + "%.0f".format(Locale.US, f.best!!.mbps) + " Мбит/с"
                f.error != null -> f.error!!
                else -> "APEX проверит 5 серверов с лучшим пингом, измерит скорость каждого и закрепит самый быстрый."
            },
            color = if (f.error != null && !f.running) Apex.Bad else Apex.Text,
            fontSize = 15.sp,
        )
        if (f.probes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val top = f.probes.maxOf { it.mbps }.coerceAtLeast(1.0)
            f.probes.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlagCircle(p.flag, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(p.title, color = Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(
                                if (p.mbps >= 0) "%.0f".format(Locale.US, p.mbps) + " Мбит/с" else "${p.pingMs} мс",
                                color = if (p.tag == f.best?.tag) Apex.Good else Apex.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Apex.SurfaceHigh)) {
                            if (p.mbps > 0) {
                                Box(Modifier.fillMaxWidth((p.mbps / top).toFloat().coerceIn(0.03f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Apex.accent))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (f.running) viewModel.cancelFastest() else viewModel.findFastest() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (f.running) Apex.SurfaceHigh else Apex.AccentSoft, contentColor = if (f.running) Apex.Text else Apex.AccentText),
        ) {
            Text(if (f.running) "Остановить" else if (f.best != null) "Подобрать снова" else "Подобрать сервер", fontWeight = FontWeight.SemiBold)
        }
    }
    Caption("Нужен включённый VPN. Подбор расходует около 30 МБ. Выбранный сервер закрепляется — автопилот выключится, пока не выберете его снова.")
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier) {
    Column(
        modifier.apexCard().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Apex.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ─── 58. Проверка сайта ─────────────────────────────────────────────────

@Composable
private fun SiteCheckPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val result by viewModel.siteCheck.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    PageScaffold("Проверка сайта", onBack) {
        Caption("Введите адрес — APEX покажет, как он откроется при текущих настройках.")
        Spacer(Modifier.height(12.dp))
        InputWithButton(text, "например, youtube.com", Icons.Rounded.Search, { text = it }) { viewModel.checkSite(text) }
        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            val (title, color) = when (r.outbound) {
                "proxy" -> "Через VPN" to Apex.Accent
                "direct" -> "Напрямую" to Apex.Good
                "block" -> "Заблокировано" to Apex.Bad
                else -> "—" to Apex.TextDim
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(20.dp),
            ) {
                if (r.domain.isNotBlank()) Text(r.domain, color = Apex.TextDim, fontSize = 14.sp)
                Text(title, color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(r.reason, color = Apex.Text, fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun InputWithButton(
    value: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Apex.Surface)
            .border(1.dp, Apex.Outline, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 17.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Apex.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(Apex.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Apex.Accent).clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Apex.OnAccent) }
    }
}

// ─── 25. Правила Wi-Fi ──────────────────────────────────────────────────

@Composable
private fun WifiPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(WifiInfoReader.hasPermission(context)) }
    var current by remember { mutableStateOf(WifiInfoReader.currentSsid(context)) }
    var text by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = WifiInfoReader.hasPermission(context)
        current = WifiInfoReader.currentSsid(context)
    }
    PageScaffold("Правила Wi-Fi", onBack) {
        Group {
            ListRow(
                "Пауза в доверенных сетях",
                "Дома VPN выключается сам, в других сетях — включается",
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Wifi, null, tint = Apex.Cyan) } },
                onClick = { viewModel.change(reload = false) { copy(wifiRules = !wifiRules) } },
            ) { ApexSwitch(s.wifiRules, { v -> viewModel.change(reload = false) { copy(wifiRules = v) } }) }
        }
        if (!hasPermission) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Apex.Warn.copy(alpha = 0.12f)).padding(16.dp),
            ) {
                Text("Android показывает имя сети Wi-Fi только приложениям с доступом к геолокации. APEX VPN использует его только для этой функции.", color = Apex.Text, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Разрешить") }
            }
        }
        SectionLabel("Доверенные сети")
        Group {
            current?.let { ssid ->
                if (ssid !in s.trustedWifi) {
                    ListRow(
                        "Добавить «$ssid»",
                        "Вы сейчас подключены к этой сети",
                        leading = { IconTile(Apex.AccentSoft) { Icon(Icons.Rounded.Add, null, tint = Apex.AccentText) } },
                        onClick = { viewModel.change(reload = false) { copy(trustedWifi = trustedWifi + ssid) } },
                    )
                    GroupDivider()
                }
            }
            s.trustedWifi.forEach { ssid ->
                ListRow(ssid, leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Wifi, null, tint = Apex.Good) } }) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { viewModel.change(reload = false) { copy(trustedWifi = trustedWifi - ssid) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
                }
                GroupDivider()
            }
            Row(Modifier.padding(8.dp)) {
                InputWithButton(text, "Имя сети вручную", Icons.Rounded.Add, { text = it }) {
                    val name = text.trim()
                    if (name.isNotEmpty() && name != WifiManager.UNKNOWN_SSID) {
                        viewModel.change(reload = false) { copy(trustedWifi = trustedWifi + name) }
                        text = ""
                    }
                }
            }
        }
        Caption("Работает, пока VPN включён: в доверенной сети он ставится на паузу и сам продолжает работу, когда вы уходите.")
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 26. Раздача на компьютер ───────────────────────────────────────────

@Composable
private fun SharePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val ip = remember { localIp() }
    PageScaffold("Раздача на компьютер", onBack) {
        Group {
            ListRow(
                "Прокси для других устройств",
                "Компьютер в той же сети Wi-Fi или через точку доступа",
                onClick = { viewModel.change { copy(share = !share) } },
            ) { ApexSwitch(s.share, { v -> viewModel.change { copy(share = v) } }) }
        }
        if (s.share) {
            SectionLabel("Настройки для компьютера")
            Group {
                val rows = listOf(
                    "Тип" to "HTTP или SOCKS5",
                    "Адрес" to (ip ?: "IP телефона в сети Wi-Fi"),
                    "Порт" to s.sharePort.toString(),
                    "Логин" to s.shareUser,
                    "Пароль" to s.sharePass,
                )
                rows.forEachIndexed { i, (k, v) ->
                    if (i > 0) GroupDivider()
                    ListRow(k, v) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable {
                                clipboard.setText(AnnotatedString(v))
                                viewModel.showMessage("Скопировано")
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.ContentCopy, "Копировать", tint = Apex.TextDim, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Новый пароль",
                color = Apex.AccentText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.change { copy(sharePass = com.apexvpn.client.data.AppSettings.randomPassword()) } }
                    .padding(8.dp),
            )
        }
        Caption("Укажите эти данные в настройках прокси на компьютере (Windows: Параметры → Сеть → Прокси). Работает, пока VPN на телефоне включён.")
        Spacer(Modifier.height(24.dp))
    }
}

private fun localIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
        .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

// ─── DNS: 27 свой DNS, 50 детский режим ─────────────────────────────────

@Composable
private fun DnsPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(s.customDns) }
    var error by remember { mutableStateOf<String?>(null) }
    PageScaffold("DNS", onBack) {
        Group {
            ListRow(
                "Семейный режим",
                "Закрывает сайты 18+, казино и букмекеров, семейный DNS",
                leading = { IconTile(Color(0xFFF59E0B)) { Icon(Icons.Rounded.FamilyRestroom, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(kids = !kids) } },
            ) { ApexSwitch(s.kids, { v -> viewModel.change { copy(kids = v) } }) }
            GroupDivider()
            ListRow(
                "Семейный режим по расписанию",
                if (s.kids) "Сейчас включён всегда — выключите переключатель выше, чтобы работало расписание"
                else if (s.kidsSchedule) KidsDaysText(s.kidsDays) + " · " + com.apexvpn.client.data.KidsSchedule.hhmm(s.kidsFrom) + "–" + com.apexvpn.client.data.KidsSchedule.hhmm(s.kidsTo)
                else "Например, для ребёнка: по будням с 8:00 до 21:00",
                leading = { IconTile(Color(0xFFF97316)) { Icon(Icons.Rounded.FamilyRestroom, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(kidsSchedule = !kidsSchedule) } },
            ) { ApexSwitch(s.kidsSchedule, { v -> viewModel.change { copy(kidsSchedule = v) } }) }
        }
        if (s.kidsSchedule && !s.kids) KidsScheduleEditor(viewModel, s)
        SectionLabel("DNS для зарубежных сайтов")
        Group {
            DnsChoice.entries.forEachIndexed { i, d ->
                if (i > 0) GroupDivider()
                ListRow(
                    d.title,
                    if (d == DnsChoice.Custom && s.customDns.isNotBlank()) s.customDns else d.subtitle,
                    leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Dns, null, tint = if (d == s.dns) Apex.AccentText else Apex.TextDim) } },
                    onClick = {
                        if (d != DnsChoice.Custom || s.customDns.isNotBlank()) viewModel.change { copy(dns = d) }
                    },
                ) { if (d == s.dns) Icon(Icons.Rounded.Check, "Выбрано", tint = Apex.Accent) }
            }
        }
        // Авто-выбор: результаты замера и кнопка «Проверить»
        if (s.dns == DnsChoice.Auto) {
            val results by com.apexvpn.client.data.DnsAuto.results.collectAsStateWithLifecycle()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (results.isEmpty()) {
                    busy = true
                    if (com.apexvpn.client.data.DnsAuto.measure()) viewModel.change { this }
                    busy = false
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
                val bestV = com.apexvpn.client.data.DnsAuto.bestVariant()
                Text("Сейчас: ${bestV.choice.title} · ${bestV.protoTitle}", color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                // 499. Свой лучший DNS для каждой сети
                Text("Для сети: " + com.apexvpn.client.data.DnsAuto.networkTitle(), color = Apex.TextDim, fontSize = 13.sp)
                if (results.isNotEmpty()) Text(
                    if (com.apexvpn.client.data.DnsAuto.lastViaVpn) "Время ответа через сервер VPN — так DNS и работает под VPN (вместе с путём до сервера)"
                    else "Время ответа напрямую, без VPN",
                    color = Apex.TextDim, fontSize = 12.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                results.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${r.variant.choice.title} · ${r.variant.protoTitle}", color = if (r.variant == bestV) Apex.AccentText else Apex.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (r.ms >= 0) "${r.ms} мс" else "нет ответа", color = if (r.ms >= 0) pingColor(r.ms) else Apex.Bad, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!busy && com.apexvpn.client.data.DnsAuto.allFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (com.apexvpn.client.vpn.VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started)
                            "Ни один DNS не ответил через сервер — возможно, сервер сейчас недоступен. DNS не менялся."
                        else "Ни один DNS не ответил. Включите VPN: DNS проверяются через сервер — так замер работает и на мобильной сети с белыми списками.",
                        color = Apex.Warn, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        if (com.apexvpn.client.data.DnsAuto.measure()) viewModel.change { this }
                        busy = false
                    }
                }) { Text(if (busy) "Проверяю…" else "Проверить сейчас", color = Apex.Accent) }
            }
            Caption("Авто сравнивает Cloudflare, Google, Quad9, Mullvad, OpenDNS и Control D — и разными способами (DoH, DoT, DoQ) — после подключения и при смене сети. Для каждой сети запоминает свой лучший DNS. Меняет DNS, только если другой заметно быстрее. Если в «Маршрутизации» задан свой удалённый DNS, он главнее.")
        }
        if (s.kids) Caption("Семейный режим закрывает сайты 18+, казино и букмекеров, а DNS — семейный AdGuard (выбор DNS не действует).")
        SectionLabel("Свой DNS-over-HTTPS")
        InputWithButton(custom, "https://dns.example.com/dns-query", Icons.Rounded.Check, { custom = it; error = null }) {
            error = viewModel.validateDns(custom)
            if (error == null) {
                viewModel.change { copy(customDns = custom.trim(), dns = DnsChoice.Custom) }
                viewModel.showMessage("Свой DNS включён")
            }
        }
        error?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        Caption("Российские сайты в «Умном» режиме используют DNS вашего провайдера.")
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 24/47/48. Профиль подключения ──────────────────────────────────────

@Composable
private fun ProfilePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    PageScaffold("Профиль подключения", onBack) {
        // 220. Автовыбор сервера по задаче
        SectionLabel("Режим автопилота")
        AutoModeGrid(s.autoMode) { viewModel.setAutoMode(it) }
        val mode = com.apexvpn.client.data.AutoModes.Mode.of(s.autoMode)
        Caption(
            when (mode) {
                com.apexvpn.client.data.AutoModes.Mode.Games -> "Каждый сервер проверяется 4 раза подряд: автопилот выбирает только из тех, где пинг низкий и ровный (маленький джиттер) и нет потерь. Проверка каждую минуту, игры не обрываются при смене."
                com.apexvpn.client.data.AutoModes.Mode.Speed -> "После подключения APEX измеряет скорость загрузки через 3 лучших сервера (≈ 9 МБ трафика) и оставляет автопилоту 2 самых быстрых. Перепроверка — раз в 30 минут."
                com.apexvpn.client.data.AutoModes.Mode.Stable -> "Автопилот выбирает только серверы без потерь, которые ни разу не пропадали, и не меняет сервер без нужды."
                com.apexvpn.client.data.AutoModes.Mode.Calls -> "Ровная связь для Telegram, WhatsApp и Discord: стабильные серверы, сервер «залипает» и не меняется во время разговора."
                com.apexvpn.client.data.AutoModes.Mode.Battery -> "Серверы проверяются раз в 30 минут — меньше расход батареи."
                else -> "Автопилот выбирает самый быстрый сервер и меняет его, только если другой заметно быстрее."
            },
        )
        SectionLabel("Профиль")
        Group {
            ConnProfile.entries.forEachIndexed { i, p ->
                if (i > 0) GroupDivider()
                ChoiceRow(p.title, p.subtitle, s.profile == p) { viewModel.change { copy(profile = p) } }
            }
        }
        Caption("Автопилот меняет сервер, только если другой заметно быстрее, и не обрывает открытые соединения. «Игры» проверяют серверы каждую минуту, «Экономия батареи» — раз в 30 минут.")
        Spacer(Modifier.height(12.dp))
        Group {
            ListRow(
                "Автопилот без России и LTE",
                "Не брать российские серверы и LTE-обход: пинг у них низкий, но заблокированные сайты не открываются",
                onClick = { viewModel.change { copy(autoSkipSpecial = !autoSkipSpecial) } },
            ) {
                ApexSwitch(s.autoSkipSpecial, { v -> viewModel.change { copy(autoSkipSpecial = v) } })
            }
            GroupDivider()
            ListRow(
                "Автопилот только по избранным",
                if (s.favorites.isEmpty()) "Отметьте серверы звёздочкой в списке серверов" else "Выбирать только среди серверов со звёздочкой (${s.favorites.size})",
                onClick = { viewModel.change { copy(autoFavoritesOnly = !autoFavoritesOnly) } },
            ) {
                ApexSwitch(s.autoFavoritesOnly, { v -> viewModel.change { copy(autoFavoritesOnly = v) } })
            }
            GroupDivider()
            // 491
            val wlActive by com.apexvpn.client.data.WhiteList.state.collectAsStateWithLifecycle()
            ListRow(
                "Белые списки на мобильной сети",
                if (wlActive && s.autoWhitelist) "Сейчас включён LTE-обход: обычные серверы недоступны"
                else "Если оператор пускает только «разрешённые» сайты, автопилот сам включит LTE-обход и потом вернёт обычные серверы",
                onClick = { viewModel.change { copy(autoWhitelist = !autoWhitelist) } },
            ) {
                ApexSwitch(s.autoWhitelist, { v -> viewModel.change { copy(autoWhitelist = v) } })
            }
        }
        // Медленный интернет на LTE: QUIC (HTTP/3) через VPN на мобильной сети часто тормозит
        SectionLabel("QUIC (HTTP/3)")
        ChipRow(listOf("mobile" to "Выкл. на мобильной", "off" to "Всегда выкл.", "on" to "Не трогать"), s.quicMode) {
            viewModel.change { copy(quicMode = it) }
        }
        Caption(
            when (s.quicMode) {
                "mobile" -> "На мобильной сети сайты и видео идут по TCP — так через VPN быстрее и стабильнее. На Wi-Fi QUIC работает как обычно."
                "off" -> "QUIC выключен везде: браузер и приложения сразу идут по TCP."
                else -> "QUIC работает всегда. Если на LTE сайты открываются медленно — выберите «Выкл. на мобильной»."
            },
        )
    }
}

// ─── 15/62. Оформление ──────────────────────────────────────────────────

@Composable
private fun AppearancePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    PageScaffold("Оформление", onBack) {
        SectionLabel("Дизайн 2026")
        val designs = Design.entries.filter { it != Design.Synthwave || s.secretUnlocked }
        designs.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    DesignPreview(d, s.theme, s.accent, s.customAccent, s.customAccent2, selected = s.design == d, modifier = Modifier.weight(1f)) {
                        ThemeReveal.run { viewModel.change(reload = false) { copy(design = d) } }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Caption(s.design.subtitle + when (s.design) {
            Design.Neon, Design.Terminal, Design.Steampunk, Design.Synthwave, Design.Cosmos -> ". Всегда тёмный."
            Design.Pastel, Design.Japan -> ". Всегда светлый."
            Design.Expressive -> ". Цвет берётся из обоев (Android 12+)."
            else -> "."
        })

        SectionLabel("Тема")
        Group {
            ThemeMode.entries.forEachIndexed { i, t ->
                if (i > 0) GroupDivider()
                ChoiceRow(t.title, null, s.theme == t) { ThemeReveal.run { viewModel.change(reload = false) { copy(theme = t) } } }
            }
        }
        // Главный экран: «Кнопка» или «Карта»
        SectionLabel("Главный экран")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("button" to "Кнопка", "map" to "Карта", "globe" to "Глобус").forEach { (id, title) ->
                val on = s.homeStyle == id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Apex.Surface)
                        .border(2.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(18.dp))
                        .clickable { viewModel.change(reload = false) { copy(homeStyle = id) } }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (id == "globe") {
                        // 353. Живой мини-глобус
                        Globe(Modifier.fillMaxWidth().height(72.dp), from = WorldMap.coords["ru"], to = WorldMap.coords["de"], connected = true, spin = false)
                    } else {
                        HomeStylePreview(id == "map")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(title, color = if (on) Apex.Text else Apex.TextDim, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (s.design == Design.Bento) Caption("В дизайне «Бенто» главный экран всегда из плиток.")

        // 17. Фон за щитом
        SectionLabel("Фон за щитом")
        ChipRow(listOf("none" to "Нет", "waves" to "Волны", "particles" to "Частицы", "stars" to "Звёзды", "country" to "Страна"), s.shieldBackdrop) {
            viewModel.change(reload = false) { copy(shieldBackdrop = it) }
        }
        if (s.shieldBackdrop == "country") Caption("Фото дня: каждый день новое фото города страны сервера (на автопилоте — вашей страны). Фото из Википедии, загружается раз в день.")
        // 157. Своё фото из галереи
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (com.apexvpn.client.data.Backdrop.save(context, uri)) {
                    viewModel.change(reload = false) { copy(shieldBackdrop = "photo", backdropStamp = System.currentTimeMillis()) }
                    viewModel.showMessage("Фото установлено на главный экран")
                } else {
                    viewModel.showMessage("Не удалось открыть фото")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            ListRow(
                "Своё фото",
                if (s.shieldBackdrop == "photo") "Выбрано — нажмите, чтобы сменить" else "Фото из галереи на главный экран",
                onClick = { pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                if (s.shieldBackdrop == "photo") {
                    androidx.compose.material3.TextButton(onClick = {
                        com.apexvpn.client.data.Backdrop.clear(context)
                        viewModel.change(reload = false) { copy(shieldBackdrop = "none") }
                    }) { Text("Убрать", color = Apex.Bad) }
                } else {
                    Chevron()
                }
            }
        }
        // Живые видео: готовые шаблоны и своё видео
        SectionLabel("Живое видео на фоне")
        val scopeV = androidx.compose.runtime.rememberCoroutineScope()
        val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scopeV.launch {
                if (LiveVideos.saveOwn(context, uri)) {
                    viewModel.change(reload = false) { copy(shieldBackdrop = "video", backdropStamp = System.currentTimeMillis()) }
                    viewModel.showMessage("Видео установлено на главный экран")
                } else {
                    viewModel.showMessage("Не удалось открыть видео (до 150 МБ)")
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(LiveVideos.templates.size) { i ->
                val tpl = LiveVideos.templates[i]
                val id = "video:" + tpl.id
                val on = s.shieldBackdrop == id
                val thumb = remember(tpl.id) {
                    runCatching { context.assets.open("videos/${tpl.id}.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
                }
                Column(
                    Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.change(reload = false) { copy(shieldBackdrop = if (on) "none" else id) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(88.dp, 132.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Apex.Surface)
                            .border(2.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        if (on) Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(tpl.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            ListRow(
                "Своё видео",
                if (s.shieldBackdrop == "video") "Выбрано — нажмите, чтобы сменить" else "Видео из галереи: без звука, по кругу",
                onClick = { pickVideo.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            ) {
                if (s.shieldBackdrop == "video") {
                    androidx.compose.material3.TextButton(onClick = {
                        LiveVideos.clearOwn(context)
                        viewModel.change(reload = false) { copy(shieldBackdrop = "none") }
                    }) { Text("Убрать", color = Apex.Bad) }
                } else {
                    Chevron()
                }
            }
        }
        if (LiveVideos.isVideo(s.shieldBackdrop)) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
                Text("Затемнение · ${s.photoDim}%", color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.Slider(
                    value = s.photoDim.toFloat(), onValueChange = { v -> viewModel.change(reload = false) { copy(photoDim = v.toInt()) } },
                    valueRange = 0f..90f,
                )
            }
            Caption("Видео идёт без звука и останавливается, когда приложение свёрнуто. На слабых телефонах может немного расходовать батарею.")
        }

        // 563. Эффекты своего фото
        if (s.shieldBackdrop == "photo") {
            Spacer(Modifier.height(10.dp))
            PhotoEffects(s.photoBlur, s.photoDim, s.photoParallax) { b, d, p ->
                viewModel.change(reload = false) { copy(photoBlur = b, photoDim = d, photoParallax = p) }
            }
            if (android.os.Build.VERSION.SDK_INT < 31) Caption("Размытие работает на Android 12 и новее.")
        }

        SectionLabel("Цвет")
        Row(Modifier.fillMaxWidth()) {
            AccentColor.entries.forEach { a ->
                val on = s.accent == a
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { ThemeReveal.run { viewModel.change(reload = false) { copy(accent = a) } } }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .then(
                                if (a == AccentColor.Custom) {
                                    Modifier.background(androidx.compose.ui.graphics.Brush.sweepGradient(rainbow))
                                } else {
                                    Modifier.background(Color(a.argb))
                                },
                            )
                            .border(3.dp, if (on) Apex.Text else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (a == AccentColor.Custom) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(s.customAccent)).border(2.dp, Color.White, CircleShape))
                        } else if (on) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp)
                }
            }
        }
        // 216. Свой цвет: оттенок, насыщенность и яркость
        if (s.accent == AccentColor.Custom) {
            Spacer(Modifier.height(8.dp))
            AccentPicker(s.customAccent) { argb -> ThemeReveal.run { viewModel.change(reload = false) { copy(customAccent = argb, customAccent2 = 0) } } }
        }
        // 564. Готовые палитры из двух цветов
        SectionLabel("Палитры")
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ColorPresets.size) { i ->
                val pr = ColorPresets[i]
                val on = s.accent == AccentColor.Custom && s.customAccent == pr.c1 && s.customAccent2 == pr.c2
                Column(
                    Modifier.width(78.dp).clip(RoundedCornerShape(18.dp))
                        .clickable { ThemeReveal.run { viewModel.change(reload = false) { copy(accent = AccentColor.Custom, customAccent = pr.c1, customAccent2 = pr.c2) } } }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(pr.c1), Color(pr.c2))))
                            .border(3.dp, if (on) Apex.Text else Color.Transparent, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Rounded.Check, null, tint = Color.White) else Text(pr.emoji, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(pr.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        if (s.design !in listOf(Design.Apex2, Design.Classic, Design.Glass, Design.Aurora, Design.Bento)) {
            Caption("В этом дизайне свои цвета — палитры работают в APEX 2, Классике, Liquid Glass, Авроре и Бенто.")
        }

        // Ощущения
        SectionLabel("Ощущения")
        Group {
            ListRow(
                "Приветствие",
                "«Доброе утро! Защищено ✓» на главном экране",
                onClick = { viewModel.change(reload = false) { copy(greeting = !greeting) } },
            ) { ApexSwitch(s.greeting, { v -> viewModel.change(reload = false) { copy(greeting = v) } }) }
            GroupDivider()
            ListRow(
                "Щит в цветах режима",
                "🎮 Игры — красный пульс, 🚀 Скорость — голубые полосы и т. д. (на автопилоте)",
                onClick = { viewModel.change(reload = false) { copy(modeColors = !modeColors) } },
            ) { ApexSwitch(s.modeColors, { v -> viewModel.change(reload = false) { copy(modeColors = v) } }) }
            GroupDivider()
            ListRow("Звук подключения", if (s.connectSound) com.apexvpn.client.data.ConnectFeedback.title(s.sound) + " · на беззвучном телефон молчит" else "Выключен") {
                ApexSwitch(s.connectSound, { v -> viewModel.change(reload = false) { copy(connectSound = v) } })
            }
            if (s.connectSound) {
                com.apexvpn.client.data.ConnectFeedback.sounds.forEach { snd ->
                    GroupDivider()
                    ListRow(
                        snd.title,
                        snd.subtitle,
                        leading = { RadioDot(s.sound == snd.id) },
                        onClick = {
                            viewModel.change(reload = false) { copy(sound = snd.id) }
                            com.apexvpn.client.data.ConnectFeedback.play(context, snd.id, force = true)
                        },
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { com.apexvpn.client.data.ConnectFeedback.play(context, snd.id, force = true) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, "Послушать", tint = Apex.Accent) }
                    }
                }
                // 271. Звук отключения из того же набора
                GroupDivider()
                ListRow(
                    "Звук отключения",
                    if (s.disconnectSound) "«${com.apexvpn.client.data.ConnectFeedback.title(s.sound)}» вниз — нажмите, чтобы послушать" else "Только при подключении",
                    onClick = { com.apexvpn.client.data.ConnectFeedback.play(context, s.sound, force = true, off = true) },
                ) {
                    ApexSwitch(s.disconnectSound, { v -> viewModel.change(reload = false) { copy(disconnectSound = v) } })
                }
            }
            GroupDivider()
            ListRow("Вибрация", "Отклик при нажатии и подключении") {
                ApexSwitch(s.vibration, { v ->
                    viewModel.change(reload = false) { copy(vibration = v) }
                    if (v) com.apexvpn.client.data.ConnectFeedback.vibrate(context)
                })
            }
        }

        // Плавность
        SectionLabel("Плавность")
        Group {
            ListRow("Плавный режим", "Меньше анимаций и свечения — для слабых телефонов и экономии батареи") {
                ApexSwitch(s.smoothMode, { v -> viewModel.change(reload = false) { copy(smoothMode = v) } })
            }
            GroupDivider()
            ListRow("Размытие стекла", if (android.os.Build.VERSION.SDK_INT >= 31) "Мягкий размытый фон за карточками" else "Нужен Android 12 или новее") {
                ApexSwitch(s.glassBlur, { v -> viewModel.change(reload = false) { copy(glassBlur = v) } }, enabled = android.os.Build.VERSION.SDK_INT >= 31)
            }
            GroupDivider()
            ListRow("Живая карта", "Дышащие кольца и бегущий по дуге свет") {
                ApexSwitch(s.liveMap, { v -> viewModel.change(reload = false) { copy(liveMap = v) } }, enabled = !s.smoothMode)
            }
            GroupDivider()
            ListRow("Сезонное оформление", "Сейчас: ${com.apexvpn.client.ui.theme.Season.now().title.lowercase()} на фоне — зимой снег, весной лепестки, летом светлячки, осенью листья") {
                ApexSwitch(s.seasonal, { v -> viewModel.change(reload = false) { copy(seasonal = v) } }, enabled = !s.smoothMode)
            }
            GroupDivider()
            ListRow("Анимированные флаги", "Флаги в списке серверов колышутся на ветру") {
                ApexSwitch(s.animatedFlags, { v -> viewModel.change(reload = false) { copy(animatedFlags = v) } }, enabled = !s.smoothMode)
            }
            GroupDivider()
            // 429
            ListRow("3D-щит", "Щит поворачивается, когда вы наклоняете телефон") {
                ApexSwitch(s.shield3d, { v -> viewModel.change(reload = false) { copy(shield3d = v) } }, enabled = !s.smoothMode)
            }
        }

        // 43. Живые обои
        SectionLabel("Живые обои")
        Group {
            ListRow("Обои APEX", "Карта мира, дуга до сервера и статус VPN на рабочем столе", onClick = {
                val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(context, com.apexvpn.client.vpn.ApexWallpaper::class.java),
                )
                runCatching { context.startActivity(intent) }.onFailure {
                    runCatching { context.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                        .onFailure { viewModel.showMessage("Телефон не поддерживает живые обои") }
                }
            }) { Chevron() }
        }
        Caption("Анимация идёт, только когда обои видны и VPN включён.")

        // 20. Иконка приложения
        SectionLabel("Иконка приложения")
        com.apexvpn.client.data.AppIcons.all.chunked(4).forEach { rowIcons ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            rowIcons.forEach { icon ->
                val on = s.appIcon == icon.id
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (!on) {
                                viewModel.change(reload = false) { copy(appIcon = icon.id) }
                                viewModel.showMessage("Иконка сменится на рабочем столе через несколько секунд")
                            }
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(58.dp).border(3.dp, if (on) Apex.Accent else Color.Transparent, RoundedCornerShape(18.dp)).padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.background), null, Modifier.size(50.dp))
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon.foreground), null, Modifier.size(50.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(icon.title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp)
                }
            }
            repeat(4 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
        }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            ListRow("Иконка показывает статус", "Зелёная точка на иконке, пока VPN включён") {
                ApexSwitch(s.iconStatus, { v -> viewModel.change(reload = false) { copy(iconStatus = v) } })
            }
        }
        Caption("Некоторые рабочие столы обновляют иконку не сразу, а иконку на рабочем столе после смены иногда приходится добавить заново. Если со «статусом» иконка пропадает с рабочего стола — выключите его.")
        Spacer(Modifier.height(24.dp))
    }
}

private val rainbow = listOf(
    Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE),
    Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D92), Color(0xFFFF3B30),
)

/**
 * 216. Выбор любого цвета акцента. Пока ползунок тянут, меняется только образец;
 * тема перекрашивается, когда палец отпущен.
 */
@Composable
private fun AccentPicker(argb: Long, onPick: (Long) -> Unit) {
    val hsv = remember(argb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV((argb and 0xFFFFFFFFL).toInt(), it) }
    }
    var hue by remember(argb) { mutableStateOf(hsv[0]) }
    var sat by remember(argb) { mutableStateOf(hsv[1].coerceIn(0.25f, 1f)) }
    var value by remember(argb) { mutableStateOf(hsv[2].coerceIn(0.35f, 1f)) }
    fun current(): Long = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL
    val preview = Color(current())
    val commit = { onPick(current()) }
    Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(preview))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Свой цвет", color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("#%06X".format(current() and 0xFFFFFF), color = Apex.TextDim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        PickerSlider("Оттенок", hue / 360f, androidx.compose.ui.graphics.Brush.horizontalGradient(rainbow), { hue = it * 360f }, commit)
        PickerSlider(
            "Насыщенность", (sat - 0.25f) / 0.75f,
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.25f, value))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value))))),
            { sat = 0.25f + it * 0.75f }, commit,
        )
        PickerSlider(
            "Яркость", (value - 0.35f) / 0.65f,
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 0.35f))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))))),
            { value = 0.35f + it * 0.65f }, commit,
        )
        // Быстрые образцы
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0xFFFF6B35, 0xFFE11D48, 0xFFF59E0B, 0xFF84CC16, 0xFF06B6D4, 0xFF6366F1, 0xFFA855F7, 0xFF94A3B8).forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .border(2.dp, if (c == argb) Apex.Text else Color.Transparent, CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

@Composable
private fun PickerSlider(title: String, fraction: Float, track: androidx.compose.ui.graphics.Brush, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text(title, color = Apex.TextDim, fontSize = 13.sp)
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(track))
        androidx.compose.material3.Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
        )
    }
}

/** 564. Палитра: акцент и второй цвет градиента щита. */
internal data class ColorPreset(val title: String, val emoji: String, val c1: Long, val c2: Long)

internal val ColorPresets = listOf(
    ColorPreset("Закат", "🌅", 0xFFFF5E62, 0xFFFFB347),
    ColorPreset("Мята", "🌿", 0xFF10B981, 0xFF6EE7B7),
    ColorPreset("Лаванда", "💜", 0xFF8B5CF6, 0xFFE9A8FF),
    ColorPreset("Кофе", "☕", 0xFFA0673C, 0xFFE3B98A),
    ColorPreset("Океан", "🌊", 0xFF0EA5E9, 0xFF2DD4BF),
    ColorPreset("Вишня", "🍒", 0xFFE11D48, 0xFFF472B6),
    ColorPreset("Лайм", "🍋", 0xFF65A30D, 0xFFFDE047),
    ColorPreset("Золото", "✨", 0xFFD4A017, 0xFFFFE08A),
    ColorPreset("Полночь", "🌙", 0xFF3B4CCA, 0xFF7DD3FC),
)

/** 563. Размытие, затемнение и движение своего фото. */
@Composable
private fun PhotoEffects(blur: Int, dim: Int, parallax: Boolean, onChange: (Int, Int, Boolean) -> Unit) {
    var b by remember(blur) { mutableStateOf(blur.toFloat()) }
    var d by remember(dim) { mutableStateOf(dim.toFloat()) }
    Column(Modifier.fillMaxWidth().apexCard().padding(16.dp)) {
        EffectSlider("Размытие", "${b.toInt()}", b, 0f..24f, { b = it }) { onChange(b.toInt(), d.toInt(), parallax) }
        EffectSlider("Затемнение", "${d.toInt()}%", d, 0f..90f, { d = it }) { onChange(b.toInt(), d.toInt(), parallax) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Движение при наклоне", color = Apex.Text, fontSize = 15.sp)
                Text("Фото слегка сдвигается, как живые обои", color = Apex.TextDim, fontSize = 13.sp)
            }
            ApexSwitch(parallax, { v -> onChange(b.toInt(), d.toInt(), v) })
        }
    }
}

@Composable
private fun EffectSlider(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, color = Apex.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Apex.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    androidx.compose.material3.Slider(
        value = v.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        onValueChangeFinished = onDone,
        valueRange = range,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Apex.Accent,
            activeTrackColor = Apex.Accent,
            inactiveTrackColor = Apex.SurfaceHigh,
        ),
    )
}

/** Мини-экран дизайна: фон, карточка и щит в его цветах. */
@Composable
private fun DesignPreview(
    design: Design,
    theme: ThemeMode,
    accent: AccentColor,
    customAccent: Long,
    customAccent2: Long,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val p = remember(design, theme, accent, customAccent, customAccent2, systemDark) {
        com.apexvpn.client.ui.theme.paletteFor(design, theme, accent, systemDark, customArgb = customAccent, customArgb2 = customAccent2)
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) Apex.Accent else Apex.Outline, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    when (design) {
                        Design.Cosmos -> androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF2A2470), Color(0xFF04050D)))
                        Design.Japan -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF7F2E8), Color(0xFFEFE8DA)))
                        Design.Steampunk -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3A2614), p.base))
                        Design.Synthwave -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3A0A5E), Color(0xFFB0186E), Color(0xFF1A0033)))
                        Design.Apex2 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.brand1.copy(alpha = if (p.dark) 0.35f else 0.2f), p.base, p.base))
                        Design.Glass, Design.Aurora -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.accent.copy(alpha = 0.7f), p.base, Color(0xFF22D3EE).copy(alpha = 0.6f)))
                        Design.Pastel -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFF1E8), Color(0xFFF6EEFF)))
                        Design.Neon -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.brand1.copy(alpha = 0.35f), p.base, p.brand2.copy(alpha = 0.3f)))
                        else -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(p.base, p.base))
                    },
                )
                .padding(10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.brand1, p.brand2))),
                )
                Spacer(Modifier.height(8.dp))
                if (design == Design.Bento) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(p.surface).border(1.dp, p.cardBorder, RoundedCornerShape(6.dp)))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(6.dp)).background(p.surfaceHigh))
                } else {
                    val r = RoundedCornerShape((p.radius.value / 3).dp)
                    Box(Modifier.fillMaxWidth().height(22.dp).clip(r).background(p.surface).border(1.dp, p.cardBorder, r))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(r).background(p.surfaceHigh).border(1.dp, p.cardBorder, r))
                }
            }
        }
        Text(
            design.title,
            color = if (selected) Apex.Text else Apex.TextDim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (design == Design.Terminal) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().background(Apex.Surface).padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

fun openVpnSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ─── История версий ─────────────────────────────────────────────────────

@Composable
private fun ChangelogPage(onBack: () -> Unit) {
    PageScaffold("Что нового", onBack) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            ApexLogo(44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("APEX VPN ${com.apexvpn.client.BuildConfig.VERSION_NAME}", color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Сборка ${com.apexvpn.client.BuildConfig.VERSION_CODE}", color = Apex.TextDim, fontSize = 14.sp)
            }
        }
        com.apexvpn.client.data.Changelog.releases.forEach { r ->
            ReleaseCard(r, installed = r.code == com.apexvpn.client.BuildConfig.VERSION_CODE)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ReleaseCard(r: com.apexvpn.client.data.Release, installed: Boolean) {
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (installed) Apex.SelectedCard else Apex.Surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Версия ${r.version}", color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (installed) {
                Spacer(Modifier.width(8.dp))
                Tag("Установлена")
            }
            Spacer(Modifier.weight(1f))
            Text(r.date, color = Apex.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        ChangeList(r.changes)
    }
}

/** Изменения версии по видам: цветная метка (Новое, Улучшено, Изменено, Исправлено, Удалено) и пункты. */
@Composable
fun ChangeList(changes: List<com.apexvpn.client.data.Change>, textColor: Color = Apex.Text) {
    com.apexvpn.client.data.ChangeKind.entries.forEach { kind ->
        val items = changes.filter { it.kind == kind }
        if (items.isEmpty()) return@forEach
        val color = Color(kind.argb)
        Spacer(Modifier.height(10.dp))
        Text(
            kind.title,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Spacer(Modifier.height(4.dp))
        items.forEach { c ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                Text(c.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
    }
}

// ─── Маршрутизация для опытных (как «Правила маршрутизации» в v2rayNG) ──────

private val ruleActions = listOf("proxy" to "VPN", "direct" to "Напрямую", "block" to "Блок")
private val dnsTypes = listOf("https" to "DoH", "tls" to "DoT", "quic" to "DoQ", "udp" to "UDP")
private val strategies = listOf(
    "as_is" to ("Как есть" to "Правила по доменам, IP узнаём только при подключении"),
    "ip_if_non_match" to ("IPIfNonMatch" to "Если домен не подошёл ни к одному правилу — проверить его IP"),
    "ip_on_demand" to ("IPOnDemand" to "Сразу узнавать IP и проверять правила по IP"),
)

@Composable
private fun AdvancedPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updating by viewModel.rulesUpdating.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    PageScaffold("Маршрутизация", onBack) {
        Caption("Для опытных. Неверные настройки могут сломать доступ к сайтам — тогда нажмите «Сбросить» внизу.")

        // ── Базы сайтов (гео-файлы)
        SectionLabel("Базы сайтов")
        var source by rememberSaveable { mutableStateOf(s.rulesSource) }
        val rulesInfo by viewModel.rulesInfo.collectAsStateWithLifecycle()
        val ri = rulesInfo
        Group {
            ListRow(
                "Автообновление баз",
                if (s.rulesAuto) "Проверка: " + when (s.rulesHours) { 6 -> "каждые 6 часов"; 12 -> "каждые 12 часов"; 168 -> "раз в неделю"; else -> "каждый день" } + ", качаются только изменившиеся базы" else "Только по кнопке «Обновить базы»",
                onClick = { viewModel.change(reload = false) { copy(rulesAuto = !rulesAuto) } },
            ) { ApexSwitch(s.rulesAuto, { v -> viewModel.change(reload = false) { copy(rulesAuto = v) } }) }
            GroupDivider()
            ListRow(
                if (ri != null && ri.basesDate > 0) "Базы от ${formatDate(ri.basesDate / 1000)}" else "Встроенные базы",
                buildString {
                    append("Проверено: ${formatAgo(ri?.checkedAt ?: 0)}")
                    if (ri != null && ri.source.isNotEmpty()) append("\nИсточник: ${com.apexvpn.client.data.RulesUpdater.sourceTitle(ri.source)}")
                    ri?.error?.let { append("\nПоследняя ошибка: $it") }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Group {
            val dir = remember(updating) { com.apexvpn.client.data.RulesUpdater.activeDir(context)?.let { java.io.File(it) } }
            com.apexvpn.client.data.RulesUpdater.FILES.forEachIndexed { i, name ->
                if (i > 0) GroupDivider()
                val f = dir?.let { java.io.File(it, name) }
                val count = ri?.counts?.get(name)?.let { "${"%,d".format(java.util.Locale("ru"), it)} записей · " } ?: ""
                val info = if (f != null && f.exists()) "$count${formatBytes(f.length())} · ${formatDate(f.lastModified() / 1000)}" else "нет файла"
                ListRow(com.apexvpn.client.data.RulesUpdater.TITLES[name] ?: name.removeSuffix(".srs"), info + "\n" + com.apexvpn.client.data.RulesUpdater.urlFor(name))
            }
        }
        Spacer(Modifier.height(10.dp))
        PlainField(source, "Свой адрес баз (пусто — по умолчанию)") { source = it.trim() }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (source.isNotEmpty() && !source.startsWith("https://")) {
                    viewModel.showMessage("Адрес должен начинаться с https://")
                } else {
                    viewModel.change(reload = false) { copy(rulesSource = source) }
                    viewModel.updateRules()
                }
            },
            enabled = !updating,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) {
            if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.OnAccent)
            else Text("Обновить базы", fontWeight = FontWeight.SemiBold)
        }

        // ── Свои правила
        SectionLabel("Свои правила", trailing = s.userRules.size.takeIf { it > 0 }?.toString())
        var value by rememberSaveable { mutableStateOf("") }
        var action by rememberSaveable { mutableStateOf("proxy") }
        var ruleError by remember { mutableStateOf<String?>(null) }
        if (s.userRules.isNotEmpty()) {
            Group {
                s.userRules.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    ListRow(r.value, ruleActions.firstOrNull { it.first == r.action }?.second ?: r.action) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable {
                                viewModel.change { copy(userRules = userRules.filterIndexed { j, _ -> j != i }) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        ChipRow(ruleActions, action) { action = it }
        Spacer(Modifier.height(8.dp))
        InputWithButton(value, "example.com, 1.2.3.4/24, keyword:…", Icons.Rounded.Add, { value = it; ruleError = null }) {
            val v = value.trim()
            ruleError = if (v.isEmpty()) "Введите домен или IP" else viewModel.validateUserRule(v)
            if (ruleError == null) {
                viewModel.change { copy(userRules = userRules.filterNot { it.value == v } + com.apexvpn.client.data.UserRule(v, action)) }
                value = ""
            }
        }
        ruleError?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        Caption("Домен — вместе с поддоменами. full: — только этот домен, keyword: — по слову, regexp: — выражение, IP или подсеть. Порты: port:443, udp:27000-27100 (игры напрямую), tcp:25565. Свои правила важнее остальных.")

        // ── Настройки доменов
        SectionLabel("Настройки доменов")
        Group {
            ListRow("Поддельный DNS (FakeDNS)", "Быстрее открывает сайты через VPN, адреса 198.18.x.x") {
                ApexSwitch(s.fakeDns, { v -> viewModel.change { copy(fakeDns = v) } })
            }
        }
        Spacer(Modifier.height(10.dp))
        Group {
            strategies.forEachIndexed { i, (id, t) ->
                if (i > 0) GroupDivider()
                ChoiceRow(t.first, t.second, s.domainStrategy == id) { viewModel.change { copy(domainStrategy = id) } }
            }
        }

        // ── 31. WARP-цепочка
        SectionLabel("WARP-цепочка")
        val warpBusy by viewModel.warpBusy.collectAsStateWithLifecycle()
        Group {
            ListRow(
                "Выход через Cloudflare WARP",
                when {
                    warpBusy -> "Регистрирую бесплатный аккаунт…"
                    s.warp -> "Вы → сервер VPN → WARP → сайт"
                    else -> "Сайты увидят IP Cloudflare, а не сервера"
                },
            ) {
                if (warpBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Apex.Accent)
                else ApexSwitch(s.warp, { v -> viewModel.setWarp(v) })
            }
            if (s.warpConfig.isNotBlank()) {
                GroupDivider()
                ListRow("Новый аккаунт WARP", "Если WARP перестал работать", onClick = { viewModel.resetWarp(); viewModel.setWarp(true) })
            }
        }
        Caption("Помогает, когда сайт не открывается с IP сервера (капча, «недоступно в вашей стране»). Скорость может быть ниже. Нужен сервер с поддержкой UDP.")

        // ── DNS
        DnsEditor("Удалённый DNS (через VPN)", "https://dns.google/dns-query", s.remoteDns, viewModel) { d -> viewModel.change { copy(remoteDns = d) } }
        DnsEditor("Домашний DNS (напрямую)", "77.88.8.8", s.directDns, viewModel) { d -> viewModel.change { copy(directDns = d) } }
        Caption("Пусто — DNS из раздела «DNS». IP нужен, чтобы адрес сервера DNS не узнавать через другой DNS.")

        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Apex.Bad),
        ) { Text("Сбросить настройки для опытных", color = Apex.Bad, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(28.dp))
    }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Apex.Dialog,
            title = { Text("Сбросить?", color = Apex.Text) },
            text = { Text("Свои правила, FakeDNS, стратегия и DNS вернутся по умолчанию.", color = Apex.TextDim) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmReset = false
                    val d = com.apexvpn.client.data.Settings()
                    viewModel.change {
                        copy(userRules = d.userRules, domainStrategy = d.domainStrategy, fakeDns = d.fakeDns,
                            remoteDns = d.remoteDns, directDns = d.directDns, rulesSource = d.rulesSource)
                    }
                    viewModel.showMessage("Настройки сброшены")
                }) { Text("Сбросить", color = Apex.Bad) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun DnsEditor(
    title: String,
    hint: String,
    current: com.apexvpn.client.data.DnsSpec,
    viewModel: MainViewModel,
    onSave: (com.apexvpn.client.data.DnsSpec) -> Unit,
) {
    var type by rememberSaveable(current) { mutableStateOf(current.type) }
    var server by rememberSaveable(current) { mutableStateOf(current.server) }
    var ip by rememberSaveable(current) { mutableStateOf(current.ip) }
    var error by remember { mutableStateOf<String?>(null) }
    SectionLabel(title, trailing = if (current.isSet) "вкл" else null)
    ChipRow(dnsTypes, type) { type = it; error = null }
    Spacer(Modifier.height(8.dp))
    PlainField(server, hint) { server = it.trim(); error = null }
    Spacer(Modifier.height(8.dp))
    PlainField(ip, "IP сервера (необязательно)") { ip = it.trim(); error = null }
    error?.let { Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                val d = com.apexvpn.client.data.DnsSpec(type, server, ip)
                error = if (d.isSet) viewModel.validateDnsSpec(d) else null
                if (error == null) {
                    onSave(d)
                    viewModel.showMessage(if (d.isSet) "DNS сохранён" else "DNS по умолчанию")
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) { Text("Сохранить") }
        if (current.isSet) {
            androidx.compose.material3.TextButton(onClick = { onSave(com.apexvpn.client.data.DnsSpec()) }) { Text("Очистить", color = Apex.TextDim) }
        }
    }
}

@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Apex.Accent else Apex.Surface)
                    .border(1.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(12.dp))
                    .clickable { onSelect(id) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Apex.OnAccent else Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
internal fun PlainField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Apex.Surface)
            .border(1.dp, Apex.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Apex.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Apex.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Мини-превью главного экрана: щит или карта с дугой. */
@Composable
private fun HomeStylePreview(map: Boolean) {
    val accent = Apex.Accent
    val accent2 = Apex.Cyan
    val dim = Apex.TextDim
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        if (map) {
            val cw = size.width / WorldMap.W
            val ch = size.height / WorldMap.H
            WorldMap.rows.forEachIndexed { j, row ->
                row.forEachIndexed { i, c ->
                    if (c == '#' && i % 2 == 0 && j % 2 == 0) drawCircle(dim.copy(alpha = 0.35f), minOf(cw, ch) * 0.6f, androidx.compose.ui.geometry.Offset((i + 0.5f) * cw, (j + 0.5f) * ch))
                }
            }
            val a = androidx.compose.ui.geometry.Offset(size.width * 0.6f, size.height * 0.32f)
            val b = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.38f)
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); quadraticBezierTo((a.x + b.x) / 2, a.y - 20f, b.x - 30f, b.y) }
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawCircle(accent, 3.dp.toPx(), a)
            drawCircle(accent2, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(b.x - 30f, b.y))
        } else {
            val r = size.height * 0.42f
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent), center, r * 1.3f), r * 1.3f)
            drawCircle(accent, r * 0.7f)
        }
    }
}
