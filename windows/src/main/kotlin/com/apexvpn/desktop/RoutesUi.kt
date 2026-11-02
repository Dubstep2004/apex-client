package com.apexvpn.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Windows 1.1: свои списки и сайты, сайты стран напрямую, базы сайтов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutesScreen() {
    val s by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    // Индекс открытого на изменение списка: -1 — никакой, size — новый
    var editing by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
        Text("Маршруты", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (s.routeMode == RouteMode.Smart) "Умный режим: российские сайты напрямую, остальное через VPN. Ниже — свои исключения."
            else "Режим «Весь трафик»: всё через VPN, кроме сайтов, отмеченных «напрямую».",
            color = C.Dim, fontSize = 13.sp,
        )

        Section("Сайты стран напрямую")
        Card(Modifier.fillMaxWidth()) {
            Text("Домены страны и её крупные сайты идут мимо VPN — быстрее и без «доступно только в стране»", color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectCountries.all.forEach { c ->
                    val on = c.id in s.directCountries
                    Chip("${c.code} ${c.title}", on) {
                        Store.update { copy(directCountries = if (on) directCountries - c.id else directCountries + c.id) }
                        Engine.reload()
                    }
                }
            }
        }

        Section("Свои списки через VPN")
        Card(Modifier.fillMaxWidth()) {
            if (s.customLists.isEmpty() && editing < 0) Text("Списков пока нет. Создайте свой или возьмите шаблон.", color = C.Dim, fontSize = 13.sp)
            s.customLists.forEachIndexed { i, l ->
                if (editing == i) {
                    ListEditor(l, onSave = { nl ->
                        Store.update { copy(customLists = customLists.toMutableList().also { it[i] = nl }) }
                        editing = -1; Engine.reload()
                    }, onDelete = {
                        Store.update { copy(customLists = customLists.filterIndexed { k, _ -> k != i }) }
                        editing = -1; Engine.reload()
                    }, onCancel = { editing = -1 })
                } else {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = i }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = C.Text, fontWeight = FontWeight.SemiBold)
                            Text("${l.items.size} адр. · " + l.items.take(3).joinToString(", ") + if (l.items.size > 3) "…" else "", color = C.Dim, fontSize = 12.sp)
                        }
                        Switch(l.enabled, { v ->
                            Store.update { copy(customLists = customLists.toMutableList().also { it[i] = l.copy(enabled = v) }) }
                            Engine.reload()
                        }, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = Color.White, uncheckedTrackColor = C.SurfaceHigh, uncheckedThumbColor = C.Dim, uncheckedBorderColor = C.Outline))
                    }
                }
            }
            if (editing == s.customLists.size) {
                ListEditor(CustomList("", emptyList()), onSave = { nl ->
                    Store.update { copy(customLists = customLists + nl) }; editing = -1; Engine.reload()
                }, onDelete = { editing = -1 }, onCancel = { editing = -1 })
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("+ Новый список") { editing = s.customLists.size }
                ListTemplates.all.filter { t -> s.customLists.none { it.name == t.name } }.forEach { t ->
                    SecondaryButton("+ ${t.name}") { Store.update { copy(customLists = customLists + t) }; Engine.reload() }
                }
            }
        }

        Section("Свои сайты")
        Card(Modifier.fillMaxWidth()) {
            DomainsEditor("Всегда через VPN", s.customDomains) { v -> Store.update { copy(customDomains = v) }; Engine.reload() }
            Spacer(Modifier.height(14.dp))
            DomainsEditor("Всегда напрямую", s.directDomains) { v -> Store.update { copy(directDomains = v) }; Engine.reload() }
            Spacer(Modifier.height(8.dp))
            Text("По одному на строку: example.com, full:точный.адрес, keyword:слово, IP или подсеть, udp:27000-27100", color = C.Dim, fontSize = 12.sp)
        }

        Section("Базы сайтов")
        val info by Rules.info.collectAsState()
        Card(Modifier.fillMaxWidth()) {
            Text("Российские сайты и IP, реклама, 18+ и букмекеры — для умного режима, блокировки рекламы", color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru")) }
            Text(
                listOfNotNull(
                    if (info.sites > 0) "Записей: ${"%,d".format(info.sites).replace(',', ' ')}" else null,
                    if (info.updatedAt > 0) "обновлены ${fmt.format(Date(info.updatedAt))}" else "встроенные в программу",
                ).joinToString(" · "),
                color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
            if (info.status.isNotBlank()) Text(info.status, color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Toggle("Обновлять автоматически", "Качаются только изменения, из GitHub или зеркала", s.rulesAuto) { Store.update { copy(rulesAuto = it) } }
            if (s.rulesAuto) {
                val opts = listOf(6, 12, 24, 168)
                Segmented(listOf("6 ч", "12 ч", "Раз в день", "Раз в неделю"), opts.indexOf(s.rulesHours).coerceAtLeast(0)) { i -> Store.update { copy(rulesHours = opts[i]) } }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(if (info.busy) "Проверяю…" else "Обновить сейчас") {
                if (!info.busy) scope.launch { if (Rules.update()) Engine.reload() }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ListEditor(list: CustomList, onSave: (CustomList) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var name by remember(list) { mutableStateOf(list.name) }
    var text by remember(list) { mutableStateOf(list.items.joinToString("\n")) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.SurfaceHigh).padding(12.dp)) {
        Field(name, "Название списка", Modifier.fillMaxWidth()) { name = it }
        Spacer(Modifier.height(8.dp))
        MultiField(text, "example.com\nkeyword:game\nudp:27000-27100") { text = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val items = parseRules(text)
            PrimaryButton("Сохранить", enabled = name.isNotBlank() && items.isNotEmpty()) { onSave(list.copy(name = name.trim(), items = items)) }
            SecondaryButton("Отмена") { onCancel() }
            if (list.name.isNotEmpty()) SecondaryButton("Удалить", C.Bad) { onDelete() }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DomainsEditor(title: String, value: List<String>, onSave: (List<String>) -> Unit) {
    var text by remember(value) { mutableStateOf(value.joinToString("\n")) }
    val changed = parseRules(text) != value
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$title · ${value.size}", color = C.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (changed) SecondaryButton("Сохранить", C.Accent) { onSave(parseRules(text)) }
    }
    Spacer(Modifier.height(6.dp))
    MultiField(text, "example.com") { text = it }
}

@Composable
private fun MultiField(value: String, hint: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 90.dp).clip(RoundedCornerShape(12.dp)).background(C.Base)
            .border(1.dp, C.Outline, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = C.Dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        BasicTextField(
            value, onChange,
            textStyle = TextStyle(color = C.Text, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(C.Accent), modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
        )
    }
}
