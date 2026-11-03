package com.apexvpn.client.data

/** Режим маршрутизации. id совпадают с ядром (core/apexcore/config.go). */
enum class RouteMode(val id: String, val title: String, val subtitle: String) {
    Smart("smart", "Умный", "Российские сайты напрямую и быстро, зарубежные через VPN"),
    All("all", "Всё через VPN", "Весь трафик телефона идёт через сервер"),
    Selected("selected", "Только выбранные", "Через VPN только сервисы ниже, остальное напрямую");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Smart
    }
}

/**
 * Сервис для переключателя «Через VPN». id совпадают с каталогом в ядре
 * (core/apexcore/services.go). Значок — монограмма на цветной плашке.
 */
data class ServiceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val color: Long,
    val glyph: String,
    val defaultOn: Boolean = true,
)

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
        ServiceItem("x", "X", "Бывший Twitter", 0xFF1F1F24, "𝕏"),
        ServiceItem("twitch", "Twitch", "Стримы", 0xFF9146FF, "▣"),
        // Новые сервисы
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

/**
 * Приложения банков и госуслуг: их исключаем из VPN, чтобы они видели
 * обычную сеть телефона и не ругались на VPN.
 */
object BankApps {
    // Список можно дополнить: пакет приложения есть в адресе его страницы в Google Play (?id=…)
    val packages = listOf(
        "ru.sberbankmobile",                       // СберБанк Онлайн
        "com.idamob.tinkoff.android",              // Т-Банк
        "ru.tinkoff.investing",                    // Т-Инвестиции
        "ru.vtb24.mobilebanking.android",          // ВТБ Онлайн
        "ru.alfabank.mobile.android",              // Альфа-Банк
        "ru.gazprombank.android.mobilebank.app",   // Газпромбанк
        "ru.rostel",                               // Госуслуги
        "ru.ozon.app.android",                     // Ozon
        "ru.ozon.fintech.finance",                 // Ozon Банк
        "com.wildberries.ru",                      // Wildberries
        "ru.nspk.mirpay",                          // Mir Pay
    )

    const val SUMMARY = "Сбер, Т-Банк, ВТБ, Госуслуги, Ozon"
}

/** Публичный DNS для зарубежных запросов (идёт через VPN). */
enum class DnsChoice(val id: String, val title: String, val subtitle: String) {
    /** Сам выбирает самый быстрый: Cloudflare, Google или Quad9 */
    Auto("auto", "Авто", "Самый быстрый из 6 DNS (DoH, DoT, DoQ) — свой для каждой сети"),
    Cloudflare("cloudflare", "Cloudflare", "1.1.1.1 — быстрый"),
    Google("google", "Google", "8.8.8.8"),
    Quad9("quad9", "Quad9", "9.9.9.9 — защита от вредоносных сайтов"),
    AdGuard("adguard", "AdGuard", "Блокирует рекламу на уровне DNS"),
    Mullvad("mullvad", "Mullvad", "Без журналов, приватный"),
    OpenDns("opendns", "OpenDNS", "Cisco — быстрый и надёжный"),
    ControlD("controld", "Control D", "Быстрый, без фильтров"),
    NextDns("nextdns", "NextDNS", "Приватный, без настройки"),
    Yandex("yandex", "Яндекс DNS", "Базовый — для российских сетей"),
    Comss("comss", "Comss.one", "Открывает ИИ-сервисы, недоступные в России"),
    Custom("custom", "Свой DNS", "Любой адрес DNS-over-HTTPS");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Cloudflare
    }
}

/** Профиль подключения: частота проверки серверов и поведение при переключении. */
enum class ConnProfile(val id: String, val title: String, val subtitle: String) {
    Normal("normal", "Обычный", "Баланс скорости и стабильности"),
    Gaming("gaming", "Игры", "Самый низкий пинг, игры не обрываются при смене сервера"),
    Calls("calls", "Звонки", "Стабильный сервер для Telegram, WhatsApp, Discord"),
    Battery("battery", "Экономия батареи", "Редкие проверки серверов");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** Какие приложения идут через VPN. */
enum class AppsMode(val id: String, val title: String, val subtitle: String) {
    AllApps("all", "Все приложения", "Через VPN работает весь телефон"),
    Exclude("exclude", "Все, кроме выбранных", "Выбранные приложения идут мимо VPN"),
    Include("include", "Только выбранные", "Через VPN только отмеченные приложения");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: AllApps
    }
}

