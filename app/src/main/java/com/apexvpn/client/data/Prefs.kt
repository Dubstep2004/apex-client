package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Простое хранилище настроек приложения. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("apex", Context.MODE_PRIVATE)
    }

    var subscriptionUrl: String
        get() = sp.getString("sub_url", "") ?: ""
        set(v) = sp.edit { putString("sub_url", v) }

    /** JSON-массив серверов из Apexcore.parseSubscription */
    var nodesJson: String
        get() = sp.getString("nodes", "[]") ?: "[]"
        set(v) = sp.edit { putString("nodes", v) }

    var selectedTag: String
        get() = sp.getString("selected", "auto") ?: "auto"
        set(v) = sp.edit { putString("selected", v) }

    var profileTitle: String
        get() = sp.getString("title", "") ?: ""
        set(v) = sp.edit { putString("title", v) }

    var trafficUsed: Long
        get() = sp.getLong("used", -1)
        set(v) = sp.edit { putLong("used", v) }

    var trafficTotal: Long
        get() = sp.getLong("total", -1)
        set(v) = sp.edit { putLong("total", v) }

    /** Время окончания подписки, секунды Unix (0 — бессрочно, -1 — неизвестно) */
    var expireAt: Long
        get() = sp.getLong("expire", -1)
        set(v) = sp.edit { putLong("expire", v) }

    var lastUpdated: Long
        get() = sp.getLong("updated", 0)
        set(v) = sp.edit { putLong("updated", v) }

    /** Интервал автообновления подписки, часы */
    var updateIntervalHours: Int
        get() = sp.getInt("interval", 12)
        set(v) = sp.edit { putInt("interval", v) }

    /** Последняя версия, о которой уже показали уведомление «Позже» */
    var dismissedUpdateCode: Int
        get() = sp.getInt("dismissed_update", 0)
        set(v) = sp.edit { putInt("dismissed_update", v) }

    var hideTemplateAt: Long
        get() = sp.getLong("hide_template_at", 0)
        set(v) = sp.edit { putLong("hide_template_at", v) }

    /** 3.18: настройки из веб-админки — docs/config/app.json (JSON), когда скачан, закрытый баннер */
    var appConfigJson: String
        get() = sp.getString("app_config", "") ?: ""
        set(v) = sp.edit { putString("app_config", v) }
    /** 3.19.8. День, когда «Обои дня» последний раз сменили обои (yyyyMMdd). */
    var wallAutoDay: String
        get() = sp.getString("wall_auto_day", "") ?: ""
        set(v) = sp.edit { putString("wall_auto_day", v) }
    /** 3.19.9. Избранные обои (JSON) */
    var wallFavs: String
        get() = sp.getString("wall_favs", "") ?: ""
        set(v) = sp.edit { putString("wall_favs", v) }
    /** 3.20. Какие живые обои последними скачали сами «Обои дня» — их можно удалить на следующий день. */
    var wallAutoLast: String
        get() = sp.getString("wall_auto_last", "") ?: ""
        set(v) = sp.edit { putString("wall_auto_last", v) }
    var appConfigAt: Long
        get() = sp.getLong("app_config_at", 0)
        set(v) = sp.edit { putLong("app_config_at", v) }
    /** 3.18.1: код предпросмотра (черновики из пульта видны только с ним) и показанное объявление */
    var previewCode: String
        get() = sp.getString("preview_code", "") ?: ""
        set(v) = sp.edit { putString("preview_code", v) }
    var announceSeen: String
        get() = sp.getString("announce_seen", "") ?: ""
        set(v) = sp.edit { putString("announce_seen", v) }
    /** 3.19. О каких техработах уже предупредили (ключи через перевод строки). */
    var maintNotified: String
        get() = sp.getString("maint_notified", "") ?: ""
        set(v) = sp.edit { putString("maint_notified", v) }
    var bannerDismissed: Int
        get() = sp.getInt("banner_dismissed", 0)
        set(v) = sp.edit { putInt("banner_dismissed", v) }

    /** 3.16: адреса подписки из docs/config/subscription.json (JSON) и когда скачан */
    var subHostsJson: String
        get() = sp.getString("sub_hosts", "") ?: ""
        set(v) = sp.edit { putString("sub_hosts", v) }
    var subHostsAt: Long
        get() = sp.getLong("sub_hosts_at", 0)
        set(v) = sp.edit { putLong("sub_hosts_at", v) }

    /** 3.10: список шаблона «Скрыть VPN», скачанный из интернета (JSON) */
    var hideTemplateJson: String
        get() = sp.getString("hide_template", "") ?: ""
        set(v) = sp.edit { putString("hide_template", v) }

    /** 3.9.1: обновление запускали из открытого приложения — после установки открыть снова */
    var reopenAfterUpdate: Boolean
        get() = sp.getBoolean("reopen_after_update", false)
        set(v) = sp.edit { putBoolean("reopen_after_update", v) }

    /** 3.7: версия APEX Watch на часах (часы сообщают при запуске) */
    var watchVersionCode: Int
        get() = sp.getInt("watch_version", 0)
        set(v) = sp.edit { putInt("watch_version", v) }

    /** 3.5: результаты проверки сервисов по серверам (JSON tag → результат) */
    var serviceChecks: String
        get() = sp.getString("service_checks", "{}") ?: "{}"
        set(v) = sp.edit { putString("service_checks", v) }

    /** 3.4: VPN был включён перед установкой обновления — включить снова после замены приложения */
    var reconnectAfterUpdate: Boolean
        get() = sp.getBoolean("reconnect_after_update", false)
        set(v) = sp.edit { putBoolean("reconnect_after_update", v) }

    /** Версия, для которой пользователь уже видел «Что нового» */
    var lastSeenVersionCode: Int
        get() = sp.getInt("seen_version", 0)
        set(v) = sp.edit { putInt("seen_version", v) }

    /** Версия приложения, которой загружены серверы (новые поля — после обновления перезагрузить) */
    var nodesVersion: Int
        get() = sp.getInt("nodes_version", 0)
        set(v) = sp.edit { putInt("nodes_version", v) }

    /** Закрытое объявление из Remnawave (по тексту): новое объявление покажется снова */
    var dismissedAnnounce: String
        get() = sp.getString("announce_closed", "") ?: ""
        set(v) = sp.edit { putString("announce_closed", v) }

    var announce: String
        get() = sp.getString("announce", "") ?: ""
        set(v) = sp.edit { putString("announce", v) }

    /** 434. Картинка и кнопка объявления из панели */
    var announceImage: String
        get() = sp.getString("announce_img", "") ?: ""
        set(v) = sp.edit { putString("announce_img", v) }
    var announceButton: String
        get() = sp.getString("announce_btn", "") ?: ""
        set(v) = sp.edit { putString("announce_btn", v) }
    var announceUrl: String
        get() = sp.getString("announce_url", "") ?: ""
        set(v) = sp.edit { putString("announce_url", v) }

    var supportUrl: String
        get() = sp.getString("support_url", "") ?: ""
        set(v) = sp.edit { putString("support_url", v) }

    var webPageUrl: String
        get() = sp.getString("web_url", "") ?: ""
        set(v) = sp.edit { putString("web_url", v) }

    /**
     * Свои локальные порты для каждого устройства (API ядра и SOCKS для Xray): фиксированные
     * 9095/10910 могли быть заняты другим приложением — тогда VPN не включался.
     */
    val localPortBase: Int
        get() = sp.getInt("port_base", 0).takeIf { it in 20000..50000 }
            ?: (20000 + java.security.SecureRandom().nextInt(3000) * 10).also { sp.edit { putInt("port_base", it) } }
    val clashApiPort: Int get() = localPortBase
    val xrayBasePort: Int get() = localPortBase + 10
    /** 197. Локальный DNS счётчика рекламы */
    val adsDnsPort: Int get() = localPortBase + 5
    /** Локальный SOCKS ядра для замеров DNS через сервер (DNS «Авто») */
    val probePort: Int get() = localPortBase + 7

    /** Секрет локального SOCKS между sing-box и Xray (постоянный, чтобы Xray не перезапускался зря) */
    val xrayAuth: String
        get() = sp.getString("xray_auth", null) ?: java.util.UUID.randomUUID().toString().also {
            sp.edit { putString("xray_auth", it) }
        }

    /** Постоянный анонимный идентификатор устройства для панелей подписок (HWID) */
    val deviceId: String
        get() = sp.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
            sp.edit { putString("device_id", it) }
        }

    /** Последний замер пинга (tag → мс) — порядок серверов для автопилота при подключении. */
    fun lastPings(): Map<String, Int> = runCatching {
        val o = org.json.JSONObject(sp.getString("last_pings", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun clearMeasurements() = sp.edit { remove("last_pings"); remove("auto_dns") }

    fun saveLastPings(map: Map<String, Int>) {
        if (map.values.none { it > 0 }) return
        sp.edit { putString("last_pings", org.json.JSONObject(map.filterValues { it > 0 }).toString()) }
    }

    /** Авто-выбор DNS: последний победитель (cloudflare | google | quad9) */
    var autoDnsBest: String
        get() = sp.getString("auto_dns", "") ?: ""
        set(v) = sp.edit { putString("auto_dns", v) }

    /** 320. Знакомство с приложением уже показано */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit { putBoolean("onboarded", v) }

    /** 382. Сколько раз VPN успешно подключался (для конфетти на юбилеях) */
    var connectCount: Int
        get() = sp.getInt("connect_count", 0)
        set(v) = sp.edit { putInt("connect_count", v) }

    /** Один раз: у тех, кто уже пользовался приложением до 2.1, подключения были. */
    fun initConnectCount() {
        if (sp.getBoolean("count_init", false)) return
        if (subscriptionUrl.isNotEmpty() && lastSeenVersionCode in 1..31 && connectCount == 0) connectCount = 1
        sp.edit { putBoolean("count_init", true) }
    }

    fun clearSubscription() = sp.edit {
        remove("sub_url"); remove("nodes"); remove("selected"); remove("title")
        remove("used"); remove("total"); remove("expire"); remove("updated")
        remove("announce"); remove("announce_img"); remove("announce_btn"); remove("announce_url"); remove("support_url"); remove("web_url")
    }
}
