package com.apexvpn.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// ─── 1.6. Сервисы через VPN (тот же каталог, что в ядре и на Android) ─────

data class ServiceItem(val id: String, val title: String, val subtitle: String, val color: Long, val glyph: String, val defaultOn: Boolean = true)

object ServiceCatalog {
    val all = listOf(
        ServiceItem("youtube", "YouTube", "Видео и сервисы Google", 0xFFE53935, "▶"),
        ServiceItem("instagram", "Instagram", "Instagram, Facebook, Threads", 0xFFD6249F, "◎"),
        ServiceItem("telegram", "Telegram", "Каналы, звонки, медиа", 0xFF2AABEE, "➤"),
        ServiceItem("whatsapp", "WhatsApp", "Звонки и сообщения", 0xFF25D366, "✆"),
        ServiceItem("tiktok", "TikTok", "Короткие видео", 0xFF1F1F24, "♪"),
        ServiceItem("discord", "Discord", "Голос и сообщества", 0xFF5865F2, "◉"),
        ServiceItem("chatgpt", "ChatGPT", "Нейросеть OpenAI", 0xFF10A37F, "✳"),
        ServiceItem("gemini", "Gemini", "Нейросеть Google", 0xFF4C7DF0, "✦"),
        ServiceItem("claude", "Claude", "Нейросеть Anthropic", 0xFFD97757, "✺"),
        ServiceItem("x", "X", "Бывший Twitter", 0xFF1F1F24, "X"),
        ServiceItem("twitch", "Twitch", "Стримы", 0xFF9146FF, "▣"),
        ServiceItem("netflix", "Netflix", "Фильмы и сериалы", 0xFFE50914, "N"),
        ServiceItem("spotify", "Spotify", "Музыка и подкасты", 0xFF1DB954, "♫"),
        ServiceItem("soundcloud", "SoundCloud", "Музыка", 0xFFFF5500, "☁"),
        ServiceItem("reddit", "Reddit", "Форумы и сообщества", 0xFFFF4500, "◕"),
        ServiceItem("linkedin", "LinkedIn", "Работа и резюме", 0xFF0A66C2, "in"),
        ServiceItem("pinterest", "Pinterest", "Идеи и картинки", 0xFFE60023, "P"),
        ServiceItem("snapchat", "Snapchat", "Сообщения и истории", 0xFFE6D800, "✿"),
        ServiceItem("signal", "Signal", "Защищённый мессенджер", 0xFF3A76F0, "✉"),
        ServiceItem("viber", "Viber", "Звонки и сообщения", 0xFF7360F2, "☎"),
        ServiceItem("medium", "Medium", "Статьи и блоги", 0xFF1F1F24, "M"),
        ServiceItem("patreon", "Patreon", "Подписки на авторов", 0xFFF96854, "◐"),
        ServiceItem("perplexity", "Perplexity", "Поиск с нейросетью", 0xFF20808D, "✲"),
        ServiceItem("copilot", "Copilot", "Нейросеть Microsoft", 0xFF0078D4, "❖"),
        ServiceItem("notion", "Notion", "Заметки и документы", 0xFF2F2F33, "N"),
        ServiceItem("figma", "Figma", "Дизайн", 0xFFA259FF, "◈"),
        ServiceItem("cloudflare", "Cloudflare", "Сайты, которые не открываются", 0xFFF38020, "☁", defaultOn = false),
    )
    val defaultEnabled: Set<String> = all.filter { it.defaultOn }.map { it.id }.toSet()
}

// ─── 1.5. Звуки подключения ───────────────────────────────────────────

/** Короткие звуки подключения и отключения: синтезируются на лету, без файлов. */
object Sounds {
    val sets = listOf("off" to "Без звука", "chime" to "Колокольчик", "soft" to "Мягкий", "space" to "Космос")

