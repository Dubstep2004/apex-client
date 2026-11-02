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
    private val exactLabels = setOf("max", "dns", "t2", "т2", "дзен", "vk", "ок", "wink", "cudy", "tether", "циан")

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
    )

    const val TITLE = "Российские приложения"
    const val SUBTITLE = "Яндекс, VK, Госуслуги, банки, маркетплейсы, операторы — те, что проверяют VPN"

    /** Подходит ли установленное приложение под шаблон. */
    fun matches(packageName: String, label: String): Boolean {
        if (packageName in packages) return true
        val l = label.trim().lowercase()
        if (l in exactLabels) return true
        return labelPrefixes.any { l.startsWith(it) }
    }

    /** Пакеты установленных приложений из шаблона (без самого APEX VPN). */
    fun installed(apps: List<AppInfo>, self: String): List<String> =
        apps.filter { it.packageName != self && matches(it.packageName, it.label) }.map { it.packageName }
}
