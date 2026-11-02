package com.apexvpn.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

// ─── Белые списки у провайдера ────────────────────────────────────────

/**
 * Если провайдер пускает только «разрешённые» сайты (российские открываются, остальные нет) —
 * автопилот сам переходит на серверы «LTE-обход». Проверка — до подключения, мимо VPN.
 */
object WhiteList {
    @Volatile var active = false
        private set
    private val _state = MutableStateFlow(false)
    val state = _state.asStateFlow()

    suspend fun check() = withContext(Dispatchers.IO) {
        val ru = Net.text("https://ya.ru", null, 4000) != null || Net.text("https://vk.com", null, 4000) != null
        val foreign = Net.trace(null).isNotEmpty() || Net.text("https://www.google.com/generate_204", null, 4000) != null
        active = ru && !foreign
        _state.value = active
    }
}

// ─── Таймер отключения ────────────────────────────────────────────────

object AutoOff {
    private val _at = MutableStateFlow(0L)
    /** Когда VPN отключится сам (мс), 0 — таймер выключен */
    val at = _at.asStateFlow()
    fun set(minutes: Int) { _at.value = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L }
    fun due() = _at.value in 1..System.currentTimeMillis()
    fun clear() { _at.value = 0L }
}

// ─── Фото страны дня (Википедия) ──────────────────────────────────────

object CountryPhoto {
    data class Photo(val image: ImageBitmap, val caption: String)

    private val cities = mapOf(
        "ru" to "Москва", "ua" to "Киев", "by" to "Минск", "kz" to "Алма-Ата", "uz" to "Ташкент", "kg" to "Бишкек",
        "am" to "Ереван", "ge" to "Тбилиси", "az" to "Баку", "tr" to "Стамбул", "de" to "Берлин", "nl" to "Амстердам",
        "fi" to "Хельсинки", "se" to "Стокгольм", "no" to "Осло", "dk" to "Копенгаген", "ee" to "Таллин", "lv" to "Рига",
        "lt" to "Вильнюс", "pl" to "Варшава", "cz" to "Прага", "at" to "Вена", "ch" to "Цюрих", "fr" to "Париж",
        "gb" to "Лондон", "ie" to "Дублин", "es" to "Мадрид", "pt" to "Лиссабон", "it" to "Рим", "ro" to "Бухарест",
        "bg" to "София", "md" to "Кишинёв", "hu" to "Будапешт", "rs" to "Белград", "gr" to "Афины", "il" to "Тель-Авив",
        "ae" to "Дубай", "in" to "Мумбаи", "sg" to "Сингапур", "hk" to "Гонконг", "jp" to "Токио", "kr" to "Сеул",
        "cn" to "Шанхай", "tw" to "Тайбэй", "vn" to "Хошимин", "th" to "Бангкок", "id" to "Джакарта", "my" to "Куала-Лумпур",
        "au" to "Сидней", "nz" to "Окленд", "us" to "Нью-Йорк", "ca" to "Торонто", "mx" to "Мехико", "br" to "Сан-Паулу",
        "ar" to "Буэнос-Айрес", "cl" to "Сантьяго", "za" to "Йоханнесбург", "eg" to "Каир", "ng" to "Лагос",
        "is" to "Рейкьявик", "be" to "Брюссель", "cy" to "Никосия", "mn" to "Улан-Батор", "tj" to "Душанбе", "tm" to "Ашхабад", "sa" to "Эр-Рияд",
    )

    fun supports(code: String?) = code != null && code in cities
    private val dir get() = File(Paths.data, "country-photo").apply { mkdirs() }
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    suspend fun load(code: String): Photo? = withContext(Dispatchers.IO) {
        val city = cities[code] ?: return@withContext null
        val img = File(dir, "$code-${today()}.jpg")
        if (img.length() > 0) runCatching { ImageIO.read(img)?.toComposeImageBitmap() }.getOrNull()?.let { return@withContext Photo(it, "$city · фото: Википедия") }
        runCatching {
            val url = pick(city) ?: return@runCatching null
            val bytes = Net.open(url, null, 10_000).inputStream.use { it.readBytes() }
            dir.listFiles()?.filter { it.name.startsWith("$code-") }?.forEach { it.delete() }
            img.writeBytes(bytes)
            ImageIO.read(img)?.toComposeImageBitmap()?.let { Photo(it, "$city · фото: Википедия") }
        }.getOrNull()
    }

