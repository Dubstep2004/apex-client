package com.apexvpn.client.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.ConnLog
import com.apexvpn.client.ui.theme.Apex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 604. Журнал соединений: сайт, путь (VPN / напрямую / заблокировано), сервер и приложение. */
@Composable
internal fun ConnLogPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val entries by ConnLog.entries.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    PageScaffold("Журнал соединений", onBack) {
        Group {
            ListRow(
                "Вести журнал",
                if (s.connLog) "Последние 500 соединений за сутки, только на этом телефоне" else "Выключен — ничего не записывается",
                onClick = { viewModel.change(reload = false) { copy(connLog = !connLog) } },
            ) { ApexSwitch(s.connLog, { v -> viewModel.change(reload = false) { copy(connLog = v) } }) }
        }
        Caption("Видно, какие сайты шли через VPN, а какие напрямую или были заблокированы. Журнал никуда не отправляется и сам стирается через сутки.")
        if (!s.connLog && entries.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            return@PageScaffold
        }
        Spacer(Modifier.height(12.dp))
        PlainField(query, "Поиск: сайт или приложение") { query = it }
        IpToggle()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Все", "vpn" to "Через VPN", "direct" to "Напрямую", "blocked" to "Заблокировано").forEach { (id, title) ->
                val n = entries.count { matches(it, id) }
                val on = filter == id
                Text(
                    "$title $n",
                    color = if (on) Apex.OnAccent else Apex.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Apex.Accent else Apex.Surface)
                        .clickable { filter = id }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        val shown = entries.filter { matches(it, filter) }
            .filter { query.isBlank() || it.host.contains(query.trim(), true) || appName(context, it.app).contains(query.trim(), true) }
            .take(300)
        Spacer(Modifier.height(10.dp))
        if (shown.isEmpty()) {
            Caption(if (entries.isEmpty()) "Пока пусто — включите VPN и откройте пару сайтов." else "Ничего не найдено")
        } else {
            Group {
                shown.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val color = when (e.route) {
                            ConnLog.Route.Vpn -> Apex.Accent
                            ConnLog.Route.Direct -> Apex.Good
                            ConnLog.Route.Blocked -> Apex.Bad
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(IpMask.apply(e.host), color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val route = when (e.route) {
                                ConnLog.Route.Vpn -> "VPN" + (e.server.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                                ConnLog.Route.Direct -> "Напрямую"
                                ConnLog.Route.Blocked -> "Заблокировано"
                            }
                            val app = appName(context, e.app)
                            Text(listOfNotNull(route, app.takeIf { it.isNotEmpty() }).joinToString(" · "), color = Apex.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(fmt.format(Date(e.time)), color = Apex.TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { ConnLog.clear(); viewModel.showMessage("Журнал очищен") }) { Text("Очистить журнал", color = Apex.Bad) }
        Spacer(Modifier.height(24.dp))
    }
}

private fun matches(e: ConnLog.Entry, filter: String) = when (filter) {
    "vpn" -> e.route == ConnLog.Route.Vpn
    "direct" -> e.route == ConnLog.Route.Direct
    "blocked" -> e.route == ConnLog.Route.Blocked
    else -> true
}

private val appNames = HashMap<String, String>()

/** Название приложения по пакету (кэш), иначе сам пакет. */
private fun appName(context: android.content.Context, pkg: String): String {
    if (pkg.isEmpty()) return ""
    return appNames.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
