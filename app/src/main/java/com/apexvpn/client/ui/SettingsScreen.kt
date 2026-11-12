package com.apexvpn.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Block
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AppsMode
import com.apexvpn.client.data.CoreMode
import com.apexvpn.client.data.DnsChoice
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.update.UpdateState

private val refreshOptions = listOf(1, 6, 12, 24)

@Composable
fun SettingsScreen(viewModel: MainViewModel, onOpenPage: (Page) -> Unit, onScanQr: () -> Unit) {
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val rulesUpdating by viewModel.rulesUpdating.collectAsStateWithLifecycle()
    val rulesInfo by viewModel.rulesInfo.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var supportDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var rulesDialog by remember { mutableStateOf(false) }
    var coreDialog by remember { mutableStateOf(false) }
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ScreenTitle("Настройки", Modifier.padding(start = 4.dp))
        // 3.12. Категории: показать только нужный раздел настроек
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            settingsCategories.forEach { (id, title) ->
                val on = cat == id
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(50))
                        .background(if (on) Apex.Accent else Apex.Surface)
                        .clickable { cat = id }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(title, color = if (on) Apex.OnAccent else Apex.Text, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }

        // 3.17.9: поиск по настройкам — сразу к нужному пункту
        var search by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
        Spacer(Modifier.height(10.dp))
        SearchField(search, "Поиск по настройкам") { search = it }
        if (search.isNotBlank()) {
            val q = search.trim().lowercase()
            val found = settingsSearch.filter { q in it.title.lowercase() || q in it.keywords }
            Spacer(Modifier.height(10.dp))
            if (found.isEmpty()) {
                Text("Ничего не нашлось", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            } else Group {
                found.forEachIndexed { i, item ->
                    if (i > 0) GroupDivider()
                    val where = if (item.cat == "mode") "Вкладка «Режим»" else settingsCategories.firstOrNull { it.first == item.cat }?.second ?: ""
                    ListRow(item.title, where, onClick = {
                        when {
                            item.page != null -> onOpenPage(item.page)
                            item.cat == "mode" -> viewModel.showMessage("Это на вкладке «Режим» внизу экрана")
                            else -> cat = item.cat
                        }
                        search = ""
                    }) { Chevron() }
                }
            }
        }
        if (search.isBlank()) {
        if (cat == "all" || cat == "sub") {
        // ── Подписка ──
        SectionLabel("Подписка")
        Group {
            ListRow("Ссылка", maskUrl(sub.url), leading = { tile(Icons.Rounded.Link) })
            GroupDivider()
            ListRow(
                "Обновить сейчас",
                "Обновлено ${formatAgo(sub.lastUpdated)}",
                leading = { tile(Icons.Rounded.Refresh) },
                onClick = { if (!refreshing) viewModel.refresh() },
            ) { if (refreshing) Spinner() }
            GroupDivider()
            ListRow("Изменить ссылку", leading = { tile(Icons.Rounded.Edit) }, onClick = { editing = true }) { Chevron() }
            GroupDivider()
            ListRow("Сканировать QR-код", "Добавить подписку с экрана другого устройства", leading = { tile(Icons.Rounded.QrCodeScanner) }, onClick = onScanQr) { Chevron() }
            GroupDivider()
            // 210. Ссылка-приглашение: страница на GitHub Pages, ссылка подписки — после # (не уходит на сервер)
            ListRow(
                "Ссылка-приглашение",
                "Для своего второго телефона или семьи: установка и подписка в одно касание",
                leading = { tile(Icons.Rounded.Share) },
                onClick = inviteClick@{
                    if (sub.url.isBlank()) { viewModel.showMessage("Сначала добавьте подписку"); return@inviteClick }
                    val invite = com.apexvpn.client.data.Invite.link(sub.url)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "APEX CLIENT — установка и подписка: $invite")
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Отправить приглашение")) }
                        .onFailure {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("APEX CLIENT", invite))
                            viewModel.showMessage("Ссылка-приглашение скопирована")
                        }
                },
            ) { Chevron() }
            GroupDivider()
            ListRow("Удалить подписку", leading = { tile(Icons.Rounded.Delete, Apex.Bad) }, titleColor = Apex.Bad, onClick = { confirmRemove = true })
        }
        SectionLabel("Автообновление подписки")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, null, tint = Apex.TextDim, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
            Column(Modifier.weight(1f)) {
                Segmented(refreshOptions.map { "$it ч" }, refreshOptions.indexOf(s.refreshHours).coerceAtLeast(0)) {
                    viewModel.change(reload = false) { copy(refreshHours = refreshOptions[it]) }
                }
            }
        }

        }
        if (cat == "all" || cat == "conn") {
        // ── Подключение ──
        SectionLabel("Подключение")
        Group {
            ListRow("Режим автопилота", com.apexvpn.client.data.AutoModes.Mode.of(s.autoMode).let { "${it.emoji} ${it.title} — ${it.subtitle}" }, leading = { tile(Icons.Rounded.SportsEsports) }, onClick = { onOpenPage(Page.Profile) }) { Chevron() }
            GroupDivider()
            ListRow(
                "Автопереключение при сбое",
                "Если сервер перестал отвечать — включить автопилот",
                leading = { tile(Icons.Rounded.AutoMode) },
                onClick = { viewModel.change(reload = false) { copy(failover = !failover) } },
            ) { ApexSwitch(s.failover, { v -> viewModel.change(reload = false) { copy(failover = v) } }) }
            GroupDivider()
            ListRow(
                "DNS",
                if (s.kids) "Семейный режим" else if (s.dns == DnsChoice.Custom) s.customDns
                else if (s.dns == DnsChoice.Auto) com.apexvpn.client.data.DnsAuto.bestVariant().let { "Авто · ${it.choice.title} ${it.protoTitle}" } else s.dns.title,
                leading = { tile(Icons.Rounded.Dns) },
                onClick = { onOpenPage(Page.Dns) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Маршрутизация",
                "Для опытных: свои правила, FakeDNS, DNS" + if (s.userRules.isNotEmpty()) " · правил: ${s.userRules.size}" else "",
                leading = { tile(Icons.Rounded.Route) },
                onClick = { onOpenPage(Page.Advanced) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Ядро",
                "${s.core.title} — ${s.core.subtitle}",
                leading = { tile(Icons.Rounded.Memory) },
                onClick = { coreDialog = true },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Без серверов с рекламой",
                "Автопилот не выбирает серверы с пометкой «реклама на YouTube»",
                leading = { tile(Icons.Rounded.Block) },
                onClick = { viewModel.change { copy(excludeAds = !excludeAds) } },
            ) { InfoHint("Без серверов с рекламой", "Некоторые бесплатные серверы в подписке показывают рекламу. С этой настройкой автопилот их не выбирает."); ApexSwitch(s.excludeAds, { v -> viewModel.change { copy(excludeAds = v) } }) }
            GroupDivider()
            ListRow(
                "IPv6",
                if (s.ipv6) "Сайты могут открываться по IPv6" else "Только IPv4 — стабильнее",
                leading = { tile(Icons.Rounded.SettingsEthernet) },
                onClick = { viewModel.change { copy(ipv6 = !ipv6) } },
            ) { InfoHint("IPv6", "Новый вид интернет-адресов. Если оператор его даёт, а сервер VPN — нет, часть сайтов может идти мимо VPN или не открываться. Не уверены — оставьте выключенным."); ApexSwitch(s.ipv6, { v -> viewModel.change { copy(ipv6 = v) } }) }
            GroupDivider()
            ListRow(
                "Аварийный выключатель",
                if (s.killSwitch) "Если VPN упадёт — интернет закроется, пока он не вернётся" else "Выключен",
                leading = { tile(Icons.Rounded.GppMaybe) },
                onClick = { viewModel.change(reload = false) { copy(killSwitch = !killSwitch) } },
            ) { InfoHint("Аварийный выключатель", "Если VPN неожиданно упадёт, интернет закроется, пока VPN не переподключится. Так ни один запрос не уйдёт без защиты. Банки и скрытые приложения работают как обычно."); ApexSwitch(s.killSwitch, { v -> viewModel.change(reload = false) { copy(killSwitch = v) } }) }
        }

        // ── Надёжность ──
        SectionLabel("Надёжность")
        Group {
            ListRow(
                "Защита без перерывов",
                "Постоянная VPN и блокировка без VPN — в настройках Android",
                leading = { tile(Icons.Rounded.Shield) },
                onClick = { openVpnSettings(context) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Чтобы VPN не отключался",
                "Батарея и автозапуск для Xiaomi, Samsung, Honor",
                leading = { tile(Icons.Rounded.BatteryChargingFull) },
                onClick = { onOpenPage(Page.KeepAlive) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Подключать при открытии",
                if (s.connectOnOpen) "VPN включается, как только вы открываете приложение" else null,
                leading = { tile(Icons.Rounded.PlayCircle) },
                onClick = { viewModel.change(reload = false) { copy(connectOnOpen = !connectOnOpen) } },
            ) { ApexSwitch(s.connectOnOpen, { v -> viewModel.change(reload = false) { copy(connectOnOpen = v) } }) }
            GroupDivider()
            ListRow(
                "Не работает?",
                "Самопроверка и выбор рабочего сервера",
                leading = { tile(Icons.Rounded.Healing) },
                onClick = { onOpenPage(Page.SelfCheck) },
            ) { Chevron() }
        }
        Spacer(Modifier.height(12.dp))
        ServerHealthCard(viewModel)

        // ── Сеть ──
        SectionLabel("Сеть")
        Group {
            ListRow(
                "Правила Wi-Fi",
                if (s.wifiRules) "Доверенных сетей: ${s.trustedWifi.size}" else "Выключено",
                leading = { tile(Icons.Rounded.Wifi) },
                onClick = { onOpenPage(Page.Wifi) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Раздача на компьютер",
                if (s.share) "Включено, порт ${s.sharePort}" else "Выключено",
                leading = { tile(Icons.Rounded.Computer) },
                onClick = { onOpenPage(Page.Share) },
            ) { Chevron() }
        }

        }
        if (cat == "all" || cat == "tools") {
        // ── Инструменты ──
        SectionLabel("Инструменты")
        Group {
            ListRow(
                "Пинг",
                when (s.pingType) {
                    "get" -> "Через прокси · GET · ${s.pingMode}"
                    "head" -> "Через прокси · HEAD · ${s.pingMode}"
                    "icmp" -> "ICMP"
                    else -> "TCP — до сервера"
                },
                leading = { tile(Icons.Rounded.NetworkPing) },
                onClick = { onOpenPage(Page.Ping) },
            ) { Chevron() }
            GroupDivider()
            ListRow("Тест скорости", "Пинг, загрузка и отдача", leading = { tile(Icons.Rounded.Speed) }, onClick = { onOpenPage(Page.SpeedTest) }) { Chevron() }
            GroupDivider()
            // 413
            ListRow("Трафик по приложениям", "Кто сколько потратил через VPN", leading = { tile(Icons.Rounded.DataUsage) }, onClick = { onOpenPage(Page.AppTraffic) }) { Chevron() }
            GroupDivider()
            // 426
            ListRow(
                "Автоматизация",
                if (s.automation) "Tasker и MacroDroid могут включать VPN — инструкция в «Справке»" else "Разрешить Tasker и MacroDroid управлять VPN",
                leading = { tile(Icons.Rounded.SmartToy) },
                onClick = { viewModel.change(reload = false) { copy(automation = !automation) } },
            ) { ApexSwitch(s.automation, { v -> viewModel.change(reload = false) { copy(automation = v) } }) }
            GroupDivider()
            ListRow(
                "Проверка утечек",
                "Видят ли сайты ваш настоящий IP: IP, DNS, IPv6, WebRTC",
                leading = { tile(Icons.Rounded.Policy) },
                onClick = { onOpenPage(Page.LeakTest) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Журнал соединений",
                if (s.connLog) "Какие сайты шли через VPN, а какие напрямую" else "Выключен",
                leading = { tile(Icons.AutoMirrored.Rounded.ReceiptLong) },
                onClick = { onOpenPage(Page.ConnLog) },
            ) { Chevron() }
            GroupDivider()
            ListRow("Проверить сайт", "Через VPN или напрямую", leading = { tile(Icons.Rounded.Search) }, onClick = { onOpenPage(Page.SiteCheck) }) { Chevron() }
            GroupDivider()
            ListRow("Правила «если — то»", if (s.autoRules.isEmpty()) "«Открыт Steam → режим „Игры“», «зарядка → обновить базы»" else "Правил: ${s.autoRules.count { it.enabled }} включено", leading = { tile(Icons.Rounded.SmartToy) }, onClick = { onOpenPage(Page.AutoRules) }) { Chevron() }
            GroupDivider()
            ListRow("Помощник", "Чат: «не работает YouTube», «медленно», «банк не открывается» — ответит и подскажет", leading = { tile(Icons.Rounded.SupportAgent) }, onClick = { onOpenPage(Page.Helper) }) { Chevron() }
            GroupDivider()
            ListRow("Отчёт и история", "Неделя по дням, последние подключения, итоги месяца", leading = { tile(Icons.Rounded.Search) }, onClick = { onOpenPage(Page.Report) }) { Chevron() }
            GroupDivider()
            ListRow("Сервисы на серверах", "Где работают ChatGPT, YouTube Premium, Netflix, Gemini", leading = { tile(Icons.Rounded.Search) }, onClick = { onOpenPage(Page.ServiceCheck) }) { Chevron() }
            GroupDivider()
            ListRow("Приложение не работает?", "Пустить его мимо VPN одной кнопкой (есть и в уведомлении VPN)", leading = { tile(Icons.Rounded.Policy) }, onClick = { onOpenPage(Page.AppIssue) }) { Chevron() }
            GroupDivider()
            val ri = rulesInfo
            ListRow(
                "Обновить базы сайтов",
                when {
                    rulesUpdating -> "Проверяем…"
                    ri != null && ri.basesDate > 0 -> "Базы от ${formatDate(ri.basesDate / 1000)} · проверено ${formatAgo(ri.checkedAt)}"
                    else -> "Встроенные базы · ${if (s.rulesAuto) "обновятся автоматически" else "автообновление выключено"}"
                },
                leading = { tile(Icons.Rounded.Storage) },
                onClick = { viewModel.updateRules() },
            ) { if (rulesUpdating) Spinner() }
            GroupDivider()
            // Автообновление баз — прямо здесь, а не только в «Маршрутизации»
            ListRow(
                "Автообновление баз",
                if (s.rulesAuto) "${rulesPeriod(s.rulesHours)} · качаются только изменения · нажмите, чтобы изменить" else "Выключено — только по кнопке выше",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { rulesDialog = true },
            ) { ApexSwitch(s.rulesAuto, { v -> viewModel.change(reload = false) { copy(rulesAuto = v) } }) }
        }
        if (rulesDialog) {
            AlertDialog(
                onDismissRequest = { rulesDialog = false },
                containerColor = Apex.Dialog,
                title = { Text("Как часто обновлять базы", color = Apex.Text) },
                text = {
                    Column {
                        listOf(6, 12, 24, 168).forEach { h ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.change(reload = false) { copy(rulesAuto = true, rulesHours = h) }
                                        rulesDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = s.rulesAuto && s.rulesHours == h,
                                    onClick = null,
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Apex.Accent),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(rulesPeriod(h), color = Apex.Text, fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Проверка почти не тратит трафик: качаются только изменившиеся базы. После обновления ядро подхватывает их само.", color = Apex.TextDim, fontSize = 13.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { rulesDialog = false }) { Text("Готово", color = Apex.Accent) } },
            )
        }

        }
        if (cat == "all" || cat == "look") {
        // ── Уведомления ──
        SectionLabel("Уведомления")
        Group {
            ListRow(
                "Скорость в уведомлении",
                "↓ загрузка ↑ отдача в шторке",
                leading = { tile(Icons.Rounded.Speed) },
                onClick = { viewModel.change(reload = false) { copy(speedInNotification = !speedInNotification) } },
            ) { ApexSwitch(s.speedInNotification, { v -> viewModel.change(reload = false) { copy(speedInNotification = v) } }) }
            // 3.13.5. Android 16: «живое» уведомление (капсула в строке состояния)
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                GroupDivider()
                ListRow(
                    "Живое уведомление",
                    if (s.liveNotification) "Капсула APEX в строке состояния (Android 16). Если её нет — разрешите «Живые уведомления» в настройках уведомлений" else "Выключено",
                    leading = { tile(Icons.Rounded.Notifications) },
                    onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    },
                ) { ApexSwitch(s.liveNotification, { v -> viewModel.change(reload = false) { copy(liveNotification = v) }; com.apexvpn.client.vpn.ApexVpnService.reload(context) }) }
            }
            GroupDivider()
            ListRow(
                "Конец подписки",
                "Уведомить за 3 дня, за 1 день и в день окончания, а ещё при остатке трафика < 10%",
                leading = { tile(Icons.Rounded.NotificationsActive) },
                onClick = { viewModel.change(reload = false) { copy(expiryAlerts = !expiryAlerts) } },
            ) { ApexSwitch(s.expiryAlerts, { v -> viewModel.change(reload = false) { copy(expiryAlerts = v) } }) }
        }

        // ── Оформление ──
        SectionLabel("Оформление")
        Group {
            ListRow("Дизайн, тема и иконка", "${s.design.title} · ${s.theme.title}", leading = { tile(Icons.Rounded.Palette) }, onClick = { onOpenPage(Page.Appearance) }) { Chevron() }
        }

        }
        if (cat == "all" || cat == "updates") {
        // ── Обновления ──
        SectionLabel("Обновления приложения")
        Group {
            ListRow(
                "Проверять автоматически",
                "При запуске и раз в час — с GitHub Releases",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { viewModel.change(reload = false) { copy(autoCheckUpdates = !autoCheckUpdates) } },
            ) { ApexSwitch(s.autoCheckUpdates, { v -> viewModel.change(reload = false) { copy(autoCheckUpdates = v) } }) }
            GroupDivider()
            ListRow(
                "Ставить само",
                if (s.updateMode == "auto") "Скачает заранее и поставит, когда VPN выключен или экран погас" else "Выключено — уведомление и кнопка «Обновить»",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { viewModel.change(reload = false) { copy(updateMode = if (updateMode == "auto") "ask" else "auto") } },
            ) { ApexSwitch(s.updateMode == "auto", { v -> viewModel.change(reload = false) { copy(updateMode = if (v) "auto" else "ask") } }) }
            GroupDivider()
            ListRow(
                "Бета-версии",
                if (s.betaChannel) "Включено: новые версии приходят раньше всех — они могут быть сырыми" else "Только обычные проверенные версии",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { viewModel.change(reload = false) { copy(betaChannel = !betaChannel) }; viewModel.checkUpdate() },
            ) { ApexSwitch(s.betaChannel, { v -> viewModel.change(reload = false) { copy(betaChannel = v) }; viewModel.checkUpdate() }) }
            GroupDivider()
            ListRow("Вернуть прошлую версию", "Если новая не понравилась — по шагам, с сохранением настроек", leading = { tile(Icons.Rounded.SystemUpdate) }, onClick = { onOpenPage(Page.Rollback) }) { Chevron() }
            GroupDivider()
            val watchStatus by com.apexvpn.client.update.WatchUpdater.status.collectAsStateWithLifecycle()
            if (com.apexvpn.client.update.WatchUpdater.watchCode > 0) {
                val outdated = com.apexvpn.client.update.WatchUpdater.outdated
                ListRow(
                    "Обновить часы APEX Watch",
                    watchStatus ?: if (outdated) "Часы на старой версии — нажмите, телефон передаст обновление" else "Часы на той же версии, что и телефон",
                    leading = { tile(Icons.Rounded.SystemUpdate) },
                    onClick = { viewModel.updateWatch() },
                ) { if (outdated) Tag("Обновить") }
                GroupDivider()
            }
            ListRow(
                "Скачивать только по Wi-Fi",
                "Для «Ставить само»: без мобильного трафика",
                leading = { tile(Icons.Rounded.Update) },
                onClick = { viewModel.change(reload = false) { copy(updateWifiOnly = !updateWifiOnly) } },
            ) { ApexSwitch(s.updateWifiOnly, { v -> viewModel.change(reload = false) { copy(updateWifiOnly = v) } }) }
            GroupDivider()
            val (subtitle, busy) = when (val u = update) {
                UpdateState.Checking -> "Проверяем…" to true
                UpdateState.UpToDate -> "У вас последняя версия" to false
                is UpdateState.Available -> "Доступна версия ${u.info.versionName} — нажмите, чтобы обновить" to false
                is UpdateState.Failed -> u.message to false
                UpdateState.Idle -> "Версия ${BuildConfig.VERSION_NAME}" to false
            }
            ListRow("Проверить сейчас", subtitle, leading = { tile(Icons.Rounded.SystemUpdate) }, onClick = {
                val u = update
                if (u is UpdateState.Available) {
                    com.apexvpn.client.data.Prefs.dismissedUpdateCode = 0
                    if (u.info.apkUrl != null && u.info.shaUrl != null) viewModel.installUpdate(u.info)
                    else com.apexvpn.client.update.UpdateManager.openPage(context, u.info)
                }
                else if (!busy) viewModel.checkUpdate()
            }) {
                if (busy) Spinner()
            }
        }

        }
        if (cat == "all" || cat == "data") {
        // ── Резервная копия настроек: после переустановки всё вернуть одним файлом ──
        SectionLabel("Резервная копия")
        // 3.8. Копия с паролем: пароль спрашиваем до выбора файла, при восстановлении — если файл зашифрован
        var exportPassword by remember { mutableStateOf("") }
        var askExportPassword by remember { mutableStateOf(false) }
        var lockedBackup by remember { mutableStateOf<org.json.JSONObject?>(null) }
        val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument(com.apexvpn.client.data.SettingsBackup.MIME),
        ) { uri ->
            if (uri != null) {
                val withPass = exportPassword.isNotEmpty()
                viewModel.showMessage(
                    if (com.apexvpn.client.data.SettingsBackup.export(context, uri, exportPassword))
                        (if (withPass) "Настройки сохранены в файл с паролем" else "Настройки сохранены в файл") else "Не удалось сохранить файл",
                )
            }
            exportPassword = ""
        }
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                val o = com.apexvpn.client.data.SettingsBackup.read(context, uri)
                when {
                    o == null -> viewModel.showMessage("Это не файл настроек APEX CLIENT")
                    com.apexvpn.client.data.SettingsBackup.isEncrypted(o) -> lockedBackup = o
                    else -> {
                        val restored = com.apexvpn.client.data.SettingsBackup.restore(o)
                        if (restored == null) viewModel.showMessage("Это не файл настроек APEX CLIENT") else viewModel.applyRestored(restored)
                    }
                }
            }
        }
        if (askExportPassword) BackupPasswordDialog(
            title = "Пароль для копии",
            text = "С паролем файл не прочитать без него — даже если он попадёт к кому-то. Пароль не восстановить, запомните его. Можно и без пароля.",
            confirm = "С паролем",
            dismiss = "Без пароля",
            onConfirm = { p -> exportPassword = p; askExportPassword = false; exportLauncher.launch(com.apexvpn.client.data.SettingsBackup.fileName()) },
            onDismiss = { exportPassword = ""; askExportPassword = false; exportLauncher.launch(com.apexvpn.client.data.SettingsBackup.fileName()) },
        )
        lockedBackup?.let { o ->
            BackupPasswordDialog(
                title = "Копия защищена паролем",
                text = "Введите пароль, который задали при сохранении.",
                confirm = "Восстановить",
                dismiss = "Отмена",
                onConfirm = { p ->
                    val plain = com.apexvpn.client.data.SettingsBackup.decrypt(o, p)
                    val restored = plain?.let { com.apexvpn.client.data.SettingsBackup.restore(it) }
                    if (restored == null) viewModel.showMessage("Неверный пароль") else { lockedBackup = null; viewModel.applyRestored(restored) }
                },
                onDismiss = { lockedBackup = null },
            )
        }
        var transferQr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var subQr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        Group {
            // 262. Перенос на новый телефон одним QR
            ListRow(
                "Перенести на новый телефон",
                "QR-код со всеми настройками и подпиской",
                leading = { tile(Icons.Rounded.QrCode2) },
                onClick = {
                    when (val r = com.apexvpn.client.data.Transfer.encode()) {
                        is com.apexvpn.client.data.Transfer.Result.Ok ->
                            transferQr = runCatching { com.apexvpn.client.data.Transfer.qrBitmap(r.text) }.getOrNull()
                                ?: run { viewModel.showMessage("Не удалось создать QR-код"); null }
                        is com.apexvpn.client.data.Transfer.Result.TooBig ->
                            viewModel.showMessage("Слишком много данных для QR — сохраните настройки в файл")
                    }
                },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Принять с другого телефона",
                "Отсканируйте QR-код переноса",
                leading = { tile(Icons.Rounded.QrCodeScanner) },
                onClick = onScanQr,
            ) { Chevron() }
            // 3.17.4 (58). Только ссылка подписки — для второго телефона или другого приложения
            if (com.apexvpn.client.data.Prefs.subscriptionUrl.isNotEmpty()) {
                GroupDivider()
                ListRow(
                    "QR-код подписки",
                    "Только ссылка подписки — для второго устройства",
                    leading = { tile(Icons.Rounded.QrCode2) },
                    onClick = {
                        subQr = runCatching { com.apexvpn.client.data.Transfer.qrBitmap(com.apexvpn.client.data.Prefs.subscriptionUrl) }.getOrNull()
                            ?: run { viewModel.showMessage("Не удалось создать QR-код"); null }
                    },
                ) { Chevron() }
            }
            GroupDivider()
            ListRow(
                "Сохранить настройки в файл",
                "Всё, включая свои списки, правила, избранное и подписку — по желанию с паролем",
                leading = { tile(Icons.Rounded.Download) },
                onClick = { askExportPassword = true },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Восстановить из файла",
                "После переустановки или на новом телефоне",
                leading = { tile(Icons.Rounded.Refresh) },
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            ) { Chevron() }
        }
        transferQr?.let { bmp ->
            TransferQrDialog(bmp) { transferQr = null }
        }
        subQr?.let { bmp ->
            TransferQrDialog(
                bmp,
                title = "QR-код подписки",
                hint = "Только ссылка подписки, без настроек. Отсканируйте в APEX CLIENT («Сканировать QR-код») или в другом приложении, например Happ.",
            ) { subQr = null }
        }
        Caption("Новый телефон: установите APEX CLIENT, нажмите «Сканировать QR-код» и наведите камеру на QR переноса. Настройки сохраняются при обновлении приложения. Файл нужен, если приложение пришлось удалить. В нём ссылка подписки — не отправляйте его другим. Ещё настройки попадают в резервную копию Android, если она включена на телефоне.")

        // ── 379. Очистить данные ──
        var confirmClear by remember { mutableStateOf(false) }
        SectionLabel("Данные")
        Group {
            ListRow(
                "Очистить данные",
                "Всё, кроме подписки: настройки, списки, история, журналы, кэш",
                leading = { tile(Icons.Rounded.DeleteSweep, Apex.Bad) },
                titleColor = Apex.Bad,
                onClick = { confirmClear = true },
            ) { Chevron() }
        }
        if (confirmClear) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmClear = false },
                containerColor = Apex.Dialog,
                title = { Text("Очистить данные?", color = Apex.Text) },
                text = {
                    Text(
                        "Сбросятся все настройки, свои списки, правила, избранное, история тестов скорости, журналы, отчёты о сбоях, фото фона и кэш. " +
                            "Подписка и выбранный сервер останутся. Отменить нельзя — сначала можно сохранить резервную копию.",
                        color = Apex.TextDim,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmClear = false
                        com.apexvpn.client.data.DataCleaner.clear(context)
                        viewModel.change { this }
                        viewModel.showMessage("Данные очищены, подписка сохранена")
                    }) { Text("Очистить", color = Apex.Bad, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = Apex.TextDim) } },
            )
        }

        }
        if (cat == "all" || cat == "about") {
        // ── О приложении ──
        SectionLabel("О приложении")
        Group {
            Row(
                // Карточка и есть «О приложении»: конфиденциальность, исходный код, лицензии
                Modifier.fillMaxWidth().clickable { onOpenPage(Page.About) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecretLogo(viewModel)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("APEX CLIENT", color = Apex.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Версия ${BuildConfig.VERSION_NAME}", color = Apex.TextDim, fontSize = 14.sp)
                    Text("Ядро sing-box ${singBoxVersion()}", color = Apex.TextDim, fontSize = 13.sp)
                    Text("Ядро Xray ${BuildConfig.XRAY_VERSION}", color = Apex.TextDim, fontSize = 13.sp)
                    Text("Конфиденциальность, исходный код и лицензии", color = Apex.TextDim, fontSize = 13.sp)
                    // Подлинность сборки
                    val integrity = remember { com.apexvpn.client.data.Integrity.status(context) }
                    Text(
                        when (integrity) {
                            com.apexvpn.client.data.Integrity.Status.Official -> "✓ Официальная сборка"
                            com.apexvpn.client.data.Integrity.Status.Test -> "Тестовая сборка"
                            com.apexvpn.client.data.Integrity.Status.Unofficial -> "⚠ Неофициальная копия"
                        },
                        color = when (integrity) {
                            com.apexvpn.client.data.Integrity.Status.Official -> Apex.Good
                            com.apexvpn.client.data.Integrity.Status.Test -> Apex.TextDim
                            com.apexvpn.client.data.Integrity.Status.Unofficial -> Apex.Bad
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Chevron()
            }
            GroupDivider()
            ListRow(
                "Отчёты об ошибках",
                "Сохранять отчёт о сбое для поддержки — без сайтов и содержимого трафика",
                leading = { tile(Icons.Rounded.BugReport) },
                onClick = { viewModel.change(reload = false) { copy(crashReports = !crashReports) } },
            ) { ApexSwitch(s.crashReports, { v -> viewModel.change(reload = false) { copy(crashReports = v) } }) }
            GroupDivider()
            ListRow(
                "Справка",
                "Инструкции с картинками",
                leading = { tile(Icons.AutoMirrored.Rounded.HelpOutline) },
                onClick = { onOpenPage(Page.Help) },
            ) { Chevron() }
            GroupDivider()
            ListRow(
                "Что нового",
                "История версий APEX CLIENT",
                leading = { tile(Icons.Rounded.NewReleases) },
                onClick = { onOpenPage(Page.Changelog) },
            ) { Chevron() }
            // Наши ссылки
            GroupDivider()
            ListRow(
                "GitHub",
                "Исходный код, все версии и инструкции",
                leading = { tile(Icons.Rounded.Code) },
                onClick = { openLink(context, com.apexvpn.client.data.Links.GITHUB) },
            ) { Chevron() }
            // 3.17.4 (70). Поделиться приложением: ссылка на скачивание и короткая инструкция
            GroupDivider()
            ListRow(
                "Поделиться APEX CLIENT",
                "Ссылка на скачивание и как установить",
                leading = { tile(Icons.Rounded.Share) },
                onClick = {
                    val text = "APEX CLIENT — VPN для Android: автопилот, обход белых списков, блокировка рекламы.\n\n" +
                        "Сайт и скачивание: ${com.apexvpn.client.data.Links.site()}\n\n" +
                        "Как установить:\n1. Скачайте файл apex-client-….apk\n2. Откройте его и разрешите установку\n" +
                        "3. В приложении вставьте ссылку подписки и нажмите «Подключить»"
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, text),
                                "Поделиться APEX CLIENT",
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            ) { Chevron() }
            // 3.18.1. Код предпросмотра: черновики из пульта видны только на этом телефоне
            GroupDivider()
            var previewDialog by remember { mutableStateOf(false) }
            val previewOn = com.apexvpn.client.data.Prefs.previewCode.isNotBlank()
            ListRow(
                "Код предпросмотра",
                if (previewOn) "Введён — видны черновики из пульта" else "Для владельца: проверить баннер до публикации",
                leading = { tile(Icons.Rounded.Visibility) },
                onClick = { previewDialog = true },
            ) { Chevron() }
            if (previewDialog) {
                var code by remember { mutableStateOf(com.apexvpn.client.data.Prefs.previewCode) }
                val previewScope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { previewDialog = false },
                    containerColor = Apex.Dialog,
                    title = { Text("Код предпросмотра", color = Apex.Text) },
                    text = {
                        Column {
                            Text("Тот же код, что в пульте (раздел «Предпросмотр»). С ним на этом телефоне видны баннеры, объявления и пометки, отмеченные «только предпросмотр».",
                                color = Apex.TextDim, fontSize = 14.sp)
                            Spacer(Modifier.height(10.dp))
                            PlainField(code, "Код") { code = it.trim() }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            com.apexvpn.client.data.Prefs.previewCode = code
                            previewDialog = false
                            // 3.18.2: сразу скачать свежий файл из пульта (код там мог смениться) и сказать, подошёл ли код
                            val entered = code
                            previewScope.launch {
                                com.apexvpn.client.data.AppConfig.refresh(force = true)
                                com.apexvpn.client.data.Prefs.bannerDismissed = 0
                                com.apexvpn.client.data.AppConfig.touch()
                                val cfg = com.apexvpn.client.data.AppConfig.value
                                viewModel.showMessage(when {
                                    entered.isBlank() -> "Предпросмотр выключен"
                                    cfg.previewer -> "Код подошёл — черновики из пульта видны"
                                    cfg.previewCode.isBlank() -> "В пульте код не задан"
                                    else -> "Код не совпадает с пультом — проверьте раздел «Предпросмотр»"
                                })
                                com.apexvpn.client.vpn.ApexVpnService.reload(context)
                            }
                        }) { Text("Сохранить", color = Apex.Accent) }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { previewDialog = false }) { Text("Отмена", color = Apex.TextDim) } },
                )
            }
            // 3.19.5. Сайт — ссылка из пульта (Ссылки → Сайт), по умолчанию визитка
            GroupDivider()
            ListRow(
                "Сайт APEX CLIENT",
                com.apexvpn.client.data.Links.site().removePrefix("https://").trimEnd('/'),
                leading = { tile(Icons.Rounded.Public) },
                onClick = { openLink(context, com.apexvpn.client.data.Links.site()) },
            ) { Chevron() }
            if (com.apexvpn.client.data.Links.channel().isNotEmpty()) {
                GroupDivider()
                ListRow(
                    "Telegram-канал",
                    "Новости и обновления APEX CLIENT",
                    leading = { tile(Icons.AutoMirrored.Rounded.Send) },
                    onClick = { openLink(context, com.apexvpn.client.data.Links.channel()) },
                ) { Chevron() }
            }
            GroupDivider()
            // 24. Поддержка: отчёт с журналом одной кнопкой
            ListRow(
                "Написать в поддержку",
                "Отчёт с журналом — без ссылки подписки и IP",
                leading = { tile(Icons.Rounded.SupportAgent) },
                onClick = { supportDialog = true },
            ) { Chevron() }
        }
        }
        } // поиск пуст — обычный список настроек
        Spacer(Modifier.height(24.dp))
    }

    // 24. Поддержка: чат из подписки (support-url), копия отчёта или файл
    if (supportDialog) {
        AlertDialog(
            onDismissRequest = { supportDialog = false },
            containerColor = Apex.Dialog,
            title = { Text("Написать в поддержку", color = Apex.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "1. Скопируйте отчёт. 2. Откройте чат поддержки и вставьте его в сообщение. " +
                            "В отчёте нет ссылки подписки, адресов серверов и IP.",
                        color = Apex.TextDim,
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = {
                            runCatching { com.apexvpn.client.data.Diagnostics.copy(context) }
                                .onSuccess { viewModel.showMessage("Отчёт скопирован — вставьте его в чат поддержки") }
                                .onFailure { viewModel.showMessage("Не удалось собрать отчёт") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                    ) { Text("Скопировать отчёт") }
                    // 3.17.9: одной кнопкой — в Telegram, остаётся только выбрать чат поддержки
                    Button(
                        onClick = {
                            runCatching { com.apexvpn.client.data.Diagnostics.sendTelegram(context) }
                                .onSuccess { viewModel.showMessage("Выберите чат ${com.apexvpn.client.data.Links.supportName()} — отчёт уже вставлен") }
                                .onFailure { viewModel.showMessage("Не удалось открыть Telegram") }
                            supportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Apex.SurfaceHigh, contentColor = Apex.Text),
                    ) { Text("✈️ Отправить отчёт в Telegram") }
                    // Чат из подписки (support-url в панели), иначе — наша поддержка @Apexx_support
                    val chat = sub.supportUrl.ifBlank { com.apexvpn.client.data.Links.support() }
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(chat))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { viewModel.showMessage("Не удалось открыть ссылку поддержки") }
                            supportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Apex.SurfaceHigh, contentColor = Apex.Text),
                    ) { Text(if (sub.supportUrl.isBlank()) "Открыть ${com.apexvpn.client.data.Links.supportName()}" else "Открыть чат поддержки") }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            runCatching { com.apexvpn.client.data.Diagnostics.share(context) }
                                .onFailure { viewModel.showMessage("Нет приложения, чтобы отправить файл") }
                            supportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Поделиться файлом отчёта…", color = Apex.TextDim) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { supportDialog = false }) { Text("Закрыть", color = Apex.TextDim) }
            },
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            containerColor = Apex.Dialog,
            title = { Text("Новая ссылка", color = Apex.Text) },
            text = {
                Column {
                    SubscriptionInput(viewModel, buttonText = "Сохранить") {
                        editing = false
                        viewModel.showMessage("Подписка обновлена")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }

    if (coreDialog) {
        AlertDialog(
            onDismissRequest = { coreDialog = false },
            containerColor = Apex.Dialog,
            title = { Text("Ядро", color = Apex.Text) },
            text = {
                Column {
                    CoreMode.entries.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (m == s.core) Apex.AccentSoft else Color.Transparent)
                                .clickable {
                                    // Без Xray выбранный XHTTP/mKCP-сервер не подключить — переходим на автопилот
                                    if (m == CoreMode.SingBox && sub.nodes.any { it.tag == sub.selectedTag && it.xrayOnly }) {
                                        viewModel.select(com.apexvpn.client.data.AUTO_TAG)
                                    }
                                    viewModel.change { copy(core = m) }
                                    coreDialog = false
                                }
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(m.title, color = Apex.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.subtitle, color = Apex.TextDim, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "sing-box ${singBoxVersion()} · Xray ${BuildConfig.XRAY_VERSION}",
                        color = Apex.TextDim,
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { coreDialog = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }

    if (confirmRemove) {
        // 670. Галочка «Удалить и данные»: настройки, журналы, замеры, фото — как «Очистить данные»
        var wipe by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Apex.Dialog,
            title = { Text("Удалить подписку?", color = Apex.Text) },
            text = {
                Column {
                    Text("VPN будет отключён, список серверов очищен.", color = Apex.TextDim)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { wipe = !wipe }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = wipe,
                            onCheckedChange = { wipe = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Apex.Bad),
                        )
                        Column {
                            Text("Удалить и все данные", color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Настройки, свои списки, журналы, замеры, историю и фото фона", color = Apex.TextDim, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removeSubscription(wipeData = wipe)
                }) { Text("Удалить", color = Apex.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun tile(icon: ImageVector, tint: Color = Apex.Text) {
    // 386. Значки «оживают» при открытии: выпрыгивают с лёгким поворотом, у каждого своя задержка
    val smooth = Apex.feel.smooth
    val anim = remember { androidx.compose.animation.core.Animatable(if (smooth) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (anim.value >= 1f) return@LaunchedEffect
        kotlinx.coroutines.delay((kotlin.math.abs(icon.name.hashCode()) % 7) * 45L)
        anim.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 260f))
    }
    IconTile(Apex.SurfaceHigh) {
        Icon(
            icon, null, tint = tint,
            modifier = Modifier.graphicsLayer {
                val v = anim.value
                scaleX = 0.4f + 0.6f * v
                scaleY = 0.4f + 0.6f * v
                rotationZ = (1f - v) * -35f
                alpha = v.coerceIn(0f, 1f)
            },
        )
    }
}

/** 387. Пасхалка: 7 быстрых нажатий на логотип открывают секретную тему «Синтвейв». */
@Composable
private fun SecretLogo(viewModel: MainViewModel) {
    var taps by remember { mutableStateOf(0) }
    var last by remember { mutableStateOf(0L) }
    val wobble = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.foundation.layout.Box(
        Modifier
            .graphicsLayer { rotationZ = wobble.value; scaleX = 1f + kotlin.math.abs(wobble.value) / 120f; scaleY = scaleX }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) {
                val now = System.currentTimeMillis()
                taps = if (now - last < 1500) taps + 1 else 1
                last = now
                scope.launch {
                    wobble.snapTo(if (taps % 2 == 0) 14f else -14f)
                    wobble.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 700f))
                }
                when {
                    taps >= 7 -> {
                        taps = 0
                        val first = !viewModel.settings.value.secretUnlocked
                        ThemeReveal.run {
                            viewModel.change(reload = false) { copy(secretUnlocked = true, design = com.apexvpn.client.data.Design.Synthwave) }
                        }
                        com.apexvpn.client.data.Celebrate.fire(
                            if (first) "🕹 Секретная тема «Синтвейв» открыта! Она теперь есть в Оформлении" else "🕹 Синтвейв включён",
                        )
                    }
                    taps >= 4 -> viewModel.showMessage("Ещё ${7 - taps}…")
                }
            },
    ) { ApexLogo(52.dp) }
}

@Composable
internal fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim)
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Accent)
}

fun singBoxVersion(): String = runCatching { io.nekohasekai.libbox.Libbox.version() }.getOrDefault("")

private fun maskUrl(url: String): String {
    val idx = url.indexOf('/', "https://".length)
    if (idx < 0) return url
    val path = url.substring(idx + 1)
    val masked = if (path.length > 6) path.take(3) + "•••" + path.takeLast(3) else "•••"
    return url.substring(0, idx + 1) + masked
}

/** 262. QR переноса: крупно, на белом фоне, с предупреждением — в нём ссылка подписки. */
@Composable
private fun TransferQrDialog(
    bitmap: android.graphics.Bitmap,
    title: String = "Перенос на новый телефон",
    hint: String = "На новом телефоне: APEX CLIENT → «Сканировать QR-код». Перенесутся настройки, свои списки, избранное и подписка.",
    onDismiss: () -> Unit,
) {
    // Пока QR на экране — максимальная яркость, чтобы камера нового телефона его быстро прочитала
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        val w = activity?.window
        val before = w?.attributes?.screenBrightness
        w?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
        onDispose { w?.let { it.attributes = it.attributes.apply { screenBrightness = before ?: -1f } } }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apex.Dialog,
        title = { Text(title, color = Apex.Text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    bitmap.asImageBitmap(), "QR-код переноса",
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    hint,
                    color = Apex.TextDim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("⚠️ В QR-коде ваша личная подписка — не показывайте его посторонним.", color = Apex.Warn, fontSize = 13.sp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Готово", color = Apex.Accent) } },
    )
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/** «Каждые 6 часов», «Каждый день»… для автообновления баз. */
private fun rulesPeriod(h: Int) = when (h) {
    6 -> "Каждые 6 часов"
    12 -> "Каждые 12 часов"
    168 -> "Раз в неделю"
    else -> "Каждый день"
}

/** 3.8. Пароль для резервной копии (сохранение и восстановление). */
@Composable
private fun BackupPasswordDialog(
    title: String, text: String, confirm: String, dismiss: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.apexvpn.client.ui.theme.Apex.Dialog,
        title = { androidx.compose.material3.Text(title, color = com.apexvpn.client.ui.theme.Apex.Text) },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(text, color = com.apexvpn.client.ui.theme.Apex.TextDim, fontSize = 13.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, singleLine = true,
                    label = { androidx.compose.material3.Text("Пароль") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(pass) }, enabled = pass.length >= 4) {
                androidx.compose.material3.Text(confirm, color = com.apexvpn.client.ui.theme.Apex.Accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text(dismiss, color = com.apexvpn.client.ui.theme.Apex.TextDim) }
        },
    )
}

/** 3.12. Категории настроек: id → подпись. */
private val settingsCategories = listOf(
    "all" to "Все", "conn" to "Подключение", "tools" to "Инструменты", "look" to "Вид и звук",
    "sub" to "Подписка", "updates" to "Обновления", "data" to "Данные", "about" to "О приложении",
)

/** 3.17.9. Поиск по настройкам: название, раздел, страница (если есть) и слова для поиска. */
private data class SearchItem(val title: String, val cat: String, val page: Page?, val keywords: String = "")

private val settingsSearch = listOf(
    SearchItem("Обновить сейчас", "sub", null, ""),
    SearchItem("Изменить ссылку", "sub", null, ""),
    SearchItem("Сканировать QR-код", "sub", null, ""),
    SearchItem("Ссылка-приглашение", "sub", null, "друг пригласить"),
    SearchItem("Удалить подписку", "sub", null, ""),
    SearchItem("Режим автопилота", "conn", Page.Profile, "игры скорость стабильность звонки профиль белые списки lte обход"),
    SearchItem("Автопереключение при сбое", "conn", null, ""),
    SearchItem("DNS", "conn", Page.Dns, "днс doh dot"),
    SearchItem("Маршрутизация", "conn", Page.Advanced, "правила базы сайтов fakedns warp домены"),
    SearchItem("Ядро", "conn", null, "xray sing-box"),
    SearchItem("Без серверов с рекламой", "conn", null, ""),
    SearchItem("IPv6", "conn", null, ""),
    SearchItem("Аварийный выключатель", "conn", null, "kill switch интернет блок"),
    SearchItem("Защита без перерывов", "conn", null, ""),
    SearchItem("Чтобы VPN не отключался", "conn", null, ""),
    SearchItem("Подключать при открытии", "conn", null, ""),
    SearchItem("Не работает?", "conn", Page.SelfCheck, ""),
    SearchItem("Правила Wi-Fi", "conn", Page.Wifi, "вайфай сеть дом"),
    SearchItem("Раздача на компьютер", "conn", Page.Share, ""),
    SearchItem("Пинг", "tools", Page.Ping, "задержка"),
    SearchItem("Тест скорости", "tools", Page.SpeedTest, ""),
    SearchItem("Трафик по приложениям", "tools", Page.AppTraffic, ""),
    SearchItem("Автоматизация", "tools", null, ""),
    SearchItem("Проверка утечек", "tools", Page.LeakTest, "webrtc ipv6 dns утечка"),
    SearchItem("Журнал соединений", "tools", Page.ConnLog, ""),
    SearchItem("Проверить сайт", "tools", Page.SiteCheck, ""),
    SearchItem("Правила «если — то»", "tools", Page.AutoRules, ""),
    SearchItem("Помощник", "tools", Page.Helper, ""),
    SearchItem("Отчёт и история", "tools", Page.Report, "отчёт за месяц неделя история подключений статистика"),
    SearchItem("Сервисы на серверах", "tools", Page.ServiceCheck, ""),
    SearchItem("Приложение не работает?", "tools", Page.AppIssue, ""),
    SearchItem("Обновить базы сайтов", "tools", Page.Advanced, ""),
    SearchItem("Автообновление баз", "tools", Page.Advanced, ""),
    SearchItem("Скорость в уведомлении", "look", null, "шторка"),
    SearchItem("Живое уведомление", "look", null, ""),
    SearchItem("Конец подписки", "look", null, ""),
    SearchItem("Дизайн, тема и иконка", "look", Page.Appearance, "оформление тёмная цвет фон обои"),
    SearchItem("Проверять автоматически", "updates", null, ""),
    SearchItem("Ставить само", "updates", null, "автообновление установка"),
    SearchItem("Бета-версии", "updates", null, "тестовые ранний доступ"),
    SearchItem("Вернуть прошлую версию", "updates", Page.Rollback, ""),
    SearchItem("Обновить часы APEX Watch", "updates", null, ""),
    SearchItem("Скачивать только по Wi-Fi", "updates", null, ""),
    SearchItem("Проверить сейчас", "updates", null, ""),
    SearchItem("Перенести на новый телефон", "data", null, ""),
    SearchItem("Принять с другого телефона", "data", null, ""),
    SearchItem("QR-код подписки", "data", null, ""),
    SearchItem("Сохранить настройки в файл", "data", null, ""),
    SearchItem("Восстановить из файла", "data", null, ""),
    SearchItem("Очистить данные", "data", null, ""),
    SearchItem("Отчёты об ошибках", "about", null, ""),
    SearchItem("Справка", "about", null, ""),
    SearchItem("Что нового", "about", Page.Changelog, ""),
    SearchItem("GitHub", "about", null, ""),
    SearchItem("Поделиться APEX CLIENT", "about", null, ""),
    SearchItem("Сайт APEX CLIENT", "about", null, "сайт визитка"),
    SearchItem("Telegram-канал", "about", null, ""),
    SearchItem("Написать в поддержку", "about", null, "отчёт помощь ошибка telegram"),
    SearchItem("Блокировка рекламы", "mode", null, "реклама трекеры hagezi"),
    SearchItem("Защита от мошенников", "mode", null, "фишинг вирусы"),
    SearchItem("Банки не увидят VPN", "mode", null, "банк сбер"),
    SearchItem("Скрыть VPN от приложений", "mode", Page.HideApps, "исключения скрыть"),
    SearchItem("Приложения через VPN", "mode", Page.Apps, "выбрать приложения"),
    SearchItem("Справка", "about", Page.Help, "помощь как пользоваться"),
)