    /** Каждый день — следующее фото из статьи о городе (гербы, флаги и карты пропускаем). */
    private fun pick(city: String): String? {
        val title = URLEncoder.encode(city.replace(' ', '_'), "UTF-8")
        val json = Net.text("https://ru.wikipedia.org/api/rest_v1/page/media-list/$title", null, 10_000) ?: return null
        val items = JSONObject(json).optJSONArray("items") ?: return null
        val skip = listOf("flag", "coat", "герб", "флаг", "map", "карта", "logo", "locator", "emblem", "seal", "svg", "signature")
        val photos = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .filter { it.optString("type") == "image" }
            .filter { o -> val t = o.optString("title").lowercase(); skip.none { it in t } && (t.endsWith(".jpg") || t.endsWith(".jpeg")) }
            .mapNotNull { o -> o.optJSONArray("srcset")?.let { s -> (0 until s.length()).mapNotNull { s.optJSONObject(it) }.lastOrNull()?.optString("src") } }
            .filter { it.isNotBlank() }
        if (photos.isEmpty()) return null
        val src = photos[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % photos.size]
        return if (src.startsWith("//")) "https:$src" else src
    }
}

/** Фото дня страны сервера за всем окном (на автопилоте — ваша страна). */
@Composable
fun CountryPhotoBackdrop() {
    val s by Store.settings.collectAsState()
    if (s.liveBg != "country") return
    val sub by Subscription.state.collectAsState()
    val autoNow by Engine.autoNow.collectAsState()
    val node = sub.nodes.find { it.tag == s.selected } ?: sub.nodes.find { it.tag == autoNow }
    val code = node?.country?.lowercase()?.takeIf { CountryPhoto.supports(it) } ?: userCountry()
    val photo by produceState<CountryPhoto.Photo?>(null, code) { value = CountryPhoto.load(code) }
    val p = photo ?: return
    Box(Modifier.fillMaxSize()) {
        Image(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Base.copy(alpha = 0.75f), C.Base.copy(alpha = 0.35f), C.Base.copy(alpha = 0.85f)))))
        Text(p.caption, color = C.Dim, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

// ─── Ссылка-приглашение ───────────────────────────────────────────────

object Invite {
    /** Страница приглашения (GitHub Pages); подписка — после #, сервер её не видит. */
    fun link(subscriptionUrl: String): String =
        "https://${AppInfo.REPO.substringBefore('/').lowercase()}.github.io/${AppInfo.REPO.substringAfter('/')}/invite/#" +
            URLEncoder.encode(subscriptionUrl, "UTF-8").replace("+", "%20")

    fun copy(): Boolean {
        val url = Store.value.subscriptionUrl.ifBlank { return false }
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(link(url)), null)
        return true
    }
}

// ─── Страницы ─────────────────────────────────────────────────────────

/** Настройки пинга — как на Android. */
@Composable
fun PingSettingsPage() {
    val s by Store.settings.collectAsState()
    var url by remember { mutableStateOf(s.pingUrl) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Как мерить пинг")
        Group {
            listOf(
                Triple("tcp", "TCP до сервера", "Время соединения с сервером — быстро, без VPN"),
                Triple("proxy", "Через сервер (URL-тест)", "Запрос к сайту через этот сервер — нужен включённый VPN"),
                Triple("icmp", "ICMP", "Классический ping (некоторые серверы не отвечают)"),
            ).forEachIndexed { i, (id, t, sub) ->
                if (i > 0) GroupDivider()
                ListRow(t, sub, onClick = { Store.update { copy(pingType = id) } }) { Check(s.pingType == id) }
            }
        }
        Section("Тайм-аут · ${s.pingTimeout} с")
        Card(Modifier.fillMaxWidth()) {
            val opts = listOf(2, 3, 5, 10, 15)
            Segmented(opts.map { "$it с" }, opts.indexOf(s.pingTimeout).coerceAtLeast(0)) { i -> Store.update { copy(pingTimeout = opts[i]) } }
        }
        Section("Адрес URL-теста (и для автопилота)")
        Card(Modifier.fillMaxWidth()) {
            Field(url, "https://www.gstatic.com/generate_204", Modifier.fillMaxWidth()) { url = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Сохранить", enabled = url.isBlank() || url.startsWith("http")) { Store.update { copy(pingUrl = url.trim()) }; Engine.reload() }
                if (s.pingUrl.isNotBlank()) SecondaryButton("По умолчанию") { url = ""; Store.update { copy(pingUrl = "") }; Engine.reload() }
            }
        }
    }
}

