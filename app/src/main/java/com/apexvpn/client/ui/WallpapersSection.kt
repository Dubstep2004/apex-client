package com.apexvpn.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    fun select(id: String) = viewModel.change(reload = false) { copy(shieldBackdrop = Wallpapers.key(id), backdropStamp = System.currentTimeMillis()) }
    fun get(item: Wallpapers.Live) = scope.launch {
        val err = Wallpapers.download(context, item)
        if (err == null) { select(item.id); viewModel.showMessage("«${item.title}» скачано и стоит на главном экране") } else viewModel.showMessage(err)
    }

    // 3.19.9. Избранное — живые обои и картинки вместе
    if (favs.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Избранное · ${favs.size}", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(favs.size) { i ->
                val f = favs[i]
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

    Spacer(Modifier.height(14.dp))
    Text("Живые обои из интернета", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    if (catalog.isEmpty()) {
        Caption("Загружаем каталог… Если не появляется — проверьте интернет.")
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(catalog.size) { i ->
                val item = catalog[i]
                val have = remember(rev, item.id) { Wallpapers.isDownloaded(context, item.id) }
                val on = s.shieldBackdrop == Wallpapers.key(item.id)
                val p = progress[item.id]
                val fav = favs.any { it.type == "live" && it.id == item.id }
                WallTile(item.title, if (have) "" else if (item.sizeMb > 0) "%.1f МБ".format(item.sizeMb) else "", item.thumb, on, p, !have, fav, { star(Wallpapers.favOf(item)) }) {
                    when {
                        p != null -> Unit
                        have -> viewModel.change(reload = false) { copy(shieldBackdrop = if (on) "none" else Wallpapers.key(item.id)) }
                        else -> get(item)
                    }
                }
            }
        }
        Caption("Нажмите — обои скачаются (2–6 МБ) и встанут на главный экран. Работают без интернета.")
    }
    Spacer(Modifier.height(8.dp))
    Group {
        ListRow("Видео по ссылке", "Прямая ссылка https://…/файл.mp4, до 150 МБ", onClick = { askLink = true }) { Chevron() }
        downloaded.forEach { d ->
            GroupDivider()
            val on = s.shieldBackdrop == Wallpapers.key(d.id)
            ListRow(d.title, (if (on) "На главном экране · " else "") + "%.1f МБ".format(d.sizeMb), onClick = { select(d.id) }) {
                TextButton(onClick = { toDelete = d }) { Text("Удалить", color = Apex.Bad) }
            }
        }
    }
    if (downloaded.isNotEmpty()) Caption("Скачанные обои хранятся в телефоне. «Удалить» освобождает место, скачать снова можно в любой момент.")

    // Обои-картинки: поиск, подборки, скачанные
    Spacer(Modifier.height(14.dp))
    Text("Обои-картинки из интернета", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
    var topic by rememberSaveable { mutableStateOf(Wallpapers.photoTopics.first().id) }
    var query by rememberSaveable { mutableStateOf("") }
    var searched by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(
        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Поиск: аниме, игра, персонаж…") },
        trailingIcon = { TextButton(onClick = { searched = query.trim(); page = 0 }, enabled = query.isNotBlank()) { Text("Найти") } },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searched = query.trim(); page = 0 }),
    )
    Spacer(Modifier.height(6.dp))
    CategoryChips(Wallpapers.photoTopics.map { it.id to it.title }, if (searched.isBlank()) topic else "") { topic = it; searched = ""; query = ""; page = 0 }
    val photos by produceState<List<Wallpapers.Photo>?>(null, topic, searched, page) {
        value = null
        value = if (searched.isNotBlank()) Wallpapers.search(searched, page) else Wallpapers.photos(topic, page)
    }
    Spacer(Modifier.height(6.dp))
    when {
        photos == null -> Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Apex.Accent) }
        photos!!.isEmpty() -> Caption(if (searched.isNotBlank()) "По запросу «$searched» ничего не нашлось. Попробуйте по-английски, например «naruto» или «genshin»." else "Не удалось загрузить — проверьте интернет.")
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val list = photos!!
            items(list.size) { i ->
                val ph = list[i]
                WallTile(ph.title, "", ph.thumb, false, if (busy == ph.full) -1f else null, false, favs.any { it.type == "pic" && it.id == ph.full }, { star(Wallpapers.favOf(ph)) }) {
                    if (busy != null) return@WallTile
                    busy = ph.full
                    scope.launch {
                        val ok = Wallpapers.setPhoto(context, ph)
                        busy = null
                        if (ok) {
                            viewModel.change(reload = false) { copy(shieldBackdrop = "photo", backdropStamp = System.currentTimeMillis()) }
                            viewModel.showMessage("Обои на главном экране" + if (ph.author.isNotBlank()) " · ${ph.author}" else "")
                        } else viewModel.showMessage("Не удалось скачать картинку")
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
    Caption("Аниме, игры и другие темы — с wallhaven.cc (только безопасные картинки), природа — лучшие фото Wikimedia Commons. Права на картинки у их авторов: ставьте их для себя.")

    val pics = remember(rev) { Wallpapers.pics(context) }
    if (pics.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Скачанные картинки · ${pics.size}", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pics.size) { i ->
                val f = pics[i]
                val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, f.path) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                    }
                }
                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp, 132.dp).clip(RoundedCornerShape(16.dp)).background(Apex.Surface)
                            .border(2.dp, Apex.Outline, RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Wallpapers.usePic(context, f) }
                                    if (ok) viewModel.change(reload = false) { copy(shieldBackdrop = "photo", backdropStamp = System.currentTimeMillis()) }
                                }
                            },
                    ) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                    TextButton(onClick = { Wallpapers.deletePic(context, f) }) { Text("Удалить", color = Apex.Bad, fontSize = 12.sp) }
                }
            }
        }
    }

    // 3.20. Эффект поверх любых обоев и «Живое фото»
    Spacer(Modifier.height(14.dp))
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
    val autoItems = listOf("off" to "Выключено", "fav" to "Из избранного", "live" to "Живые по кругу") + Wallpapers.photoTopics.map { "pics:" + it.id to it.title }
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
            s.wallAuto == "off" -> "Каждый день новые обои на главном экране: картинка выбранной темы или следующие скачанные живые обои."
            s.wallAuto == "live" -> "Каждый день — следующие из скачанных живых обоев. Интернет не нужен."
            s.wallAuto == "fav" -> if (favs.isEmpty()) "Избранное пусто — нажмите ☆ на обоях, которые нравятся." else "Каждый день — следующие из избранного (${favs.size})."
            else -> "Каждый день новая картинка темы «${autoItems.firstOrNull { it.first == s.wallAuto }?.second.orEmpty()}». Меняется при первом открытии за день, нужен интернет."
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
private fun WallTile(title: String, sub: String, thumb: String, on: Boolean, progress: Float?, cloud: Boolean, fav: Boolean? = null, onFav: () -> Unit = {}, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, thumb) { value = Wallpapers.bitmap(thumb, context)?.asImageBitmap() }
    Column(Modifier.width(88.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
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
