package com.apexvpn.client.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
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

/** Общий HTTP-клиент для служебных запросов к вашему серверу. */
internal object Http {
    fun open(url: String, timeout: Int = 15_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
            setRequestProperty("Cache-Control", "no-cache")
        }

    fun getText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

// ─── 59. Обновление баз российских сайтов и рекламы ─────────────────────

/**
 * Базы сайтов (*.srs) для маршрутизации, рекламы и семейного режима.
 *
 * Откуда: свой адрес из «Маршрутизации» → адрес при сборке → ветка «rules» нашего GitHub
 * (обновляется раз в день) → её зеркало jsDelivr → официальные базы sing-box.
 * В ветке есть rules.json с контрольными суммами: качаются только изменившиеся файлы,
 * поэтому проверять можно раз в день почти без трафика.
 */
object RulesUpdater {
    val FILES = listOf(
        "geoip-ru.srs", "geosite-category-ru.srs", "geosite-category-ads-all.srs",
        // 15. Семейный режим
        "geosite-category-porn.srs", "geosite-category-betting-ru.srs",
    )

    /**
     * 3.17.5. Необязательные базы: есть только в ветке «rules» APEX VPN (у SagerNet их нет).
     * Не скачались — ядро работает без них, обновление при этом не считается неудачным.
     */
    val OPTIONAL = listOf("ads-hagezi.srs")

    /** Понятные названия баз для экрана «Маршрутизация». */
    val TITLES = mapOf(
        "geoip-ru.srs" to "IP-адреса России",
        "geosite-category-ru.srs" to "Российские сайты",
        "geosite-category-ads-all.srs" to "Реклама и трекеры",
        "ads-hagezi.srs" to "Реклама и трекеры — HaGeZi Multi Normal",
        "geosite-category-porn.srs" to "Сайты 18+ (семейный режим)",
        "geosite-category-betting-ru.srs" to "Букмекеры (семейный режим)",
    )

    private const val DAY = 24L * 3600 * 1000
    private const val WEEK = 7 * DAY
    /** Метка «официальные базы sing-box» — у них свой адрес для каждого файла. */
    private const val UPSTREAM = "upstream"

    /** Итог обновления. */
    sealed interface Result {
        /** Скачано [files] изменившихся баз. */
        data class Updated(val files: Int) : Result
        /** Базы и так свежие. */
        data object Fresh : Result
        /** Проверять ещё рано или автообновление выключено. */
        data object Skipped : Result
        data class Failed(val error: String) : Result
    }

    /** Состояние для экрана: когда проверяли, от какого числа базы, откуда и сколько в них записей. */
    data class Info(
        val checkedAt: Long,
        val basesDate: Long,
        val source: String,
        val counts: Map<String, Int>,
        val error: String?,
    )

    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info.asStateFlow()

