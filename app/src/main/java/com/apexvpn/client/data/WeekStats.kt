package com.apexvpn.client.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 3.19. История за неделю: время с VPN, трафик и реклама по дням + последние подключения
 * (когда, сколько длилось, через какой сервер, сколько трафика). Только на телефоне, хранится 14 дней.
 */
object WeekStats {
    data class Day(val key: String, val down: Long = 0, val up: Long = 0, val seconds: Long = 0, val connects: Int = 0, val ads: Int = 0)
    data class Session(val start: Long, val end: Long, val server: String, val bytes: Long)

    private const val KEEP_DAYS = 14
    private const val MAX_SESSIONS = 40
    private lateinit var sp: SharedPreferences
    private var cur: Day? = null
    private var dirty = 0
    private var sStart = 0L
    private var sBytes = 0L
    private val sServers = HashMap<String, Long>()

    fun init(context: Context) {
        sp = context.getSharedPreferences("weekstats", Context.MODE_PRIVATE)
    }

    private fun key(t: Long = System.currentTimeMillis()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    @Synchronized
    private fun current(): Day {
        val k = key()
        val c = cur
        if (c != null && c.key == k) return c
        c?.let { write(it) }
        return read(k).also { cur = it }
    }

    @Synchronized
    fun tick(down: Long, up: Long, server: String) {
        if (!::sp.isInitialized) return
        val c = current()
        cur = c.copy(down = c.down + down, up = c.up + up, seconds = c.seconds + 1)
        if (sStart == 0L) sStart = System.currentTimeMillis() // подключились до запуска истории
        sBytes += down + up
        sServers[server] = (sServers[server] ?: 0L) + 1L
        if (++dirty >= 60) flush()
    }

    @Synchronized
    fun connected() {
        if (!::sp.isInitialized) return
        endSession()
        cur = current().let { it.copy(connects = it.connects + 1) }
        sStart = System.currentTimeMillis()
        flush()
    }

    @Synchronized
    fun ad() {
        if (!::sp.isInitialized) return
        cur = current().let { it.copy(ads = it.ads + 1) }
    }

    /** VPN выключился — записать подключение (короче 10 секунд не считаем). */
    @Synchronized
    fun endSession() {
        if (!::sp.isInitialized) return
        val start = sStart
        sStart = 0L
        val bytes = sBytes; sBytes = 0L
        val server = sServers.maxByOrNull { it.value }?.key ?: "—"
        sServers.clear()
        val now = System.currentTimeMillis()
        if (start == 0L || now - start < 10_000L) return
        val a = runCatching { JSONArray(sp.getString("sessions", "[]")) }.getOrDefault(JSONArray())
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
        list.add(0, JSONObject().put("s", start).put("e", now).put("v", server).put("b", bytes))
        sp.edit().putString("sessions", JSONArray(list.take(MAX_SESSIONS)).toString()).apply()
        flush()
    }

    @Synchronized
    fun flush() {
        cur?.let { write(it) }
        dirty = 0
    }

    private fun write(d: Day) {
        val e = sp.edit().putString(
            "d:${d.key}",
            JSONObject().put("down", d.down).put("up", d.up).put("sec", d.seconds).put("conn", d.connects).put("ads", d.ads).toString(),
        )
        // Старше двух недель — удаляем
        val oldest = key(System.currentTimeMillis() - KEEP_DAYS * 86_400_000L)
        sp.all.keys.filter { it.startsWith("d:") && it.removePrefix("d:") < oldest }.forEach { e.remove(it) }
        e.apply()
    }

    private fun read(k: String): Day = runCatching {
        val o = JSONObject(sp.getString("d:$k", null) ?: return Day(k))
        Day(k, o.optLong("down"), o.optLong("up"), o.optLong("sec"), o.optInt("conn"), o.optInt("ads"))
    }.getOrDefault(Day(k))

    /** Последние 7 дней, от старого к сегодняшнему. */
    @Synchronized
    fun lastDays(n: Int = 7): List<Day> {
        if (!::sp.isInitialized) return emptyList()
        flush()
        val now = System.currentTimeMillis()
        return (n - 1 downTo 0).map { i -> key(now - i * 86_400_000L) }.map { k -> if (cur?.key == k) cur!! else read(k) }
    }

    /** Последние подключения, новые первыми; текущее — сверху, с end = 0. */
    @Synchronized
    fun sessions(): List<Session> {
        if (!::sp.isInitialized) return emptyList()
        val a = runCatching { JSONArray(sp.getString("sessions", "[]")) }.getOrDefault(JSONArray())
        val saved = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Session(it.optLong("s"), it.optLong("e"), it.optString("v"), it.optLong("b")) }
        val live = if (sStart > 0L) listOf(Session(sStart, 0L, sServers.maxByOrNull { it.value }?.key ?: "—", sBytes)) else emptyList()
        return live + saved
    }

    /** «Пн», «Вт»… */
    fun weekday(k: String): String = runCatching {
        val c = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(k)!! }
        arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")[c.get(Calendar.DAY_OF_WEEK) - 1]
    }.getOrDefault("")

    /** «Сегодня, 14:02» / «Вчера, 22:10» / «10 нояб., 09:15» */
    fun whenText(t: Long): String {
        val today = key(); val yest = key(System.currentTimeMillis() - 86_400_000L)
        val hm = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(t))
        return when (key(t)) {
            today -> "Сегодня, $hm"
            yest -> "Вчера, $hm"
            else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(t)) + ", " + hm
        }
    }
}
