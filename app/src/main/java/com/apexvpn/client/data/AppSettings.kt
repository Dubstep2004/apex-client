package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class Settings(
    // Маршрутизация
    val mode: RouteMode = RouteMode.Smart,
    val services: Set<String> = ServiceCatalog.defaultEnabled,
    /** Сервисы, которые приложение уже показывало: новые из каталога включаются сами */
    val knownServices: Set<String> = ServiceCatalog.all.map { it.id }.toSet(),
    /** Свои списки «Через VPN» */
    val customLists: List<CustomList> = emptyList(),
    val customDomains: List<String> = emptyList(),   // всегда через VPN
    val directDomains: List<String> = emptyList(),   // всегда напрямую
    val hideFromBanks: Boolean = true,
    val adBlock: Boolean = false,
    // Приложения
    val appsMode: AppsMode = AppsMode.AllApps,
    val apps: Set<String> = emptySet(),
    // DNS
    val dns: DnsChoice = DnsChoice.Auto,
    val customDns: String = "",
    val kids: Boolean = false,
    // Подключение
    val ipv6: Boolean = false,
    val profile: ConnProfile = ConnProfile.Normal,
    val failover: Boolean = true,
    val killSwitch: Boolean = false,                 // 7. аварийный выключатель
    val autoDownloadUpdates: Boolean = true,         // 1. скачивать обновления заранее по Wi-Fi
    val appIcon: String = "classic",                 // 20. иконка приложения
    val wifiRules: Boolean = false,
    val trustedWifi: Set<String> = emptySet(),
    val share: Boolean = false,
    val sharePort: Int = 2080,
    val shareUser: String = "apex",
    val sharePass: String = "",
    // Серверы
    val favorites: Set<String> = emptySet(),          // по названию сервера
    val core: CoreMode = CoreMode.Auto,               // ядро: авто / только Xray / только sing-box
    val excludeAds: Boolean = true,                   // автопилот не выбирает серверы с рекламой
    val protocol: String = "",                        // категория протокола в списке и для автопилота; "" — все
    // Уведомления и фон
    val speedInNotification: Boolean = true,
    val expiryAlerts: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    /** 3.4: «ask» — уведомление и кнопка «Обновить», «auto» — скачать заранее и поставить, когда VPN выключен или экран погас */
    val updateMode: String = "ask",
    /** 3.4: скачивать обновление только по Wi-Fi (без мобильного трафика) */
    val updateWifiOnly: Boolean = true,
    val crashReports: Boolean = true,
    val refreshHours: Int = 12,
    // Внешний вид
    val theme: ThemeMode = ThemeMode.Dark,
    val design: Design = Design.Apex2,
    val accent: AccentColor = AccentColor.Violet,
    val customAccent: Long = 0xFF7B6CFF,              // 216. свой цвет (accent = Custom)
    val customAccent2: Long = 0,                      // 564. второй цвет палитры (0 — подбирается сам)
    val homeStyle: String = "button",                 // главный экран: button | map
    val connectSound: Boolean = true,                 // звук подключения (3.3.1: включён по умолчанию)
    val sound: String = "signal",                     // 271. набор звуков (ConnectFeedback.sounds)
    val disconnectSound: Boolean = true,              // 271. звук и при отключении
    val vibration: Boolean = true,
    val smoothMode: Boolean = false,                  // меньше анимаций для слабых телефонов
    val glassBlur: Boolean = true,                    // размытие фона под стеклом
    val liveMap: Boolean = true,                      // дышащие кольца и бегущий свет на карте
    // Для опытных
    val userRules: List<UserRule> = emptyList(),
    val domainStrategy: String = "as_is",             // as_is | ip_if_non_match | ip_on_demand
    val fakeDns: Boolean = false,
    val remoteDns: DnsSpec = DnsSpec(),
    val directDns: DnsSpec = DnsSpec(),
    val rulesSource: String = "",                     // свой адрес баз сайтов (*.srs)
    val warp: Boolean = false,                        // 31. WARP-цепочка
    val warpConfig: String = "",                      // данные аккаунта WARP (JSON из ядра)
    // 17. Фон за щитом: none | waves | particles | stars | photo (157)
    val shieldBackdrop: String = "none",
    val backdropStamp: Long = 0,                      // 157. меняется при выборе нового фото
    val photoBlur: Int = 6,                           // 563. размытие своего фото, dp (0…24)
    val photoDim: Int = 55,                           // 563. затемнение, % (0…90)
    val photoParallax: Boolean = true,                // 563. фото сдвигается при наклоне телефона
    val rulesAuto: Boolean = true,                    // базы сайтов: проверять обновления сами
    val rulesHours: Int = 24,
    val speedUnit: String = "mbps",                   // тест скорости: mbps | MBps | kBps
    val speedScale: Int = 1000,                       // шкала спидометра: 100 | 500 | 1000
    val speedMulti: Boolean = true,                   // «Множественное» соединение (4 потока) или «Одинарное»                         // как часто: 6 | 12 | 24 | 168 часов
    val connectOnOpen: Boolean = false,               // подключать VPN при открытии приложения
    // «Пинг»: tcp — до сервера (TCP/QUIC) | get, head — через прокси (HTTP-запрос через сервер) | icmp
    val pingType: String = "tcp",
    val autoMode: String = "balance",                 // режим автопилота: balance | games | speed | stable | calls | battery
    val modeColors: Boolean = true,                   // 588. щит в цветах режима автопилота
    val directCountries: Set<String> = emptySet(),    // 602. сайты этих стран — напрямую
    val connLog: Boolean = false,                     // 604. журнал соединений (только на телефоне)
    val greeting: Boolean = true,                     // 615. приветствие по времени суток на главном
    val pingMode: String = "keepalive",               // через прокси: default | double | keepalive
    val pingTimeout: Int = 5,                         // тайм-аут пинга через прокси, с (5…15)
    val pingUrl: String = "",                         // свой адрес проверки (пусто — gstatic)
    val pingView: String = "time",                    // результат: time — мс | bars — столбики | both
    val quicMode: String = "mobile",                  // QUIC (HTTP/3): mobile — выкл. на мобильной сети | off — всегда выкл. | on — не трогать
    // 20. Иконка на рабочем столе показывает, включён ли VPN
    val iconStatus: Boolean = false,
    // 94. Флаги в списке серверов колышутся
    val animatedFlags: Boolean = true,
    // 220. Задача: games | video | calls ("" — не выбрана)
    val task: String = "",
    // 349. Приложения, от которых VPN скрыт (идут мимо туннеля, как банки)
    val hiddenApps: Set<String> = emptySet(),
    // 387. Секретная тема открыта
    val secretUnlocked: Boolean = false,
    // 429. Щит поворачивается при наклоне телефона
    val shield3d: Boolean = true,
    // 426. Разрешить управление из Tasker / MacroDroid
    val automation: Boolean = false,
    // 413. Считать трафик по приложениям
    val appTraffic: Boolean = true,
    // Стабильный автопилот: не выбирать серверы России и обхода LTE
    val autoSkipSpecial: Boolean = true,
    // 491. На мобильной сети сам включать LTE-обход, если оператор пускает только по белым спискам
    val autoWhitelist: Boolean = true,
) {
    /** Цвет акцента с учётом «Своего». */
    val accentArgb: Long get() = if (accent == AccentColor.Custom) customAccent else accent.argb
}

