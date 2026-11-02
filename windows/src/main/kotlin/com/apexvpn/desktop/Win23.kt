package com.apexvpn.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.system.exitProcess

/** Запуск команды Windows без окна; код выхода (или -1). */
internal fun runHidden(vararg cmd: String, timeoutSec: Long = 30): Int = runCatching {
    val p = ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    if (!p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); -1 } else p.exitValue()
}.getOrDefault(-1)

/** Команда с правами администратора (окно UAC), когда программа запущена без них. */
internal fun runElevated(exe: String, args: String): Int = runHidden(
    "powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
    "\$p = Start-Process -FilePath '$exe' -ArgumentList '${args.replace("'", "''")}' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit \$p.ExitCode",
    timeoutSec = 120,
)

// ─── Обновление в один клик ───────────────────────────────────────────

/**
 * Скачивает установщик из релиза windows-v<версия> на GitHub (через VPN, если он включён),
 * сверяет SHA-256 с файлом .sha256 из того же релиза и тихо ставит (/VERYSILENT).
 * Установщик сам закрывает старую версию и запускает новую.
 */
object Updater {
    data class State(
        val available: Updates.Info? = null,
        /** 0..100 — идёт загрузка, -1 — нет */
        val progress: Int = -1,
        val ready: File? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val lock = Mutex()
    private val dir get() = File(Paths.data, "update").apply { mkdirs() }
    private val reconnectFlag get() = File(Paths.data, "reconnect-after-update")
    private const val TRUSTED = "https://github.com/${AppInfo.REPO}/releases/download/windows-v"

    suspend fun check(): Updates.Info? {
        val info = Updates.check()
        _state.update { it.copy(available = info, message = if (info == null) "У вас последняя версия ${AppInfo.VERSION}" else null) }
        return info
    }

    private fun proxy() = if (Engine.status.value == VpnStatus.On) Net.viaVpn() else null

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp -> val buf = ByteArray(64 * 1024); while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Скачать и проверить установщик; null — не получилось (причина — в state.message). */
    suspend fun download(info: Updates.Info): File? = lock.withLock {
        withContext(Dispatchers.IO) {
            _state.value.ready?.takeIf { it.exists() && it.name.contains(info.tag) }?.let { return@withContext it }
            fun fail(msg: String): File? { _state.update { it.copy(progress = -1, message = msg) }; return null }
            // Только файлы из релизов этого репозитория
            if (!info.url.startsWith(TRUSTED) || !info.url.endsWith(".exe")) return@withContext fail("В релизе нет установщика — скачайте на странице релизов")
            val expected = runCatching {
                Net.open(info.url + ".sha256", proxy(), 10_000).inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).first().lowercase()
            }.getOrNull()?.takeIf { it.length == 64 } ?: return@withContext fail("В релизе нет контрольной суммы — скачайте на странице релизов")
            val name = info.url.substringAfterLast('/')
            val file = File(dir, name)
            if (file.exists() && sha256(file) == expected) {
                _state.update { it.copy(progress = -1, ready = file, message = null) }
                return@withContext file
            }
            dir.listFiles()?.filter { it.name != "$name.part" }?.forEach { it.delete() }
            val part = File(dir, "$name.part")
            _state.update { it.copy(progress = 0, message = null) }
            var done = false
            for (attempt in 1..4) {
                val ok = runCatching {
                    val have = part.length()
                    val c = Net.open(info.url, proxy(), 15_000)
                    if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                    val code = c.responseCode
                    val append = code == 206 && have > 0
                    check(code == 200 || append) { "HTTP $code" }
                    val total = (if (append) have else 0L) + c.contentLengthLong.coerceAtLeast(0)
                    var got = if (append) have else 0L
                    FileOutputStream(part, append).use { out ->
                        c.inputStream.use { inp ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                if (total > 0) _state.update { it.copy(progress = (got * 100 / total).toInt().coerceIn(0, 99)) }
                            }
                        }
                    }
                }.isSuccess
                if (ok) { done = true; break }
                delay(2000L * attempt)  // докачаем с того же места
            }
            if (!done) return@withContext fail("Не удалось скачать — проверьте интернет и нажмите ещё раз")
            if (sha256(part) != expected) { part.delete(); return@withContext fail("Файл повреждён или подменён — не устанавливаем. Нажмите ещё раз") }
            file.delete()
            if (!part.renameTo(file)) return@withContext fail("Не удалось сохранить файл обновления")
            _state.update { it.copy(progress = -1, ready = file, message = null) }
            file
        }
    }

    /** Тихая установка: VPN выключается, программа закрывается, установщик ставит и запускает новую версию. */
    fun install(file: File) {
        val wasOn = Engine.running
        runCatching { if (wasOn) reconnectFlag.writeText("1") else reconnectFlag.delete() }
        KillSwitch.release()
        Engine.shutdown()
        runCatching { ProcessBuilder(file.absolutePath, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-").start() }
            .onFailure { _state.update { s -> s.copy(message = "Не удалось запустить установщик: ${it.message}") }; return }
        exitProcess(0)
    }

    /** Одна кнопка: скачать, проверить и поставить. */
    suspend fun updateNow() {
        val info = _state.value.available ?: check() ?: return
        download(info)?.let { install(it) }
    }

    /** После обновления: было подключено — подключаемся снова. */
    fun consumeReconnect(): Boolean = reconnectFlag.exists().also { if (it) reconnectFlag.delete() }
}

// ─── Kill Switch ──────────────────────────────────────────────────────

/**
 * VPN неожиданно упал — интернет закрыт, пока APEX не переподключится (пробует каждые 10 с).
 * TUN (с правами администратора): правила брандмауэра Windows блокируют всё, кроме серверов VPN,
 * локальной сети и DNS. Системный прокси: прокси остаётся на выключенном ядре — браузеры и программы,
 * которые идут через прокси, не выходят в интернет напрямую.
 */
object KillSwitch {
    private const val RULE = "APEX-VPN-KillSwitch"
    private val _engaged = MutableStateFlow(false)
    val engaged = _engaged.asStateFlow()
    private val marker get() = File(Paths.data, "killswitch.on")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retry: Job? = null
    @Volatile private var mode = ""
    @Volatile private var serverIps: Set<String> = emptySet()

    /** Адреса серверов запоминаем, пока сеть работает (после подключения). */
    fun remember(nodes: List<Node>) {
        scope.launch {
            serverIps = nodes.map { it.server }.distinct().flatMap { h ->
                runCatching { InetAddress.getAllByName(h).toList() }.getOrDefault(emptyList())
            }.filterIsInstance<Inet4Address>().map { it.hostAddress }.toSet()
        }
    }

    /** Ядро упало (не по кнопке) — закрываем интернет. */
    fun onDrop() {
        val s = Store.value
        if (!s.killSwitch || _engaged.value) return
        val tun = s.connectType == ConnectType.Tun
        val ok = if (tun) WinSys.isAdmin() && block() else runCatching { WinSys.setProxy("127.0.0.1:${Store.proxyPort}") }.isSuccess
        if (!ok) return
        mode = if (tun) "tun" else "proxy"
        runCatching { marker.writeText(mode) }
        _engaged.value = true
        retry?.cancel()
        retry = scope.launch {
            while (isActive && _engaged.value) {
                delay(10_000)
                if (Engine.status.value != VpnStatus.On && Engine.status.value != VpnStatus.Connecting) Engine.connect()
            }
        }
    }

    /** Снова подключено — открываем интернет (прокси уже ставит само подключение). */
    fun onConnected() { if (_engaged.value) release(keepProxy = true) }

    /** Выключить блокировку: отключение по кнопке, выход, выключенный переключатель. */
    fun release(keepProxy: Boolean = false) {
        retry?.cancel(); retry = null
        if (!_engaged.value && !marker.exists()) return
        val m = mode.ifBlank { runCatching { marker.readText() }.getOrDefault("") }
        if (m == "tun") unblock()
        if (m == "proxy" && !keepProxy) runCatching { WinSys.clearProxy() }
        runCatching { marker.delete() }
        mode = ""
        _engaged.value = false
    }

    /** При запуске: блокировка осталась от прошлого раза (программу закрыли) — снимаем. Было — true. */
    fun startupCleanup(): Boolean {
        if (!marker.exists()) return false
        val m = runCatching { marker.readText() }.getOrDefault("")
        if (m == "tun") unblock()
        runCatching { marker.delete() }
        return true
    }

    private fun unblock() {
        val args = arrayOf("advfirewall", "firewall", "delete", "rule", "name=$RULE")
        if (WinSys.isAdmin()) runHidden("netsh", *args) else runElevated("netsh", args.joinToString(" "))
    }

    private fun block(): Boolean {
        runHidden("netsh", "advfirewall", "firewall", "delete", "rule", "name=$RULE")
        val v4 = blockedRanges(serverIps)
        val v6 = "2000::-3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"
        val ports = "1-52,54-65535"  // DNS (53) оставляем — чтобы ядро нашло сервер
        val rules = listOf("TCP" to v4, "UDP" to v4, "TCP" to v6, "UDP" to v6)
        return rules.all { (proto, ips) ->
            runHidden("netsh", "advfirewall", "firewall", "add", "rule", "name=$RULE", "dir=out", "action=block",
                "protocol=$proto", "remoteport=$ports", "remoteip=$ips", "enable=yes") == 0
        }.also { if (!it) runHidden("netsh", "advfirewall", "firewall", "delete", "rule", "name=$RULE") }
    }

    /** Все публичные IPv4, кроме серверов VPN: локальная сеть, loopback и multicast не трогаем. */
    internal fun blockedRanges(allowed: Set<String>): String {
        fun ip(s: String): Long = s.split('.').fold(0L) { a, p -> a * 256 + p.toLong() }
        fun str(v: Long) = "${v shr 24 and 255}.${v shr 16 and 255}.${v shr 8 and 255}.${v and 255}"
        val keep = mutableListOf(
            ip("0.0.0.0") to ip("0.255.255.255"), ip("10.0.0.0") to ip("10.255.255.255"),
            ip("127.0.0.0") to ip("127.255.255.255"), ip("169.254.0.0") to ip("169.254.255.255"),
            ip("172.16.0.0") to ip("172.31.255.255"), ip("192.168.0.0") to ip("192.168.255.255"),
            ip("224.0.0.0") to ip("255.255.255.255"),
        )
        allowed.filter { it.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) }.forEach { keep += ip(it) to ip(it) }
        keep.sortBy { it.first }
        val out = mutableListOf<String>()
        var next = 0L
        for ((a, b) in keep) {
            if (a > next) out += if (a - 1 == next) str(next) else "${str(next)}-${str(a - 1)}"
            next = maxOf(next, b + 1)
        }
        if (next <= 0xFFFFFFFFL) out += "${str(next)}-255.255.255.255"
        return out.joinToString(",")
    }
}

