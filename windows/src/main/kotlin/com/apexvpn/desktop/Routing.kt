package com.apexvpn.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** Сайты стран напрямую — те же наборы, что на Android. */
object DirectCountries {
    data class Country(val id: String, val code: String, val title: String, val suffixes: List<String>)

    val all = listOf(
        Country("kz", "KZ", "Казахстан", listOf("kz", "xn--80ao21a", "kaspi.kz", "halykbank.kz", "egov.kz", "kolesa.kz", "krisha.kz", "olx.kz")),
        Country("by", "BY", "Беларусь", listOf("by", "xn--90ais", "onliner.by", "kufar.by", "belarusbank.by", "priorbank.by")),
        Country("uz", "UZ", "Узбекистан", listOf("uz", "olx.uz", "uzum.uz", "payme.uz", "click.uz")),
        Country("kg", "KG", "Кыргызстан", listOf("kg", "lalafo.kg", "mbank.kg")),
        Country("am", "AM", "Армения", listOf("am", "xn--y9a3aq", "list.am", "idram.am")),
        Country("az", "AZ", "Азербайджан", listOf("az", "tap.az", "kapitalbank.az")),
        Country("ge", "GE", "Грузия", listOf("ge", "xn--node", "myhome.ge", "tbcbank.com.ge")),
        Country("tj", "TJ", "Таджикистан", listOf("tj", "somon.tj")),
        Country("md", "MD", "Молдова", listOf("md", "999.md")),
        Country("tr", "TR", "Турция", listOf("com.tr", "gov.tr", "edu.tr", "org.tr", "net.tr", "tr", "trendyol.com", "hepsiburada.com", "sahibinden.com")),
    )

    fun suffixes(ids: Set<String>): List<String> = all.filter { it.id in ids }.flatMap { it.suffixes }.distinct()
}

/** Готовые шаблоны своих списков (как на Android). */
object ListTemplates {
    val all = listOf(
        CustomList("Нейросети", listOf("openai.com", "chatgpt.com", "oaistatic.com", "anthropic.com", "claude.ai", "gemini.google.com", "perplexity.ai", "midjourney.com", "deepl.com")),
        CustomList("Игры", listOf("steampowered.com", "steamcommunity.com", "epicgames.com", "riotgames.com", "battle.net", "ea.com", "ubisoft.com", "playstation.com", "xbox.com")),
        CustomList("Работа", listOf("slack.com", "zoom.us", "notion.so", "figma.com", "atlassian.net", "github.com", "linear.app", "miro.com")),
        CustomList("Соцсети", listOf("instagram.com", "cdninstagram.com", "facebook.com", "fbcdn.net", "x.com", "twitter.com", "twimg.com", "threads.net", "linkedin.com")),
    )
}

/** Строка из поля ввода → правило: адрес сайта без https:// и пути; full:, keyword:, IP, udp:/tcp: — как есть. */
fun normalizeRule(raw: String): String? {
    val s = raw.trim()
    if (s.isEmpty() || s.startsWith("#")) return null
    val low = s.lowercase()
    if (listOf("full:", "keyword:", "regexp:", "udp:", "tcp:", "domain:").any { low.startsWith(it) }) return low
    if (low.first().isDigit() && (low.contains('/') || low.count { it == '.' } == 3) || low.contains(':') && low.count { it == ':' } > 1) return low
    var d = low
    d.indexOf("://").takeIf { it >= 0 }?.let { d = d.substring(it + 3) }
    d = d.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').removePrefix("www.").trim('.')
    return d.takeIf { it.contains('.') || it.length == 2 }
}

fun parseRules(text: String): List<String> = text.lines().mapNotNull(::normalizeRule).distinct()

/**
 * Базы сайтов (российские сайты и IP, реклама, 18+, букмекеры): обновляются каждый день из
 * ветки rules репозитория APEX VPN (по контрольным суммам — качаются только изменившиеся),
 * запасной источник — зеркало jsDelivr. Свежие лежат в %APPDATA%\APEX VPN\rules.
 */
object Rules {
    val FILES = listOf("geoip-ru.srs", "geosite-category-ru.srs", "geosite-category-ads-all.srs", "geosite-category-porn.srs", "geosite-category-betting-ru.srs")
    private val SOURCES = listOf("https://raw.githubusercontent.com/${AppInfo.REPO}/rules", "https://cdn.jsdelivr.net/gh/${AppInfo.REPO}@rules")

    data class Info(val checkedAt: Long = 0, val updatedAt: Long = 0, val sites: Int = 0, val status: String = "", val busy: Boolean = false)

    private val _info = MutableStateFlow(load())
    val info = _info.asStateFlow()
    private val lock = Mutex()

    private val dir get() = File(Paths.data, "rules")

    /** Папка для ядра: свежие базы, если все на месте, иначе встроенные в программу. */
    fun dirForCore(): File = dir.takeIf { d -> FILES.all { File(d, it).length() > 0 } } ?: Paths.rules

    fun due(): Boolean {
        val s = Store.value
        return s.rulesAuto && System.currentTimeMillis() - _info.value.checkedAt > s.rulesHours * 3_600_000L
    }

