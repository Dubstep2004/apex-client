package com.apexvpn.client.update

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import com.apexvpn.client.ApexApp
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.R
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Ход обновления в один клик. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val percent: Int) : InstallState
    data object Verifying : InstallState
    /** Ждём, пока пользователь нажмёт «Установить» в окне Android */
    data object Installing : InstallState
    /** Нужно разрешить установку из APEX VPN (Настройки Android) */
    data object NeedPermission : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * 3.4. Обновление в один клик: скачивает apex-vpn-<версия>.apk из последнего релиза на GitHub
 * (с докачкой), сверяет SHA-256 из apex-vpn-<версия>.apk.sha256 и подпись (тот же ключ, что у
 * установленного приложения) и ставит через PackageInstaller. Android 12+: если прошлую версию
 * поставил сам APEX, обновление может пройти без вопроса; иначе — одна кнопка «Установить».
 */
object UpdateInstaller {
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()
    private val lock = Mutex()
    private const val NOTIFY_ID = 6
    const val ACTION_STATUS = "com.apexvpn.client.UPDATE_STATUS"

    @Volatile private var resumed = 0
    /** Окно приложения на экране — окно установки можно открыть сразу */
    val visible get() = resumed > 0

    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { resumed++ }
            override fun onActivityPaused(a: Activity) { resumed = (resumed - 1).coerceAtLeast(0) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    internal fun set(s: InstallState) { _state.value = s }

    fun reset() { if (_state.value !is InstallState.Downloading) _state.value = InstallState.Idle }

    /** Разрешена ли установка обновлений из APEX VPN (Android 8+ спрашивает один раз). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val i = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_SECURITY_SETTINGS)
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Одна кнопка «Обновить»: скачать, проверить, поставить. */
    suspend fun updateNow(context: Context, info: UpdateInfo, silent: Boolean = false, showProgress: Boolean = true): Boolean {
        val ctx = context.applicationContext
        if (info.apkUrl == null || info.shaUrl == null) {
            set(InstallState.Failed("В релизе нет файла для обновления — откройте страницу версии на GitHub"))
            return false
        }
        if (!canInstall(ctx)) {
            set(InstallState.NeedPermission)
            if (!silent) openInstallPermission(ctx)
            return false
        }
        val apk = download(ctx, info, showProgress) ?: return false
        return install(ctx, apk, silent)
    }

    private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String, timeout: Int): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeout; readTimeout = timeout * 2
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "APEX VPN/${BuildConfig.VERSION_NAME}")
    }

    /**
     * Скачать файл из релиза и сверить SHA-256 (с докачкой при обрыве).
     * Ответ: файл или текст ошибки. Общий для телефона, отката и часов.
     */
    internal suspend fun fetchVerified(
        ctx: Context, url: String, shaUrl: String, name: String, onProgress: (Int) -> Unit,
    ): Pair<File?, String?> = withContext(Dispatchers.IO) {
        val expected = runCatching {
            val c = open(shaUrl, 15_000)
            try { c.inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).first().lowercase() } finally { c.disconnect() }
        }.getOrNull()?.takeIf { it.length == 64 } ?: return@withContext null to "Не удалось получить контрольную сумму — проверьте интернет"
        val dir = updatesDir(ctx)
        val file = File(dir, name)
        if (file.exists() && sha256(file) == expected) return@withContext file to null
        val part = File(dir, "$name.part")
        var done = false
        for (attempt in 1..4) {
            val ok = runCatching {
                val have = part.length()
                val c = open(url, 20_000)
                try {
                    if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                    val code = c.responseCode
                    val append = code == 206 && have > 0
                    check(code == 200 || append) { "HTTP $code" }
                    val total = (if (append) have else 0L) + c.contentLengthLong.coerceAtLeast(0)
                    var got = if (append) have else 0L
                    var lastPct = -1
                    FileOutputStream(part, append).use { out ->
                        c.inputStream.use { inp ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                if (total > 0) {
                                    val pct = (got * 100 / total).toInt().coerceIn(0, 99)
                                    if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                                }
                            }
                        }
                    }
                } finally { c.disconnect() }
            }.isSuccess
            if (ok) { done = true; break }
            delay(2000L * attempt) // докачаем с того же места
        }
        if (!done) return@withContext null to "Не удалось скачать — проверьте интернет и нажмите ещё раз"
        if (sha256(part) != expected) {
            part.delete()
            return@withContext null to "Файл повреждён или подменён — не используем. Нажмите ещё раз"
        }
        file.delete()
        if (!part.renameTo(file)) return@withContext null to "Не удалось сохранить файл"
        file to null
    }

    /** Скачать и проверить APK; null — не получилось (причина — в state). */
    suspend fun download(ctx: Context, info: UpdateInfo, showProgress: Boolean): File? = lock.withLock {
        fun fail(msg: String): File? { set(InstallState.Failed(msg)); cancelProgress(ctx); return null }
        val apkUrl = info.apkUrl ?: return@withLock fail("В релизе нет файла apk")
        val shaUrl = info.shaUrl ?: return@withLock fail("В релизе нет контрольной суммы")
        set(InstallState.Downloading(0))
        val name = "apex-vpn-${info.versionName}.apk"
        updatesDir(ctx).listFiles()?.filter { it.name.startsWith("apex-vpn-") && !it.name.startsWith(name) }?.forEach { it.delete() }
        val (file, err) = fetchVerified(ctx, apkUrl, shaUrl, name) { pct ->
            set(InstallState.Downloading(pct))
            if (showProgress && pct % 5 == 0) notifyProgress(ctx, info.versionName, pct)
        }
        if (file == null) return@withLock fail(err ?: "Не удалось скачать обновление")
        set(InstallState.Verifying)
        if (!withContext(Dispatchers.IO) { signedByUs(ctx, file) }) {
            file.delete()
            return@withLock fail("Файл подписан другим ключом — не устанавливаем")
        }
        cancelProgress(ctx)
        file
    }

    /** Тот же пакет, версия новее и та же подпись, что у установленного приложения. Не смогли прочитать подпись — решит сам Android. */
    @Suppress("DEPRECATION")
    private fun signedByUs(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return@runCatching false
        if (archive.packageName != ctx.packageName) return@runCatching false
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        val newer = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode > mine.longVersionCode else archive.versionCode > mine.versionCode
        if (!newer) return@runCatching false
        val a = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val b = if (Build.VERSION.SDK_INT >= 28) mine.signingInfo?.apkContentsSigners else mine.signatures
        if (a.isNullOrEmpty() || b.isNullOrEmpty()) true // Android сам не даст поставить чужую подпись
        else a.map { it.toCharsString() }.toSet() == b.map { it.toCharsString() }.toSet()
    }.getOrDefault(false)

    /** Установка через PackageInstaller; ответ придёт в UpdateReceiver. */
    suspend fun install(ctx: Context, apk: File, silent: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (VpnState.status.value == VpnStatus.Started) Prefs.reconnectAfterUpdate = true
            // Обновляли из открытого приложения — после замены откроем его снова
            Prefs.reopenAfterUpdate = visible
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                setSize(apk.length())
                // Android 12+: обновление себя без вопроса, если прошлую версию поставил APEX
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("apex-vpn.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(
                    ctx, id,
                    Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS).putExtra("silent", silent),
                    flags,
                )
                set(InstallState.Installing)
                s.commit(pi.intentSender)
            }
            true
        }.getOrElse {
            Prefs.reconnectAfterUpdate = false
            set(InstallState.Failed("Не удалось начать установку: ${it.message}"))
            false
        }
    }

    // ─── Фоновая проверка (BackgroundWork) ───────────────────────────────

    /** «Ставить само»: скачать заранее; поставить, когда VPN выключен или экран погас. */
    suspend fun autoUpdate(ctx: Context, info: UpdateInfo): Boolean {
        val s = AppSettings.value
        if (s.updateWifiOnly && ctx.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered != false) return false
        if (!canInstall(ctx)) return false
        val apk = download(ctx, info, showProgress = false) ?: return false
        val screenOff = ctx.getSystemService<PowerManager>()?.isInteractive == false
        if (VpnState.status.value != VpnStatus.Stopped && !screenOff) return false // поставим в следующий раз
        return install(ctx, apk, silent = true)
    }

    // ─── Уведомления ─────────────────────────────────────────────────────

    private fun notifyProgress(ctx: Context, version: String, pct: Int) {
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        runCatching {
            nm.notify(
                NOTIFY_ID,
                NotificationCompat.Builder(ctx, ApexApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_apex)
                    .setContentTitle("Загрузка обновления $pct%")
                    .setContentText("APEX VPN $version")
                    .setProgress(100, pct, false)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setSilent(true)
                    .build(),
            )
        }
    }

    /** Новая версия встала — скачанные файлы обновления больше не нужны. */
    fun cleanup(ctx: Context) {
        runCatching { File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() } }
    }

    internal fun notifyUpdated(ctx: Context, open: Intent) {
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(ctx, 15, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            nm.notify(
                NOTIFY_ID,
                NotificationCompat.Builder(ctx, ApexApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_apex)
                    .setContentTitle("APEX VPN обновлён до ${BuildConfig.VERSION_NAME}")
                    .setContentText("Нажмите, чтобы открыть")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    internal fun cancelProgress(ctx: Context) {
        runCatching { ctx.getSystemService<NotificationManager>()?.cancel(NOTIFY_ID) }
    }

    /** Окно Android «Установить?» не открылось само (приложение в фоне) — уведомление с ним. */
    internal fun notifyConfirm(ctx: Context, confirm: Intent) {
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        val pi = PendingIntent.getActivity(ctx, 13, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            nm.notify(
                NOTIFY_ID,
                NotificationCompat.Builder(ctx, ApexApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_stat_apex)
                    .setContentTitle("Обновление APEX VPN скачано")
                    .setContentText("Нажмите, чтобы установить")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

/** Ответ установщика Android и запуск после обновления. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UpdateInstaller.ACTION_STATUS -> when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                    if (UpdateInstaller.visible) {
                        runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { UpdateInstaller.notifyConfirm(context, confirm) }
                    } else {
                        UpdateInstaller.notifyConfirm(context, confirm)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> Unit // приложение заменено — дальше MY_PACKAGE_REPLACED
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Prefs.reconnectAfterUpdate = false
                    UpdateInstaller.set(InstallState.Idle)
                }
                else -> {
                    Prefs.reconnectAfterUpdate = false
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    UpdateInstaller.set(InstallState.Failed("Android не установил обновление" + (msg?.let { ": $it" } ?: "")))
                }
            }
            // Новая версия встала: VPN был включён — включаем снова
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Prefs.init(context)
                UpdateInstaller.cleanup(context)
                if (Prefs.reconnectAfterUpdate) {
                    Prefs.reconnectAfterUpdate = false
                    if (android.net.VpnService.prepare(context) == null) runCatching { ApexVpnService.start(context) }
                }
                // Обновляли из открытого приложения: открываем снова. Если Android не даст открыть
                // из фона — уведомление «Обновлено, открыть» (окно приложения само его уберёт)
                if (Prefs.reopenAfterUpdate) {
                    Prefs.reopenAfterUpdate = false
                    val open = Intent(context, com.apexvpn.client.ui.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    runCatching { context.startActivity(open) }
                    UpdateInstaller.notifyUpdated(context, open)
                }
            }
        }
    }
}