enum class ThemeMode(val id: String, val title: String) {
    System("system", "Как в системе"),
    Dark("dark", "Тёмная"),
    Light("light", "Светлая"),
    Amoled("amoled", "AMOLED");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Dark
    }
}

enum class AccentColor(val id: String, val title: String, val argb: Long) {
    Violet("violet", "Фиолетовый", 0xFF7B6CFF),
    Blue("blue", "Синий", 0xFF3B82F6),
    Green("green", "Зелёный", 0xFF22C55E),
    Turquoise("turquoise", "Бирюзовый", 0xFF14B8C4),
    Pink("pink", "Розовый", 0xFFEC4899),
    /** 216. Любой цвет: значение в Settings.customAccent */
    Custom("custom", "Свой", 0xFF7B6CFF);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Violet
    }
}

/** Каким ядром подключать серверы. */
enum class CoreMode(val id: String, val title: String, val subtitle: String) {
    Auto("auto", "Авто", "sing-box, а Xray — для XHTTP, mKCP и подобных"),
    Xray("xray", "Только Xray", "Как в Happ: всё, что умеет Xray, — через Xray"),
    SingBox("singbox", "Только sing-box", "Без Xray: серверы XHTTP и mKCP скрыты");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Auto
    }
}

/** Дизайн приложения (2026). Меняет цвета, форму карточек, фон, шрифт и главный экран. */
enum class Design(val id: String, val title: String, val subtitle: String) {
    /** Название идёт за версией приложения: «APEX 2.3», «APEX 2.4»… */
    Apex2("apex2", "APEX " + com.apexvpn.client.BuildConfig.VERSION_NAME.split('.').take(2).joinToString("."), "Фирменный облик 2026: крупные карточки и мягкое свечение"),
    Classic("classic", "Классика", "Строгий тёмный APEX"),
    Glass("glass", "Liquid Glass 2.0", "Настоящее стекло: блики двигаются при наклоне телефона"),
    Expressive("expressive", "Material Expressive", "Цвета от обоев, крупные формы"),
    Neon("neon", "Neon Cyber", "Неоновое свечение на чёрном"),
    Minimal("minimal", "Минимализм", "Чёрно-белый, ничего лишнего"),
    Aurora("aurora", "Aurora", "Живой переливающийся фон"),
    Bento("bento", "Бенто", "Главный экран из плиток"),
    Terminal("terminal", "Ретро-терминал", "Зелёный моноширинный текст"),
    Pastel("pastel", "Пастель", "Мягкие тёплые цвета"),
    Steampunk("steampunk", "Стимпанк", "Латунь, медь и шестерёнки"),
    /** 430 */
    Cosmos("cosmos", "Космос", "Звёзды, туманность и планеты вокруг щита"),
    /** 431 */
    Japan("japan", "Японский минимализм", "Бумага, тушь и красный акцент"),
    /** 3.13. Новые дизайны */
    Carbon("carbon", "Карбон", "Графит и текстура углеволокна, красный акцент"),
    Sakura("sakura", "Сакура", "Розовая бумага и падающие лепестки"),
    Matrix("matrix", "Матрица", "Зелёный дождь символов на чёрном"),
    Luxe("luxe", "Люкс", "Чёрный бархат и золото"),
    Ocean("ocean", "Океан", "Глубокая синева и медленные волны"),
    /** 3.13. Свой дизайн: цвета, скругления, стекло — в «Оформлении» */
    Custom("custom", "Свой дизайн", "Ваши цвета, скругления и стекло"),
    /** 387. Секретная тема — открывается 7 нажатиями на логотип */
    Synthwave("synthwave", "Синтвейв", "Секретная тема: неоновый закат 80-х");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Classic
    }
}

/** Свой список «Через VPN»: название, домены/IP (по одному на строку) и включён ли он. */
data class CustomList(
    val id: String,
    val name: String,
    val items: List<String>,
    val enabled: Boolean = true,
    val color: Long = 0xFF7B6CFF,
)
