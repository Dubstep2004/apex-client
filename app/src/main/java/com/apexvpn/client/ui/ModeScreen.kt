package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.BankApps
import com.apexvpn.client.data.RouteMode
import com.apexvpn.client.data.ServiceCatalog
import com.apexvpn.client.ui.theme.Apex

/** «Режим»: как маршрутизировать трафик, какие сервисы пускать через VPN. */
@Composable
fun ModeScreen(viewModel: MainViewModel, onOpenPage: (Page) -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        ScreenTitle("Режим", Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(18.dp))

        RouteMode.entries.forEach { mode ->
            ModeCard(mode, selected = s.mode == mode) { viewModel.change { copy(mode = mode) } }
            Spacer(Modifier.height(10.dp))
        }

        SectionLabel("Через VPN")
        Group {
            ServiceCatalog.all.forEachIndexed { i, item ->
                if (i > 0) GroupDivider()
                val on = item.id in s.services
                ListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    leading = { GlyphTile(item.glyph, Color(item.color)) },
                    onClick = { viewModel.change { copy(services = if (on) services - item.id else services + item.id) } },
                ) {
                    ApexSwitch(on, { v -> viewModel.change { copy(services = if (v) services + item.id else services - item.id) } })
                }
            }
        }
        Caption("Выключенные идут напрямую, без VPN")

        // Свои списки «Через VPN»: создать, включить/выключить, изменить, удалить
        SectionLabel("Свои списки", trailing = s.customLists.size.takeIf { it > 0 }?.toString())
        CustomListsSection(s.customLists, viewModel)

        SectionLabel("Свои сайты")
        SiteLists(s.customDomains, s.directDomains, viewModel)

        // 602. Сайты стран напрямую
        SectionLabel("Сайты стран напрямую", trailing = s.directCountries.size.takeIf { it > 0 }?.toString())
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            com.apexvpn.client.data.DirectCountries.all.chunked(2).forEach { pair ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    pair.forEach { c ->
                        val on = c.id in s.directCountries
                        androidx.compose.material3.Text(
                            "${c.flag}  ${c.title}" + if (on) "  ✓" else "",
                            color = if (on) Apex.OnAccent else Apex.Text,
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(if (on) Apex.Accent else Apex.Surface)
                                .clickable {
                                    viewModel.change { copy(directCountries = if (on) directCountries - c.id else directCountries + c.id) }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Caption("Сайты выбранных стран (.kz, .by…) и их крупные сервисы — банки, маркетплейсы — открываются напрямую, без VPN: быстрее и без «доступно только в стране».")

        Spacer(Modifier.height(22.dp))
        Group {
            ListRow(
                title = "Блокировка рекламы",
                subtitle = "Реклама и трекеры не загружаются",
                leading = { IconTile(Color(0xFFE5484D)) { Icon(Icons.Rounded.Block, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(adBlock = !adBlock) } },
            ) { ApexSwitch(s.adBlock, { v -> viewModel.change { copy(adBlock = v) } }) }
            GroupDivider()
            // 3.17.9: мошенники, фишинг, вирусы — отдельно от рекламы
            ListRow(
                title = "Защита от мошенников",
                subtitle = "Фишинг, поддельные банки и вирусные сайты не откроются",
                leading = { IconTile(Color(0xFFF59E0B)) { Icon(Icons.Rounded.Shield, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(blockThreats = !blockThreats) } },
            ) { ApexSwitch(s.blockThreats, { v -> viewModel.change { copy(blockThreats = v) } }) }
            GroupDivider()
            ListRow(
                title = "Банки не увидят VPN",
                subtitle = BankApps.SUMMARY,
                leading = { IconTile(Color(0xFF1F8A5B)) { Icon(Icons.Rounded.AccountBalance, null, tint = Color.White) } },
                onClick = { viewModel.change { copy(hideFromBanks = !hideFromBanks) } },
            ) { ApexSwitch(s.hideFromBanks, { v -> viewModel.change { copy(hideFromBanks = v) } }) }
            GroupDivider()
            // 349. Свой список приложений, которые не увидят VPN
            ListRow(
                title = "Скрыть VPN от приложений",
                subtitle = if (s.hiddenApps.isEmpty()) "Выберите приложения — они пойдут мимо VPN и не увидят его" else "Скрыт от ${s.hiddenApps.size} прил.",
                leading = { IconTile(Color(0xFF6B5BD6)) { Icon(Icons.Rounded.VisibilityOff, null, tint = Color.White) } },
                onClick = { onOpenPage(Page.HideApps) },
            ) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim) }
            GroupDivider()
            ListRow(
                title = "Проверить сайт",
                subtitle = "Узнать, пойдёт ли сайт через VPN",
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Search, null, tint = Apex.Cyan) } },
                onClick = { onOpenPage(Page.SiteCheck) },
            )
        }
        Caption("Режим меняется сразу, без переподключения.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeCard(mode: RouteMode, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Apex.Radius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Apex.SelectedCard else Apex.Surface)
            .border(1.5.dp, if (selected) Apex.Accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.title, color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(mode.subtitle, color = Apex.TextDim, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Apex.Accent else Apex.TextDim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Apex.Accent), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Apex.OnAccent))
            }
        }
    }
}

