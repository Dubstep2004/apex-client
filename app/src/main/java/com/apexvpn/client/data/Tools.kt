package com.apexvpn.client.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

// ─── 5. Тест скорости ───────────────────────────────────────────────────

data class SpeedResult(
    val pingMs: Int = -1,
    val downMbps: Double = -1.0,
    val upMbps: Double = -1.0,
    /** Джиттер — разброс пинга, мс */
    val jitterMs: Int = -1,
)

/** Провайдер и сервер теста: кто вас видит (через VPN — сервер VPN) и ближайший узел Cloudflare. */
data class SpeedMeta(val provider: String, val ip: String, val city: String, val country: String, val colo: String)

/** Замер через серверы Cloudflare. Если VPN включён — меряется скорость через VPN. */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"

    /**
     * Провайдер, IP, город и узел Cloudflare (colo). Сначала /meta Cloudflare; если он не ответил
     * или не назвал провайдера — запасные сервисы (ipwho.is, ipinfo.io, ip.sb), а узел — из /cdn-cgi/trace.
     */
    suspend fun meta(): SpeedMeta? = withContext(Dispatchers.IO) {
        val cf = runCatching {
            json("$BASE/meta")?.let { o ->
                SpeedMeta(o.optString("asOrganization"), o.optString("clientIp"), o.optString("city"), o.optString("country"), o.optString("colo"))
            }
        }.getOrNull()
        if (cf != null && cf.provider.isNotBlank() && cf.colo.isNotBlank()) return@withContext cf
        // Узел и IP из trace — он отвечает почти всегда
        val trace = runCatching {
            val c = open("$BASE/cdn-cgi/trace")
            try {
                if (c.responseCode != 200) null
                else c.inputStream.bufferedReader().readLines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            } finally { c.disconnect() }
        }.getOrNull().orEmpty()
        val geo = cf?.takeIf { it.provider.isNotBlank() }
            ?: runCatching {
                json("https://ipwho.is/")?.takeIf { it.optBoolean("success", true) }?.let { o ->
                    val conn = o.optJSONObject("connection")
                    SpeedMeta(conn?.optString("isp").orEmpty().ifBlank { conn?.optString("org").orEmpty() }, o.optString("ip"), o.optString("city"), o.optString("country_code"), "")
                }
            }.getOrNull()?.takeIf { it.provider.isNotBlank() }
            ?: runCatching {
                json("https://ipinfo.io/json")?.let { o ->
                    // org: «AS12389 Rostelecom» — номер AS убираем
                    SpeedMeta(o.optString("org").replace(Regex("^AS\\d+\\s*"), ""), o.optString("ip"), o.optString("city"), o.optString("country"), "")
                }
            }.getOrNull()?.takeIf { it.provider.isNotBlank() }
            ?: runCatching {
                json("https://api.ip.sb/geoip")?.let { o ->
                    SpeedMeta(o.optString("isp").ifBlank { o.optString("organization") }, o.optString("ip"), o.optString("city"), o.optString("country_code"), "")
                }
            }.getOrNull()?.takeIf { it.provider.isNotBlank() }
        if (geo == null && trace.isEmpty()) return@withContext cf
        SpeedMeta(
            provider = geo?.provider.orEmpty(),
            ip = geo?.ip?.ifBlank { null } ?: trace["ip"].orEmpty(),
            city = geo?.city.orEmpty(),
            country = geo?.country?.ifBlank { null } ?: trace["loc"].orEmpty(),
            colo = cf?.colo?.ifBlank { null } ?: trace["colo"].orEmpty(),
        )
    }

    private fun json(url: String): org.json.JSONObject? {
        val c = open(url)
        return try {
            if (c.responseCode != 200) null else org.json.JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /** Пинг и джиттер: 8 коротких запросов, первый (с установкой соединения) не считаем. */
    suspend fun ping(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val samples = (1..8).mapNotNull {
            runCatching {
                val c = open("$BASE/__down?bytes=0")
                val t = System.nanoTime()
                try {
                    c.inputStream.use { it.readBytes() }
                } finally {
                    c.disconnect()
                }
                ((System.nanoTime() - t) / 1_000_000).toInt()
            }.getOrNull()
        }
        val warm = if (samples.size > 2) samples.drop(1) else samples
        val ping = warm.minOrNull() ?: -1
        val jitter = if (warm.size < 2) -1 else warm.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toInt()
        ping to jitter
    }

    /**
     * Загрузка до 10 секунд. [streams] — сколько соединений одновременно: 1 — «одинарное»,
     * 4 — «множественное» (как в Speedtest: лучше показывает ширину канала).
     */
    suspend fun download(streams: Int = 4, progress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val total = java.util.concurrent.atomic.AtomicLong()
        val start = System.nanoTime()
        val limit = 10_000_000_000L
        kotlinx.coroutines.coroutineScope {
            val jobs = (1..streams.coerceIn(1, 8)).map {
                launch {
                    while (System.nanoTime() - start < limit) {
                        val c = open("$BASE/__down?bytes=25000000")
                        try {
                            c.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    total.addAndGet(n.toLong())
                                    if (System.nanoTime() - start > limit) break
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            break
                        } finally {
                            c.disconnect()
                        }
                    }
                }
            }
            launch {
                while (jobs.any { it.isActive }) {
                    delay(250)
                    progress(mbps(total.get(), System.nanoTime() - start))
                }
            }
        }
        mbps(total.get(), System.nanoTime() - start)
    }

    /** Отдача до 8 секунд: POST по 4 МБ, [streams] соединений одновременно. */
    suspend fun upload(streams: Int = 4, progress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val chunk = Random.nextBytes(4 * 1024 * 1024)
        val total = java.util.concurrent.atomic.AtomicLong()
        val start = System.nanoTime()
        val limit = 8_000_000_000L
        kotlinx.coroutines.coroutineScope {
            val jobs = (1..streams.coerceIn(1, 8)).map {
                launch {
                    while (System.nanoTime() - start < limit) {
                        ensureActive()
                        val c = open("$BASE/__up")
                        try {
                            c.requestMethod = "POST"
                            c.doOutput = true
                            c.setFixedLengthStreamingMode(chunk.size)
                            c.setRequestProperty("Content-Type", "application/octet-stream")
                            c.outputStream.use { out ->
                                var off = 0
                                while (off < chunk.size && System.nanoTime() - start < limit) {
                                    val n = minOf(64 * 1024, chunk.size - off)
                                    out.write(chunk, off, n)
                                    off += n
                                    total.addAndGet(n.toLong())
                                }
                            }
                            c.responseCode
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            break
                        } finally {
                            c.disconnect()
                        }
                    }
                }
            }
            launch {
                while (jobs.any { it.isActive }) {
                    delay(250)
                    progress(mbps(total.get(), System.nanoTime() - start))
                }
            }
        }
        mbps(total.get(), System.nanoTime() - start)
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1_000_000.0

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 15000
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
        // Cloudflare и сервисы IP отвечают 403 «ботам» без браузерного User-Agent
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
        setRequestProperty("Accept", "application/json, text/plain, */*")
        setRequestProperty("Referer", "https://speed.cloudflare.com/")
    }

    /** Скорость в выбранных единицах: Мбит/с, МБ/с или КБ/с. */
    fun format(mbps: Double, unit: String): Pair<String, String> {
        if (mbps < 0) return "—" to unitTitle(unit)
        val v = when (unit) {
            "MBps" -> mbps / 8.0
            "kBps" -> mbps * 1000.0 / 8.0
            else -> mbps
        }
        val txt = when {
            v >= 100 -> "%.0f".format(java.util.Locale.US, v)
            v >= 10 -> "%.1f".format(java.util.Locale.US, v)
            else -> "%.2f".format(java.util.Locale.US, v)
        }
        return txt to unitTitle(unit)
    }

    fun unitTitle(unit: String) = when (unit) {
        "MBps" -> "МБ/с"
        "kBps" -> "КБ/с"
        else -> "Мбит/с"
    }
}

// ─── 49. Живой пинг ─────────────────────────────────────────────────────

object LivePing {
    private const val URL_TEST = "https://www.gstatic.com/generate_204"

    /**
     * Задержка до интернета через текущий сервер, мс; -1 — нет ответа.
     * Меряем внутри ядра (Clash API, тот же способ, что и пинг в списке серверов): так результат
     * не зависит от того, пропускает ли туннель запросы самого приложения и как настроены правила.
     * Если ядро не ответило — обычный HTTPS-запрос, как раньше.
     */
    suspend fun measure(): Int = withContext(Dispatchers.IO) {
        // Сначала обычный запрос: он не трогает замеры автопилота. Замер в ядре — только если
        // запрос приложения не прошёл (так бывает на LTE-обходе), иначе частые замеры могли бы
        // заставить автопилот лишний раз менять сервер
        viaHttp(URL_TEST).takeIf { it >= 0 } ?: viaCore() ?: viaHttp("https://cp.cloudflare.com/generate_204")
    }

    /** null — Clash API недоступен (тогда пробуем HTTP), -1 — сервер не ответил. */
    private fun viaCore(): Int? = runCatching {
        val path = "/proxies/proxy/delay?timeout=4000&url=" + java.net.URLEncoder.encode(URL_TEST, "UTF-8")
        val c = URL("http://127.0.0.1:${Prefs.clashApiPort}$path").openConnection() as HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 6000
        c.setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
        try {
            when (c.responseCode) {
                200 -> org.json.JSONObject(c.inputStream.bufferedReader().readText()).optInt("delay", -1).takeIf { it > 0 } ?: -1
                // Таймаут или ошибка сервера — ядро на месте, но сервер не ответил
                408, 503, 504 -> -1
                else -> null
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun viaHttp(url: String): Int = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.useCaches = false
        val t = System.nanoTime()
        try {
            c.responseCode
        } finally {
            c.disconnect()
        }
        ((System.nanoTime() - t) / 1_000_000).toInt()
    }.getOrDefault(-1)
}

// ─── 6. Список приложений ───────────────────────────────────────────────

data class AppInfo(val packageName: String, val label: String, val icon: Drawable?)

object InstalledApps {
    suspend fun load(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
    }
}
