package com.apexvpn.client.data

import android.content.Context
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 3.19.7. Обои из интернета.
 * Живые обои — каталог docs/config/wallpapers.json (меняется из пульта, вкладка «Обои»), видео 1080×1920.
 * Скачиваются по нажатию в папку приложения, ненужные удаляются. Можно добавить видео по своей ссылке.
 * Фото-обои — лучшие фото Wikimedia Commons (свободные лицензии) по темам.
 */
object Wallpapers {
    data class Live(val id: String, val title: String, val url: String, val thumb: String, val sizeMb: Double = 0.0, val credit: String = "")
    data class Photo(val title: String, val thumb: String, val full: String, val author: String)

    /** Где лежат файлы каталога: сайт, затем зеркала (если github.io недоступен). */
    private val bases get() = listOf(
        "https://dubstep2004.github.io/apex-client/",
        "https://cdn.jsdelivr.net/gh/${BuildConfig.GITHUB_REPO}@main/docs/",
        "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/docs/",
    )
    private const val UA_SITE = "https://dubstep2004.github.io"
    private const val MAX_BYTES = 150L * 1024 * 1024

    private val _catalog = MutableStateFlow<List<Live>>(emptyList())
    val catalog: StateFlow<List<Live>> = _catalog.asStateFlow()

    /** Сколько скачано, 0..1, по id; нет ключа — не качается. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    /** Меняется после скачивания или удаления — чтобы экран пересчитал список. */
    private val _rev = MutableStateFlow(0)
    val rev: StateFlow<Int> = _rev.asStateFlow()

    fun dir(context: Context) = File(context.filesDir, "live").apply { mkdirs() }
    fun file(context: Context, id: String) = File(dir(context), "$id.mp4")
    private fun meta(context: Context, id: String) = File(dir(context), "$id.json")

    /** Значение shieldBackdrop для скачанных обоев. */
    fun key(id: String) = "video:net:$id"
    fun idOf(backdrop: String) = backdrop.removePrefix("video:net:").takeIf { backdrop.startsWith("video:net:") }