/** Своё правило маршрутизации: домен/IP и действие proxy | direct | block. */
data class UserRule(val value: String, val action: String)

/** DNS для «опытных»: тип (https/tls/quic/udp/tcp), адрес и IP. Пустой адрес — по умолчанию. */
data class DnsSpec(val type: String = "https", val server: String = "", val ip: String = "") {
    val isSet get() = server.isNotBlank() || ip.isNotBlank()
    fun json(): JSONObject = JSONObject().put("type", type).put("server", server).put("ip", ip)
    companion object {
        fun from(o: JSONObject?) = if (o == null) DnsSpec() else DnsSpec(o.optString("type", "https"), o.optString("server"), o.optString("ip"))
    }
}

/** Настройки маршрутизации и приложения. Хранятся одним JSON, изменения сразу видны всем. */
object AppSettings {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val raw = sp.getString("json", null)
        val parsed = raw?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
        if (raw != null && parsed == null) {
            // Не затираем: сохраняем копию, чтобы следующая версия могла её прочитать
            sp.edit { putString("json_broken_${System.currentTimeMillis()}", raw) }
        }
        val loaded = (parsed ?: Settings(refreshHours = Prefs.updateIntervalHours)).let { it.copy(refreshHours = snapRefresh(it.refreshHours)) }
        _state.value = if (loaded.sharePass.isEmpty()) loaded.copy(sharePass = randomPassword()) else loaded
        save()
    }

    fun update(change: Settings.() -> Settings) {
        _state.value = _state.value.change()
        Prefs.updateIntervalHours = _state.value.refreshHours
        save()
    }

    private fun save() = sp.edit { putString("json", toJson(_state.value).toString()) }

    /** Все настройки одним JSON — для резервной копии в файл. */
    fun exportJson(): JSONObject = toJson(_state.value)

    /** Восстановить из резервной копии; false — файл не подошёл. */
    fun importJson(o: JSONObject): Boolean {
        val s = runCatching { fromJson(o) }.getOrNull() ?: return false
        _state.value = if (s.sharePass.isEmpty()) s.copy(sharePass = randomPassword()) else s
        Prefs.updateIntervalHours = _state.value.refreshHours
        save()
        return true
    }

    /** 379. Все настройки по умолчанию; иконку оставляем, чтобы она не пропала с рабочего стола. */
    fun reset() {
        val keepIcon = _state.value.appIcon
        _state.value = Settings(refreshHours = 12, appIcon = keepIcon, sharePass = randomPassword())
        Prefs.updateIntervalHours = 12
        save()
    }

    /** Возвращает null при успехе или текст ошибки. */
    fun addDomain(raw: String, direct: Boolean): String? {
        val d = normalizeDomain(raw) ?: return "Введите адрес сайта, например twitch.tv"
        val s = _state.value
        if (d in s.customDomains || d in s.directDomains) return "Этот сайт уже есть в списках"
        update { if (direct) copy(directDomains = directDomains + d) else copy(customDomains = customDomains + d) }
        return null
    }

    fun removeDomain(domain: String) = update {
        copy(customDomains = customDomains - domain, directDomains = directDomains - domain)
    }

    fun toggleFavorite(name: String) = update {
        copy(favorites = if (name in favorites) favorites - name else favorites + name)
    }

    /** Настройки для ядра (routeOptions в core/apexcore/config.go). */
    /** Мобильная сеть сейчас (без VPN-туннеля). */
    fun onMobile(): Boolean = com.apexvpn.client.vpn.NetworkMonitor.transport.value.substringBefore(':') == "mobile" ||
        (com.apexvpn.client.vpn.NetworkMonitor.transport.value.isEmpty() && DnsAuto.networkKey() == "mobile")

    /** Список серверов режима автопилота, который сейчас в ядре. */
    @Volatile var autoApplied: List<String> = emptyList()

    /** Что сейчас в ядре — чтобы при смене Wi-Fi ↔ мобильная понять, нужна ли перезагрузка. */
    @Volatile var quicApplied: Boolean? = null

    /** Выключать ли QUIC сейчас: на мобильной сети он через VPN часто тормозит. */
    fun quicBlocked(s: Settings = value): Boolean = when (s.quicMode) {
        "off" -> true
        "on" -> false
        else -> onMobile()
    }

    fun coreOptions(rulesDir: String?): String = JSONObject().apply {
        val s = _state.value
        put("mode", s.mode.id)
        put("services", JSONArray(s.services.toList()))
        put("customLists", JSONArray(s.customLists.filter { it.enabled && it.items.isNotEmpty() }.map {
            JSONObject().put("name", it.name).put("items", JSONArray(it.items))
        }))
        put("customDomains", JSONArray(s.customDomains))
        put("directDomains", JSONArray(s.directDomains))
        if (s.directCountries.isNotEmpty()) put("directSuffixes", JSONArray(DirectCountries.suffixes(s.directCountries)))
        put("rulesDir", rulesDir ?: "")
        if (s.dns == DnsChoice.Auto) {
            // DNS «Авто»: лучший для текущей сети, способом, который быстрее (DoH / DoT / DoQ)
            val v = DnsAuto.bestVariant()
            DnsAuto.applied = v.key
            put("dns", v.choice.id)
            if (v.proto != "https" && !s.remoteDns.isSet) {
                put("remoteDns", JSONObject().put("type", v.proto).put("server", v.sni).put("ip", v.ip))
            }
        } else {
            put("dns", s.dns.id)
        }
        put("customDns", s.customDns)
        put("kids", s.kids)
        put("adBlock", s.adBlock)
        // 413. Ядро определяет приложение каждого соединения (Android 10+)
        put("blockQuic", quicBlocked(s).also { quicApplied = it })
        // DNS «Авто» меряет DNS через сервер, как они и работают под VPN
        put("probePort", Prefs.probePort)
        // Адрес проверки — и для автопилота
        if (s.pingUrl.isNotBlank()) put("urlTest", s.pingUrl.trim())
        put("findProcess", s.appTraffic && android.os.Build.VERSION.SDK_INT >= 29)
        put("ipv6", s.ipv6)
        put("profile", s.profile.id)
        put("core", s.core.id)
        put("excludeAds", s.excludeAds)
        put("clashApi", Prefs.clashApiPort)
        // 197. Счётчик рекламы: DNS приложения, если он запущен
        if (s.adBlock && AdCounter.running) put("adsDnsPort", Prefs.adsDnsPort)
        put("xrayPort", Prefs.xrayBasePort)
        put("xrayAuth", Prefs.xrayAuth)
        put("userRules", JSONArray(s.userRules.map { JSONObject().put("value", it.value).put("action", it.action) }))
        put("domainStrategy", s.domainStrategy)
        put("fakeDns", s.fakeDns)
        if (s.remoteDns.isSet) put("remoteDns", s.remoteDns.json())
        if (s.directDns.isSet) put("directDns", s.directDns.json())
        runCatching { put("logFile", Diagnostics.coreLog(com.apexvpn.client.ApexApp.instance).path) }
        if (s.warp && s.warpConfig.isNotBlank()) runCatching { put("warp", JSONObject(s.warpConfig)) }
        put("autoProtocols", JSONArray(if (s.protocol.isEmpty()) emptyList() else listOf(s.protocol)))
        // Стабильный автопилот: без России и LTE-обхода (их пинг самый низкий, но сайты через них не открываются)
        val nodes = Subscription.state.value.nodes
        if (WhiteList.active && s.autoWhitelist) {
            // 491. Белые списки: только LTE-обход
            put("autoExclude", JSONArray(nodes.filter { !ServerInfo.isLte(it.name) }.map { it.tag }))
        } else {
            // Режим автопилота (игры, скорость, надёжный, звонки): только подходящие серверы
            val allowed = AutoModes.allowed(AutoModes.Mode.of(s.autoMode), nodes)
            if (allowed.isNotEmpty()) {
                put("autoExclude", JSONArray(nodes.map { it.tag }.filter { it !in allowed }))
                put("autoOrder", JSONArray(allowed))
            } else if (s.autoSkipSpecial) {
                put("autoExclude", JSONArray(nodes.filter { ServerInfo.isSpecial(it.name) }.map { it.tag }))
            }
            autoApplied = allowed
        }
        // …и до первой проверки — сначала серверы, которые были быстрее при прошлом замере
        if (!has("autoOrder")) put("autoOrder", JSONArray(Prefs.lastPings().entries.filter { it.value > 0 }.sortedBy { it.value }.map { it.key }))
        put(
            "share",
            JSONObject().put("enabled", s.share).put("port", s.sharePort)
                .put("username", s.shareUser).put("password", s.sharePass),
        )
    }.toString()

    fun normalizeDomain(raw: String): String? {
        var d = raw.trim().lowercase()
        d.indexOf("://").takeIf { it >= 0 }?.let { d = d.substring(it + 3) }
        d = d.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        d = d.trim('.').removePrefix("www.")
        val ok = d.contains('.') && d.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return d.takeIf { ok }
    }

    fun randomPassword(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..10).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    // ─── JSON ───────────────────────────────────────────────────────────

    /** Строки массива; не строки (из старых или повреждённых настроек) пропускаем, а не падаем. */
    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it, null)?.takeIf { s -> s.isNotEmpty() } }

    /** Одно поле не должно сбрасывать все настройки: при ошибке — значение по умолчанию. */
    private inline fun <T> safe(default: T, block: () -> T): T = runCatching(block).getOrDefault(default)
    private fun list(v: Collection<String>) = JSONArray(v.toList())

    private fun toJson(s: Settings) = JSONObject().apply {
        put("mode", s.mode.id); put("services", list(s.services)); put("knownServices", list(s.knownServices))
        put("customLists", JSONArray(s.customLists.map {
            JSONObject().put("id", it.id).put("name", it.name).put("items", JSONArray(it.items)).put("enabled", it.enabled).put("color", it.color)
        }))
        put("custom", list(s.customDomains)); put("direct", list(s.directDomains))
        put("banks", s.hideFromBanks); put("adBlock", s.adBlock)
        put("appsMode", s.appsMode.id); put("apps", list(s.apps))
        put("dns", s.dns.id); put("customDns", s.customDns); put("kids", s.kids)
        put("ipv6", s.ipv6); put("profile", s.profile.id); put("failover", s.failover); put("killSwitch", s.killSwitch); put("autoDl", s.autoDownloadUpdates); put("appIcon", s.appIcon)
        put("wifiRules", s.wifiRules); put("trustedWifi", list(s.trustedWifi))
        put("share", s.share); put("sharePort", s.sharePort); put("shareUser", s.shareUser); put("sharePass", s.sharePass)
        put("favorites", list(s.favorites)); put("protocol", s.protocol); put("core", s.core.id); put("excludeAds", s.excludeAds)
        put("speedNotif", s.speedInNotification); put("expiryAlerts", s.expiryAlerts)
        put("autoUpdates", s.autoCheckUpdates); put("updateMode", s.updateMode); put("updateWifiOnly", s.updateWifiOnly); put("crashReports", s.crashReports); put("refreshHours", s.refreshHours)
        put("theme", s.theme.id); put("design", s.design.id); put("accent", s.accent.id); put("customAccent", s.customAccent)
        put("homeStyle", s.homeStyle); put("connectSound", s.connectSound); put("sound", s.sound); put("disconnectSound", s.disconnectSound); put("vibration", s.vibration)
        put("smooth", s.smoothMode); put("glassBlur", s.glassBlur); put("liveMap", s.liveMap)
        put("userRules", JSONArray(s.userRules.map { JSONObject().put("value", it.value).put("action", it.action) }))
        put("domainStrategy", s.domainStrategy); put("fakeDns", s.fakeDns)
        put("remoteDns", s.remoteDns.json()); put("directDns", s.directDns.json()); put("rulesSource", s.rulesSource)
        put("warp", s.warp); put("warpConfig", s.warpConfig); put("shieldBackdrop", s.shieldBackdrop); put("backdropStamp", s.backdropStamp)
        put("iconStatus", s.iconStatus); put("animatedFlags", s.animatedFlags); put("task", s.task); put("hiddenApps", list(s.hiddenApps)); put("secret", s.secretUnlocked); put("shield3d", s.shield3d); put("automation", s.automation); put("appTraffic", s.appTraffic); put("autoSkipSpecial", s.autoSkipSpecial); put("autoWhitelist", s.autoWhitelist)
        put("customAccent2", s.customAccent2); put("photoBlur", s.photoBlur); put("photoDim", s.photoDim); put("photoParallax", s.photoParallax); put("rulesAuto", s.rulesAuto); put("rulesHours", s.rulesHours); put("speedUnit", s.speedUnit); put("speedScale", s.speedScale); put("speedMulti", s.speedMulti); put("quicMode", s.quicMode); put("connectOnOpen", s.connectOnOpen)
        put("pingType", s.pingType); put("pingMode", s.pingMode); put("pingTimeout", s.pingTimeout); put("pingUrl", s.pingUrl); put("pingView", s.pingView); put("autoMode", s.autoMode); put("modeColors", s.modeColors); put("directCountries", list(s.directCountries)); put("connLog", s.connLog); put("greeting", s.greeting)
    }

    private fun fromJson(o: JSONObject): Settings {
        val d = Settings()
        fun arr(k: String, def: Collection<String>) = o.optJSONArray(k)?.strings() ?: def.toList()
        return Settings(
            mode = RouteMode.of(o.optString("mode", d.mode.id)),
            // Новые сервисы каталога включаются сами (иначе их сайты пошли бы напрямую)
            services = safe(d.services) {
                val known = o.optJSONArray("knownServices")?.strings()?.toSet() ?: KNOWN_BEFORE_CATALOG
                arr("services", d.services).toSet() + (ServiceCatalog.defaultEnabled - known)
            },
            knownServices = d.knownServices,
            customLists = safe(d.customLists) { o.optJSONArray("customLists")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { c ->
                        CustomList(
                            id = c.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            name = c.optString("name"),
                            items = c.optJSONArray("items")?.strings() ?: emptyList(),
                            enabled = c.optBoolean("enabled", true),
                            color = c.optLong("color", 0xFF7B6CFF),
                        )
                    }
                }
            } ?: d.customLists },
            customDomains = arr("custom", d.customDomains),
            directDomains = arr("direct", d.directDomains),
            hideFromBanks = o.optBoolean("banks", d.hideFromBanks),
            adBlock = o.optBoolean("adBlock", d.adBlock),
            // 3.3: пункт «Приложения» убран — «Кроме выбранных» перешло в «Скрыть VPN»
            appsMode = AppsMode.AllApps,
            apps = arr("apps", d.apps).toSet(),
            dns = DnsChoice.of(o.optString("dns", d.dns.id)),
            customDns = o.optString("customDns", d.customDns),
            kids = o.optBoolean("kids", d.kids),
            ipv6 = o.optBoolean("ipv6", d.ipv6),
            profile = ConnProfile.of(o.optString("profile", d.profile.id)),
            failover = o.optBoolean("failover", d.failover),
            killSwitch = o.optBoolean("killSwitch", d.killSwitch),
            autoDownloadUpdates = o.optBoolean("autoDl", d.autoDownloadUpdates),
            appIcon = o.optString("appIcon", d.appIcon),
            wifiRules = o.optBoolean("wifiRules", d.wifiRules),
            trustedWifi = arr("trustedWifi", d.trustedWifi).toSet(),
            share = o.optBoolean("share", d.share),
            sharePort = o.optInt("sharePort", d.sharePort),
            shareUser = o.optString("shareUser", d.shareUser),
            sharePass = o.optString("sharePass", d.sharePass),
            favorites = arr("favorites", d.favorites).toSet(),
            protocol = o.optString("protocol", d.protocol),
            core = CoreMode.of(o.optString("core", if (o.optBoolean("xrayAll")) "xray" else d.core.id)),
            excludeAds = o.optBoolean("excludeAds", d.excludeAds),
            speedInNotification = o.optBoolean("speedNotif", d.speedInNotification),
            expiryAlerts = o.optBoolean("expiryAlerts", d.expiryAlerts),
            autoCheckUpdates = o.optBoolean("autoUpdates", d.autoCheckUpdates),
            updateMode = o.optString("updateMode", d.updateMode).takeIf { it == "ask" || it == "auto" } ?: d.updateMode,
            updateWifiOnly = o.optBoolean("updateWifiOnly", d.updateWifiOnly),
            crashReports = o.optBoolean("crashReports", d.crashReports),
            refreshHours = snapRefresh(o.optInt("refreshHours", d.refreshHours)),
            theme = ThemeMode.of(o.optString("theme", d.theme.id)),
            design = Design.of(o.optString("design", d.design.id)),
            accent = AccentColor.of(o.optString("accent", d.accent.id)),
            customAccent = o.optLong("customAccent", d.customAccent).let { if (it == 0L) d.customAccent else it or 0xFF000000L },
            homeStyle = o.optString("homeStyle", d.homeStyle),
            connectSound = o.optBoolean("connectSound", d.connectSound),
            sound = o.optString("sound", d.sound),
            disconnectSound = o.optBoolean("disconnectSound", d.disconnectSound),
            vibration = o.optBoolean("vibration", d.vibration),
            smoothMode = o.optBoolean("smooth", d.smoothMode),
            glassBlur = o.optBoolean("glassBlur", d.glassBlur),
            liveMap = o.optBoolean("liveMap", d.liveMap),
            userRules = safe(d.userRules) {
                o.optJSONArray("userRules")?.let { a ->
                    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { UserRule(it.optString("value"), it.optString("action", "proxy")) } }
                        .filter { it.value.isNotBlank() }
                } ?: d.userRules
            },
            domainStrategy = o.optString("domainStrategy", d.domainStrategy),
            fakeDns = o.optBoolean("fakeDns", d.fakeDns),
            remoteDns = DnsSpec.from(o.optJSONObject("remoteDns")),
            directDns = DnsSpec.from(o.optJSONObject("directDns")),
            rulesSource = o.optString("rulesSource", d.rulesSource),
            warp = o.optBoolean("warp", d.warp),
            warpConfig = o.optString("warpConfig", d.warpConfig),
            shieldBackdrop = o.optString("shieldBackdrop", d.shieldBackdrop),
            backdropStamp = o.optLong("backdropStamp", d.backdropStamp),
            iconStatus = o.optBoolean("iconStatus", d.iconStatus),
            animatedFlags = o.optBoolean("animatedFlags", d.animatedFlags),
            task = "", // задачи стали режимами автопилота (3.0)
            hiddenApps = arr("hiddenApps", d.hiddenApps).toSet() +
                (if (o.optString("appsMode") == AppsMode.Exclude.id) arr("apps", d.apps) else emptyList()),
            secretUnlocked = o.optBoolean("secret", d.secretUnlocked),
            shield3d = o.optBoolean("shield3d", d.shield3d),
            automation = o.optBoolean("automation", d.automation),
            appTraffic = o.optBoolean("appTraffic", d.appTraffic),
            autoSkipSpecial = o.optBoolean("autoSkipSpecial", d.autoSkipSpecial),
            autoWhitelist = o.optBoolean("autoWhitelist", d.autoWhitelist),
            customAccent2 = o.optLong("customAccent2", d.customAccent2).let { if (it == 0L) 0L else it or 0xFF000000L },
            photoBlur = o.optInt("photoBlur", d.photoBlur).coerceIn(0, 24),
            photoDim = o.optInt("photoDim", d.photoDim).coerceIn(0, 90),
            photoParallax = o.optBoolean("photoParallax", d.photoParallax),
            rulesAuto = o.optBoolean("rulesAuto", d.rulesAuto),
            speedUnit = o.optString("speedUnit", d.speedUnit).takeIf { it in listOf("mbps", "MBps", "kBps") } ?: d.speedUnit,
            speedScale = o.optInt("speedScale", d.speedScale).takeIf { it in listOf(100, 500, 1000) } ?: d.speedScale,
            speedMulti = o.optBoolean("speedMulti", d.speedMulti),
            rulesHours = o.optInt("rulesHours", d.rulesHours).takeIf { it in listOf(6, 12, 24, 168) } ?: d.rulesHours,
            connectOnOpen = o.optBoolean("connectOnOpen", d.connectOnOpen),
            autoMode = AutoModes.Mode.of(
                o.optString("autoMode", "").ifEmpty {
                    when (o.optString("task")) { "video" -> "speed"; "games" -> "games"; "calls" -> "calls"; else -> d.autoMode }
                },
            ).id,
            modeColors = o.optBoolean("modeColors", d.modeColors),
            directCountries = arr("directCountries", d.directCountries).toSet(),
            connLog = o.optBoolean("connLog", d.connLog),
            greeting = o.optBoolean("greeting", d.greeting),
            pingType = o.optString("pingType", d.pingType).takeIf { it in listOf("tcp", "get", "head", "icmp") } ?: d.pingType,
            pingMode = o.optString("pingMode", d.pingMode).takeIf { it in listOf("default", "double", "keepalive") } ?: d.pingMode,
            pingTimeout = o.optInt("pingTimeout", d.pingTimeout).coerceIn(5, 15),
            pingUrl = o.optString("pingUrl", d.pingUrl),
            pingView = o.optString("pingView", d.pingView).takeIf { it in listOf("time", "bars", "both") } ?: d.pingView,
            quicMode = o.optString("quicMode", d.quicMode).takeIf { it in listOf("mobile", "off", "on") } ?: d.quicMode,
        )
    }
}

/** Сервисы, которые были в каталоге до появления «новых» (1.9.1) — для тех, кто обновился. */
private val KNOWN_BEFORE_CATALOG = setOf(
    "youtube", "instagram", "telegram", "whatsapp", "tiktok", "discord", "chatgpt", "gemini", "claude", "x", "twitch", "cloudflare",
)

/** Интервал автообновления — только 1, 6, 12 или 24 ч (старые копии и заголовок подписки могли дать 0 или 48). */
internal fun snapRefresh(h: Int): Int = listOf(1, 6, 12, 24).minByOrNull { kotlin.math.abs(it - h) } ?: 12
