package com.apexvpn.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Папки приложения: данные — в %APPDATA%\APEX VPN, ядра — рядом с программой. */
object Paths {
    val data: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".apex-vpn")
        File(base, "APEX VPN").apply { mkdirs() }
    }
    val run: File get() = File(data, "run").apply { mkdirs() }
    val log: File get() = File(data, "core.log")

    /** Папка с sing-box.exe, xray.exe, apexcli.exe и базами (при разработке — windows/resources/windows). */
    val bin: File by lazy {
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }
            ?: File("resources/windows").absoluteFile
    }
    val rules: File get() = File(bin, "rules")
    fun exe(name: String) = File(bin, name)
}

/** Режим маршрутизации. */
enum class RouteMode(val id: String, val title: String, val subtitle: String) {
    Smart("smart", "Умный", "Российские сайты напрямую, остальное через VPN"),
    All("all", "Весь трафик", "Всё через VPN"),
}

/** Как подключаться к Windows. */
enum class ConnectType(val id: String, val title: String, val subtitle: String) {
    Proxy("proxy", "Системный прокси", "Без прав администратора: браузеры и большинство программ"),
    Tun("tun", "TUN (весь компьютер)", "Все программы и игры. Нужны права администратора"),
}

/** Режимы автопилота — как на Android. */
enum class AutoMode(val id: String, val emoji: String, val title: String, val subtitle: String, val profile: String) {
    Balance("balance", "⚖️", "Обычный", "Баланс скорости и стабильности", "normal"),
    Games("games", "🎮", "Игры", "Самый низкий пинг, без обрывов при смене", "gaming"),
    Calls("calls", "📞", "Звонки", "Сервер не меняется в разговоре", "calls"),
    Battery("battery", "🔋", "Экономный", "Редкие проверки серверов", "battery"),
}

enum class CoreChoice(val id: String, val title: String, val subtitle: String) {
    Auto("auto", "Авто", "sing-box, а Xray — для XHTTP, mKCP и подобных"),
    Xray("xray", "Только Xray", "Как в Happ: всё, что умеет Xray, — через Xray"),
    SingBox("singbox", "Только sing-box", "Без Xray"),
}

enum class DnsChoice(val id: String, val title: String) {
    Auto("auto", "Авто"),
    Cloudflare("cloudflare", "Cloudflare"), Google("google", "Google"), Quad9("quad9", "Quad9"),
    AdGuard("adguard", "AdGuard (без рекламы)"), Mullvad("mullvad", "Mullvad"),
}

/** Свой список «Через VPN»: сайты по одному на строку (example.com, full:, keyword:, IP, udp:порты). */
data class CustomList(val name: String, val items: List<String>, val enabled: Boolean = true)

data class Settings(
    val subscriptionUrl: String = "",
    val selected: String = "auto",
    val routeMode: RouteMode = RouteMode.Smart,
    val connectType: ConnectType = ConnectType.Proxy,
    val autoMode: AutoMode = AutoMode.Balance,
    val core: CoreChoice = CoreChoice.Auto,
    val dns: DnsChoice = DnsChoice.Auto,
    val customLists: List<CustomList> = emptyList(),
    /** Свои сайты: всегда через VPN / всегда напрямую */
    val customDomains: List<String> = emptyList(),
    val directDomains: List<String> = emptyList(),
    /** Сайты стран напрямую (kz, by…) */
    val directCountries: Set<String> = emptySet(),
    val rulesAuto: Boolean = true,
    val rulesHours: Int = 24,
    val adBlock: Boolean = false,
    val autostart: Boolean = false,
    val connectOnStart: Boolean = false,
    val minimizeToTray: Boolean = true,
    val refreshHours: Int = 6,
    val skipSpecial: Boolean = true,
    val checkUpdates: Boolean = true,
)

object Store {
    private val file get() = File(Paths.data, "settings.json")
    private val _s = MutableStateFlow(load())
    val settings = _s.asStateFlow()
    val value get() = _s.value

    fun update(block: Settings.() -> Settings) {
        _s.value = _s.value.block()
        save()
    }

