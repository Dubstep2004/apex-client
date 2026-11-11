package com.apexvpn.client.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import androidx.core.content.getSystemService
import com.apexvpn.client.vpn.NetworkMonitor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * 3.11. Правила «если — то»: «если открыт Steam → режим „Игры“», «если идёт зарядка → обновить базы сайтов».
 * Пока условие выполняется, действует действие; условие прошло — настройка возвращается как была
 * (если вы сами её не поменяли). Работают, пока VPN включён: проверка раз в 5 секунд.
 */
data class AutoRule(
    val id: String = UUID.randomUUID().toString().take(8),
    val enabled: Boolean = true,
    /** app — открыто приложение (arg — пакет), charging — идёт зарядка, mobile — мобильный интернет,
     *  wifi — Wi-Fi, time — время (arg — «23:00-07:00») */
    val trigger: String,
    val arg: String = "",
    /** mode — режим автопилота, route — маршрут, server — сервер (value — tag или auto),
     *  adblock — блокировка рекламы (on/off), rules — обновить базы сайтов, off — выключить VPN */
    val action: String,
    val value: String = "",
    /** Подпись для списка (название приложения и т.п.) */
    val label: String = "",
) {
    /** Разовое действие (без возврата, когда условие прошло) */
    val oneShot get() = action == "rules" || action == "off"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("on", enabled).put("t", trigger).put("a", arg)
        .put("do", action).put("v", value).put("l", label)

    companion object {
        fun fromJson(o: JSONObject) = AutoRule(
            o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) }, o.optBoolean("on", true),
            o.optString("t"), o.optString("a"), o.optString("do"), o.optString("v"), o.optString("l"),
        )

        fun listToJson(list: List<AutoRule>) = JSONArray(list.map { it.toJson() })
        fun listFrom(a: JSONArray?): List<AutoRule> =
            a?.let { (0 until it.length()).mapNotNull { i -> it.optJSONObject(i)?.let(::fromJson) } }.orEmpty()
                .filter { it.trigger.isNotBlank() && it.action.isNotBlank() }
    }
}

object AutoRulesEngine {
    /** id правила → значение, которое было до него (для возврата) */
    private val applied = HashMap<String, Triple<String, String, String>>() // id → (действие, прежнее значение, что поставили)
    @Volatile private var foreground = ""
    private var lastEventCheck = 0L

    /** Что сейчас действует — для экрана правил. */
    val active: Set<String> get() = synchronized(applied) { applied.keys.toSet() }

