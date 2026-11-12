package com.apexvpn.client.data

/**
 * Готовый шаблон «Скрыть VPN»: российские приложения, которые проверяют, включён ли VPN
 * (по сообщениям СМИ и пользователей за 2026 год), и часто не работают с ним.
 * Ищем среди установленных и по пакету, и по названию — у части приложений пакет меняется.
 */
object HideTemplates {
    /** Известные пакеты (Google Play / RuStore). */
    private val packages = setOf(
        // Яндекс (у всех приложений Яндекса общая проверка VPN)
        "ru.yandex.searchplugin", "com.yandex.browser", "ru.yandex.yandexmaps", "ru.yandex.yandexnavi",
        "ru.yandex.taxi", "ru.beru.android", "ru.yandex.music", "ru.kinopoisk", "ru.yandex.mail",
        "ru.yandex.disk", "ru.yandex.weatherplugin", "ru.foodfox.client", "com.yandex.bank",
        "ru.yandex.market", "com.yandex.lavka", "ru.yandex.lavka", "ru.yandex.translate",
        // VK и Mail.ru
        "com.vkontakte.android", "com.vk.im", "com.vk.vkvideo", "com.uma.musicvk", "ru.ok.android",
        "ru.mail.mailapp", "ru.oneme.app", "ru.vk.store", "com.perm.kate_new_6",
        // Госуслуги и платежи
        "ru.rostel", "ru.nspk.mirpay", "ru.sberbankmobile", "com.idamob.tinkoff.android",
        "ru.tinkoff.investing", "ru.vtb24.mobilebanking.android", "ru.alfabank.mobile.android",
        "ru.gazprombank.android.mobilebank.app", "ru.ozon.fintech.finance",
        // Маркетплейсы и магазины
        "ru.ozon.app.android", "com.wildberries.ru", "com.avito.android", "com.lamoda.lite",
        "ru.vkusvill", "com.alibaba.aliexpresshd", "ru.dns.shop.android",
        // Связь
        "ru.mts.mymts", "ru.megafon.mlk", "ru.tele2.mytele2",
        // Остальное
        "ru.dublgis.dgismobile", "ru.rutube.app", "ru.rt.video.app.mobile", "ru.litres.android",
        "ru.ivi.client", "ru.rzd.pass", "ru.aviasales", "ru.hh.android", "ru.cian.main",
        "com.octopod.russianpost.client.android", "com.tplink.tether",
    )

    /** Название целиком (без учёта регистра). */
    private val exactLabels = setOf("max", "dns", "t2", "т2", "дзен", "vk", "ок", "wink", "cudy", "tether", "циан", "ivi", "mir pay", "max мессенджер")

    /** Название начинается с… */
    private val labelPrefixes = listOf(
        "яндекс", "yandex", "алиса", "кинопоиск", "госуслуги", "ozon", "озон", "wildberries", "авито", "avito",
        "vk ", "вконтакте", "одноклассники", "rustore", "сбер", "т-банк", "т-инвест", "втб", "альфа",
        "газпромбанк", "mir pay", "мегамаркет", "магнит", "пятёрочка", "пятерочка", "перекрёсток", "перекресток",
        "почта россии", "мой ростелеком", "ростелеком", "мой мтс", "мтс", "мегафон", "мой t2", "2гис",
        "rutube", "литрес", "lamoda", "самокат", "лента", "вкусвилл", "детский мир", "дикси",
        "всеинструменты", "ржд", "aviasales", "headhunter", "hh.ru", "иви", "лемана", "аэрофлот",
        "aliexpress", "kate mobile", "гдепосылка", "wikkeo", "sova v re", "почта mail", "mail.ru",
        "купер", "мегамаркет", "золотое яблоко", "спортмастер", "м.видео", "эльдорадо", "ситилинк",
        // 3.10: по спискам СМИ (апрель–июль 2026)
        "парковки", "магнит", "дзен", "мтс банк", "почта банк", "совкомбанк", "росбанк", "райффайзен",
        "домклик", "госключ", "мос.ру", "моя москва", "емиас", "налоги фл", "кинопоиск", "иви", "wink",
        "яндекс go", "яндекс пэй", "yandex pay", "мегафон", "билайн", "мой билайн", "литрес",
    )