    /** true — базы изменились (нужно переподключиться). */
    suspend fun update(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            _info.value = _info.value.copy(busy = true, status = "Проверяю…")
            var last: String? = null
            for (base in SOURCES) {
                try {
                    val changed = updateFrom(base)
                    _info.value = _info.value.copy(
                        checkedAt = System.currentTimeMillis(),
                        updatedAt = if (changed > 0) System.currentTimeMillis() else _info.value.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        status = if (changed > 0) "Обновлено файлов: $changed" else "Базы свежие",
                        busy = false,
                    )
                    save()
                    return@withLock changed > 0
                } catch (e: Exception) {
                    last = e.message
                }
            }
            _info.value = _info.value.copy(busy = false, status = "Не удалось обновить: ${last ?: "нет связи"}")
            false
        }
    }

    private fun get(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "APEX VPN Windows ${AppInfo.VERSION}")
    }

    private fun updateFrom(base: String): Int {
        val manifest = get("$base/rules.json").let { c ->
            try { if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()) else error("rules.json: HTTP ${c.responseCode}") } finally { c.disconnect() }
        }
        val files = manifest.optJSONObject("files") ?: error("rules.json без списка файлов")
        _info.value = _info.value.copy(sites = FILES.sumOf { files.optJSONObject(it)?.optInt("count") ?: 0 })
        val d = dir.apply { mkdirs() }
        val need = FILES.filter { n ->
            val want = files.optJSONObject(n)?.optString("sha256").orEmpty()
            val f = File(d, n)
            !f.exists() || f.length() == 0L || want.isEmpty() || sha256(f) != want
        }
        for (n in need) {
            val part = File(d, "$n.part")
            val c = get("$base/$n")
            try {
                if (c.responseCode != 200) error("$n: HTTP ${c.responseCode}")
                c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally { c.disconnect() }
            val head = part.inputStream().use { s -> ByteArray(3).also { s.read(it) } }
            check(String(head) == "SRS") { "повреждён $n" }
            val want = files.optJSONObject(n)?.optString("sha256").orEmpty()
            check(want.isEmpty() || sha256(part) == want) { "не совпала сумма $n" }
            val target = File(d, n)
            target.delete()
            check(part.renameTo(target)) { "не удалось записать $n" }
        }
        return need.size
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun save() {
        val i = _info.value
        runCatching { File(Paths.data, "rules-info.json").writeText(JSONObject().put("checkedAt", i.checkedAt).put("updatedAt", i.updatedAt).put("sites", i.sites).put("status", i.status).toString()) }
    }

    private fun load(): Info = runCatching {
        val o = JSONObject(File(Paths.data, "rules-info.json").readText())
        Info(o.optLong("checkedAt"), o.optLong("updatedAt"), o.optInt("sites"), o.optString("status"))
    }.getOrDefault(Info())
}

/**
 * DNS «Авто»: сравнивает Cloudflare, Google, Quad9, Mullvad и AdGuard по DNS-over-HTTPS
 * (лучшее из трёх запросов, по два DNS одновременно) и берёт самый быстрый.
 */
object DnsAuto {
    data class Result(val choice: DnsChoice, val ms: Int)

    private val urls = mapOf(
        DnsChoice.Cloudflare to "https://1.1.1.1/dns-query",
        DnsChoice.Google to "https://dns.google/dns-query",
        DnsChoice.Quad9 to "https://dns.quad9.net/dns-query",
        DnsChoice.Mullvad to "https://dns.mullvad.net/dns-query",
    )

    private val _results = MutableStateFlow<List<Result>>(emptyList())
    val results = _results.asStateFlow()
    @Volatile private var measuredAt = 0L
    @Volatile var best: DnsChoice = loadBest()
        private set

    /** DNS для ядра: выбранный вручную или лучший по замеру. */
    fun effective(s: Settings): DnsChoice = if (s.dns == DnsChoice.Auto) best else s.dns

    fun stale() = System.currentTimeMillis() - measuredAt > 30 * 60_000L

    suspend fun measure() = withContext(Dispatchers.IO) {
        val gate = Semaphore(2)
        val res = coroutineScope {
            urls.map { (c, u) -> async { gate.withPermit { Result(c, probe(u)) } } }.awaitAll()
        }.sortedBy { if (it.ms < 0) Int.MAX_VALUE else it.ms }
        _results.value = res
        measuredAt = System.currentTimeMillis()
        val win = res.firstOrNull { it.ms > 0 } ?: return@withContext
        val cur = res.firstOrNull { it.choice == best }?.ms ?: -1
        // Меняем, только если новый заметно быстрее — чтобы DNS не «прыгал»
        if (win.choice != best && (cur < 0 || cur - win.ms > 25)) {
            best = win.choice
            runCatching { File(Paths.data, "dns-best.txt").writeText(best.id) }
        }
    }

    private fun loadBest(): DnsChoice = runCatching {
        val id = File(Paths.data, "dns-best.txt").readText().trim()
        DnsChoice.entries.first { it.id == id && it != DnsChoice.Auto }
    }.getOrDefault(DnsChoice.Cloudflare)

    private fun probe(url: String): Int {
        val q = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(query())
        var bestMs = -1
        repeat(3) {
            runCatching {
                val c = URI("$url?dns=$q").toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 3000; c.readTimeout = 3000
                c.setRequestProperty("Accept", "application/dns-message")
                val t = System.nanoTime()
                try {
                    if (c.responseCode == 200) {
                        c.inputStream.use { it.readBytes() }
                        val ms = ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
                        if (bestMs < 0 || ms < bestMs) bestMs = ms
                    }
                } finally { c.disconnect() }
            }
        }
        return bestMs
    }

    private fun query(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in "www.example.com".split('.')) { out.write(label.length); out.write(label.toByteArray()) }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        return out.toByteArray()
    }
}