    fun play(connected: Boolean) {
        val set = Store.value.sound
        if (set == "off") return
        Thread {
            runCatching {
                val notes: List<Pair<Double, Int>> = when (set) {
                    "soft" -> if (connected) listOf(523.25 to 120, 659.25 to 220) else listOf(659.25 to 120, 523.25 to 220)
                    "space" -> if (connected) listOf(392.0 to 90, 587.33 to 90, 783.99 to 260) else listOf(783.99 to 90, 587.33 to 90, 392.0 to 260)
                    else -> if (connected) listOf(880.0 to 110, 1318.5 to 260) else listOf(1046.5 to 110, 698.46 to 260)
                }
                val rate = 44100f
                val pcm = java.io.ByteArrayOutputStream()
                for ((f, ms) in notes) {
                    val n = (rate * ms / 1000).toInt()
                    for (i in 0 until n) {
                        val t = i / rate
                        val env = exp(-t * (if (set == "space") 5.0 else 9.0)) * minOf(1.0, i / 200.0)
                        val tone = sin(2 * PI * f * t) + (if (set == "space") 0.35 * sin(2 * PI * f * 1.5 * t) else 0.25 * sin(2 * PI * f * 2 * t))
                        val v = (tone * env * 0.25 * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        pcm.write(v and 0xFF); pcm.write((v shr 8) and 0xFF)
                    }
                }
                val data = pcm.toByteArray()
                val fmt = AudioFormat(rate, 16, 1, true, false)
                val line = AudioSystem.getSourceDataLine(fmt)
                line.open(fmt); line.start(); line.write(data, 0, data.size); line.drain(); line.close()
            }
        }.apply { isDaemon = true }.start()
    }
}

// ─── 1.5. Своё фото на фоне ───────────────────────────────────────────

object Photo {
    private var cachedPath = ""
    private var cached: ImageBitmap? = null

    fun load(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        if (path == cachedPath) return cached
        cachedPath = path
        cached = runCatching { ImageIO.read(File(path))?.toComposeImageBitmap() }.getOrNull()
        return cached
    }

    /** Выбор картинки в окне Windows. */
    fun pick(): String? {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Выберите фото для фона", java.awt.FileDialog.LOAD)
        d.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".bmp") || it.endsWith(".gif") } }
        d.file = "*.jpg;*.jpeg;*.png;*.bmp"
        d.isVisible = true
        val f = d.file ?: return null
        val src = File(d.directory, f)
        // Копия к себе: фото не пропадёт, если оригинал удалят
        return runCatching {
            val dst = File(Paths.data, "backdrop." + src.extension.lowercase().ifBlank { "jpg" })
            src.copyTo(dst, overwrite = true)
            cachedPath = ""
            dst.absolutePath
        }.getOrNull()
    }
}

// ─── 1.6. WARP-цепочка ────────────────────────────────────────────────

object Warp {
    private const val API = "https://api.cloudflareclient.com/v0a1922/reg"

    /** Бесплатный аккаунт Cloudflare WARP; возвращает данные для ядра (JSON). */
    suspend fun register(): String = withContext(Dispatchers.IO) {
        val pair = CoreCli.warpKeypair().split(" ")
        check(pair.size == 2) { "не удалось создать ключи" }
        val (priv, pub) = pair
        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = """{"install_id":"","fcm_token":"","tos":"$tos","key":"$pub","type":"Android","model":"PC","locale":"en_US","warp_enabled":true}"""
        val c = (URI(API).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("User-Agent", "okhttp/3.12.1")
            setRequestProperty("CF-Client-Version", "a-6.3-1922")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) error("Cloudflare ответил HTTP ${c.responseCode}")
            CoreCli.warpParse(c.inputStream.bufferedReader().readText(), priv)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Cloudflare недоступен: ${e.message ?: "нет связи"}")
        } finally {
            c.disconnect()
        }
    }
}

// ─── 1.7. Самопроверка «Не работает?» ─────────────────────────────────

