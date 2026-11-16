package com.apexvpn.client.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 197. Счётчик заблокированной рекламы. Ядро отправляет DNS-запросы к рекламным доменам
 * сюда (127.0.0.1), мы отвечаем «нет такого адреса» и считаем. Никуда ничего не уходит.
 */
object AdCounter {
    private val _today = MutableStateFlow(0)
    val today: StateFlow<Int> = _today.asStateFlow()

    @Volatile private var socket: DatagramSocket? = null

    val running: Boolean get() = socket?.isClosed == false
    private val recent = HashMap<String, Long>() // домен → время, чтобы A и AAAA не считались дважды
    private lateinit var appContext: Context

    private val sp by lazy { appContext.getSharedPreferences("adcounter", Context.MODE_PRIVATE) }
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 379. Обнулить счётчик (очистка данных). */
    fun reset() {
        _today.value = 0
        if (::appContext.isInitialized) sp.edit().clear().apply()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        rollDay()
        _today.value = if (sp.getString("day", "") == day()) sp.getInt("count", 0) else 0
        _share.value = sp.getBoolean("share", true)
    }

    // ── 3.24: общий счётчик рекламы — раз в день только число за прошлый день и случайный номер установки.
    // Ни адресов сайтов, ни имени, ни номера телефона. Выключается переключателем «Делиться числом».
    private val _share = MutableStateFlow(true)
    val share: StateFlow<Boolean> = _share.asStateFlow()
    fun setShare(on: Boolean) { _share.value = on; if (::appContext.isInitialized) sp.edit().putBoolean("share", on).apply() }

    /** Новый день — вчерашнее число откладываем для отправки. */
    @Synchronized
    private fun rollDay() {
        val d = sp.getString("day", "").orEmpty()
        if (d.isNotEmpty() && d != day()) sp.edit().putString("prevDay", d).putInt("prevCount", sp.getInt("count", 0)).putString("day", day()).putInt("count", 0).apply()
    }

    /** Отправить число за прошлый день на адрес из пульта (один раз в день). */
    fun report() {
        if (!::appContext.isInitialized || !_share.value) return
        val cfg = AppConfig.value
        val url = cfg.adsStatsUrl.takeIf { it.startsWith("https://") } ?: return
        if (!cfg.on("adstats") || !AppSettings.value.adBlock) return
        rollDay()
        val day = sp.getString("prevDay", "").orEmpty(); val n = sp.getInt("prevCount", 0)
        if (day.isEmpty() || sp.getString("sentDay", "") == day) return
        val dev = sp.getString("dev", "").orEmpty().ifEmpty {
            java.util.UUID.randomUUID().toString().replace("-", "").take(16).also { sp.edit().putString("dev", it).apply() }
        }
        runCatching {
            val c = java.net.URL(url.trimEnd('/') + "/api/ads").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(org.json.JSONObject().put("day", day).put("ads", n).put("dev", dev).toString().toByteArray()) }
            if (c.responseCode in 200..299) sp.edit().putString("sentDay", day).apply()
            c.disconnect()
        }
    }

    /** Запускает локальный DNS; возвращает порт или 0, если не получилось (тогда реклама просто отклоняется ядром). */
    @Synchronized
    fun ensure(port: Int): Int {
        socket?.let { if (!it.isClosed && it.localPort == port) return port }
        stop()
        return runCatching {
            val s = DatagramSocket(port, InetAddress.getByName("127.0.0.1"))
            socket = s
            Thread({ loop(s) }, "apex-adcounter").apply { isDaemon = true }.start()
            port
        }.getOrDefault(0)
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(1500)
        while (!s.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            runCatching { s.receive(packet) }.onFailure { return }
            runCatching {
                val (answer, name) = nxdomain(buf, packet.length) ?: return@runCatching
                s.send(DatagramPacket(answer, answer.size, packet.address, packet.port))
                count(name)
            }
        }
    }

    private fun count(name: String) {
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val last = recent[name]
            recent[name] = now
            if (recent.size > 500) recent.entries.removeAll { now - it.value > 10_000 }
            if (last != null && now - last < 5_000) return
        }
        val d = day()
        if (sp.getString("day", "").orEmpty().let { it.isNotEmpty() && it != d }) rollDay()
        val n = (if (sp.getString("day", "") == d) sp.getInt("count", 0) else 0) + 1
        sp.edit().putString("day", d).putInt("count", n).apply()
        _today.value = n
        MonthStats.ad()
    }

    /** Ответ «нет такого домена» на запрос; null — пакет не похож на DNS-запрос. */
    private fun nxdomain(q: ByteArray, len: Int): Pair<ByteArray, String>? {
        if (len < 17) return null
        var i = 12
        val name = StringBuilder()
        while (i < len) {
            val l = q[i].toInt() and 0xFF
            if (l == 0) { i++; break }
            if (l > 63 || i + 1 + l > len) return null
            if (name.isNotEmpty()) name.append('.')
            name.append(String(q, i + 1, l, Charsets.US_ASCII))
            i += 1 + l
        }
        val end = i + 4 // тип и класс
        if (end > len) return null
        val out = q.copyOf(end)
        out[2] = ((q[2].toInt() and 0x79) or 0x80).toByte() // QR=1, тот же код операции и RD
        out[3] = 0x83.toByte()                              // RA=1, NXDOMAIN
        out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        return out to name.toString().lowercase()
    }
}
