package com.apexvpn.client.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 3.19.7. Разбор docs/config/app.json (пульт) — без Android, чтобы проверять автотестами.
 * Битый файл — null, приложение работает как обычно.
 */
object AppConfigJson {
    private fun strings(a: org.json.JSONArray?) = a?.let { arr -> (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.toSet() }.orEmpty()

    private fun parseBanner(b: JSONObject, i: Int): AppConfig.Banner? {
        val text = b.optString("text").trim().take(300)
        if (text.isEmpty()) return null
        // Старый формат: until = дата «до конца дня»
        val oldUntil = b.optString("until").let { d -> runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!.time + 24 * 3_600_000L }.getOrDefault(0L) }
        return AppConfig.Banner(
            id = b.optString("id").ifBlank { "b$i-" + text.hashCode() },
            text = text, level = b.optString("level", "info"), url = b.optString("url").trim().takeIf { it.startsWith("https://") }.orEmpty(),
            from = b.optLong("from", 0), to = b.optLong("to", 0).takeIf { it > 0 } ?: oldUntil,
            minCode = b.optInt("minCode", 0), maxCode = b.optInt("maxCode", 0),
            devices = strings(b.optJSONArray("devices")), countries = strings(b.optJSONArray("countries")).map { it.uppercase() }.toSet(),
            preview = b.optBoolean("preview", false),
        )
    }

    fun parse(text: String): AppConfig.Config? = runCatching {
        if (text.isBlank()) return null
        val o = JSONObject(text)
        val banners = buildList {
            o.optJSONArray("banners")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { parseBanner(it, i) }?.let(::add) }
            o.optJSONObject("banner")?.let { parseBanner(it, 99) }?.let(::add)
        }
        val srv = o.optJSONObject("servers")
        val status = srv?.optJSONObject("status")
        val servers = status?.keys()?.asSequence()?.mapNotNull { k ->
            status.optJSONObject(k)?.let { s -> k to AppConfig.ServerState(s.optString("state"), s.optString("note").take(120), s.optLong("until", 0), s.optBoolean("preview", false), s.optLong("from", 0)) }
        }?.toMap().orEmpty()
        val links = o.optJSONObject("links")
        val upd = o.optJSONObject("update")
        val feats = o.optJSONObject("features")
        val an = o.optJSONObject("announce")?.takeIf { it.optString("title").isNotBlank() || it.optString("text").isNotBlank() }?.let { a ->
            AppConfig.Announce(
                id = a.optString("id").ifBlank { (a.optString("title") + a.optString("text")).hashCode().toString() },
                title = a.optString("title").take(80), text = a.optString("text").take(1500),
                image = a.optString("image").trim().takeIf { it.startsWith("https://") }.orEmpty(),
                url = a.optString("url").trim().takeIf { it.startsWith("https://") }.orEmpty(),
                button = a.optString("button").ifBlank { "Подробнее" }.take(30),
                minCode = a.optInt("minCode", 0), maxCode = a.optInt("maxCode", 0), preview = a.optBoolean("preview", false),
                from = a.optLong("from", 0), to = a.optLong("to", 0),
            )
        }
        AppConfig.Config(
            banners = banners,
            servers = servers,
            recommended = srv?.optString("recommended").orEmpty(),
            hidden = strings(srv?.optJSONArray("hidden")),
            support = links?.optString("support").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            channel = links?.optString("channel").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            site = links?.optString("site").orEmpty().takeIf { it.startsWith("https://") }.orEmpty(),
            requiredCode = upd?.optInt("required", 0) ?: 0,
            revoked = upd?.optJSONArray("revoked")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() }.orEmpty(),
            features = feats?.keys()?.asSequence()?.associate { it to feats.optBoolean(it, true) }.orEmpty(),
            announce = an,
            previewCode = o.optString("previewCode"),
        )
    }.getOrNull()


    /**
     * 3.18.3. Что из настроек влияет на ядро: пометки и скрытие серверов, выключенные функции, код предпросмотра.
     * Баннер, объявление и ссылки ядро не трогают — из-за них VPN не перезагружается.
     */
    fun coreKey(c: AppConfig.Config) = listOf(
        c.servers.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.state}/${it.value.from}/${it.value.until}/${it.value.preview}" },
        c.hidden.sorted().joinToString(), c.recommended,
        c.features.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" },
        c.previewCode,
    ).joinToString("|")
}