object SelfCheck {
    enum class Mark { Wait, Run, Ok, Warn, Fail }
    data class Step(val title: String, val mark: Mark = Mark.Wait, val detail: String = "")
    data class State(val running: Boolean = false, val steps: List<Step> = emptyList(), val verdict: String? = null, val fixTag: String? = null, val fixTitle: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val titles = listOf("Интернет без VPN", "Подписка", "Серверы", "VPN", "Сайты через VPN")

    private fun set(i: Int, m: Mark, d: String = "") {
        _state.value = _state.value.copy(steps = _state.value.steps.toMutableList().also { it[i] = it[i].copy(mark = m, detail = d) })
    }

    suspend fun run() = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext
        _state.value = State(running = true, steps = titles.map { Step(it) })
        val problems = mutableListOf<String>()
        set(0, Mark.Run)
        val net = Net.trace(null).isNotEmpty() || Net.text("https://ya.ru", null) != null
        if (net) set(0, Mark.Ok, "Есть") else { set(0, Mark.Fail, "Нет связи — проверьте интернет"); problems += "Нет интернета без VPN: сначала проверьте подключение компьютера." }

        set(1, Mark.Run)
        val sub = Subscription.state.value
        val nowSec = System.currentTimeMillis() / 1000
        when {
            sub.nodes.isEmpty() -> { set(1, Mark.Fail, "В подписке нет серверов"); problems += "В подписке нет серверов — обновите её." }
            sub.expireAt in 1 until nowSec -> { set(1, Mark.Fail, "Подписка закончилась"); problems += "Подписка закончилась — продлите её." }
            sub.total > 0 && sub.used >= sub.total -> { set(1, Mark.Fail, "Трафик закончился"); problems += "Трафик по подписке закончился." }
            else -> set(1, Mark.Ok, "Серверов: ${sub.nodes.size}")
        }

        if (sub.nodes.isNotEmpty() && net) {
            set(2, Mark.Run, "Проверяю ${sub.nodes.size}…")
            runCatching { Pinger.pingAll() }
            val p = Pinger.pings.value
            val ok = sub.nodes.count { (p[it.tag] ?: -1) > 0 }
            when {
                ok == sub.nodes.size -> set(2, Mark.Ok, "Отвечают все ($ok)")
                ok > 0 -> set(2, Mark.Warn, "Отвечают $ok из ${sub.nodes.size}")
                else -> { set(2, Mark.Fail, "Ни один не ответил"); problems += "Серверы не отвечают — возможно, провайдер блокирует VPN. Попробуйте другую сеть или серверы «LTE-обход»." }
            }
            val sel = Store.value.selected
            val best = sub.nodes.filter { !ServerInfo.isSpecial(it.name) && (p[it.tag] ?: -1) > 0 }.minByOrNull { p[it.tag]!! }
            if (best != null && sel != "auto" && (p[sel] ?: -1) <= 0) {
                _state.value = _state.value.copy(fixTag = best.tag, fixTitle = best.title)
                problems += "Выбранный сервер не отвечает — выберите рабочий."
            }
        } else set(2, Mark.Wait, "Пропущено")

        set(3, Mark.Run)
        val on = Engine.status.value == VpnStatus.On
        if (on) set(3, Mark.Ok, "Подключён") else set(3, Mark.Warn, "Выключен — подключитесь и проверьте ещё раз")

        if (on) {
            set(4, Mark.Run)
            val t = System.nanoTime()
            val ok = Net.trace(Net.viaVpn()).isNotEmpty()
            val ms = ((System.nanoTime() - t) / 1_000_000).toInt()
            if (ok) set(4, Mark.Ok, "Открываются · $ms мс") else {
                set(4, Mark.Fail, "Не открываются")
                problems += "Через VPN сайты не открываются — выберите другой сервер или автопилот."
            }
        } else set(4, Mark.Wait, "Нужно подключиться")

        _state.value = _state.value.copy(
            running = false,
            verdict = problems.firstOrNull() ?: if (!on) "Всё в порядке. Подключитесь — и можно пользоваться." else "Всё работает ✓",
        )
    }
}

// ─── 1.7. Напоминания о конце подписки ────────────────────────────────

object ExpiryAlerts {
    private val file get() = File(Paths.data, "alerts.json")

    /** Текст уведомления, если пора напомнить (каждое — один раз: за 3 дня, за 1 день, в конце). */
    fun due(): String? {
        if (!Store.value.expiryAlerts) return null
        val sub = Subscription.state.value
        if (sub.expireAt <= 0) return null
        val left = sub.expireAt - System.currentTimeMillis() / 1000
        val stage = when { left <= 0 -> 0; left <= 86400 -> 1; left <= 3 * 86400 -> 3; else -> return null }
        val o = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        val key = "e${sub.expireAt}"
        if (o.optInt(key, 99) <= stage) return null
        runCatching { file.writeText(o.put(key, stage).toString()) }
        return when (stage) {
            0 -> "Подписка закончилась — продлите её"
            1 -> "Подписка закончится меньше чем через сутки"
            else -> "Подписка закончится через ${((left + 86399) / 86400)} дн."
        }
    }
}