    /** Разбор каталога (чистая функция — проверяется автотестами). */
    fun parseCatalog(text: String): List<Live> = runCatching {
        val a = JSONObject(text).optJSONArray("live") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val id = o.optString("id").trim().takeIf { it.matches(Regex("[a-z0-9_-]{1,40}")) } ?: return@mapNotNull null
            val url = o.optString("url").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (o.optBoolean("hidden", false)) return@mapNotNull null
            Live(id, o.optString("title").ifBlank { id }.take(30), url, o.optString("thumb").trim(), o.optDouble("sizeMb", 0.0))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    /** Относительный путь каталога — на сайте и зеркалах; https:// — как есть. */
    fun sources(path: String): List<String> = when {
        path.startsWith("https://") -> listOf(path)
        path.isBlank() || path.contains("..") || path.contains("://") -> emptyList()
        else -> bases.map { it + path.trimStart('/') }
    }

    /** 3.20. Ключ Pexels из каталога (вкладка «Обои» в пульте): без него поиска живых обоев нет. */
    private val _pexelsKey = MutableStateFlow("")
    /** Ключ из пульта. */
    val pultKey: StateFlow<String> = _pexelsKey.asStateFlow()
    /** Свой ключ из приложения (важнее ключа из пульта). */
    private val _ownKey = MutableStateFlow<String?>(null)
    val ownKey: StateFlow<String?> = _ownKey.asStateFlow()
    fun loadOwnKey() { if (_ownKey.value == null) _ownKey.value = Prefs.pexelsOwnKey }
    fun validKey(k: String) = k.trim().matches(Regex("[A-Za-z0-9]{20,80}"))
    /** Сохранить свой ключ (пусто — убрать). false — ключ неправильного вида. */
    fun setOwnKey(k: String): Boolean {
        val t = k.trim()
        if (t.isNotEmpty() && !validKey(t)) return false
        Prefs.pexelsOwnKey = t; _ownKey.value = t; recalcKey(); return true
    }
    /** Ключ, которым ищем: свой, иначе из пульта. */
    val pexelsKey: StateFlow<String> get() = _effective
    private val _effective = MutableStateFlow("")
    // Ключ задаётся только в пульте (свой ключ в приложении убран)
    private fun recalcKey() { _effective.value = _pexelsKey.value }
    fun parseKey(text: String): String = runCatching { JSONObject(text).optString("pexelsKey").trim().takeIf { it.matches(Regex("[A-Za-z0-9]{20,80}")) }.orEmpty() }.getOrDefault("")

    suspend fun loadCatalog(context: Context) = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, "wallpapers.json")
        loadOwnKey()
        if (_catalog.value.isEmpty() && cache.exists()) { val t = cache.readText(); _catalog.value = parseCatalog(t); _pexelsKey.value = parseKey(t) }
        recalcKey()
        val text = sources("config/wallpapers.json").firstNotNullOfOrNull { u ->
            runCatching { open(u + "?t=" + System.currentTimeMillis() / 600_000).use { it.bufferedReader().readText() } }.getOrNull()
                ?.takeIf { parseCatalog(it).isNotEmpty() }
        } ?: return@withContext
        cache.writeText(text)
        _catalog.value = parseCatalog(text)
        _pexelsKey.value = parseKey(text)
        recalcKey()
    }

    /** Скачанные обои: из каталога и по ссылке. */
    fun downloaded(context: Context): List<Live> = dir(context).listFiles().orEmpty()
        .filter { it.name.endsWith(".mp4") && it.length() > 0 }
        .sortedByDescending { it.lastModified() }
        .map { f ->
            val id = f.name.removeSuffix(".mp4")
            val m = runCatching { JSONObject(meta(context, id).readText()) }.getOrNull()
            Live(id, m?.optString("title").orEmpty().ifBlank { "Видео" }, m?.optString("url").orEmpty(), m?.optString("thumb").orEmpty(), f.length() / 1_048_576.0)
        }

    fun isDownloaded(context: Context, id: String) = file(context, id).let { it.exists() && it.length() > 0 }

    /** Скачать видео. Ошибка — текст для пользователя, null — получилось. */
    suspend fun download(context: Context, item: Live): String? = withContext(Dispatchers.IO) {
        if (_progress.value.containsKey(item.id)) return@withContext "Уже скачивается"
        if (isDownloaded(context, item.id)) return@withContext null
        _progress.value = _progress.value + (item.id to 0f)
        val tmp = File(dir(context), item.id + ".part")
        try {
            var last: String? = "Не удалось скачать — проверьте интернет"
            for (u in sources(item.url)) {
                last = runCatching {
                    val c = connect(u)
                    val type = c.contentType.orEmpty()
                    if (type.startsWith("text/") || type.contains("html")) return@runCatching "По ссылке не видео, а страница. Нужна прямая ссылка на файл .mp4"
                    val total = c.contentLengthLong
                    if (total > MAX_BYTES) return@runCatching "Видео больше 150 МБ"
                    c.inputStream.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024); var done = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                done += n; if (done > MAX_BYTES) error("big")
                                out.write(buf, 0, n)
                                if (total > 0) _progress.value = _progress.value + (item.id to (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (!looksLikeVideo(tmp)) return@runCatching "Файл не похож на видео MP4"
                    tmp.renameTo(file(context, item.id))
                    meta(context, item.id).writeText(JSONObject().put("title", item.title).put("url", item.url).put("thumb", item.thumb).toString())
                    null
                }.getOrElse { if (it.message == "big") "Видео больше 150 МБ" else "Не удалось скачать — проверьте интернет" }
                if (last == null) break
            }
            last
        } finally {
            tmp.delete()
            _progress.value = _progress.value - item.id
            _rev.value++
        }
    }

    /** Видео по своей ссылке: прямая https-ссылка на .mp4. */
    suspend fun downloadLink(context: Context, url: String): Pair<String?, String> {
        val u = url.trim()
        if (!u.startsWith("https://")) return "Нужна ссылка, которая начинается с https://" to ""
        val id = "link-" + Integer.toHexString(u.hashCode())
        val name = u.substringAfterLast('/').substringBefore('?').removeSuffix(".mp4").take(24).ifBlank { "Видео по ссылке" }
        return download(context, Live(id, name, u, "")) to id
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete(); meta(context, id).delete()
        _rev.value++
    }

    /** MP4: в начале файла блок «ftyp». */
    private fun looksLikeVideo(f: File): Boolean = runCatching {
        f.inputStream().use { val b = ByteArray(12); it.read(b) == 12 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp" }
    }.getOrDefault(false)

    // ── Обои-картинки: поиск на Wallhaven (только безопасные, purity=100) и лучшие фото Wikimedia Commons ──
    data class Topic(val id: String, val title: String, val commons: Boolean, val query: String)
    val photoTopics = listOf(
        Topic("anime", "Аниме", false, "anime"),
        Topic("games", "Игры", false, "video games"),
        Topic("cyberpunk", "Киберпанк", false, "cyberpunk"),
        Topic("pixel", "Пиксель-арт", false, "pixel art"),
        Topic("space", "Космос", false, "space"),
        Topic("cars", "Машины", false, "cars"),
        Topic("minimal", "Минимализм", false, "minimalism"),
        // 3.19.8. Ещё темы
        Topic("fantasy", "Фэнтези", false, "fantasy art"),
        Topic("neon", "Неон", false, "neon"),
        Topic("amoled", "Тёмные AMOLED", false, "amoled dark"),
        Topic("abstract", "Абстракция", false, "abstract"),
        Topic("japan", "Япония", false, "japan"),
        Topic("cities", "Города", false, "city"),
        Topic("animals", "Животные", false, "animals"),
        Topic("cute", "Милые", false, "cute"),
        Topic("moto", "Мотоциклы", false, "motorcycle"),
        Topic("planes", "Авиация", false, "aircraft"),
        Topic("football", "Футбол", false, "football"),
        Topic("sci-fi", "Фантастика", false, "science fiction"),
        Topic("retro", "Ретро", false, "retro"),
        Topic("flowers", "Цветы", true, "flower"),
        Topic("winter", "Зима", true, "winter snow"),
        Topic("nature", "Природа", true, "landscape"),
        Topic("mountains", "Горы", true, "mountain"),
        Topic("sea", "Море", true, "sea OR ocean OR coast"),
    )

    /** Подборка по теме. */
    suspend fun photos(topicId: String, page: Int = 0): List<Photo> {
        val t = photoTopics.firstOrNull { it.id == topicId } ?: return emptyList()
        return if (t.commons) commons(t.query, page) else wallhaven(t.query, page, toplist = true)
    }

    /** Поиск по словам: «Genshin», «Naruto», «Cyberpunk 2077»… */
    suspend fun search(query: String, page: Int = 0): List<Photo> = wallhaven(query.trim().take(80), page, toplist = false)

    private suspend fun wallhaven(q: String, page: Int, toplist: Boolean): List<Photo> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        // 3.20: «безопасные» на Wallhaven бывают откровенными — убираем такие теги из выдачи
        val clean = "$q $NOT_TAGS"
        fun url(portrait: Boolean) = "https://wallhaven.cc/api/v1/search?purity=100&categories=111&page=${page + 1}" +
            (if (portrait) "&ratios=portrait" else "") +
            (if (toplist) "&sorting=toplist&topRange=1y" else "&sorting=relevance") + "&q=" + URLEncoder.encode(clean, "UTF-8")
        val portrait = runCatching { parseWallhaven(open(url(true)).use { it.bufferedReader().readText() }) }.getOrDefault(emptyList())
        if (portrait.size >= 6) portrait
        else portrait + runCatching { parseWallhaven(open(url(false)).use { it.bufferedReader().readText() }) }.getOrDefault(emptyList()).filter { p -> portrait.none { it.full == p.full } }
    }

    /** Теги, которые исключаются из поиска картинок. */
    const val NOT_TAGS = "-ecchi -bikini -swimwear -swimsuit -lingerie -underwear -panties -cleavage -thighs -feet -nude -sideboob -underboob -bunny_girl -in_bed -fan_service"

    /** Ответ Wallhaven: только purity = sfw (на всякий случай проверяем и здесь). */
    fun parseWallhaven(json: String): List<Photo> = runCatching {
        val a = JSONObject(json).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("purity") == "sfw" }.mapNotNull { o ->
            val full = o.optString("path").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val th = o.optJSONObject("thumbs")?.let { t -> t.optString("original").ifBlank { t.optString("large") } }?.takeIf { it.startsWith("https://") } ?: full
            Photo("", th, full, "wallhaven.cc")
        }
    }.getOrDefault(emptyList())

    private suspend fun commons(q0: String, page: Int): List<Photo> = withContext(Dispatchers.IO) {
        val q = "incategory:Featured_pictures_on_Wikimedia_Commons " + q0
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=30" +
            "&gsroffset=${page * 30}&gsrsearch=" + URLEncoder.encode(q, "UTF-8") +
            "&prop=imageinfo&iiprop=url|mime|extmetadata&iiextmetadatafilter=Artist|LicenseShortName&iiurlwidth=360"
        runCatching { parsePhotos(open(url).use { it.bufferedReader().readText() }) }.getOrDefault(emptyList())
    }

    fun parsePhotos(json: String): List<Photo> = runCatching {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching emptyList()
        pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }.sortedBy { it.optInt("index") }.mapNotNull { p ->
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            if (ii.optString("mime") !in setOf("image/jpeg", "image/png")) return@mapNotNull null
            val thumb = ii.optString("thumburl").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val full = if (thumb.contains("/360px-")) thumb.replace("/360px-", "/1600px-") else ii.optString("url")
            val artist = ii.optJSONObject("extmetadata")?.optJSONObject("Artist")?.optString("value").orEmpty()
                .replace(Regex("<[^>]+>"), "").trim().take(60)
            val lic = ii.optJSONObject("extmetadata")?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
            Photo(p.optString("title").removePrefix("File:").substringBeforeLast('.').take(60), thumb, full, listOf(artist, lic).filter { it.isNotBlank() }.joinToString(" · "))
        }.toList()
    }.getOrDefault(emptyList())

    // ── Скачанные картинки: можно снова поставить или удалить ──
    fun picsDir(context: Context) = File(context.filesDir, "pics").apply { mkdirs() }
    fun pics(context: Context): List<File> = picsDir(context).listFiles().orEmpty().filter { it.name.endsWith(".jpg") }.sortedByDescending { it.lastModified() }
    fun deletePic(context: Context, f: File) { f.delete(); _rev.value++ }
    fun usePic(context: Context, f: File): Boolean = Backdrop.save(context, f).also { if (it) f.setLastModified(System.currentTimeMillis()) }

    /** Скачать картинку, поставить на главный экран и сохранить в «Скачанные». */
    suspend fun setPhoto(context: Context, photo: Photo): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "wall-photo.tmp")
        val ok = listOf(photo.full, photo.thumb.replace("/360px-", "/1280px-")).distinct().any { u ->
            runCatching {
                val c = connect(u)
                if (c.contentLengthLong > 40L * 1024 * 1024) return@runCatching false
                c.inputStream.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                Backdrop.save(context, tmp)
            }.getOrDefault(false)
        }
        tmp.delete()
        if (ok) runCatching { Backdrop.file(context).copyTo(File(picsDir(context), Integer.toHexString(photo.full.hashCode()) + ".jpg"), overwrite = true) }
        _rev.value++
        ok
    }

    // ── 3.20. Живые обои из интернета по темам и поиску — бесплатные вертикальные видео Pexels ──
    data class PxTopic(val id: String, val title: String, val query: String)
    val liveTopics = listOf(
        PxTopic("anime", "Аниме", "anime"),
        PxTopic("games", "Игры", "video game"),
        PxTopic("cyberpunk", "Киберпанк", "cyberpunk"),
        PxTopic("neon", "Неон", "neon lights"),
        PxTopic("pixel", "Пиксель-арт", "pixel art animation"),
        PxTopic("space", "Космос", "space galaxy"),
        PxTopic("city", "Город ночью", "city night"),
        PxTopic("rain", "Дождь", "rain window"),
        PxTopic("ocean", "Океан", "ocean waves"),
        PxTopic("nature", "Природа", "nature"),
        PxTopic("forest", "Лес", "forest"),
        PxTopic("fire", "Огонь", "fire flames"),
        PxTopic("snow", "Зима", "snow falling"),
        PxTopic("abstract", "Абстракция", "abstract background"),
        PxTopic("aquarium", "Рыбки", "fish aquarium"),
        PxTopic("sunset", "Закат", "sunset"),
        PxTopic("cars", "Машины", "car night"),
        PxTopic("cats", "Котики", "cat"),
    )

    suspend fun liveSearch(query: String, page: Int = 0): List<Live> = withContext(Dispatchers.IO) {
        loadOwnKey(); recalcKey()
        val key = _effective.value
        if (key.isBlank() || query.isBlank()) return@withContext emptyList()
        runCatching {
            val c = URL("https://api.pexels.com/videos/search?orientation=portrait&size=medium&per_page=24&page=${page + 1}&query=" + URLEncoder.encode(query.trim().take(80), "UTF-8"))
                .openConnection() as HttpURLConnection
            c.connectTimeout = 12_000; c.readTimeout = 20_000
            c.setRequestProperty("Authorization", key)
            c.setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android; $UA_SITE)")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            parsePexels(c.inputStream.use { it.bufferedReader().readText() })
        }.getOrDefault(emptyList())
    }

    suspend fun liveTopic(topicId: String, page: Int = 0): List<Live> =
        liveSearch(liveTopics.firstOrNull { it.id == topicId }?.query ?: topicId, page)

    /** Ответ Pexels: вертикальный mp4 ближе всего к 1080×1920, не больше 1920 по высоте. */
    fun parsePexels(json: String): List<Live> = runCatching {
        val a = JSONObject(json).optJSONArray("videos") ?: return@runCatching emptyList()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { v ->
            val files = v.optJSONArray("video_files") ?: return@mapNotNull null
            val best = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
                .filter { it.optString("file_type") == "video/mp4" && it.optString("link").startsWith("https://") && it.optInt("height") >= it.optInt("width") && it.optInt("height") in 960..1920 }
                .maxByOrNull { it.optInt("height") } ?: return@mapNotNull null
            val thumb = v.optString("image").takeIf { it.startsWith("https://") }.orEmpty()
            val author = v.optJSONObject("user")?.optString("name").orEmpty().take(40)
            Live("px" + v.optLong("id"), author.ifBlank { "Pexels" }, best.optString("link"), thumb, 0.0, "Видео: ${author.ifBlank { "автор" }} · Pexels")
        }
    }.getOrDefault(emptyList())

    // ── 3.19.9. Избранное: живые обои и картинки ──
    /** type = live | pic; id — id из каталога или ссылка на картинку; full — откуда скачать. */
    data class Fav(val type: String, val id: String, val title: String, val thumb: String, val full: String)

    private val _favs = MutableStateFlow<List<Fav>>(emptyList())
    val favs: StateFlow<List<Fav>> = _favs.asStateFlow()
    @Volatile private var favsLoaded = false

    fun encodeFavs(list: List<Fav>): String = JSONArray(list.map { JSONObject().put("type", it.type).put("id", it.id).put("title", it.title).put("thumb", it.thumb).put("full", it.full) }).toString()
    fun decodeFavs(text: String): List<Fav> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val type = o.optString("type").takeIf { it == "live" || it == "pic" } ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Fav(type, id, o.optString("title"), o.optString("thumb"), o.optString("full"))
        }.distinctBy { it.type + it.id }
    }.getOrDefault(emptyList())

    fun loadFavs() { if (!favsLoaded) { _favs.value = decodeFavs(Prefs.wallFavs); favsLoaded = true } }
    fun isFav(type: String, id: String) = _favs.value.any { it.type == type && it.id == id }
    /** true — добавили, false — убрали. */
    fun toggleFav(f: Fav): Boolean {
        loadFavs()
        val had = isFav(f.type, f.id)
        _favs.value = if (had) _favs.value.filterNot { it.type == f.type && it.id == f.id } else listOf(f) + _favs.value
        Prefs.wallFavs = encodeFavs(_favs.value)
        return !had
    }
    fun favOf(item: Live) = Fav("live", item.id, item.title, item.thumb, item.url)
    fun favOf(p: Photo) = Fav("pic", p.full, p.title.ifBlank { "Картинка" }, p.thumb, p.full)

    /** Поставить избранное на главный экран (живые — скачать, если ещё нет). Ошибка — текст, null — получилось. */
    suspend fun applyFav(context: Context, f: Fav): String? = when (f.type) {
        "live" -> {
            val err = if (isDownloaded(context, f.id)) null else download(context, Live(f.id, f.title, f.full, f.thumb))
            if (err == null) AppSettings.update { copy(shieldBackdrop = key(f.id), backdropStamp = System.currentTimeMillis()) }
            err
        }
        else -> if (setPhoto(context, Photo(f.title, f.thumb, f.full, ""))) {
            AppSettings.update { copy(shieldBackdrop = "photo", backdropStamp = System.currentTimeMillis()) }; null
        } else "Не удалось скачать картинку"
    }

    // ── 3.19.8. Обои дня: раз в день новая картинка выбранной темы или следующие скачанные живые обои ──
    private val autoLock = kotlinx.coroutines.sync.Mutex()

    /** Выбор на сегодня (чистая функция — для автотестов): день года → элемент списка по кругу. */
    fun <T> pickForDay(list: List<T>, dayOfYear: Int): T? = if (list.isEmpty()) null else list[Math.floorMod(dayOfYear, list.size)]

    /** Сменить обои, если сегодня ещё не меняли (force — сейчас). true — сменили. */
    suspend fun autoTick(context: Context, force: Boolean = false): Boolean = autoLock.withLock {
        val mode = AppSettings.value.wallAuto
        if (mode == "off") return@withLock false
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        if (!force && Prefs.wallAutoDay == day) return@withLock false
        val doy = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) + (if (force) (System.currentTimeMillis() / 1000 % 997).toInt() else 0)
        val ok = when {
            mode == "live" -> {
                val pick = pickForDay(downloaded(context).sortedBy { it.id }, doy) ?: return@withLock false
                AppSettings.update { copy(shieldBackdrop = key(pick.id), backdropStamp = System.currentTimeMillis()) }
                true
            }
            mode == "fav" -> {
                loadFavs()
                val pick = pickForDay(_favs.value, doy) ?: return@withLock false
                applyFav(context, pick) == null
            }
            mode.startsWith("px:") -> {
                // Каждый день новые живые обои темы (~2–8 МБ в день); вчерашние удаляем, если они не в избранном
                val list = liveTopic(mode.removePrefix("px:"), page = (doy / 20) % 5)
                val pick = pickForDay(list, doy) ?: return@withLock false
                val prev = idOf(AppSettings.value.shieldBackdrop)
                val isDownloadedBefore = isDownloaded(context, pick.id)
                val ok = download(context, pick) == null
                if (ok) {
                    AppSettings.update { copy(shieldBackdrop = key(pick.id), backdropStamp = System.currentTimeMillis()) }
                    // Удаляем только то, что вчера скачали сами «Обои дня» (не то, что пользователь скачал руками)
                    if (prev != null && prev == Prefs.wallAutoLast && prev != pick.id && !isFav("live", prev)) delete(context, prev)
                    if (!isDownloadedBefore) Prefs.wallAutoLast = pick.id
                }
                ok
            }
            mode.startsWith("pics:") -> {
                val list = photos(mode.removePrefix("pics:"), page = (doy / 30) % 3)
                val pick = pickForDay(list, doy) ?: return@withLock false
                setPhoto(context, pick).also { if (it) AppSettings.update { copy(shieldBackdrop = "photo", backdropStamp = System.currentTimeMillis()) } }
            }
            else -> false
        }
        if (ok) Prefs.wallAutoDay = day
        ok
    }

    /** Миниатюра (кэш в памяти не нужен — Coil нет, грузим один раз на экран). */
    suspend fun bitmap(url: String, context: Context): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val f = File(context.cacheDir, "wall-" + Integer.toHexString(url.hashCode()) + ".img")
        runCatching {
            if (!f.exists() || f.length() == 0L) {
                sources(url).firstNotNullOfOrNull { u -> runCatching { open(u).use { i -> f.outputStream().use { i.copyTo(it) } }; true }.getOrNull() }
            }
            // Миниатюра 88×132 dp: уменьшаем при чтении (превью Pexels бывают 1080×1920 — 24 штуки в полном размере не влезли бы в память)
            val b = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, b)
            var sample = 1
            while (b.outWidth / (sample * 2) >= 300) sample *= 2
            android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun connect(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        // Wikimedia просит указывать приложение в User-Agent
        c.setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android; $UA_SITE)")
        check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
        return c
    }
    private fun open(url: String) = connect(url).inputStream
}
