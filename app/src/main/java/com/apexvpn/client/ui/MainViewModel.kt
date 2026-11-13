package com.apexvpn.client.ui

import com.apexvpn.client.BuildConfig

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexvpn.client.data.AppInfo
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.InstalledApps
import com.apexvpn.client.data.LivePing
import com.apexvpn.client.data.Pinger
import com.apexvpn.client.data.RulesUpdater
import com.apexvpn.client.data.Settings
import com.apexvpn.client.data.SpeedResult
import com.apexvpn.client.data.SpeedTest
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.update.UpdateInfo
import com.apexvpn.client.update.UpdateManager
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Результат проверки сайта: куда пойдёт трафик. */
data class SiteCheck(val domain: String, val outbound: String, val reason: String)

enum class SpeedPhase { Idle, Ping, Download, Upload, Done, Error }

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.Idle,
    val live: Double = 0.0,
    val result: SpeedResult = SpeedResult(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val subscription = Subscription.state
    val update = UpdateManager.state
    val settings = AppSettings.state

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _pings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pings = _pings.asStateFlow()

    private val _pinging = MutableStateFlow(false)
    val pinging = _pinging.asStateFlow()

    init {
        viewModelScope.launch {
            // После обновления приложения перезагружаем серверы один раз: у новых версий новые поля
            val oldFormat = Subscription.state.value.nodes.isNotEmpty() && Prefs.nodesVersion < BuildConfig.VERSION_CODE
            if (Subscription.needsRefreshOnLaunch() || oldFormat) refresh(silent = true)
        }
        if (AppSettings.value.autoCheckUpdates) {
            viewModelScope.launch { UpdateManager.check(silent = true) }
        }
        // Отчёты о сбоях с прошлого запуска — сразу, а не через 3 часа
        runCatching { com.apexvpn.client.data.CrashReporter.cleanup(getApplication()) }
        viewModelScope.launch {
            VpnState.notice.collect { n ->
                if (n != null) {
                    _message.value = n
                    VpnState.consumeNotice()
                }
            }
        }
    }

    // ─── Настройки: применяются к работающему VPN сразу ──────────────────

    private fun applyToVpn() = ApexVpnService.reload(getApplication<Application>())

    /** Изменить настройки; [reload] — нужно ли перезапустить туннель. */
    fun change(reload: Boolean = true, block: Settings.() -> Settings) {
        AppSettings.update(block)
        if (reload) applyToVpn()
    }

    /** null — добавлено, иначе текст ошибки. */
    fun addDomain(raw: String, direct: Boolean): String? =
        AppSettings.addDomain(raw, direct).also { if (it == null) applyToVpn() }

    fun removeDomain(d: String) {
        AppSettings.removeDomain(d)
        applyToVpn()
    }

    fun toggleFavorite(name: String) = AppSettings.toggleFavorite(name)

    // ─── Подписка ────────────────────────────────────────────────────────

    fun refresh(silent: Boolean = false) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                Subscription.refresh()
                _pings.value = emptyMap() // номера серверов могли смениться — старые замеры не к тем строкам
                applyToVpn()
                if (!silent) _message.value = "Подписка обновлена"
            } catch (e: Exception) {
                if (!silent) _message.value = e.message ?: "Не удалось обновить подписку"
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Добавить подписку. onDone(null) — успех, иначе текст ошибки. */
    fun setSubscription(url: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                Subscription.setUrl(url)
                _pings.value = emptyMap()
                applyToVpn()
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Не удалось загрузить подписку")
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun removeSubscription(wipeData: Boolean = false) {
        ApexVpnService.stop(getApplication<Application>())
        Subscription.remove()
        _pings.value = emptyMap()
        // 670. Вместе с подпиской — все данные приложения на телефоне
        if (wipeData) {
            com.apexvpn.client.data.DataCleaner.clear(getApplication())
            com.apexvpn.client.data.RulesUpdater.remoteDir(getApplication()).deleteRecursively()
            getApplication<Application>().getSharedPreferences("auto_modes", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            showMessage("Подписка и все данные удалены")
        }
    }

    /** Применить восстановленную копию (файл или QR переноса): подписка, выбранный сервер, настройки. */
    fun applyRestored(restored: com.apexvpn.client.data.SettingsBackup.Restored, done: String = "Настройки восстановлены") {
        val current = Subscription.state.value.url
        if (restored.subscription.isNotBlank() && restored.subscription != current) {
            setSubscription(restored.subscription) { err ->
                // Новая подписка сбрасывает выбор сервера — возвращаем выбранный в копии
                if (err == null && restored.selected.isNotBlank()) select(restored.selected)
                showMessage(err ?: "$done вместе с подпиской")
            }
        } else {
            // Подписка та же: выбранный сервер из копии нужно применить и в состоянии, и в ядре
            if (restored.selected.isNotBlank()) Subscription.select(restored.selected)
            change { this }
            showMessage(done)
        }
    }

    fun select(tag: String) {
        Subscription.select(tag)
        applyToVpn()
    }

    /** Серверы, которые сейчас перепроверяются по нажатию на пинг. */
    private val _pingingTags = MutableStateFlow<Set<String>>(emptySet())
    val pingingTags = _pingingTags.asStateFlow()
    /** Сколько раз перепроверяли сервер — по смене числа строка «вспыхивает» новым пингом. */
    private val _pingFlash = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pingFlash = _pingFlash.asStateFlow()

    /** Нажатие на пинг: перепроверить только этот сервер (все его протоколы). */
    fun pingGroup(tags: List<String>) {
        if (tags.isEmpty() || tags.any { it in _pingingTags.value }) return
        viewModelScope.launch {
            _pingingTags.value = _pingingTags.value + tags
            try {
                val nodes = Subscription.state.value.nodes.filter { it.tag in tags }
                val r = Pinger.pingAll(nodes, save = false).filterKeys { it in tags }
                val merged = _pings.value.ifEmpty { com.apexvpn.client.data.Prefs.lastPings() } + r
                _pings.value = merged
                com.apexvpn.client.data.Prefs.saveLastPings(merged)
                _pingFlash.value = _pingFlash.value + tags.associateWith { (_pingFlash.value[it] ?: 0) + 1 }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _pingingTags.value = _pingingTags.value - tags.toSet()
            }
        }
    }

    fun pingAll() {
        if (_pinging.value) return
        viewModelScope.launch {
            _pinging.value = true
            try {
                _pings.value = Pinger.pingAll(Subscription.state.value.nodes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // замер — не повод закрывать приложение
            } finally {
                _pinging.value = false
            }
        }
    }

    /** «Не работает?» — самопроверка; заодно обновляет пинги в списке серверов. */
    fun runSelfCheck() {
        viewModelScope.launch {
            val p = com.apexvpn.client.data.SelfCheck.run()
            if (p.isNotEmpty()) _pings.value = p
        }
    }

    /** 3.21: «Проверка сети» — работает, даже если экран закрыли; после — обновляет плашку на главном. */
    fun runNetCheck() {
        viewModelScope.launch { com.apexvpn.client.data.NetCheck.run(getApplication<android.app.Application>()) }
    }

    // ─── 49. Живой пинг на главном экране ────────────────────────────────

    private val _livePing = MutableStateFlow<List<Int>>(emptyList())
    val livePing = _livePing.asStateFlow()
    private var livePingJob: Job? = null

    /** Сервер, который сейчас выбрал автопилот (обновляется вместе с живым пингом). */
    private val _autoNow = MutableStateFlow<String?>(null)
    val autoNow = _autoNow.asStateFlow()

    fun startLivePing() {
        if (livePingJob?.isActive == true) return
        livePingJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (VpnState.status.value == VpnStatus.Started) {
                    if (tick++ % 3 == 0 && Subscription.state.value.selectedTag == com.apexvpn.client.data.AUTO_TAG) {
                        _autoNow.value = com.apexvpn.client.data.Pinger.autopilotNow()
                    }
                    val ms = LivePing.measure()
                    _livePing.value = (_livePing.value + ms).takeLast(40)
                } else {
                    if (_livePing.value.isNotEmpty()) _livePing.value = emptyList()
                    _autoNow.value = null
                }
                delay(2000)
            }
        }
    }

    fun stopLivePing() {
        livePingJob?.cancel()
        livePingJob = null
    }

    // ─── 5. Тест скорости ────────────────────────────────────────────────

    private val _speed = MutableStateFlow(SpeedState())
    val speed = _speed.asStateFlow()
    private var speedJob: Job? = null

    /** Провайдер и сервер теста (Cloudflare) — показываются до начала теста. */
    private val _speedMeta = MutableStateFlow<com.apexvpn.client.data.SpeedMeta?>(null)
    val speedMeta = _speedMeta.asStateFlow()
    private val _speedMetaLoading = MutableStateFlow(false)
    val speedMetaLoading = _speedMetaLoading.asStateFlow()

    fun loadSpeedMeta() {
        if (_speedMetaLoading.value) return
        viewModelScope.launch {
            _speedMetaLoading.value = true
            _speedMeta.value = SpeedTest.meta()
            _speedMetaLoading.value = false
        }
    }

    /** 3.21.1: на каком сервере идёт (или шёл) тест — для подписи «Авто → …». */
    private val _speedServer = MutableStateFlow<com.apexvpn.client.data.SpeedServer?>(null)
    val speedServer = _speedServer.asStateFlow()

    fun runSpeedTest() {
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            var r = SpeedResult()
            try {
                _speed.value = SpeedState(SpeedPhase.Ping)
                if (_speedMeta.value == null) loadSpeedMeta()
                val srv = SpeedTest.choose(AppSettings.value.speedServer)
                _speedServer.value = srv
                val (ping, jitter) = SpeedTest.ping(srv)
                r = r.copy(pingMs = ping, jitterMs = jitter)
                if (r.pingMs < 0) error("Нет связи с сервером теста")
                // «Тип соединения»: множественное — 4 потока, одинарное — 1
                val streams = if (AppSettings.value.speedMulti) 4 else 1
                _speed.value = SpeedState(SpeedPhase.Download, result = r)
                r = r.copy(downMbps = SpeedTest.download(streams, srv) { v -> _speed.value = _speed.value.copy(live = v) })
                _speed.value = SpeedState(SpeedPhase.Upload, result = r)
                r = r.copy(upMbps = SpeedTest.upload(streams) { v -> _speed.value = _speed.value.copy(live = v) })
                _speed.value = SpeedState(SpeedPhase.Done, result = r)
                // 229. История замеров
                val running = com.apexvpn.client.vpn.VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started
                val sub = Subscription.state.value
                val server = when {
                    !running -> "Без VPN"
                    sub.selectedTag == com.apexvpn.client.data.AUTO_TAG -> "Автопилот"
                    else -> sub.nodes.firstOrNull { it.tag == sub.selectedTag }?.let { com.apexvpn.client.data.ServerInfo.title(it.name) } ?: "VPN"
                }
                com.apexvpn.client.data.SpeedHistory.add(
                    getApplication(),
                    com.apexvpn.client.data.SpeedHistory.Entry(System.currentTimeMillis(), r.pingMs, r.downMbps, r.upMbps, server),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _speed.value = SpeedState(SpeedPhase.Error, result = r, error = e.message ?: "Ошибка теста")
            }
        }
    }

    // ─── 220. Сервер под задачу ──────────────────────────────────────────

    val fastest = com.apexvpn.client.data.FastestServer.state
    private var fastestJob: Job? = null

    fun findFastest() {
        if (fastestJob?.isActive == true) return
        fastestJob = viewModelScope.launch {
            val best = com.apexvpn.client.data.FastestServer.run(getApplication())
            if (best != null) showMessage("Самый быстрый: ${best.title} — ${"%.0f".format(java.util.Locale.US, best.mbps)} Мбит/с")
        }
    }

    fun cancelFastest() {
        com.apexvpn.client.data.FastestServer.cancel()
        fastestJob?.cancel()
    }

    /** Задача: игры, видео или звонки — профиль и способ выбора сервера. */
    /** Режим автопилота: включает автопилот и сразу подбирает подходящие серверы. */
    fun setAutoMode(mode: com.apexvpn.client.data.AutoModes.Mode) {
        change(reload = false) { copy(autoMode = mode.id, profile = mode.profile, task = "") }
        Subscription.select(com.apexvpn.client.data.AUTO_TAG)
        showMessage("${mode.emoji} ${mode.title}: ${mode.subtitle.replaceFirstChar { it.lowercase() }}")
        viewModelScope.launch {
            if (mode.measured) {
                com.apexvpn.client.data.AutoModes.measure(getApplication(), mode)
                _pings.value = _pings.value + com.apexvpn.client.data.AutoModes.quality.value
                    .mapValues { it.value.avg }.filterValues { it > 0 }
            }
            applyToVpn()
        }
    }

    fun chooseTask(task: String) {
        when (task) {
            "games" -> setAutoMode(com.apexvpn.client.data.AutoModes.Mode.Games)
            "calls" -> setAutoMode(com.apexvpn.client.data.AutoModes.Mode.Calls)
            "video" -> setAutoMode(com.apexvpn.client.data.AutoModes.Mode.Speed)
            else -> setAutoMode(com.apexvpn.client.data.AutoModes.Mode.Balance)
        }
    }

    fun cancelSpeedTest() {
        speedJob?.cancel()
        _speed.value = SpeedState()
    }

    // ─── 58. Проверка сайта ──────────────────────────────────────────────

    private val _siteCheck = MutableStateFlow<SiteCheck?>(null)
    val siteCheck = _siteCheck.asStateFlow()

    fun checkSite(domain: String) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val json = withContext(Dispatchers.IO) {
                runCatching { Apexcore.matchRoute(AppSettings.coreOptions(RulesUpdater.activeDir(ctx)), domain) }.getOrNull()
            }
            _siteCheck.value = json?.let {
                runCatching {
                    val o = JSONObject(it)
                    SiteCheck(o.optString("domain"), o.optString("outbound"), o.optString("reason"))
                }.getOrNull()
            }
        }
    }

    // ─── 59. Базы сайтов ─────────────────────────────────────────────────

    private val _rulesUpdating = MutableStateFlow(false)
    val rulesUpdating = _rulesUpdating.asStateFlow()

    fun updateRules() {
        if (_rulesUpdating.value) return
        viewModelScope.launch {
            _rulesUpdating.value = true
            val r = RulesUpdater.update(getApplication<Application>(), force = true)
            _rulesUpdating.value = false
            if (r is RulesUpdater.Result.Updated) applyToVpn()
            _message.value = when (r) {
                is RulesUpdater.Result.Updated -> "Базы сайтов обновлены: ${r.files} из ${RulesUpdater.FILES.size}"
                RulesUpdater.Result.Fresh -> "Базы сайтов уже свежие"
                RulesUpdater.Result.Skipped -> "Базы сайтов уже свежие"
                is RulesUpdater.Result.Failed -> "Не удалось обновить базы: ${r.error}"
            }
        }
    }

    fun rulesUpdatedAt(): Long = RulesUpdater.lastUpdated(getApplication<Application>())
    val rulesInfo = RulesUpdater.info

    // ─── 6. Приложения ───────────────────────────────────────────────────

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps = _apps.asStateFlow()

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = InstalledApps.load(getApplication<Application>()) }
    }

    // ─── 31. WARP ─────────────────────────────────────────────────────────

    private val _warpBusy = MutableStateFlow(false)
    val warpBusy = _warpBusy.asStateFlow()

    /** Включить WARP: при первом включении регистрирует бесплатный аккаунт Cloudflare. */
    fun setWarp(on: Boolean) {
        if (!on) { change { copy(warp = false) }; return }
        if (AppSettings.value.warpConfig.isNotBlank()) { change { copy(warp = true) }; return }
        if (_warpBusy.value) return
        viewModelScope.launch {
            _warpBusy.value = true
            runCatching { com.apexvpn.client.data.Warp.register() }
                .onSuccess { cfg ->
                    change { copy(warp = true, warpConfig = cfg) }
                    _message.value = "WARP подключён: сайты увидят IP Cloudflare"
                }
                .onFailure { _message.value = it.message ?: "Не удалось подключить WARP" }
            _warpBusy.value = false
        }
    }

    fun resetWarp() = change { copy(warp = false, warpConfig = "") }

    fun validateUserRule(value: String): String? =
        runCatching { Apexcore.validateUserRule(value).ifBlank { null } }.getOrElse { it.message ?: "Неверное правило" }

    fun validateDnsSpec(d: com.apexvpn.client.data.DnsSpec): String? =
        runCatching { Apexcore.validateDNSSpec(d.type, d.server, d.ip).ifBlank { null } }.getOrElse { it.message ?: "Неверный DNS" }

    fun validateDns(url: String): String? =
        runCatching { Apexcore.validateDNS(url).ifBlank { null } }.getOrElse { it.message ?: "Неверный адрес" }

    // ─── Обновления приложения ───────────────────────────────────────────

    fun checkUpdate() {
        com.apexvpn.client.update.UpdateManager.reopen()
        viewModelScope.launch { UpdateManager.check(silent = false) }
    }

    /** 3.7. Передать обновление на часы */
    fun updateWatch() {
        viewModelScope.launch { com.apexvpn.client.update.WatchUpdater.send(getApplication()) }
    }

    /** 3.4. Обновление в один клик: скачать, проверить SHA-256 и подпись, установить. */
    fun installUpdate(info: com.apexvpn.client.update.UpdateInfo) {
        viewModelScope.launch {
            com.apexvpn.client.update.UpdateInstaller.updateNow(getApplication(), info)
        }
    }



    fun consumeMessage() {
        _message.value = null
    }

    fun showMessage(text: String) {
        _message.value = text
    }
}
