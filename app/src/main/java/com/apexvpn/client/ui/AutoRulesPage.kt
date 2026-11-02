package com.apexvpn.client.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.AutoModes
import com.apexvpn.client.data.AutoRule
import com.apexvpn.client.data.AutoRulesEngine
import com.apexvpn.client.data.KidsSchedule
import com.apexvpn.client.data.RouteMode
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.ui.theme.Apex

/** Ряд «таблеток» на выбор (листается вбок). */
@Composable
private fun Pills(items: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, title) ->
            Box(Modifier.clip(RoundedCornerShape(50)).clickable { onPick(id) }) { Tag(title, outlined = selected != id) }
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
            AutoRulesEngine.presets.forEachIndexed { i, (title, rule) ->
                if (i > 0) GroupDivider()
                val has = s.autoRules.any { it.trigger == rule.trigger && it.arg == rule.arg && it.action == rule.action && it.value == rule.value }
                ListRow(title, onClick = { if (!has) save(s.autoRules + rule.copy(id = java.util.UUID.randomUUID().toString().take(8))) }) {
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
                                Text("Нужен доступ к истории использования — так APEX узнаёт, какое приложение открыто.", color = Apex.TextDim)
                                Box(Modifier.clip(RoundedCornerShape(50)).clickable { RecentApps.openSettings(ctx) }) { Tag("Разрешить") }
                                Spacer(Modifier.height(6.dp))
                            }
                            Box(Modifier.clip(RoundedCornerShape(50)).clickable { viewModel.loadApps(); pickApp = true }) {
                                Tag(app?.second?.let { "Приложение: $it" } ?: "Выбрать приложение")
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
        AlertDialog(
            onDismissRequest = { pickApp = false },
            containerColor = Apex.Dialog,
            title = { Text("Какое приложение?", color = Apex.Text) },
            text = {
                val list = appList
                if (list == null) Text("Загружаем список…", color = Apex.TextDim)
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list.filter { it.packageName != ctx.packageName }, key = { it.packageName }) { a ->
                        ListRow(a.label, a.packageName, onClick = { app = a.packageName to a.label; pickApp = false })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickApp = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }
}
