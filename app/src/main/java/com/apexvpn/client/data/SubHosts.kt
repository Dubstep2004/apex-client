package com.apexvpn.client.data

import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.16. Адрес подписки берётся из файла docs/config/subscription.json в репозитории:
 *  - hosts — разрешённые адреса подписки (первый — основной, его показывают подсказки);
 *  - moved — «старый → новый»: сохранённая ссылка на старом адресе переносится сама (путь и токен те же).
 * Сменили домен — поправьте файл на GitHub, приложения подхватят его при запуске и раз в 6 часов.
 * Без интернета или если файл не скачался — работает адрес, вшитый в приложение при сборке.
 */
object SubHosts {
    private const val PATH = "docs/config/subscription.json"
    private val sources = listOf(
        "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/$PATH",
        "https://cdn.jsdelivr.net/gh/${BuildConfig.GITHUB_REPO}@main/$PATH",
    )
    private val domain = Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$""")

    private data class Config(val hosts: List<String>, val moved: Map<String, String>)

    @Volatile private var config: Config? = null

    private fun parse(text: String): Config? = runCatching {
        val o = JSONObject(text)
        val a = o.getJSONArray("hosts")
        val hosts = (0 until a.length()).map { a.getString(it).trim().lowercase() }.filter { domain.matches(it) }
        val m = o.optJSONObject("moved")
        val moved = m?.keys()?.asSequence()?.associate { it.trim().lowercase() to m.getString(it).trim().lowercase() }
            ?.filter { (k, v) -> domain.matches(k) && v in hosts }.orEmpty()
        if (hosts.isEmpty()) null else Config(hosts, moved)
    }.getOrNull()

    private fun cached(): Config? = config ?: Prefs.subHostsJson.takeIf { it.isNotBlank() }?.let(::parse)?.also { config = it }

    /** Основной адрес — для подсказок «https://…/». */
    fun primary(): String = cached()?.hosts?.firstOrNull() ?: BuildConfig.SUBSCRIPTION_HOST

    /** Все разрешённые адреса (из файла на GitHub + вшитый при сборке). */
    fun allowed(): Set<String> = (cached()?.hosts.orEmpty() + BuildConfig.SUBSCRIPTION_HOST.lowercase()).toSet()

    fun isAllowed(host: String?): Boolean = host != null && host.lowercase() in allowed()

    /** Старые адреса (из moved) — ссылки на них тоже узнаём и переписываем на новый. */
    fun movedFrom(): Set<String> = (cached()?.moved?.keys.orEmpty() + "sub.knownhosts.ru").toSet()

    /** Ссылка на старом адресе → тот же путь на новом (для QR-кодов и старых приглашений). */
    fun rewrite(url: String): String {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return url
        val to = cached()?.moved?.get(host) ?: (if (host == "sub.knownhosts.ru") "sub.knownhosts.xyz" else null) ?: return url
        return url.replaceFirst(Regex("(?i)^https://" + Regex.escape(host)), "https://$to")
    }

    /** Сохранённая ссылка на старом адресе → тот же путь на новом. true — перенесли. */
    fun migrate(): Boolean {
        val url = Prefs.subscriptionUrl
        if (url.isBlank()) return false
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        val to = cached()?.moved?.get(host) ?: return false
        Prefs.subscriptionUrl = url.replaceFirst(Regex("(?i)^https://" + Regex.escape(host)), "https://$to")
        return true
    }

    /** Скачать файл (не чаще раза в 6 часов, если не force). true — ссылку перенесли на новый адрес. */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && cached() != null && System.currentTimeMillis() - Prefs.subHostsAt < 6 * 3_600_000L) return@withContext migrate()
        val text = sources.firstNotNullOfOrNull { src ->
            runCatching {
                val c = URL(src).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
            }.getOrNull()?.takeIf { parse(it) != null }
        }
        if (text != null) {
            Prefs.subHostsJson = text
            Prefs.subHostsAt = System.currentTimeMillis()
            config = parse(text)
        }
        migrate()
    }
}