/** Свои сайты: «всегда через VPN» и «всегда напрямую» (8). */
@Composable
private fun SiteLists(viaVpn: List<String>, direct: List<String>, viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val toDirect = tab == 1
    val submit = {
        error = viewModel.addDomain(text, toDirect)
        if (error == null) text = ""
    }
    Segmented(listOf("Через VPN", "Напрямую"), tab) { tab = it; error = null }
    Spacer(Modifier.height(10.dp))
    Group {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, if (error != null) Apex.Bad else Apex.Outline, RoundedCornerShape(20.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(if (toDirect) "например, kinopoisk.ru" else "например, twitch.tv", color = Apex.TextDim, fontSize = 17.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    textStyle = TextStyle(color = Apex.Text, fontSize = 17.sp),
                    cursorBrush = SolidColor(Apex.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Apex.Accent).clickable { submit() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Добавить", tint = Apex.OnAccent) }
        }
        error?.let {
            Text(it, color = Apex.Bad, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        }
        (if (toDirect) direct else viaVpn).forEach { d ->
            GroupDivider()
            ListRow(
                title = d,
                leading = {
                    IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Language, null, tint = if (toDirect) Apex.Good else Apex.Cyan) }
                },
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { viewModel.removeDomain(d) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Удалить", tint = Apex.TextDim) }
            }
        }
    }
    Caption(
        if (toDirect) {
            "Эти сайты всегда открываются напрямую, без VPN — например, российские сервисы, которые не попали в базу."
        } else {
            "Добавленные сайты всегда идут через VPN, вместе с их поддоменами."
        },
    )
}

private val listColors = listOf(0xFF7B6CFF, 0xFF22D3EE, 0xFF34D399, 0xFFF59E0B, 0xFFEC4899, 0xFFEF4444, 0xFF3B82F6, 0xFF8B5CF6)

/** Свои списки «Через VPN»: название + домены/IP по одному на строку. */
@Composable
private fun CustomListsSection(lists: List<com.apexvpn.client.data.CustomList>, viewModel: MainViewModel) {
    var editing by remember { mutableStateOf<com.apexvpn.client.data.CustomList?>(null) }
    var picking by remember { mutableStateOf(false) }
    Group {
        lists.forEachIndexed { i, l ->
            if (i > 0) GroupDivider()
            ListRow(
                title = l.name.ifBlank { "Без названия" },
                subtitle = if (l.items.isEmpty()) "Пустой — нажмите, чтобы добавить сайты"
                else l.items.take(3).joinToString(", ") + if (l.items.size > 3) " и ещё ${l.items.size - 3}" else "",
                leading = { GlyphTile(l.name.trim().firstOrNull()?.uppercase() ?: "★", Color(l.color)) },
                onClick = { editing = l },
            ) {
                ApexSwitch(l.enabled, { v ->
                    viewModel.change { copy(customLists = customLists.map { if (it.id == l.id) it.copy(enabled = v) else it }) }
                })
            }
        }
        if (lists.isNotEmpty()) GroupDivider()
        ListRow(
            title = "Новый список",
            subtitle = "Готовые шаблоны: Игры, Нейросети, Работа, Учёба… или свой",
            leading = { IconTile(Apex.AccentSoft) { Icon(Icons.Rounded.Add, null, tint = Apex.AccentText) } },
            onClick = { picking = true },
        )
    }
    Caption("Нажмите на список, чтобы изменить или удалить его. Можно писать домены (example.com), IP и подсети, keyword:слово.")

    // Выбор: готовый шаблон или пустой список
    if (picking) {
        TemplatePicker(
            taken = lists.map { it.name }.toSet(),
            onPick = { t ->
                picking = false
                editing = com.apexvpn.client.data.CustomList(
                    id = java.util.UUID.randomUUID().toString(),
                    name = t?.name ?: "",
                    items = t?.items ?: emptyList(),
                    color = t?.color ?: listColors[lists.size % listColors.size],
                )
            },
            onAddAll = { chosen ->
                picking = false
                val add = chosen.map {
                    com.apexvpn.client.data.CustomList(java.util.UUID.randomUUID().toString(), it.name, it.items, true, it.color)
                }
                viewModel.change { copy(customLists = customLists + add) }
                viewModel.showMessage(if (add.size == 1) "Список «${add[0].name}» добавлен" else "Добавлено списков: ${add.size}")
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { l ->
        CustomListEditor(
            list = l,
            isNew = lists.none { it.id == l.id },
            viewModel = viewModel,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CustomListEditor(
    list: com.apexvpn.client.data.CustomList,
    isNew: Boolean,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(list.id) { mutableStateOf(list.name) }
    var text by rememberSaveable(list.id) { mutableStateOf(list.items.joinToString("\n")) }
    var color by rememberSaveable(list.id) { mutableStateOf(list.color) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apex.Dialog,
        title = { Text(if (isNew) "Новый список" else "Список", color = Apex.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Название", color = Apex.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                EditorField(name, "Например, Работа", singleLine = true) { name = it; error = null }
                Spacer(Modifier.height(12.dp))
                Text("Сайты — по одному на строку", color = Apex.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                EditorField(text, "example.com\nnews.example.org\n10.8.0.0/16", singleLine = false) { text = it; error = null }
                Spacer(Modifier.height(12.dp))
                Text("Цвет", color = Apex.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row {
                    listColors.forEach { c ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(3.dp, if (c == color) Apex.Text else Color.Transparent, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Apex.Bad, fontSize = 13.sp)
                }
                if (!isNew) {
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Close, null, tint = Apex.Bad)
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить список", color = Apex.Bad)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val items = text.lines().map { it.trim().removePrefix("https://").removePrefix("http://").trimEnd('/') }
                    .filter { it.isNotEmpty() }.distinct()
                val bad = items.firstNotNullOfOrNull { viewModel.validateUserRule(it) }
                when {
                    name.isBlank() -> error = "Введите название"
                    items.isEmpty() -> error = "Добавьте хотя бы один сайт"
                    bad != null -> error = bad
                    else -> {
                        val updated = list.copy(name = name.trim(), items = items, color = color)
                        viewModel.change {
                            copy(customLists = if (customLists.any { it.id == list.id }) customLists.map { if (it.id == list.id) updated else it } else customLists + updated)
                        }
                        viewModel.showMessage(if (isNew) "Список «${updated.name}» добавлен" else "Список сохранён")
                        onDismiss()
                    }
                }
            }) { Text("Сохранить", color = Apex.Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена", color = Apex.TextDim) } },
    )
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Apex.Dialog,
            title = { Text("Удалить «${list.name}»?", color = Apex.Text) },
            text = { Text("Сайты из этого списка пойдут по общим правилам.", color = Apex.TextDim) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.change { copy(customLists = customLists.filterNot { it.id == list.id }) }
                    viewModel.showMessage("Список удалён")
                    confirmDelete = false
                    onDismiss()
                }) { Text("Удалить", color = Apex.Bad) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun EditorField(value: String, hint: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Apex.Surface)
            .border(1.dp, Apex.Outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(if (singleLine) Modifier else Modifier.height(140.dp)),
    ) {
        if (value.isEmpty()) Text(hint, color = Apex.TextDim, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = Apex.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Apex.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (singleLine) KeyboardType.Text else KeyboardType.Uri),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Готовые шаблоны: нажатие — открыть для правки, «Добавить» — сразу в списки. */
@Composable
private fun TemplatePicker(
    taken: Set<String>,
    onPick: (com.apexvpn.client.data.ListTemplate?) -> Unit,
    onAddAll: (List<com.apexvpn.client.data.ListTemplate>) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates = com.apexvpn.client.data.ListTemplates.all
    var selected by remember { mutableStateOf(setOf<String>()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Apex.Dialog,
        title = { Text("Новый список", color = Apex.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Отметьте готовые шаблоны или нажмите на шаблон, чтобы сначала изменить его.", color = Apex.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                templates.forEach { t ->
                    val already = t.name in taken
                    val on = t.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !already) { onPick(t) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(t.color).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(t.glyph, fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = if (already) Apex.TextDim else Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (already) "Уже добавлен" else "${t.items.size} сайтов · ${t.subtitle}",
                                color = Apex.TextDim, fontSize = 12.sp, maxLines = 2,
                            )
                        }
                        if (!already) {
                            androidx.compose.material3.Checkbox(
                                checked = on,
                                onCheckedChange = { v -> selected = if (v) selected + t.id else selected - t.id },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Apex.Accent),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(null) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Apex.AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = Apex.AccentText)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Пустой список", color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onAddAll(templates.filter { it.id in selected }) },
                enabled = selected.isNotEmpty(),
            ) { Text(if (selected.isEmpty()) "Добавить" else "Добавить (${selected.size})", color = if (selected.isEmpty()) Apex.TextDim else Apex.Accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена", color = Apex.TextDim) } },
    )
}
