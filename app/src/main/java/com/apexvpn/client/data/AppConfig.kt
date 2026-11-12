package com.apexvpn.client.data

import com.apexvpn.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 3.18. Управление приложением из веб-админки (docs/admin) — файл docs/config/app.json на GitHub.
 * Без своего сервера и без новых версий: баннер для всех, статус серверов, ссылки,
 * обязательное обновление, отзыв версии, экстренное выключение функций.
 * Нет файла или он битый — всё работает как обычно.
 */
object AppConfig {
    private const val PATH = "docs/config/app.json"
    private val sources = listOf(
        "https://raw.githubusercontent.com/${BuildConfig.GITHUB_REPO}/main/$PATH",
        "https://cdn.jsdelivr.net/gh/${BuildConfig.GITHUB_REPO}@main/$PATH",
    )

    /**
     * Баннер. 3.18.1: расписание (from/to — время в мс), устройства (phone / tv / watch),
     * страна оператора (RU…), версии, «предпросмотр» — только для телефонов с кодом.
     */
    data class Banner(
        val id: String, val text: String, val level: String, val url: String,
        val from: Long = 0, val to: Long = 0, val minCode: Int = 0, val maxCode: Int = 0,
        val devices: Set<String> = emptySet(), val countries: Set<String> = emptySet(), val preview: Boolean = false,
    )
    data class ServerState(val state: String, val note: String, val until: Long = 0, val preview: Boolean = false, val from: Long = 0) // maintenance | slow; 3.19: from — начало по расписанию
    /** Большое окно «Что нового» / объявление — показывается один раз. */
    data class Announce(
        val id: String, val title: String, val text: String, val image: String, val url: String, val button: String,
        val minCode: Int = 0, val maxCode: Int = 0, val preview: Boolean = false,
        val from: Long = 0, val to: Long = 0, // 3.18.4: показать по расписанию
    )
    data class Config(
        val banners: List<Banner> = emptyList(),
        val servers: Map<String, ServerState> = emptyMap(),
        val recommended: String = "",
        val hidden: Set<String> = emptySet(),
        val support: String = "",
        val channel: String = "",
        val site: String = "",
        val requiredCode: Int = 0,
        val revoked: Set<Int> = emptySet(),
        val features: Map<String, Boolean> = emptyMap(),
        val announce: Announce? = null,
        val previewCode: String = "",
        val rev: Int = 0, // меняется, когда ввели код предпросмотра — чтобы экраны пересчитались
    ) {
        /** Функция включена (по умолчанию — да). */
        fun on(feature: String) = features[feature] != false
        /** На этом телефоне введён код предпросмотра — видны черновики из пульта. */
        val previewer get() = previewCode.isNotBlank() && Prefs.previewCode.trim() == previewCode.trim()
        // Сервер ищется по части названия без флага: «Германия» — все немецкие, «Обход №1» — один сервер
        private fun hit(title: String, key: String) = key.isNotBlank() && title.contains(key.trim(), ignoreCase = true)
        fun stateOf(title: String): ServerState? {
            val now = System.currentTimeMillis()
            return servers.entries.firstOrNull { (k, v) -> hit(title, k) && (v.from == 0L || now >= v.from) && (v.until == 0L || now < v.until) && (!v.preview || previewer) }?.value
        }
        /** 3.19. Пометки, которые действуют прямо сейчас — чтобы перестроить ядро, когда техработы начались или закончились. */
        fun activeKey(now: Long = System.currentTimeMillis()): String = servers.entries
            .filter { (_, v) -> (v.from == 0L || now >= v.from) && (v.until == 0L || now < v.until) && (!v.preview || previewer) }
            .sortedBy { it.key }.joinToString { it.key + "=" + it.value.state }
        /** 3.19. Техработы на сервере с таким названием: идут сейчас или начнутся в ближайшие [within] мс. */
        fun maintenanceFor(title: String, within: Long, now: Long = System.currentTimeMillis()): Pair<String, ServerState>? =
            servers.entries.firstOrNull { (k, v) ->
                hit(title, k) && v.state == "maintenance" && (!v.preview || previewer) &&
                    (v.until == 0L || now < v.until) && (v.from == 0L || v.from <= now + within)
            }?.let { it.key to it.value }
        fun isHidden(title: String) = hidden.any { hit(title, it) }
        fun isRecommended(title: String) = hit(title, recommended)
        /** Сервер не берёт автопилот: на обслуживании или скрыт. */
        fun blocked(title: String) = isHidden(title) || stateOf(title)?.state == "maintenance"

        /** Баннер для этого устройства прямо сейчас (первый подходящий). */
        fun bannerFor(device: String, now: Long = System.currentTimeMillis()): Banner? = banners.firstOrNull { b ->
            val code = BuildConfig.VERSION_CODE
            (b.from == 0L || now >= b.from) && (b.to == 0L || now < b.to) &&
                (b.minCode == 0 || code >= b.minCode) && (b.maxCode == 0 || code <= b.maxCode) &&
                (b.devices.isEmpty() || device in b.devices) &&
                (b.countries.isEmpty() || country() in b.countries) &&
                (!b.preview || previewer)
        }

        /** Объявление для этой версии (ещё не показанное — см. Prefs.announceSeen). */
        fun announceNow(): Announce? = announce?.takeIf { a ->
            val code = BuildConfig.VERSION_CODE
            val now = System.currentTimeMillis()
            (a.minCode == 0 || code >= a.minCode) && (a.maxCode == 0 || code <= a.maxCode) && (!a.preview || previewer) &&
                (a.from == 0L || now >= a.from) && (a.to == 0L || now < a.to)
        }
    }