/** «Скрыть VPN» для программ (режим TUN): выбранные программы идут мимо VPN. */
@Composable
fun ExcludeAppsPage() {
    val s by Store.settings.collectAsState()
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("В режиме TUN эти программы работают мимо VPN и не видят его — как «Скрыть VPN» на телефоне. В режиме «Системный прокси» программы сами решают, идти ли через прокси.",
            color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Card(Modifier.fillMaxWidth()) {
            Field(name, "Имя программы, например Telegram.exe", Modifier.fillMaxWidth()) { name = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val n = name.trim().let { if (it.isNotEmpty() && !it.lowercase().endsWith(".exe")) "$it.exe" else it }
                PrimaryButton("Добавить", enabled = n.length > 4) { Store.update { copy(excludeApps = (excludeApps + n).distinct()) }; name = ""; Engine.reload() }
                SecondaryButton("Выбрать файл…") {
                    val d = java.awt.FileDialog(null as java.awt.Frame?, "Выберите программу", java.awt.FileDialog.LOAD)
                    d.file = "*.exe"; d.isVisible = true
                    d.file?.let { f -> Store.update { copy(excludeApps = (excludeApps + f).distinct()) }; Engine.reload() }
                }
            }
        }
        if (s.excludeApps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Group {
                s.excludeApps.forEachIndexed { i, a ->
                    if (i > 0) GroupDivider()
                    ListRow(a, "VPN скрыт") {
                        Icon(Icons.Rounded.Close, "Убрать", tint = C.Dim, modifier = Modifier.clip(CircleShape).clickable { Store.update { copy(excludeApps = excludeApps - a) }; Engine.reload() }.padding(6.dp))
                    }
                }
            }
        }
    }
}