    // ── Постоянные значения устройства ──
    private val prefs: JSONObject by lazy {
        val f = File(Paths.data, "device.json")
        val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        var changed = false
        if (!o.has("hwid")) { o.put("hwid", UUID.randomUUID().toString()); changed = true }
        if (!o.has("portBase")) { o.put("portBase", 30000 + SecureRandom().nextInt(2000) * 10); changed = true }
        if (!o.has("xrayAuth")) {
            val b = ByteArray(18).also { SecureRandom().nextBytes(it) }
            o.put("xrayAuth", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)); changed = true
        }
        if (changed) runCatching { f.writeText(o.toString()) }
        o
    }
    val hwid: String get() = prefs.getString("hwid")
    val portBase: Int get() = prefs.getInt("portBase")
    val clashPort get() = portBase
    val proxyPort get() = portBase + 1
    val xrayPort get() = portBase + 10
    val xrayAuth: String get() = prefs.getString("xrayAuth")

    private fun load(): Settings = runCatching {
        val o = JSONObject(File(Paths.data, "settings.json").readText())
        val d = Settings()
        Settings(
            subscriptionUrl = o.optString("subscriptionUrl", d.subscriptionUrl),
            selected = o.optString("selected", d.selected),
            routeMode = RouteMode.entries.firstOrNull { it.id == o.optString("routeMode") } ?: d.routeMode,
            connectType = ConnectType.entries.firstOrNull { it.id == o.optString("connectType") } ?: d.connectType,
            autoMode = AutoMode.entries.firstOrNull { it.id == o.optString("autoMode") } ?: d.autoMode,
            core = CoreChoice.entries.firstOrNull { it.id == o.optString("core") } ?: d.core,
            dns = DnsChoice.entries.firstOrNull { it.id == o.optString("dns") } ?: d.dns,
            adBlock = o.optBoolean("adBlock", d.adBlock),
            autostart = o.optBoolean("autostart", d.autostart),
            connectOnStart = o.optBoolean("connectOnStart", d.connectOnStart),
            minimizeToTray = o.optBoolean("minimizeToTray", d.minimizeToTray),
            refreshHours = o.optInt("refreshHours", d.refreshHours).takeIf { it in listOf(1, 6, 12, 24) } ?: d.refreshHours,
            skipSpecial = o.optBoolean("skipSpecial", d.skipSpecial),
            checkUpdates = o.optBoolean("checkUpdates", d.checkUpdates),
            customLists = o.optJSONArray("customLists")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.map { l ->
                    CustomList(l.optString("name"), strings(l.optJSONArray("items")), l.optBoolean("enabled", true))
                }
            } ?: d.customLists,
            customDomains = strings(o.optJSONArray("customDomains")),
            directDomains = strings(o.optJSONArray("directDomains")),
            directCountries = strings(o.optJSONArray("directCountries")).toSet(),
            rulesAuto = o.optBoolean("rulesAuto", d.rulesAuto),
            rulesHours = o.optInt("rulesHours", d.rulesHours).takeIf { it in listOf(6, 12, 24, 168) } ?: d.rulesHours,
        )
    }.getOrDefault(Settings())

    private fun save() {
        val s = _s.value
        val o = JSONObject()
            .put("subscriptionUrl", s.subscriptionUrl).put("selected", s.selected)
            .put("routeMode", s.routeMode.id).put("connectType", s.connectType.id).put("autoMode", s.autoMode.id)
            .put("core", s.core.id).put("dns", s.dns.id).put("adBlock", s.adBlock)
            .put("autostart", s.autostart).put("connectOnStart", s.connectOnStart).put("minimizeToTray", s.minimizeToTray)
            .put("refreshHours", s.refreshHours).put("skipSpecial", s.skipSpecial).put("checkUpdates", s.checkUpdates)
            .put("customLists", JSONArray(s.customLists.map { JSONObject().put("name", it.name).put("items", JSONArray(it.items)).put("enabled", it.enabled) }))
            .put("customDomains", JSONArray(s.customDomains)).put("directDomains", JSONArray(s.directDomains))
            .put("directCountries", JSONArray(s.directCountries.toList()))
            .put("rulesAuto", s.rulesAuto).put("rulesHours", s.rulesHours)
        runCatching {
            val tmp = File(Paths.data, "settings.json.tmp")
            tmp.writeText(o.toString(2))
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    /** Последние пинги серверов (tag → мс) — для порядка автопилота до первой проверки. */
    fun savePings(p: Map<String, Int>) {
        runCatching { File(Paths.data, "pings.json").writeText(JSONObject(p).toString()) }
    }

    fun lastPings(): Map<String, Int> = runCatching {
        val o = JSONObject(File(Paths.data, "pings.json").readText())
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun jsonArray(items: Collection<String>) = JSONArray(items.toList())

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
}
