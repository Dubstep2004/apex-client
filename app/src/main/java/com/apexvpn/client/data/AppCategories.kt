package com.apexvpn.client.data

import android.content.pm.ApplicationInfo

/**
 * 3.12. Категории приложений для «Скрыть VPN»: банки, госуслуги, маркетплейсы, Яндекс, VK…
 * Сначала по известным пакетам и названиям, потом — по категории, которую указал сам разработчик в Google Play.
 */
object AppCategories {
    enum class Cat(val title: String, val emoji: String) {
        Banks("Банки и платежи", "🏦"),
        Gov("Госуслуги и город", "🏛"),
        Shops("Маркетплейсы и магазины", "🛒"),
        Yandex("Яндекс", "🟡"),
        Vk("VK и Mail.ru", "🔵"),
        Telecom("Операторы связи", "📶"),
        Delivery("Такси и доставка", "🚕"),
        Media("Видео, музыка, книги", "🎬"),
        Travel("Карты и поездки", "🗺"),
        Social("Мессенджеры и соцсети", "💬"),
        Games("Игры", "🎮"),
        Other("Другие", "📱"),
    }

    private val rules: List<Pair<Cat, List<String>>> = listOf(
        Cat.Yandex to listOf("ru.yandex.", "com.yandex.", "ru.kinopoisk", "ru.beru.", "ru.foodfox.", "яндекс", "yandex", "алиса", "кинопоиск"),
        Cat.Vk to listOf("com.vkontakte.", "com.vk.", "ru.ok.", "ru.mail.", "ru.oneme.", "com.uma.musicvk", "ru.vk.", "вконтакте", "одноклассники", "mail.ru", "rustore", "max"),
        Cat.Banks to listOf("sber", "tinkoff", "vtb", "alfabank", "gazprombank", "mirpay", "ozon.fintech", "raiffeisen", "sovcombank", "rosbank", "pochtabank",
            "банк", "сбер", "т-банк", "втб", "альфа", "mir pay", "домклик", "т-инвест", "инвест", "bank"),
        Cat.Gov to listOf("ru.rostel", "gosuslugi", "goskey", "ru.mos.", "госуслуги", "госключ", "мос.ру", "моя москва", "емиас", "налог", "парковки", "почта россии", "russianpost"),
        Cat.Telecom to listOf("ru.mts.", "megafon", "tele2", "beeline", "мтс", "мегафон", "билайн", "t2", "т2", "ростелеком"),
        Cat.Shops to listOf("ozon", "wildberries", "avito", "lamoda", "aliexpress", "dns.shop", "vkusvill", "megamarket", "magnit", "lenta", "perekrestok", "pyaterochka",
            "озон", "авито", "магнит", "пятёрочка", "пятерочка", "перекрёсток", "перекресток", "лента", "вкусвилл", "мегамаркет", "детский мир", "дикси", "спортмастер",
            "м.видео", "эльдорадо", "ситилинк", "лемана", "всеинструменты", "золотое яблоко", "самокат", "купер"),
        Cat.Delivery to listOf("taxi", "такси", "delivery", "доставка", "eda", "еда", "lavka", "лавка", "samokat", "kuper", "citymobil", "ситимобил"),
        Cat.Travel to listOf("2gis", "dgis", "2гис", "rzd", "ржд", "aviasales", "aeroflot", "аэрофлот", "tutu", "туту", "maps", "карты", "navi", "навигатор", "metro", "метро"),
        Cat.Media to listOf("rutube", "ivi", "иви", "wink", "litres", "литрес", "music", "музыка", "okko", "premier", "kion", "start.", "дзен", "zen", "youtube", "netflix", "spotify", "tiktok"),
        Cat.Social to listOf("telegram", "whatsapp", "viber", "discord", "instagram", "facebook", "twitter", "messenger", "мессенджер"),
    )

    /** Ключ с точкой — часть пакета; короткий (до 3 букв) — только всё название; остальные — начало или часть названия, латинские — и пакета. */
    private fun hit(k: String, p: String, l: String): Boolean = when {
        '.' in k && !k.contains(' ') && k.all { it.code < 128 } -> p.contains(k) || l.contains(k)
        k.length <= 3 -> l == k || l.startsWith("$k ")
        k.all { it.code < 128 } -> p.contains(k) || l.startsWith(k) || l.contains(k)
        else -> l.startsWith(k) || l.contains(k)
    }

    fun of(app: AppInfo): Cat {
        val p = app.packageName.lowercase()
        val l = app.label.trim().lowercase()
        rules.forEach { (cat, keys) -> if (keys.any { k -> hit(k, p, l) }) return cat }
        return when (app.category) {
            ApplicationInfo.CATEGORY_GAME -> Cat.Games
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_NEWS -> Cat.Media
            ApplicationInfo.CATEGORY_SOCIAL -> Cat.Social
            ApplicationInfo.CATEGORY_MAPS -> Cat.Travel
            else -> Cat.Other
        }
    }
}
