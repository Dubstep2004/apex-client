package com.apexvpn.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.Settings
import com.apexvpn.client.data.Wallpapers
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.launch

/**
 * 3.19.7. «Оформление» → обои из интернета: живые обои 1080×1920 из каталога (скачать, выбрать, удалить),
 * видео по своей ссылке и фото-обои Wikimedia Commons по темам.
 */
@Composable
internal fun NetWallpapers(viewModel: MainViewModel, s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog by Wallpapers.catalog.collectAsState()
    val progress by Wallpapers.progress.collectAsState()
    val rev by Wallpapers.rev.collectAsState()
    LaunchedEffect(Unit) { Wallpapers.loadCatalog(context) }
    val downloaded = remember(rev) { Wallpapers.downloaded(context) }
    var askLink by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Wallpapers.Live?>(null) }

    val favs by Wallpapers.favs.collectAsState()
    LaunchedEffect(Unit) { Wallpapers.loadFavs() }
    fun star(f: Wallpapers.Fav) = viewModel.showMessage(if (Wallpapers.toggleFav(f)) "Добавлено в избранное" else "Убрано из избранного")
    var favBusy by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Wallpapers.Live?>(null) }
    var askClear by remember { mutableStateOf(false) }

    fun select(id: String) = viewModel.change(reload = false) { copy(shieldBackdrop = Wallpapers.key(id), backdropStamp = System.currentTimeMillis()) }
    fun get(item: Wallpapers.Live) = scope.launch {
        val err = Wallpapers.download(context, item)
        if (err == null) { select(item.id); viewModel.showMessage("«${item.title}» скачано и стоит на главном экране") } else viewModel.showMessage(err)
    }

    // 3.20.7. Всё про живые обои из интернета — вкладками, по одной ленте за раз
    Spacer(Modifier.height(14.dp))
    Text("Живые обои из интернета", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    var tab by rememberSaveable { mutableStateOf("cat") }
    CategoryChips(
        listOf("cat" to "Каталог", "topics" to "По темам", "dl" to "Скачанные · ${downloaded.size}", "fav" to "Избранное · ${favs.size}"),
        tab,
    ) { tab = it }
    Spacer(Modifier.height(8.dp))

    // 3.19.9. Избранное — живые обои и картинки вместе
    if (tab == "fav" && favs.isEmpty()) Caption("Пока пусто. Нажмите ☆ на обоях в «Каталоге» или «По темам» — они появятся здесь.")
    if (tab == "fav" && favs.isNotEmpty()) {
        val favList = favs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(favList.size, key = { favList[it].type + favList[it].id }) { i ->
                val f = favList[i]
                val on = if (f.type == "live") s.shieldBackdrop == Wallpapers.key(f.id) else false
                WallTile(f.title, if (f.type == "live") "живые" else "картинка", f.thumb, on, if (favBusy == f.type + f.id) -1f else progress[f.id], false, true, { star(f) }) {
                    if (favBusy != null) return@WallTile
                    favBusy = f.type + f.id
                    scope.launch {
                        val err = Wallpapers.applyFav(context, f)
                        favBusy = null
                        viewModel.showMessage(err ?: "«${f.title}» на главном экране")
                    }
                }
            }
        }
        Caption("☆ на обоях — добавить в избранное, ★ — убрать.")
    }

    if (tab == "cat" && catalog.isEmpty()) {
        Caption("Загружаем каталог… Если не появляется — проверьте интернет.")
    } else if (tab == "cat") {
        // Разделы каталога (из пульта): Аниме-стиль, Город, Природа…
        val cats = remember(catalog) { listOf("" to "Все") + catalog.map { it.cat }.filter { it.isNotBlank() }.distinct().map { it to Wallpapers.catTitle(it) } }
        var cat by rememberSaveable { mutableStateOf("") }
        if (cats.size > 2) { CategoryChips(cats, cat) { cat = it }; Spacer(Modifier.height(8.dp)) }
        val catList = catalog.filter { cat.isBlank() || it.cat == cat }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(catList.size) { i ->
                val item = catList[i]
                val have = remember(rev, item.id) { Wallpapers.isDownloaded(context, item.id) }
                val on = s.shieldBackdrop == Wallpapers.key(item.id)
                val p = progress[item.id]
                val fav = favs.any { it.type == "live" && it.id == item.id }
                WallTile(item.title, if (have) "" else if (item.sizeMb > 0) "%.1f МБ".format(item.sizeMb) else "", item.thumb, on, p, !have, fav, { star(Wallpapers.favOf(item)) }, { preview = item }) {
                    when {
                        p != null -> Unit
                        have -> viewModel.change(reload = false) { copy(shieldBackdrop = if (on) "none" else Wallpapers.key(item.id)) }
                        else -> get(item)
                    }
                }
            }
        }
        Caption("Нажмите — обои скачаются (2–6 МБ) и встанут на главный экран. Долгое нажатие — посмотреть перед скачиванием.")
    }
    if (tab == "dl" && downloaded.isNotEmpty()) {
        // 3.20.8. Сколько места занимают обои и «Удалить всё»
        val total = downloaded.sumOf { it.sizeMb }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Занято: %.1f МБ · %d шт.".format(total, downloaded.size), color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { askClear = true }) { Text("Удалить всё", color = Apex.Bad) }
        }
    }
    if (tab == "dl") Group {
        ListRow("Видео по ссылке", "Прямая ссылка https://…/файл.mp4, до 150 МБ", onClick = { askLink = true }) { Chevron() }
        downloaded.forEach { d ->
            GroupDivider()
            val on = s.shieldBackdrop == Wallpapers.key(d.id)
            ListRow(d.title, (if (on) "На главном экране · " else "") + "%.1f МБ".format(d.sizeMb), onClick = { select(d.id) }) {
                TextButton(onClick = { toDelete = d }) { Text("Удалить", color = Apex.Bad) }
            }
        }
    }
    if (tab == "dl" && downloaded.isNotEmpty()) Caption("Скачанные обои хранятся в телефоне. «Удалить» освобождает место, скачать снова можно в любой момент.")

    // 3.20. Живые обои из интернета по темам и поиску
    val pxKey by Wallpapers.pexelsKey.collectAsState()
    if (tab != "topics") Unit else if (pxKey.isBlank()) {
        Caption("Поиск живых обоев по темам (аниме, игры, неон, космос…) скоро появится. Пока есть каталог выше и «Видео по ссылке».")
    } else {
        val topicList by Wallpapers.topics.collectAsState()
        var topic by rememberSaveable { mutableStateOf("") }
        // Тема по умолчанию — первая из пульта; если выбранную убрали в пульте — тоже первая
        if (topicList.none { it.id == topic }) topic = topicList.firstOrNull()?.id.orEmpty()
        var query by rememberSaveable { mutableStateOf("") }
        var searched by rememberSaveable { mutableStateOf("") }
        var page by rememberSaveable { mutableIntStateOf(0) }
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск: неон, аниме, дождь, космос…") },
            trailingIcon = { TextButton(onClick = { searched = query.trim(); page = 0 }, enabled = query.isNotBlank()) { Text("Найти") } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searched = query.trim(); page = 0 }),
        )
        Spacer(Modifier.height(6.dp))
        CategoryChips(topicList.map { it.id to it.title }, if (searched.isBlank()) topic else "") { topic = it; searched = ""; query = ""; page = 0 }
        val vids by produceState<List<Wallpapers.Live>?>(null, topic, searched, page, pxKey) {
            value = null
            value = if (searched.isNotBlank()) Wallpapers.liveSearch(searched, page) else Wallpapers.liveTopic(topic, page)
        }
        Spacer(Modifier.height(6.dp))
        // 3.20.3: снимок списка. LazyRow строит элементы позже (при раскладке) — если к тому времени
        // начался новый поиск, vids уже null, и vids!! ронял приложение (NullPointerException)
        val vidList = vids
        when {
            vidList == null -> Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Apex.Accent) }
            vidList.isEmpty() -> Caption(if (searched.isNotBlank()) "По запросу «$searched» ничего не нашлось. Попробуйте по-английски: «neon», «anime», «rain»." else "Не удалось загрузить — проверьте интернет.")
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val list = vidList
                items(list.size) { i ->
                    val v = list[i]
                    val have = remember(rev, v.id) { Wallpapers.isDownloaded(context, v.id) }
                    val on = s.shieldBackdrop == Wallpapers.key(v.id)
                    WallTile("", "", v.thumb, on, progress[v.id], !have, favs.any { it.type == "live" && it.id == v.id }, { star(Wallpapers.favOf(v)) }, { preview = v }) {
                        when {
                            progress[v.id] != null -> Unit
                            have -> viewModel.change(reload = false) { copy(shieldBackdrop = if (on) "none" else Wallpapers.key(v.id)) }
                            else -> scope.launch {
                                val err = Wallpapers.download(context, v)
                                if (err == null) { select(v.id); viewModel.showMessage("Живые обои на главном экране · ${v.credit}") } else viewModel.showMessage(err)
                            }
                        }
                    }
                }
                item {
                    Box(
                        Modifier.size(88.dp, 132.dp).clip(RoundedCornerShape(16.dp)).background(Apex.Surface).clickable { page++ },
                        contentAlignment = Alignment.Center,
                    ) { Text("Ещё", color = Apex.Accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Caption("Видео из интернета: Pixabay, Pexels, Wikimedia Commons или GIPHY (Powered by GIPHY). Нажмите — скачаются (2–10 МБ) и встанут на главный экран; ☆ — в избранное. Удалить — во вкладке «Скачанные».")
    }

    // 3.20. Эффект поверх любых обоев и «Живое фото»
    Spacer(Modifier.height(14.dp))
    SectionLabel("Настройка обоев")
    Text("Эффект поверх обоев", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    CategoryChips(com.apexvpn.client.ui.theme.WallFx.all, s.wallFx) { fx -> viewModel.change(reload = false) { copy(wallFx = fx) } }
    Caption("Снег, дождь, сакура и другие — поверх картинки, живых обоев или дизайна. В «Плавном режиме» эффект не показывается.")
    if (s.shieldBackdrop == "photo") {
        Spacer(Modifier.height(6.dp))
        Group {
            ListRow("Живое фото", "Картинка медленно приближается и плывёт — как живые обои", onClick = { viewModel.change(reload = false) { copy(photoMotion = !s.photoMotion) } }) {
                androidx.compose.material3.Switch(checked = s.photoMotion, onCheckedChange = { on -> viewModel.change(reload = false) { copy(photoMotion = on) } })
            }
        }
    }

    // 3.19.8. Обои дня
    Spacer(Modifier.height(14.dp))
    Text("Обои дня", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    val autoItems = listOf("off" to "Выключено", "fav" to "Из избранного", "live" to "Скачанные по кругу") +
        (if (Wallpapers.pexelsKey.collectAsState().value.isNotBlank()) Wallpapers.topics.collectAsState().value.map { "px:" + it.id to it.title } else emptyList())
    CategoryChips(autoItems, s.wallAuto) { mode ->
        viewModel.change(reload = false) { copy(wallAuto = mode) }
        if (mode != "off") scope.launch {
            if (mode == "live" && Wallpapers.downloaded(context).isEmpty()) { viewModel.showMessage("Сначала скачайте живые обои — они будут меняться по кругу"); return@launch }
            if (mode == "fav" && favs.isEmpty()) { viewModel.showMessage("Избранное пусто — нажмите ☆ на обоях, которые нравятся"); return@launch }
            viewModel.showMessage("Ставим обои на сегодня…")
            // Настройка сохраняется асинхронно — даём ей примениться
            kotlinx.coroutines.delay(300)
            if (!Wallpapers.autoTick(context, force = true)) viewModel.showMessage("Не удалось сменить обои — проверьте интернет")
        }
    }
    Caption(
        when {
            s.wallAuto == "off" -> "Каждый день новые живые обои на главном экране: из избранного, из скачанных или новые по теме."
            s.wallAuto == "live" -> "Каждый день — следующие из скачанных живых обоев. Интернет не нужен."
            s.wallAuto == "fav" -> if (favs.isEmpty()) "Избранное пусто — нажмите ☆ на обоях, которые нравятся." else "Каждый день — следующие из избранного (${favs.size})."
            s.wallAuto.startsWith("pics:") -> "Картинки заменены живыми обоями — выберите тему заново."
            else -> "Каждый день новые живые обои темы «${autoItems.firstOrNull { it.first == s.wallAuto }?.second.orEmpty()}» (2–10 МБ в день). Вчерашние удаляются, если не в избранном."
        },
    )
    if (s.wallAuto != "off") {
        TextButton(onClick = { scope.launch { if (!Wallpapers.autoTick(context, force = true)) viewModel.showMessage("Не удалось сменить обои — проверьте интернет") } }) { Text("Сменить сейчас", color = Apex.Accent) }
    }

    if (askLink) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askLink = false },
            title = { Text("Видео по ссылке") },
            text = {
                Column {
                    Text("Прямая ссылка на файл .mp4 (например, со страницы бесплатных видео — «Скачать» → скопировать ссылку).", fontSize = 14.sp, color = Apex.TextDim)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(url, { url = it }, singleLine = true, placeholder = { Text("https://…/video.mp4") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askLink = false
                    scope.launch {
                        viewModel.showMessage("Скачиваем видео…")
                        val (err, id) = Wallpapers.downloadLink(context, url)
                        if (err == null) { select(id); viewModel.showMessage("Видео скачано и стоит на главном экране") } else viewModel.showMessage(err)
                    }
                }) { Text("Скачать") }
            },
            dismissButton = { TextButton(onClick = { askLink = false }) { Text("Отмена") } },
        )
    }
    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Удалить все скачанные обои?") },
            text = { Text("Освободится %.1f МБ. Избранное останется — скачать снова можно в любой момент.".format(downloaded.sumOf { it.sizeMb })) },
            confirmButton = {
                TextButton(onClick = {
                    if (Wallpapers.idOf(s.shieldBackdrop) != null) viewModel.change(reload = false) { copy(shieldBackdrop = "none") }
                    downloaded.forEach { Wallpapers.delete(context, it.id) }
                    askClear = false; viewModel.showMessage("Скачанные обои удалены")
                }) { Text("Удалить всё", color = Apex.Bad) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Отмена") } },
        )
    }
    preview?.let { v ->
        WallPreview(v, onDismiss = { preview = null }) {
            preview = null
            if (Wallpapers.isDownloaded(context, v.id)) select(v.id) else get(v)
        }
    }
    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${d.title}»?") },
            text = { Text("Файл удалится из телефона. Скачать снова можно в любой момент.") },
            confirmButton = {
                TextButton(onClick = {
                    if (s.shieldBackdrop == Wallpapers.key(d.id)) viewModel.change(reload = false) { copy(shieldBackdrop = "none") }
                    Wallpapers.delete(context, d.id); toDelete = null
                }) { Text("Удалить", color = Apex.Bad) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

/** Плитка обоев: миниатюра 88×132, отметка выбора, загрузка (progress -1 — без процентов). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun WallTile(title: String, sub: String, thumb: String, on: Boolean, progress: Float?, cloud: Boolean, fav: Boolean? = null, onFav: () -> Unit = {}, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, thumb) { value = Wallpapers.bitmap(thumb, context)?.asImageBitmap() }
    Column(Modifier.width(88.dp).clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLong), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp, 132.dp).clip(RoundedCornerShape(16.dp)).background(Apex.Surface)
                .border(2.dp, if (on) Apex.Accent else Apex.Outline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            // 3.19.9. Звёздочка «в избранное»
            if (fav != null) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onFav),
                contentAlignment = Alignment.Center,
            ) { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Убрать из избранного" else "В избранное", tint = if (fav) Color(0xFFFFC94D) else Color.White, modifier = Modifier.size(18.dp)) }
            when {
                progress != null -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    if (progress < 0f || progress == 0f) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    else CircularProgressIndicator(progress = { progress }, color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
                on -> Icon(Icons.Rounded.Check, null, tint = Color.White)
                cloud -> Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CloudDownload, "Скачать", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = if (on) Apex.Text else Apex.TextDim, fontSize = 12.sp, maxLines = 1)
        if (sub.isNotEmpty()) Text(sub, color = Apex.TextDim, fontSize = 11.sp, maxLines = 1)
    }
}

/** 3.20.8. Посмотреть живые обои перед скачиванием: видео играет прямо из интернета, без звука, по кругу. */
@Composable
private fun WallPreview(v: Wallpapers.Live, onDismiss: () -> Unit, onSet: () -> Unit) {
    val context = LocalContext.current
    val local = Wallpapers.file(context, v.id).takeIf { it.exists() && it.length() > 0 }
    val src = local?.absolutePath ?: Wallpapers.sources(v.url).firstOrNull().orEmpty()
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(v.title.ifBlank { "Живые обои" }) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(200.dp, 356.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (src.isBlank() || failed) Text("Не удалось показать", color = Color.White, fontSize = 13.sp)
                    else androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                                setOnErrorListener { _, _, _ -> failed = true; true }
                                if (local != null) setVideoPath(src) else setVideoURI(android.net.Uri.parse(src))
                            }
                        },
                        onRelease = { it.stopPlayback() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (v.credit.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(v.credit, color = Apex.TextDim, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onSet) { Text(if (local != null) "Поставить" else "Скачать и поставить", color = Apex.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