/** Справка — короткие инструкции, как на Android. */
@Composable
fun HelpPage() {
    val topics = listOf(
        "Как подключиться" to listOf("Добавьте ссылку подписки https://sub.knownhosts.ru/…", "Нажмите на щит (или кнопку питания)", "Значок у часов показывает, подключено ли"),
        "Прокси или TUN" to listOf("Системный прокси — без прав администратора: браузеры и большинство программ", "TUN — весь компьютер и игры; нужен запуск от администратора", "Сменить — на Главной или в меню значка у часов"),
        "Режимы автопилота" to listOf("🎮 Игры — ровный пинг без потерь", "🚀 Скорость — самые быстрые по замеру (при включённом VPN)", "🛡 Надёжный и 📞 Звонки — серверы без пропаданий", "🔋 Экономный — редкие проверки"),
        "Что идёт через VPN" to listOf("Умный — российские сайты напрямую, остальное через VPN", "Весь трафик — всё через VPN", "Только выбранные — сервисы и свои списки", "Свои сайты и списки — в «Маршрутах»"),
        "Не работает" to listOf("Откройте «Не работает?» — проверка по шагам", "Попробуйте другой сервер или автопилот", "Напишите в @Apexx_support и приложите файлы из папки журналов"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        topics.forEach { (title, steps) ->
            Section(title)
            Card(Modifier.fillMaxWidth()) {
                steps.forEachIndexed { k, st ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(C.AccentSoft), contentAlignment = Alignment.Center) { Text("${k + 1}", color = C.AccentText, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Text(st, color = C.Text, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** «Что нового» — из windows/CHANGELOG.md, вложенного в программу. */
@Composable
fun WhatsNewPage() {
    val text = remember { runCatching { Thread.currentThread().contextClassLoader.getResourceAsStream("CHANGELOG.md")!!.bufferedReader().readText() }.getOrDefault("") }
    val versions = remember(text) {
        text.split("\n## ").drop(1).map { block -> block.substringBefore('\n').trim() to block.substringAfter('\n').lines().filter { it.startsWith("- ") }.map { it.removePrefix("- ") } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (versions.isEmpty()) Text("Список изменений — на GitHub в релизах windows-v…", color = C.Dim)
        versions.forEach { (head, items) ->
            Section(head)
            Card(Modifier.fillMaxWidth()) {
                items.forEach { Text("• $it", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Режимы автопилота и таймер — на главном экране ──────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeModes(modifier: Modifier = Modifier) {
    val s by Store.settings.collectAsState()
    val sub by Subscription.state.collectAsState()
    val status by Engine.status.collectAsState()
    val modeStatus by AutoModes.status.collectAsState()
    val quality by AutoModes.quality.collectAsState()
    val offAt by AutoOff.at.collectAsState()
    val wl by WhiteList.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Card(modifier) {
        Text("Режим автопилота", color = C.Dim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AutoMode.entries.forEach { m ->
                Chip("${m.emoji} ${m.title}", m == s.autoMode && s.selected == "auto") {
                    Store.update { copy(autoMode = m, selected = "auto") }
                    if (m.measured) scope.launch { AutoModes.measure(m); Engine.reload() } else Engine.reload()
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val fit = remember(quality, s.autoMode, sub) { AutoModes.allowed(s.autoMode, sub.nodes).size }
        Text(
            modeStatus ?: when {
                wl && s.autoWhitelist -> "Белые списки у провайдера — автопилот на серверах «LTE-обход»"
                s.selected != "auto" -> "Выбран сервер вручную — нажмите режим, чтобы вернуть автопилот"
                s.autoMode.measured && fit > 0 -> "${s.autoMode.subtitle} · подходящих серверов: $fit"
                else -> s.autoMode.subtitle
            },
            color = if (modeStatus != null || wl) C.Accent else C.Dim, fontSize = 12.sp,
        )
        if (status == VpnStatus.On) {
            Spacer(Modifier.height(12.dp))
            val left = if (offAt > 0) ((offAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1 else 0
            Text(if (offAt > 0) "Таймер: отключится через $left мин" else "Таймер отключения", color = C.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val opts = listOf(0, 30, 60, 120, 240)
            Segmented(opts.map { if (it == 0) "Выкл" else if (it < 60) "$it мин" else "${it / 60} ч" }, if (offAt == 0L) 0 else -1) { i -> AutoOff.set(opts[i]) }
        }
    }
}

/** Живой фон: как в дизайне или свой — звёзды, сияние, неон, шестерёнки, фото страны дня. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LiveBgPicker() {
    val s by Store.settings.collectAsState()
    val opts = listOf("design" to "Как в дизайне", "stars" to "Звёзды", "aurora" to "Сияние", "grid" to "Неон", "gears" to "Шестерёнки", "country" to "Фото страны")
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (id, t) -> Chip(t, s.liveBg == id) { Store.update { copy(liveBg = id) } } }
    }
    if (s.liveBg == "country") Text("Каждый день новое фото города страны сервера (на автопилоте — вашей страны). Фото из Википедии.", color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

/** Свой DNS-over-HTTPS. */
@Composable
fun CustomDnsField() {
    val s by Store.settings.collectAsState()
    var v by remember { mutableStateOf(s.customDns) }
    val ok = v.trim().startsWith("https://") && v.trim().length > 12
    Column(Modifier.fillMaxWidth()) {
        Field(v, "https://dns.example.com/dns-query", Modifier.fillMaxWidth()) { v = it }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Сохранить", enabled = ok) { Store.update { copy(customDns = v.trim()) }; Engine.reload() }
            Spacer(Modifier.width(10.dp))
            Text(if (s.customDns.isBlank()) "Пока не задан — используется Cloudflare" else "Сейчас: ${s.customDns}", color = C.Dim, fontSize = 12.sp)
        }
    }
}