    const val TITLE = "Российские приложения"
    const val SUBTITLE = "Яндекс, VK, Госуслуги, банки, маркетплейсы, операторы — те, что проверяют VPN"

    // ─── 3.10. Шаблон обновляется из интернета ──────────────────────────
    // Файл docs/templates/hide-vpn.json в репозитории: новые приложения, которые начали проверять VPN,
    // добавляются туда — и приходят всем без новой версии приложения.
    private const val REMOTE = "https://raw.githubusercontent.com/${com.apexvpn.client.BuildConfig.GITHUB_REPO}/main/docs/templates/hide-vpn.json"
    private const val MIRROR = "https://cdn.jsdelivr.net/gh/${com.apexvpn.client.BuildConfig.GITHUB_REPO}@main/docs/templates/hide-vpn.json"

    private data class Remote(val version: Int, val updated: String, val packages: Set<String>, val exact: Set<String>, val prefixes: List<String>)

    @Volatile private var remote: Remote? = null

    private fun parse(text: String): Remote? = runCatching {
        val o = org.json.JSONObject(text)
        fun arr(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
        Remote(o.optInt("version"), o.optString("updated"), arr("packages").toSet(), arr("exactLabels").map { it.lowercase() }.toSet(), arr("labelPrefixes").map { it.lowercase() })
    }.getOrNull()

    private fun cached(): Remote? = remote ?: Prefs.hideTemplateJson.takeIf { it.isNotBlank() }?.let(::parse)?.also { remote = it }

    /** Версия и дата загруженного из интернета списка (для подписи), null — только встроенный. */
    fun remoteInfo(): String? = cached()?.let { "список №${it.version}" + (if (it.updated.isNotBlank()) " от ${it.updated}" else "") }

    /** Скачать свежий список (раз в сутки из фоновой проверки). true — список изменился. */
    suspend fun refresh(force: Boolean = false): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Список меняется редко — не чаще раза в 12 часов (фоновая проверка идёт каждый час)
        if (!force && cached() != null && System.currentTimeMillis() - Prefs.hideTemplateAt < 12 * 3_600_000L) return@withContext false
        val text = listOf(REMOTE, MIRROR).firstNotNullOfOrNull { url ->
            runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
            }.getOrNull()?.takeIf { parse(it) != null }
        } ?: return@withContext false
        val r = parse(text)!!
        val old = cached()
        Prefs.hideTemplateJson = text
        Prefs.hideTemplateAt = System.currentTimeMillis()
        remote = r
        old == null || old.version != r.version
    }

    /** Подходит ли установленное приложение под шаблон (встроенный список + из интернета). */
    fun matches(packageName: String, label: String): Boolean {
        val r = cached()
        if (packageName in packages || r?.packages?.contains(packageName) == true) return true
        val l = label.trim().lowercase()
        if (l in exactLabels || r?.exact?.contains(l) == true) return true
        return labelPrefixes.any { l.startsWith(it) } || r?.prefixes?.any { l.startsWith(it) } == true
    }

    /**
     * «Обновлять автоматически»: приложения из шаблона, которых ещё нет в «Скрыть VPN»
     * (и которые пользователь сам не убирал), добавляются сами. Ответ — сколько добавили.
     */
    suspend fun autoApply(context: android.content.Context): Int {
        val s = AppSettings.value
        if (!s.hideTemplateAuto) return 0
        val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), android.content.pm.PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
                .map { AppInfo(it.packageName, it.loadLabel(pm).toString(), null) } // без значков — быстро и без лишней памяти
        }
        val add = installed(apps, context.packageName).filter { it !in s.hiddenApps && it !in s.hideTemplateSkip }
        if (add.isNotEmpty()) AppSettings.update { copy(hiddenApps = hiddenApps + add) }
        return add.size
    }

    /** Пакеты установленных приложений из шаблона (без самого APEX CLIENT). */
    fun installed(apps: List<AppInfo>, self: String): List<String> =
        apps.filter { it.packageName != self && matches(it.packageName, it.label) }.map { it.packageName }
}