// ─── Раздача VPN по дому ──────────────────────────────────────────────

object LanShare {
    private const val RULE = "APEX-VPN-Share"

    fun options(s: Settings): JSONObject = JSONObject()
        .put("enabled", s.shareLan).put("port", s.sharePort)
        .put("username", if (s.sharePassOn && s.sharePass.isNotBlank()) "apex" else "")
        .put("password", if (s.sharePassOn) s.sharePass else "")

    /** Адреса компьютера в домашней сети (без туннеля и виртуальных адаптеров). */
    fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filter { n -> listOf("vmware", "virtualbox", "hyper-v", "vethernet", "wintun", "tun", "wsl").none { it in n.displayName.lowercase() } }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress && !it.hostAddress.startsWith("172.19.") }
            .map { it.hostAddress }
    }.getOrDefault(emptyList())

    /** Сколько устройств сейчас ходит через раздачу. */
    fun devices(): Int = runCatching {
        val a = Clash.connections()?.optJSONArray("connections") ?: return 0
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("metadata") }
            .filter { "share-in" in it.optString("type") || "share-in" in it.optString("inbound") }
            .map { it.optString("sourceIP") }.toSet().size
    }.getOrDefault(0)

    /** Разрешение в брандмауэре для домашней сети (если программа запущена от администратора). */
    fun allowFirewall(port: Int) {
        if (!WinSys.isAdmin()) return
        runHidden("netsh", "advfirewall", "firewall", "delete", "rule", "name=$RULE")
        listOf("TCP", "UDP").forEach { proto ->
            runHidden("netsh", "advfirewall", "firewall", "add", "rule", "name=$RULE", "dir=in", "action=allow",
                "protocol=$proto", "localport=$port", "profile=private,domain", "enable=yes")
        }
    }

    fun randomPass(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        return (1..8).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }
}