// ─── 1.7. Объявления из панели: текст, картинка и кнопка ──────────────

object Announce {
    data class Rich(val text: String, val image: String, val button: String, val url: String)

    private val imageRe = Regex("""!\[[^\]]*]\((https://[^\s)]+)\)""")
    private val linkRe = Regex("""\[([^\]]+)]\((https?://[^\s)]+)\)""")

    fun parse(raw: String): Rich {
        var t = raw
        val img = imageRe.find(t)?.groupValues?.get(1).orEmpty()
        t = imageRe.replace(t, "")
        val link = linkRe.find(t)
        if (link != null) t = t.replace(link.value, "")
        return Rich(t.trim(), img, link?.groupValues?.get(1)?.trim().orEmpty(), link?.groupValues?.get(2).orEmpty())
    }

    private val images = HashMap<String, ImageBitmap?>()

    suspend fun image(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        images[url]?.let { return@withContext it }
        val bmp = runCatching { Net.open(url, null, 8000).inputStream.use { ImageIO.read(it) }?.toComposeImageBitmap() }.getOrNull()
        images[url] = bmp
        bmp
    }
}

// ─── 1.7. Резервная копия и перенос с телефона ────────────────────────

object Backup {
    /** Сохранить настройки и ссылку подписки в файл (тот же формат, что у Android). */
    fun export(): String? {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Сохранить резервную копию", java.awt.FileDialog.SAVE)
        d.file = "apex-vpn-settings.json"
        d.isVisible = true
        val f = d.file ?: return null
        val s = Store.value
        val settings = JSONObject(File(Paths.data, "settings.json").readText())
            // Ключи, как на Android: телефон поймёт эту копию
            .put("mode", s.routeMode.id)
        val o = JSONObject().put("app", "APEX VPN").put("format", 1).put("version", "Windows ${AppInfo.VERSION}")
            .put("settings", settings).put("subscription", s.subscriptionUrl).put("selected", s.selected)
        File(d.directory, f).writeText(o.toString(2))
        return f
    }

    /**
     * Загрузить копию: с Windows или с телефона (Android: «Резервная копия в файл»).
     * Переносятся подписка, свои списки и сайты, сервисы, правила, сайты стран и выбор DNS.
     */
    suspend fun import(): String? {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Открыть резервную копию APEX VPN", java.awt.FileDialog.LOAD)
        d.file = "*.json"
        d.isVisible = true
        val f = d.file ?: return null
        val o = JSONObject(File(d.directory, f).readText())
        require(o.optString("app") == "APEX VPN") { "Это не резервная копия APEX VPN" }
        val st = o.optJSONObject("settings") ?: JSONObject()
        fun arr(k: String) = st.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }
        Store.update {
            copy(
                customLists = st.optJSONArray("customLists")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { l ->
                        CustomList(l.optString("name"), (l.optJSONArray("items") ?: JSONArray()).let { i -> (0 until i.length()).map { i.optString(it) } }, l.optBoolean("enabled", true))
                    }
                } ?: customLists,
                customDomains = arr("customDomains") ?: customDomains,
                directDomains = arr("directDomains") ?: directDomains,
                directCountries = arr("directCountries")?.toSet() ?: directCountries,
                services = arr("services")?.toSet() ?: services,
                userRules = st.optJSONArray("userRules")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { UserRule(it.optString("value"), it.optString("action", "proxy")) } } ?: userRules,
                routeMode = RouteMode.entries.firstOrNull { it.id == st.optString("mode", st.optString("routeMode")) } ?: routeMode,
                kids = st.optBoolean("kids", kids),
                adBlock = st.optBoolean("adBlock", adBlock),
                dns = DnsChoice.entries.firstOrNull { it.id == st.optString("dns") } ?: dns,
            )
        }
        val url = o.optString("subscription")
        if (url.startsWith(Subscription.PREFIX)) Subscription.refresh(url)
        return f
    }
}