    /** Страна оператора связи (по сети, иначе по SIM, иначе по языку телефона): «RU», «KZ»… */
    fun country(): String = runCatching {
        val tm = com.apexvpn.client.ApexApp.instance.getSystemService(android.telephony.TelephonyManager::class.java)
        listOf(tm?.networkCountryIso, tm?.simCountryIso).firstOrNull { !it.isNullOrBlank() }?.uppercase()
    }.getOrNull() ?: Locale.getDefault().country.uppercase()

    /** Это телевизор? (для баннеров «только для Android TV») */
    fun isTv(): Boolean = runCatching {
        com.apexvpn.client.ApexApp.instance.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)

    private val _state = MutableStateFlow(parse(Prefs.appConfigJson) ?: Config())
    /** 3.19.6: какие пометки серверов ядро уже учло — чтобы не перезапускать VPN дважды (после скачивания и по таймеру). */
    @Volatile var appliedActive: String = ""
    val state: StateFlow<Config> = _state.asStateFlow()
    val value get() = _state.value

    /** Пересчитать экраны (сменили код предпросмотра). */
    fun touch() { _state.value = _state.value.copy(rev = _state.value.rev + 1) }

    /** 3.19.7: разбор вынесен в AppConfigJson — его проверяют автотесты без телефона. */
    fun parse(text: String): Config? = AppConfigJson.parse(text)

    /** Баннер ещё показывать: не просрочен и не закрыт пользователем. */
    fun bannerVisible(b: Banner?): Boolean = b != null && Prefs.bannerDismissed != (b.id + b.text).hashCode()

    private fun coreKey(c: Config) = AppConfigJson.coreKey(c)

    /** Скачать свежий файл (не чаще раза в 5 минут, если не force). true — изменилось то, что влияет на ядро (нужно перезагрузить VPN). */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - Prefs.appConfigAt < 5 * 60_000L) return@withContext false
        val text = sources.firstNotNullOfOrNull { src ->
            runCatching {
                val c = URL(src + "?t=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
            }.getOrNull()?.takeIf { parse(it) != null }
        } ?: return@withContext false
        Prefs.appConfigAt = System.currentTimeMillis()
        if (text == Prefs.appConfigJson) return@withContext false
        Prefs.appConfigJson = text
        val before = coreKey(_state.value)
        _state.value = parse(text) ?: Config()
        val changed = before != coreKey(_state.value)
        if (changed) appliedActive = _state.value.activeKey()
        changed
    }
}