// ─── Ограничение скорости для программ ────────────────────────────────

/**
 * Политики QoS Windows (New-NetQosPolicy): ограничивают ОТДАЧУ программы — то, чем торрент
 * обычно забивает канал. Скорость загрузки Windows сама ограничить не умеет — её задают в самой программе.
 * Нужны права администратора.
 */
object AppLimits {
    private val safe = Regex("""^[A-Za-z0-9._ ()\-]+\.exe$""")

    fun valid(exe: String) = safe.matches(exe)

    /** Применить список; null — получилось, иначе причина. */
    fun apply(list: List<AppLimit>): String? {
        if (!WinSys.isAdmin()) return "Нужны права администратора: перезапустите APEX VPN от администратора"
        val script = buildString {
            append("Get-NetQosPolicy -ErrorAction SilentlyContinue | Where-Object { \$_.Name -like 'APEX VPN *' } | Remove-NetQosPolicy -Confirm:\$false; ")
            list.filter { valid(it.exe) }.forEach {
                append("New-NetQosPolicy -Name 'APEX VPN ${it.exe}' -AppPathNameMatchCondition '${it.exe}' ")
                append("-ThrottleRateActionBitsPerSecond ${it.mbps.toLong() * 1_000_000} -Confirm:\$false | Out-Null; ")
            }
        }
        return if (runHidden("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script, timeoutSec = 60) == 0) null
        else "Windows не принял правило — проверьте имя программы"
    }
}

// ─── Страницы ─────────────────────────────────────────────────────────

@Composable
fun UpdatePage() {
    val s by Store.settings.collectAsState()
    val st by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (st.available == null) runCatching { Updater.check() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Text("Установлена версия ${AppInfo.VERSION}", color = C.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val av = st.available
            Text(
                when {
                    st.progress >= 0 -> "Загрузка обновления ${st.progress}%"
                    av != null -> "Доступна версия ${av.tag}"
                    else -> st.message ?: "Проверяем…"
                },
                color = if (av != null) C.Accent else C.Dim, fontSize = 14.sp,
            )
            if (st.progress >= 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { st.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = C.Accent)
            }
            if (av != null && st.message != null) { Spacer(Modifier.height(6.dp)); Text(st.message!!, color = C.Bad, fontSize = 13.sp) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (av != null) PrimaryButton(if (busy) "Обновляем…" else "Обновить сейчас", enabled = !busy) {
                    busy = true; scope.launch { try { Updater.updateNow() } finally { busy = false } }
                } else SecondaryButton("Проверить ещё раз") { scope.launch { runCatching { Updater.check() } } }
                SecondaryButton("Страница релизов") { WinSys.open(Updates.PAGE) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Скачивается с GitHub (из релизов windows-v…), через VPN, если он включён. Файл сверяется по SHA-256 — повреждённый или подменённый не ставится. " +
                "Установка тихая: VPN на минуту выключится, программа перезапустится и подключится снова. Настройки и подписка сохраняются.",
                color = C.Dim, fontSize = 12.sp)
        }
        Section("Как обновлять")
        Card(Modifier.fillMaxWidth()) {
            Toggle("Проверять обновления", "Раз в 6 часов", s.checkUpdates) { Store.update { copy(checkUpdates = it) } }
            Spacer(Modifier.height(6.dp))
            Choice("Спросить меня", "Уведомление у часов и кнопка «Обновить» в меню значка", s.updateMode == "ask") { Store.update { copy(updateMode = "ask") } }
            Choice("Ставить само", "Скачает заранее и поставит, когда VPN выключен", s.updateMode == "auto") { Store.update { copy(updateMode = "auto") } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun KillSwitchPage() {
    val s by Store.settings.collectAsState()
    val engaged by KillSwitch.engaged.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Toggle("Kill Switch", "Если VPN неожиданно упал — интернет закрыт, пока APEX не переподключится", s.killSwitch) { v ->
                Store.update { copy(killSwitch = v) }
                if (!v) KillSwitch.release()
            }
            if (engaged) {
                Spacer(Modifier.height(8.dp))
                Text("Сейчас интернет закрыт: VPN переподключается…", color = C.Bad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Вернуть интернет без VPN", C.Bad) { KillSwitch.release() }
            }
        }
        Section("Как работает")
        Card(Modifier.fillMaxWidth()) {
            listOf(
                "TUN (от администратора): брандмауэр Windows пропускает только серверы VPN, домашнюю сеть и DNS — всё остальное ждёт VPN",
                "Системный прокси: прокси остаётся на выключенном VPN — браузеры и программы через прокси не выходят напрямую. Программы, которые прокси не используют, так не закрыть — для полной защиты выберите TUN",
                "APEX пробует переподключиться каждые 10 секунд, и интернет сам откроется",
                "Отключение по кнопке, выход из программы и выключенный переключатель интернет не закрывают",
            ).forEach { Text("• $it", color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            if (s.connectType == ConnectType.Tun && !WinSys.isAdmin()) {
                Spacer(Modifier.height(8.dp))
                Text("Для TUN нужны права администратора", color = C.Bad, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SharePage() {
    val s by Store.settings.collectAsState()
    val status by Engine.status.collectAsState()
    var port by remember { mutableStateOf(s.sharePort.toString()) }
    var devices by remember { mutableStateOf(0) }
    val ips = remember(s.shareLan, status) { LanShare.addresses() }
    LaunchedEffect(s.shareLan, status) {
        while (s.shareLan && status == VpnStatus.On) { devices = LanShare.devices(); delay(3000) }
    }
    fun change(block: Settings.() -> Settings) {
        Store.update(block)
        if (Store.value.shareLan) LanShare.allowFirewall(Store.value.sharePort)
        Engine.reload()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) {
            Toggle("Раздавать VPN по дому", "Телевизор, приставка, телефон или другой ПК в той же Wi-Fi сети идут через этот компьютер", s.shareLan) { v ->
                change { copy(shareLan = v, sharePass = sharePass.ifBlank { LanShare.randomPass() }) }
            }
            if (s.shareLan) {
                Spacer(Modifier.height(10.dp))
                Text("На устройстве укажите прокси:", color = C.Dim, fontSize = 13.sp)
                if (ips.isEmpty()) Text("Компьютер не в домашней сети", color = C.Bad, fontSize = 14.sp)
                ips.forEach { Text("$it   порт ${s.sharePort}", color = C.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Text("Тип: HTTP или SOCKS5" + if (s.sharePassOn) " · логин apex · пароль ${s.sharePass}" else " · без пароля", color = C.Text, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        status != VpnStatus.On -> "Начнёт работать, когда VPN подключён"
                        devices > 0 -> "Сейчас подключено устройств: $devices"
                        else -> "Пока никто не подключён"
                    }, color = if (devices > 0) C.Good else C.Dim, fontSize = 13.sp,
                )
            }
        }
        if (s.shareLan) {
            Section("Настройки")
            Card(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(port, "Порт", Modifier.weight(1f)) { port = it.filter(Char::isDigit).take(5) }
                    PrimaryButton("Сохранить", enabled = (port.toIntOrNull() ?: 0) in 1025..65535 && port.toIntOrNull() != s.sharePort) {
                        change { copy(sharePort = port.toInt()) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Toggle("Пароль", "Телевизоры на Android TV пароль для прокси не поддерживают — тогда оставьте выключенным", s.sharePassOn) { v -> change { copy(sharePassOn = v) } }
                if (s.sharePassOn) SecondaryButton("Новый пароль") { change { copy(sharePass = LanShare.randomPass()) } }
            }
        }
        Section("Как настроить")
        Card(Modifier.fillMaxWidth()) {
            listOf(
                "Телефон: Wi-Fi → ваша сеть → Прокси → Вручную → адрес и порт выше",
                "Android TV / приставка: Настройки сети → Прокси → Вручную",
                "Другой компьютер: Параметры → Сеть и Интернет → Прокси",
                "Если Windows спросит разрешение для sing-box — разрешите для частной сети",
                "Работает только в домашней сети: из интернета к компьютеру никто не подключится",
            ).forEach { Text("• $it", color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LimitsPage() {
    val s by Store.settings.collectAsState()
    var name by remember { mutableStateOf("") }
    var mbps by remember { mutableStateOf(10) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(list: List<AppLimit>) {
        scope.launch {
            val err = withContext(Dispatchers.IO) { AppLimits.apply(list) }
            if (err == null) Store.update { copy(appLimits = list) }
            msg = err ?: "Готово"
        }
    }
    val opts = listOf(1, 3, 5, 10, 25, 50)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Программа не будет отдавать быстрее заданного — торрент не забьёт канал, и у остальных не вырастет пинг. " +
            "Ограничивается отдача (upload): так умеет сама Windows. Скорость загрузки задайте в самой программе (в торрент-клиенте — «Ограничение приёма»).",
            color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 10.dp))
        Card(Modifier.fillMaxWidth()) {
            Field(name, "Имя программы, например qbittorrent.exe", Modifier.fillMaxWidth()) { name = it }
            Spacer(Modifier.height(8.dp))
            Text("Не быстрее", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Segmented(opts.map { "$it Мбит/с" }, opts.indexOf(mbps).coerceAtLeast(0)) { mbps = opts[it] }
            Spacer(Modifier.height(10.dp))
            val n = name.trim().let { if (it.isNotEmpty() && !it.lowercase().endsWith(".exe")) "$it.exe" else it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Добавить", enabled = AppLimits.valid(n)) {
                    save(s.appLimits.filter { !it.exe.equals(n, true) } + AppLimit(n, mbps)); name = ""
                }
                SecondaryButton("Выбрать файл…") {
                    val d = java.awt.FileDialog(null as java.awt.Frame?, "Выберите программу", java.awt.FileDialog.LOAD)
                    d.file = "*.exe"; d.isVisible = true
                    val f = d.file
                    if (f != null) {
                        if (AppLimits.valid(f)) save(s.appLimits.filter { !it.exe.equals(f, true) } + AppLimit(f, mbps))
                        else msg = "Такое имя файла Windows не примет"
                    }
                }
            }
            msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = if (it == "Готово") C.Good else C.Bad, fontSize = 13.sp) }
        }
        if (s.appLimits.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Group {
                s.appLimits.forEachIndexed { i, a ->
                    if (i > 0) GroupDivider()
                    ListRow(a.exe, "Отдача не быстрее ${a.mbps} Мбит/с") {
                        Icon(Icons.Rounded.Close, "Убрать", tint = C.Dim, modifier = Modifier.clip(CircleShape).clickable { save(s.appLimits - a) }.padding(6.dp))
                    }
                }
            }
        }
        if (!WinSys.isAdmin()) {
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Text("Нужны права администратора", color = C.Bad, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                SecondaryButton("Перезапустить от администратора") { if (WinSys.relaunchAsAdmin()) { Engine.shutdown(); exitProcess(0) } }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Окно «Что нового» после обновления: только текущая версия. */
@Composable
fun WhatsNewLatest(onClose: () -> Unit) {
    val text = remember { runCatching { Thread.currentThread().contextClassLoader.getResourceAsStream("CHANGELOG.md")!!.bufferedReader().readText() }.getOrDefault("") }
    val block = remember(text) { text.split("\n## ").drop(1).firstOrNull { it.startsWith(AppInfo.VERSION) } }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("APEX VPN обновлён до ${AppInfo.VERSION}", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        block?.substringAfter('\n')?.lines()?.forEach { line ->
            when {
                line.startsWith("- ") -> Text("• " + line.removePrefix("- ").replace("**", "").replace("`", ""), color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
                line.isNotBlank() -> Text(line.replace("**", ""), color = C.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
        } ?: Text("Список изменений — в «Настройках → Что нового»", color = C.Dim)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Отлично") { onClose() }
    }
}