    /** Приложение на экране (нужен доступ к истории использования). */
    private fun foregroundApp(ctx: Context): String {
        val usm = ctx.getSystemService<UsageStatsManager>() ?: return foreground
        val now = System.currentTimeMillis()
        val from = if (lastEventCheck == 0L) now - 60_000 else lastEventCheck
        lastEventCheck = now
        runCatching {
            val ev = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                @Suppress("DEPRECATION")
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = e.packageName
            }
        }
        return foreground
    }

    private fun inWindow(arg: String, now: Calendar = Calendar.getInstance()): Boolean {
        val m = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""").find(arg) ?: return false
        val (h1, m1, h2, m2) = m.destructured
        val from = h1.toInt() * 60 + m1.toInt(); val to = h2.toInt() * 60 + m2.toInt()
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (from <= to) cur in from until to else cur >= from || cur < to
    }

    private fun holds(ctx: Context, r: AutoRule): Boolean = when (r.trigger) {
        "app" -> r.arg.isNotBlank() && foregroundApp(ctx) == r.arg
        "charging" -> ctx.getSystemService<BatteryManager>()?.isCharging == true
        "mobile" -> NetworkMonitor.transport.value.startsWith("mobile")
        "wifi" -> NetworkMonitor.transport.value.startsWith("wifi")
        "time" -> inWindow(r.arg)
        else -> false
    }

    private fun current(r: AutoRule): String = current(r.action)

    private fun current(action: String): String {
        val s = AppSettings.value
        return when (action) {
            "mode" -> s.autoMode
            "route" -> s.mode.id
            "server" -> Subscription.state.value.selectedTag
            "adblock" -> if (s.adBlock) "on" else "off"
            else -> ""
        }
    }

    private fun set(action: String, value: String) {
        when (action) {
            "mode" -> AppSettings.update { copy(autoMode = value) }
            "route" -> AppSettings.update { copy(mode = RouteMode.of(value)) }
            "server" -> if (value == AUTO_TAG || Subscription.state.value.nodes.any { it.tag == value }) Subscription.select(value)
            "adblock" -> AppSettings.update { copy(adBlock = value == "on") }
        }
    }

    /**
     * Проверка (раз в 5 с, пока VPN включён). Ответ: что сделать сервису —
     * reload (настройки поменялись), rules (обновить базы), off (выключить VPN).
     */
    private var lastWarn = 0L

    fun tick(ctx: Context): Set<String> {
        val rules = AppSettings.value.autoRules
        if (rules.any { it.enabled && it.trigger == "app" } && !com.apexvpn.client.ui.RecentApps.granted(ctx) &&
            System.currentTimeMillis() - lastWarn > 24 * 3_600_000L
        ) {
            lastWarn = System.currentTimeMillis()
            warnUsage(ctx)
        }
        val out = HashSet<String>()
        synchronized(applied) {
            // Удалённые и выключенные правила — вернуть, что меняли
            applied.keys.filter { id -> rules.none { it.id == id && it.enabled } }.forEach { id ->
                val (action, prev, value) = applied.remove(id) ?: return@forEach
                // Как и при обычном окончании правила: возвращаем, только если вручную не меняли
                if (action != "rules" && action != "off" && current(action) == value && prev != value) { set(action, prev); out += "reload" }
            }
            for (r in rules.filter { it.enabled }) {
                val on = holds(ctx, r)
                val was = r.id in applied
                if (on && !was) {
                    applied[r.id] = Triple(r.action, current(r), r.value)
                    when (r.action) {
                        "rules" -> out += "rules"
                        "off" -> out += "off"
                        else -> if (current(r) != r.value) { set(r.action, r.value); out += "reload" }
                    }
                } else if (!on && was) {
                    val (action, prev, value) = applied.remove(r.id) ?: continue
                    // Возвращаем, только если настройку за это время не меняли вручную
                    // (действие и значение — те, что ставили: правило могли отредактировать, пока оно действовало)
                    if (!r.oneShot && action != "rules" && action != "off" && current(action) == value && prev != value) { set(action, prev); out += "reload" }
                }
            }
        }
        return out
    }

    /** Уведомление: правилам «Открыто приложение» нужен доступ к истории использования. */
    private fun warnUsage(ctx: Context) {
        val nm = ctx.getSystemService<android.app.NotificationManager>() ?: return
        val pi = android.app.PendingIntent.getActivity(
            ctx, 16,
            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            nm.notify(
                8,
                androidx.core.app.NotificationCompat.Builder(ctx, com.apexvpn.client.ApexApp.CHANNEL_ALERTS)
                    .setSmallIcon(com.apexvpn.client.R.drawable.ic_stat_apex)
                    .setContentTitle("Правила «если — то» не видят приложения")
                    .setContentText("Android отключил доступ к истории использования. Нажмите и включите его для APEX VPN")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    /** VPN выключили — забываем, что действовало (настройки остаются как есть). */
    fun reset() = synchronized(applied) { applied.clear(); lastEventCheck = 0L }

    // ─── Подписи ─────────────────────────────────────────────────────────

    fun triggerTitle(r: AutoRule): String = when (r.trigger) {
        "app" -> "Открыто «${r.label.substringBefore('|').ifBlank { r.arg }}»"
        "charging" -> "Идёт зарядка"
        "mobile" -> "Мобильный интернет"
        "wifi" -> "Wi-Fi"
        "time" -> "Время ${r.arg}"
        else -> r.trigger
    }

    fun actionTitle(r: AutoRule): String = when (r.action) {
        "mode" -> "режим «" + (AutoModes.Mode.entries.firstOrNull { it.id == r.value }?.let { "${it.emoji} ${it.title}" } ?: r.value) + "»"
        "route" -> "маршрут «${RouteMode.of(r.value).title}»"
        "server" -> if (r.value == AUTO_TAG) "автопилот" else "сервер «${r.label.substringAfter('|', "").ifBlank { Subscription.state.value.nodes.firstOrNull { it.tag == r.value }?.name?.let { ServerInfo.title(it) } ?: r.value }}»"
        "adblock" -> if (r.value == "on") "блокировка рекламы вкл" else "блокировка рекламы выкл"
        "rules" -> "обновить базы сайтов"
        "off" -> "выключить VPN"
        else -> r.action
    }

    /** Готовые правила для быстрого старта. */
    val presets: List<Pair<String, AutoRule>> = listOf(
        "Зарядка → обновить базы сайтов" to AutoRule(trigger = "charging", action = "rules"),
        "Мобильный интернет → режим «Батарея»" to AutoRule(trigger = "mobile", action = "mode", value = "battery"),
        "Ночь 23:00–07:00 → режим «Батарея»" to AutoRule(trigger = "time", arg = "23:00-07:00", action = "mode", value = "battery"),
        "Wi-Fi → режим «Скорость»" to AutoRule(trigger = "wifi", action = "mode", value = "speed"),
    )
}
