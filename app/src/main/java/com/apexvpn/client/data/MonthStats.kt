package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 3.6. Отчёт за месяц: трафик через VPN, время с VPN, подключения, заблокированная реклама,
 * средний пинг и любимые серверы. Всё хранится только на телефоне.
 */
object MonthStats {
    data class Month(
        val key: String,
        val down: Long = 0, val up: Long = 0,
        val seconds: Long = 0, val connects: Int = 0, val ads: Int = 0,
        val pingSum: Long = 0, val pingN: Int = 0,
        val servers: Map<String, Long> = emptyMap(),
    ) {
        val avgPing: Int get() = if (pingN > 0) (pingSum / pingN).toInt() else 0
        val empty: Boolean get() = seconds == 0L && connects == 0
    }

    private lateinit var sp: SharedPreferences
    private var cur: Month? = null
    private var dirty = 0

    fun init(context: Context) {
        sp = context.getSharedPreferences("monthstats", Context.MODE_PRIVATE)
    }

    fun key(date: Date = Date()): String = SimpleDateFormat("yyyy-MM", Locale.US).format(date)

    fun previousKey(): String = key(Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time)

    @Synchronized
    private fun current(): Month {
        val k = key()
        val c = cur
        if (c != null && c.key == k) return c
        c?.let { write(it) }
        return read(k).also { cur = it }
    }

    @Synchronized
    fun get(k: String): Month = if (cur?.key == k) cur!! else read(k)

    /** Месяцы с данными, новые первыми. */
    fun months(): List<String> = if (!::sp.isInitialized) emptyList()
    else (sp.all.keys.filter { it.startsWith("m:") }.map { it.removePrefix("m:") } + key()).distinct().sortedDescending()

    /** Каждую секунду, пока VPN включён. */
    @Synchronized
    fun tick(down: Long, up: Long, server: String) {
        if (!::sp.isInitialized) return
        val c = current()
        cur = c.copy(
            down = c.down + down, up = c.up + up, seconds = c.seconds + 1,
            servers = c.servers + (server to (c.servers[server] ?: 0L) + 1L),
        )
        WeekStats.tick(down, up, server)
        if (++dirty >= 60) flush()
    }

    /** Замер пинга через VPN (раз в минуту). */
    @Synchronized
    fun ping(ms: Int) {
        if (!::sp.isInitialized || ms <= 0) return
        cur = current().let { it.copy(pingSum = it.pingSum + ms, pingN = it.pingN + 1) }
    }

    @Synchronized
    fun connected() {
        if (!::sp.isInitialized) return
        cur = current().let { it.copy(connects = it.connects + 1) }
        WeekStats.connected()
        flush()
    }

    @Synchronized
    fun ad() {
        if (!::sp.isInitialized) return
        cur = current().let { it.copy(ads = it.ads + 1) }
        WeekStats.ad()
        if (++dirty >= 60) flush()
    }

    @Synchronized
    fun flush() {
        cur?.let { write(it) }
        dirty = 0
        WeekStats.flush()
    }

    private fun write(m: Month) {
        val o = JSONObject().put("down", m.down).put("up", m.up).put("sec", m.seconds).put("conn", m.connects)
            .put("ads", m.ads).put("ps", m.pingSum).put("pn", m.pingN)
            .put("srv", JSONObject().apply { m.servers.entries.sortedByDescending { it.value }.take(30).forEach { put(it.key, it.value) } })
        sp.edit().putString("m:${m.key}", o.toString()).apply()
    }

    private fun read(k: String): Month = runCatching {
        val o = JSONObject(sp.getString("m:$k", null) ?: return Month(k))
        val srv = o.optJSONObject("srv")
        Month(
            k, o.optLong("down"), o.optLong("up"), o.optLong("sec"), o.optInt("conn"), o.optInt("ads"),
            o.optLong("ps"), o.optInt("pn"),
            srv?.keys()?.asSequence()?.associateWith { srv.optLong(it) } ?: emptyMap(),
        )
    }.getOrDefault(Month(k))

    /** «ноябрь 2026» */
    fun title(k: String): String = runCatching {
        val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(k)!!
        SimpleDateFormat("LLLL yyyy", Locale("ru")).format(d)
    }.getOrDefault(k)

    /** 1-го числа — уведомление с итогами прошлого месяца (один раз). */
    fun reportDue(): String? {
        if (!::sp.isInitialized) return null
        val prev = previousKey()
        if (sp.getString("notified", "") == prev) return null
        val m = get(prev)
        if (m.empty) return null
        sp.edit().putString("notified", prev).apply()
        return prev
    }
}

/** 3.6. Семейный фильтр по расписанию: дни недели и время (например, будни 8:00–21:00). */
object KidsSchedule {
    /** Семейный режим сейчас действует: включён всегда или попали в окно расписания. */
    fun active(s: Settings, now: Calendar = Calendar.getInstance()): Boolean {
        if (s.kids) return true
        if (!s.kidsSchedule || s.kidsDays.isEmpty()) return false
        // Calendar: воскресенье = 1 … суббота = 7 → 1 = понедельник … 7 = воскресенье
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val min = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val from = s.kidsFrom; val to = s.kidsTo
        return if (from <= to) day in s.kidsDays && min in from until to
        else {
            // Окно через полночь (например, 20:00–07:00): утро относится к вчерашнему дню
            val yesterday = (day + 5) % 7 + 1
            (day in s.kidsDays && min >= from) || (yesterday in s.kidsDays && min < to)
        }
    }

    fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)
}
