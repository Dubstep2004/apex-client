package com.apexvpn.client.data

import android.content.Context
import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class Live(val id: String, val title: String, val url: String, val thumb: String, val sizeMb: Double = 0.0)
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

    suspend fun loadCatalog(context: Context) = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, "wallpapers.json")
        if (_catalog.value.isEmpty() && cache.exists()) _catalog.value = parseCatalog(cache.readText())
        val text = sources("config/wallpapers.json").firstNotNullOfOrNull { u ->
            runCatching { open(u + "?t=" + System.currentTimeMillis() / 600_000).use { it.bufferedReader().readText() } }.getOrNull()
                ?.takeIf { parseCatalog(it).isNotEmpty() }
        } ?: return@withContext
        cache.writeText(text)
        _catalog.value = parseCatalog(text)
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
        fun url(portrait: Boolean) = "https://wallhaven.cc/api/v1/search?purity=100&categories=111&page=${page + 1}" +
            (if (portrait) "&ratios=portrait" else "") +
            (if (toplist) "&sorting=toplist&topRange=1y" else "&sorting=relevance") + "&q=" + URLEncoder.encode(q, "UTF-8")
        val portrait = runCatching { parseWallhaven(open(url(true)).use { it.bufferedReader().readText() }) }.getOrDefault(emptyList())
        if (portrait.size >= 6) portrait
        else portrait + runCatching { parseWallhaven(open(url(false)).use { it.bufferedReader().readText() }) }.getOrDefault(emptyList()).filter { p -> portrait.none { it.full == p.full } }
    }

    /** Ответ Wallhaven: только purity = sfw (на всякий случай проверяем и здесь). */
    fun parseWallhaven(json: String): List<Photo> = runCatching {
        val a = JSONObject(json).optJSONArray("data") ?: return@runCatching emptyList()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("purity") == "sfw" }.mapNotNull { o ->
            val full = o.optString("path").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val th = o.optJSONObject("thumbs")?.let { t -> t.optString("original").ifBlank { t.optString("large") } }?.takeIf { it.startsWith("https://") } ?: full
            Photo(o.optString("resolution").ifBlank { "Обои" }, th, full, "wallhaven.cc")
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

    /** Миниатюра (кэш в памяти не нужен — Coil нет, грузим один раз на экран). */
    suspend fun bitmap(url: String, context: Context): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val f = File(context.cacheDir, "wall-" + Integer.toHexString(url.hashCode()) + ".img")
        runCatching {
            if (!f.exists() || f.length() == 0L) {
                sources(url).firstNotNullOfOrNull { u -> runCatching { open(u).use { i -> f.outputStream().use { i.copyTo(it) } }; true }.getOrNull() }
            }
            android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 1 })
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
