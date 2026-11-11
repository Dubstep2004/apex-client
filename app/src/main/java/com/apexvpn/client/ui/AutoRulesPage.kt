package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.AutoModes
import com.apexvpn.client.data.AutoRule
import com.apexvpn.client.data.AutoRulesEngine
import com.apexvpn.client.data.KidsSchedule
import com.apexvpn.client.data.RouteMode
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.ui.theme.Apex

/**
 * Варианты на выбор. 3.17.10: переносятся на новую строку (раньше уезжали за край экрана),
 * выбранный — залит цветом, чтобы было видно, что выбрано.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Pills(items: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val on = selected == id
            Box(
                Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Apex.Accent else Apex.SurfaceHigh)
                    .clickable { onPick(id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(title, color = if (on) Apex.OnAccent else Apex.Text, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** 3.11. Правила «если — то». */
@Composable
internal fun AutoRulesPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val appList by viewModel.apps.collectAsStateWithLifecycle()

    var trigger by remember { mutableStateOf("app") }
    var app by remember { mutableStateOf<Pair<String, String>?>(null) } // пакет, название
    var from by remember { mutableIntStateOf(23 * 60) }
    var to by remember { mutableIntStateOf(7 * 60) }
    var action by remember { mutableStateOf("mode") }
    var value by remember { mutableStateOf("games") }
    var pickApp by remember { mutableStateOf(false) }
    var usageOk by remember { mutableStateOf(RecentApps.granted(ctx)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        usageOk = RecentApps.granted(ctx)
        onPauseOrDispose { }
    }

    fun save(list: List<AutoRule>) = viewModel.change(reload = false) { copy(autoRules = list) }

    PageScaffold("Правила «если — то»", onBack) {
        Caption("Приложение само меняет настройки, когда что-то происходит: «если открыт Steam → режим „Игры“», «если идёт зарядка → обновить базы сайтов». Условие прошло — настройка возвращается как была. Работает, пока VPN включён.")

        if (s.autoRules.isNotEmpty()) {
            SectionLabel("Мои правила")
            Group {
                val active = AutoRulesEngine.active
                s.autoRules.forEachIndexed { i, r ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        "Если: ${AutoRulesEngine.triggerTitle(r)}",
                        "То: ${AutoRulesEngine.actionTitle(r)}" + if (r.id in active) " · действует сейчас" else "",
                    ) {
                        ApexSwitch(r.enabled, { v -> save(s.autoRules.map { if (it.id == r.id) it.copy(enabled = v) else it }) })
                        Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim,
                            modifier = Modifier.clip(CircleShape).clickable { save(s.autoRules.filter { it.id != r.id }) }.padding(6.dp))
                    }
                }
            }
        }

        SectionLabel("Готовые")
        Group {
            AutoRulesEngine.presets.forEachIndexed { i, (_, rule) ->
                if (i > 0) GroupDivider()
                val has = s.autoRules.any { it.trigger == rule.trigger && it.arg == rule.arg && it.action == rule.action && it.value == rule.value }
                // 3.17.10: «если» и «то» на двух строках — длинные названия больше не обрезаются
                ListRow(
                    "Если: ${AutoRulesEngine.triggerTitle(rule)}",
                    "То: ${AutoRulesEngine.actionTitle(rule)}",
                    onClick = { if (!has) save(s.autoRules + rule.copy(id = java.util.UUID.randomUUID().toString().take(8))) },
                ) {
                    Tag(if (has) "Добавлено" else "Добавить", outlined = has)
                }
            }
        }

        SectionLabel("Своё правило")
        Group {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                androidx.compose.foundation.layout.Column {
                    Text("Если", color = Apex.Text, fontWeight = FontWeight.SemiBold)
                    Pills(listOf("app" to "Открыто приложение", "charging" to "Идёт зарядка", "mobile" to "Мобильный интернет", "wifi" to "Wi-Fi", "time" to "Время"), trigger) { trigger = it }
                    when (trigger) {
                        "app" -> {
                            if (!usageOk) {
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Apex.Warn.copy(alpha = 0.12f)).padding(12.dp)) {
                                    androidx.compose.foundation.layout.Column {
                                        Text("Нужен доступ к истории использования — так APEX узнаёт, какое приложение открыто.",
                                            color = Apex.Text, fontSize = 14.sp, lineHeight = 19.sp)
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = { RecentApps.openSettings(ctx) }) { Text("Разрешить доступ", color = Apex.Accent, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            // Выбранное приложение — с иконкой, по нажатию можно сменить
                            val chosenIcon = remember(app?.first, appList) {
                                appList?.firstOrNull { it.packageName == app?.first }?.icon
                                    ?.let { d -> runCatching { d.toBitmap(80, 80).asImageBitmap() }.getOrNull() }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(Apex.SurfaceHigh)
                                    .clickable { viewModel.loadApps(); pickApp = true }.padding(12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                if (chosenIcon != null) {
                                    androidx.compose.foundation.Image(chosenIcon, null, Modifier.size(32.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(app?.second ?: "Выбрать приложение…", color = if (app != null) Apex.Text else Apex.Accent,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(if (app != null) "Сменить" else "", color = Apex.TextDim, fontSize = 13.sp)
                            }
                        }
                        "time" -> {
                            TimeStepper("С", from) { from = it }
                            TimeStepper("До", to) { to = it }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("То", color = Apex.Text, fontWeight = FontWeight.SemiBold)
                    Pills(
                        listOf("mode" to "Режим автопилота", "server" to "Сервер", "route" to "Маршрут", "adblock" to "Блокировка рекламы",
                            "rules" to "Обновить базы сайтов", "off" to "Выключить VPN"),
                        action,
                    ) { a ->
                        action = a
                        value = when (a) { "mode" -> "games"; "server" -> AUTO_TAG; "route" -> "all"; "adblock" -> "on"; else -> "" }
                    }
                    when (action) {
                        "mode" -> Pills(AutoModes.Mode.entries.map { it.id to "${it.emoji} ${it.title}" }, value) { value = it }
                        "route" -> Pills(RouteMode.entries.map { it.id to it.title }, value) { value = it }
                        "adblock" -> Pills(listOf("on" to "Включить", "off" to "Выключить"), value) { value = it }
                        "server" -> Pills(
                            listOf(AUTO_TAG to "Автопилот") + sub.nodes.filter { !it.ads }.distinctBy { ServerInfo.title(it.name) }
                                .map { it.tag to listOfNotNull(ServerInfo.flag(it.name), ServerInfo.title(it.name)).joinToString(" ") },
                            value,
                        ) { value = it }
                    }
                    Spacer(Modifier.height(12.dp))
                    val ready = trigger != "app" || app != null
                    Button(
                        onClick = {
                            val arg = when (trigger) { "app" -> app!!.first; "time" -> "${KidsSchedule.hhmm(from)}-${KidsSchedule.hhmm(to)}"; else -> "" }
                            val serverName = if (action == "server" && value != AUTO_TAG)
                                sub.nodes.firstOrNull { it.tag == value }?.name?.let { ServerInfo.title(it) }.orEmpty() else ""
                            val label = if (trigger == "app") app!!.second else ""
                            save(s.autoRules + AutoRule(trigger = trigger, arg = arg, action = action, value = value,
                                label = if (serverName.isNotEmpty()) "$label|$serverName" else label))
                            viewModel.showMessage("Правило добавлено")
                        },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                    ) { Text("Добавить правило", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pickApp) {
        LaunchedEffect(Unit) { viewModel.loadApps() }
        // 3.17.10: поиск, иконки, недавно открытые — сверху
        var q by remember { mutableStateOf("") }
        val recent = remember { if (usageOk) runCatching { RecentApps.recent(ctx) }.getOrDefault(emptyList()) else emptyList() }
        AlertDialog(
            onDismissRequest = { pickApp = false },
            containerColor = Apex.Dialog,
            title = { Text("Какое приложение?", color = Apex.Text) },
            text = {
                androidx.compose.foundation.layout.Column {
                    SearchField(q, "Поиск приложения") { q = it }
                    Spacer(Modifier.height(8.dp))
                    val list = appList
                    if (list == null) Text("Загружаем список…", color = Apex.TextDim)
                    else {
                        val shown = list.filter { it.packageName != ctx.packageName }
                            .filter { q.isBlank() || it.label.contains(q, true) || it.packageName.contains(q, true) }
                            .sortedWith(compareBy<com.apexvpn.client.data.AppInfo> { a -> recent.indexOf(a.packageName).let { if (it < 0) Int.MAX_VALUE else it } }
                                .thenBy { it.label.lowercase() })
                        if (shown.isEmpty()) Text("Ничего не нашлось", color = Apex.TextDim)
                        LazyColumn(Modifier.heightIn(max = 460.dp)) {
                            items(shown, key = { it.packageName }) { a ->
                                val icon = remember(a.packageName) { runCatching { a.icon?.toBitmap(80, 80)?.asImageBitmap() }.getOrNull() }
                                ListRow(
                                    a.label,
                                    if (a.packageName in recent) "Недавно открыто" else null,
                                    leading = { if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(36.dp)) else GlyphTile(a.label.take(1), Apex.Accent) },
                                    onClick = { app = a.packageName to a.label; pickApp = false },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickApp = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }
}
