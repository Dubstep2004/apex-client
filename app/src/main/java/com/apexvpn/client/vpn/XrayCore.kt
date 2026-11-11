package com.apexvpn.client.vpn

import android.content.Context
import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Второе ядро — Xray (как в Happ). Запускается отдельным процессом из
 * nativeLibraryDir/libxray.so. sing-box подключается к нему как к SOCKS на 127.0.0.1,
 * порты начинаются с Prefs.xrayBasePort (свои на каждом устройстве, см. xrayPort в core/apexcore).
 */
object XrayCore {
    private const val TAG = "ApexXray"

    private var process: Process? = null
    private var runningConfig: String? = null
    private val log = ArrayDeque<String>()

    fun isInstalled(context: Context) = binary(context).canExecute()

    private fun binary(context: Context) = File(context.applicationInfo.nativeLibraryDir, "libxray.so")

    /** Запускает Xray с конфигом, перезапускает при изменении, останавливает при пустом. */
    @Synchronized
    fun apply(context: Context, config: String) {
        if (config.isBlank()) {
            stop()
            return
        }
        if (config == runningConfig && process?.isAlive == true) return
        stop()
        val bin = binary(context)
        check(bin.canExecute()) { "Ядро Xray не найдено в этой сборке приложения" }

        // Порт занят, а своего процесса нет — остался Xray от прошлого запуска (приложение
        // было убито системой). Иначе новый Xray не сможет открыть порт.
        if (portOpen(com.apexvpn.client.data.Prefs.xrayBasePort)) {
            runCatching { ProcessBuilder("pkill", "-f", "libxray.so").start().waitFor() }
            Thread.sleep(300)
        }
        val dir = File(context.filesDir, "xray").apply { mkdirs() }
        val file = File(dir, "config.json")
        file.writeText(config)
        val p = ProcessBuilder(bin.absolutePath, "run", "-c", file.absolutePath)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment()["XRAY_LOCATION_ASSET"] = dir.absolutePath
                environment()["XRAY_LOCATION_CONFIG"] = dir.absolutePath
            }
            .start()
        process = p
        runningConfig = config
        synchronized(log) { log.clear() }
        Thread({
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    Log.i(TAG, line)
                    synchronized(log) {
                        log.addLast(line)
                        while (log.size > 30) log.removeFirst()
                    }
                }
            }
        }, "xray-log").apply { isDaemon = true }.start()

        // Ждём, пока Xray откроет порты всех серверов (или упадёт). 3.17.3: раньше ждали только
        // первый — и автопилот в первые секунды проверял остальные Xray-серверы, когда их порт
        // ещё закрыт («connection refused»), и считал их мёртвыми до следующей проверки
        val ports = runCatching {
            val arr = org.json.JSONObject(config).optJSONArray("inbounds")
            (0 until (arr?.length() ?: 0)).mapNotNull { arr!!.optJSONObject(it)?.optInt("port", 0)?.takeIf { p -> p > 0 } }
        }.getOrDefault(emptyList()).ifEmpty { listOf(com.apexvpn.client.data.Prefs.xrayBasePort) }
        val deadline = System.currentTimeMillis() + 5_000
        var firstUp = false
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive) break
            if (!firstUp) firstUp = portOpen(ports.first())
            if (firstUp && ports.all { portOpen(it) }) return
            Thread.sleep(100)
        }
        // Xray жив и открыл хотя бы первый порт — работаем, остальные догонят
        if (p.isAlive && firstUp) return
        val reason = lastError()
        stop()
        error(if (reason.isNotEmpty()) "Xray не запустился: $reason" else "Xray не запустился")
    }

    @Synchronized
    fun stop() {
        process?.let { p ->
            runCatching { p.destroy() }
            runCatching { if (!p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
        }
        process = null
        runningConfig = null
    }

    /** Xray должен работать, но процесс завершился. */
    fun crashed(): Boolean = runningConfig != null && process?.isAlive == false

    fun lastError(): String = synchronized(log) {
        log.lastOrNull { it.contains("failed", true) || it.contains("error", true) || it.contains("panic", true) }
            ?: log.lastOrNull().orEmpty()
    }.take(200)

    private fun portOpen(port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
        true
    }.getOrDefault(false)
}