    private fun sp(context: Context) = context.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(context: Context): Info {
        val p = sp(context)
        val counts = runCatching {
            val o = JSONObject(p.getString("counts", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.optInt(it) }
        }.getOrDefault(emptyMap())
        return Info(
            checkedAt = p.getLong("checkedAt", 0),
            basesDate = p.getLong("basesDate", 0).takeIf { it > 0 } ?: lastUpdated(context),
            source = p.getString("source", "") ?: "",
            counts = counts,
            error = p.getString("error", null),
        ).also { _info.value = it }
    }

    /** Источники по порядку: если первый недоступен (например, заблокирован), берётся следующий. */
    fun sources(): List<String> {
        val custom = AppSettings.value.rulesSource.trim().trimEnd('/')
        if (custom.isNotEmpty()) return listOf(custom)
        val list = mutableListOf<String>()
        BuildConfig.RULES_BASE_URL.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let(list::add)
        val repo = BuildConfig.GITHUB_REPO
        list += "https://raw.githubusercontent.com/$repo/rules"
        list += "https://cdn.jsdelivr.net/gh/$repo@rules"
        list += UPSTREAM
        return list
    }

    private fun fileUrl(base: String, name: String): String {
        if (base != UPSTREAM) return "$base/$name"
        val repo = if (name.startsWith("geoip")) "sing-geoip" else "sing-geosite"
        return "https://cdn.jsdelivr.net/gh/SagerNet/$repo@rule-set/$name"
    }

    /** Адрес файла у первого источника — для подписи на экране. */
    fun urlFor(name: String): String = fileUrl(sources().first(), name)

    fun sourceTitle(base: String): String = when {
        base.isEmpty() -> "встроенные в приложение"
        base == UPSTREAM -> "официальные базы sing-box"
        base.startsWith("https://raw.githubusercontent.com/") -> "GitHub APEX VPN"
        base.startsWith("https://cdn.jsdelivr.net/") -> "зеркало jsDelivr"
        else -> base.removePrefix("https://")
    }

    fun remoteDir(context: Context) = File(context.filesDir, "rules-remote")

    /** Папка с базами для ядра: свежие скачанные, иначе встроенные в приложение. */
    fun activeDir(context: Context): String? {
        val remote = remoteDir(context)
        if (FILES.all { File(remote, it).length() > 0 }) {
            // Необязательной базы ещё нет среди скачанных — берём встроенную в приложение
            OPTIONAL.forEach { name ->
                val f = File(remote, name)
                if (f.length() == 0L) runCatching {
                    val part = File(remote, "$name.part")
                    context.assets.open("rules/$name").use { input -> part.outputStream().use { input.copyTo(it) } }
                    part.renameTo(f)
                }
            }
            return remote.path
        }
        return installBundled(context)
    }

    /** Встроенные базы копируются один раз на версию приложения, а не при каждом подключении. */
    private fun installBundled(context: Context): String? = runCatching {
        val dir = File(context.filesDir, "rules").apply { mkdirs() }
        val stamp = File(dir, ".version")
        val current = BuildConfig.VERSION_CODE.toString()
        val ready = stamp.exists() && stamp.readText() == current && FILES.all { File(dir, it).length() > 0 }
        if (!ready) {
            for (name in FILES) {
                val tmp = File(dir, "$name.tmp")
                context.assets.open("rules/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                check(tmp.renameTo(File(dir, name))) { "не удалось записать $name" }
            }
            // Необязательные — если есть в этой сборке
            for (name in OPTIONAL) runCatching {
                val tmp = File(dir, "$name.tmp")
                context.assets.open("rules/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(File(dir, name))
            }
            stamp.writeText(current)
        }
        dir.path
    }.getOrNull()

    /** Когда скачаны базы (0 — пока встроенные). */
    fun lastUpdated(context: Context): Long =
        FILES.minOfOrNull { File(remoteDir(context), it).lastModified() } ?: 0L

    private fun sha256(f: File): String? = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /**
     * Проверка раз в день (с rules.json) или раз в неделю (источник без него).
     * [force] — сразу, даже если автообновление выключено.
     */
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun update(context: Context, force: Boolean = false): Result = lock.withLock { withContext(Dispatchers.IO) {
        val p = sp(context)
        if (!force) {
            if (!AppSettings.value.rulesAuto) return@withContext Result.Skipped
            // Как часто — по настройке; источник без rules.json качает всё целиком, ему не чаще раза в сутки
            val chosen = AppSettings.value.rulesHours.coerceIn(6, 168) * 3600_000L
            val interval = if (p.getBoolean("hasManifest", true)) chosen else maxOf(chosen, DAY)
            val last = maxOf(p.getLong("checkedAt", 0), lastUpdated(context))
            if (System.currentTimeMillis() - last < interval) return@withContext Result.Skipped
        }
        var lastError = "нет источников"
        for (base in sources()) {
            val r = runCatching { updateFrom(context, base) }
            r.onSuccess { (result, manifest) ->
                p.edit {
                    putLong("checkedAt", System.currentTimeMillis())
                    putString("source", base)
                    putBoolean("hasManifest", manifest != null)
                    remove("error")
                    val date = manifest?.optLong("updated")?.takeIf { it > 0 }?.times(1000)
                    if (date != null) putLong("basesDate", date)
                    else if (result is Result.Updated) putLong("basesDate", System.currentTimeMillis())
                    manifest?.optJSONObject("files")?.let { files ->
                        val counts = JSONObject()
                        files.keys().forEach { k -> files.optJSONObject(k)?.optInt("count", -1)?.takeIf { it >= 0 }?.let { counts.put(k, it) } }
                        putString("counts", counts.toString())
                    }
                }
                load(context)
                return@withContext result
            }
            lastError = r.exceptionOrNull()?.message ?: "ошибка"
        }
        p.edit { putString("error", lastError) }
        load(context)
        Result.Failed(lastError)
    } }

    /** Необязательные базы из ветки «rules»: сколько обновилось. Ошибки — не страшно. */
    private fun updateOptional(base: String, files: JSONObject?, dir: File): Int {
        var n = 0
        for (name in OPTIONAL) runCatching {
            val want = files?.optJSONObject(name)?.optString("sha256").orEmpty()
            if (want.isEmpty()) return@runCatching // в ветке нет — пропускаем
            val local = File(dir, name)
            if (local.length() > 0 && sha256(local) == want) return@runCatching
            val part = File(dir, "$name.part")
            val c = Http.open(fileUrl(base, name), 60_000)
            try {
                if (c.responseCode != 200) error("$name: HTTP ${c.responseCode}")
                c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            val head = part.inputStream().use { st -> ByteArray(3).also { st.read(it) } }
            if (String(head) != "SRS" || sha256(part) != want) { part.delete(); error("повреждён $name") }
            if (part.renameTo(local)) n++
        }
        return n
    }

    /** Обновление из одного источника. Ошибка — исключение, чтобы перейти к следующему. */
    private fun updateFrom(context: Context, base: String): Pair<Result, JSONObject?> {
        val manifest = if (base == UPSTREAM) null else runCatching { JSONObject(Http.getText("$base/rules.json")) }.getOrNull()
        val files = manifest?.optJSONObject("files")
        val dir = remoteDir(context).apply { mkdirs() }
        // Что качать: с rules.json — только изменившиеся, без него — всё
        val need = FILES.filter { name ->
            val local = File(dir, name)
            val want = files?.optJSONObject(name)?.optString("sha256").orEmpty()
            !local.exists() || local.length() == 0L || want.isEmpty() || sha256(local) != want
        }
        if (need.isEmpty()) return Result.Fresh to manifest
        val tmp = File(context.cacheDir, "rules-tmp").apply { deleteRecursively(); mkdirs() }
        try {
            for (name in need) {
                val c = Http.open(fileUrl(base, name), 30_000)
                try {
                    if (c.responseCode != 200) error("$name: HTTP ${c.responseCode}")
                    c.inputStream.use { input -> File(tmp, name).outputStream().use { input.copyTo(it) } }
                } finally {
                    c.disconnect()
                }
                // В начале файла .srs — сигнатура «SRS»
                val head = File(tmp, name).inputStream().use { s -> ByteArray(3).also { s.read(it) } }
                check(String(head) == "SRS") { "повреждён $name" }
                val want = files?.optJSONObject(name)?.optString("sha256").orEmpty()
                check(want.isEmpty() || sha256(File(tmp, name)) == want) { "не совпала сумма $name" }
            }
            // Без rules.json сравниваем сами: одинаковые файлы — значит, базы свежие
            val changed = need.filter { name ->
                val local = File(dir, name)
                !local.exists() || sha256(local) != sha256(File(tmp, name))
            }
            // Сначала копия рядом, потом переименование: ядро никогда не прочитает файл наполовину
            for (name in changed) {
                val part = File(dir, "$name.part")
                File(tmp, name).copyTo(part, overwrite = true)
                check(part.renameTo(File(dir, name))) { "не удалось записать $name" }
            }
            // Неизменённые файлы тоже должны быть на месте — иначе ядро возьмёт встроенные
            if (!FILES.all { File(dir, it).length() > 0 }) error("скачаны не все базы")
            val extra = if (base == UPSTREAM) 0 else updateOptional(base, files, dir)
            val total = changed.size + extra
            return (if (total == 0) Result.Fresh else Result.Updated(total)) to manifest
        } finally {
            tmp.deleteRecursively()
        }
    }
}

// ─── Отчёты о сбоях: хранятся на телефоне, уходят только в «Написать в поддержку» ─

object CrashReporter {
    private fun dir(context: Context) = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                // «Отчёты об ошибках» выключены — ничего не сохраняем
                if (!runCatching { AppSettings.value.crashReports }.getOrDefault(true)) return@runCatching
                // Только техническая информация: без ссылки подписки, IP и списков сайтов
                val report = buildString {
                    appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                    appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("thread=${thread.name}")
                    appendLine()
                    append(error.stackTraceToString().take(20_000))
                }
                File(dir(app), "crash-${System.currentTimeMillis()}.txt").writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Сбой ядра (Go) закрывает приложение мимо обработчика Java — его след остаётся только
     * в stderr.log. Если там panic/fatal error с прошлого запуска, сохраняем как отчёт.
     */
    fun collectNativeCrash(context: Context, stderr: File) {
        runCatching {
            if (!stderr.exists() || stderr.length() == 0L) return@runCatching
            if (!runCatching { AppSettings.value.crashReports }.getOrDefault(true)) return@runCatching
            val text = stderr.readText().takeLast(30_000)
            val start = listOf("panic:", "fatal error:", "SIGSEGV", "SIGABRT").map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
                ?: return@runCatching
            val report = buildString {
                appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("type=native (ядро)")
                appendLine()
                append(text.substring(start).take(20_000))
            }
            File(dir(context), "crash-native-${System.currentTimeMillis()}.txt").writeText(report)
        }
        runCatching { stderr.delete() }
    }

    /** Отчёты хранятся только на телефоне (для «Написать в поддержку»); старше 30 дней удаляются. */
    fun cleanup(context: Context) {
        dir(context).listFiles().orEmpty()
            .filter { System.currentTimeMillis() - it.lastModified() > 30L * 24 * 3600 * 1000 }
            .forEach { it.delete() }
    }
}
