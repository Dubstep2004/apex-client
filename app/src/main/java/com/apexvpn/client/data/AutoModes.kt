package com.apexvpn.client.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.abs

/**
 * Режимы автопилота: под игры, под скорость, надёжный, для звонков, экономный.
 * Автопилот по-прежнему сам переключается при сбое, но выбирает только из подходящих
 * серверов: для игр — с низким и ровным пингом без потерь, для скорости — с самой
 * высокой скоростью загрузки, для надёжного — те, что ни разу не «пропадали».
 */
object AutoModes {
    /** [c1], [c2] — 588. цвета щита в этом режиме (0 — цвета дизайна). */
    enum class Mode(val id: String, val emoji: String, val title: String, val subtitle: String, val profile: ConnProfile, val c1: Long = 0, val c2: Long = 0) {
        Balance("balance", "⚖️", "Обычный", "Баланс скорости и стабильности", ConnProfile.Normal),
        Games("games", "🎮", "Игры", "Низкий и ровный пинг без потерь", ConnProfile.Gaming, 0xFFFF4D5E, 0xFFFF9F43),
        Speed("speed", "🚀", "Скорость", "Самая высокая скорость загрузки", ConnProfile.Normal, 0xFF3B82F6, 0xFF22D3EE),
        Stable("stable", "🛡", "Надёжный", "Серверы, которые ни разу не пропадали", ConnProfile.Calls, 0xFF10B981, 0xFF6EE7B7),
        Calls("calls", "📞", "Звонки", "Ровная связь, сервер не меняется в разговоре", ConnProfile.Calls, 0xFF14B8A6, 0xFF60A5FA),
        Battery("battery", "🔋", "Батарея", "Редкие проверки — меньше расход", ConnProfile.Battery, 0xFF84CC16, 0xFFFDE047),
        ;

        /** Нужны замеры качества (пинг несколько раз, скорость). */
        val measured get() = this == Games || this == Speed || this == Stable || this == Calls

        companion object {
            fun of(id: String) = entries.firstOrNull { it.id == id } ?: Balance
        }
    }

    /** Качество сервера по последним замерам. */
    data class Quality(
        val avg: Int = -1,
        val jitter: Int = -1,
        /** Потери, % */
        val loss: Int = 100,
        val mbps: Double = -1.0,
        /** Сколько проверок подряд сервер не отвечал (для «Надёжного») */
        val fails: Int = 0,
    )

    private val _quality = MutableStateFlow<Map<String, Quality>>(emptyMap())
    val quality = _quality.asStateFlow()

    /** Что сейчас делает подбор — для подписи в шторке серверов. */
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private fun sp(context: Context) = context.getSharedPreferences("auto_modes", Context.MODE_PRIVATE)

    fun load(context: Context) {
        _quality.value = runCatching {
            val o = JSONObject(sp(context).getString("q", "{}") ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val q = o.getJSONObject(k)
                Quality(q.optInt("a", -1), q.optInt("j", -1), q.optInt("l", 100), q.optDouble("m", -1.0), q.optInt("f", 0))
            }
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context) {
        val o = JSONObject()
        _quality.value.forEach { (k, q) ->
            o.put(k, JSONObject().put("a", q.avg).put("j", q.jitter).put("l", q.loss).put("m", q.mbps).put("f", q.fails))
        }
        sp(context).edit().putString("q", o.toString()).putLong("at", System.currentTimeMillis()).apply()
    }

    fun clear(context: Context) {
        _quality.value = emptyMap()
        sp(context).edit().clear().apply()
    }

    fun lastMeasured(context: Context) = sp(context).getLong("at", 0)
    fun lastSpeed(context: Context) = sp(context).getLong("speed_at", 0)

    /** Обычные серверы: без России, LTE-обхода и рекламы. */
    private fun pool(nodes: List<Node>) = nodes.filter { !ServerInfo.isSpecial(it.name) && !it.ads && !it.ai }.ifEmpty { nodes }

    /**
     * Замер пинга: 4 круга по всем серверам — среднее, джиттер (разброс) и потери.
     * Для «Скорости» — ещё и загрузка через 3 лучших по пингу (нужен включённый VPN).
     */
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun measure(context: Context, mode: Mode, force: Boolean = true) = lock.withLock {
        // Только что мерили (второй вызов ждал первый) — повторять незачем
        val fresh = System.currentTimeMillis() - lastMeasured(context) < 60_000L &&
            (mode != Mode.Speed || System.currentTimeMillis() - lastSpeed(context) < 60_000L)
        if (fresh && !force) return@withLock
        measureLocked(context, mode)
    }

    private suspend fun measureLocked(context: Context, mode: Mode) {
        val nodes = pool(Subscription.state.value.nodes)
        if (nodes.isEmpty() || !mode.measured) return
        _status.value = when (mode) {
            Mode.Games -> "Подбираю серверы для игр…"
            Mode.Speed -> "Проверяю пинг серверов…"
            else -> "Проверяю стабильность серверов…"
        }
        try {
            val rounds = mutableListOf<Map<String, Int>>()
            repeat(4) { i ->
                rounds += runCatching { Pinger.pingAll(nodes, save = false, type = "tcp") }.getOrDefault(emptyMap())
                if (i < 3) delay(400)
            }
            val old = _quality.value
            val next = old.toMutableMap()
            for (n in nodes) {
                val v = rounds.mapNotNull { r -> r[n.tag]?.takeIf { it > 0 } }
                val loss = 100 * (rounds.size - v.size) / rounds.size.coerceAtLeast(1)
                val avg = if (v.isEmpty()) -1 else v.average().toInt()
                val jitter = if (v.size < 2) -1 else v.zipWithNext { a, b -> abs(a - b) }.average().toInt()
                val prev = old[n.tag] ?: Quality()
                next[n.tag] = prev.copy(avg = avg, jitter = jitter, loss = loss, fails = if (v.isEmpty()) prev.fails + 1 else 0)
            }
            _quality.value = next
            if (mode == Mode.Speed && com.apexvpn.client.vpn.VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) {
                _status.value = "Проверяю скорость серверов…"
                val top = nodes.filter { (next[it.tag]?.avg ?: -1) > 0 }
                    .sortedBy { next[it.tag]!!.avg }
                    .distinctBy { ServerInfo.title(it.name) }
                    .take(3)
                val speeds = FastestServer.speeds(top)
                if (speeds.isNotEmpty()) {
                    _quality.value = _quality.value.toMutableMap().also { m ->
                        speeds.forEach { (tag, mbps) -> m[tag] = (m[tag] ?: Quality()).copy(mbps = mbps) }
                    }
                    sp(context).edit().putLong("speed_at", System.currentTimeMillis()).apply()
                }
            }
            save(context)
        } finally {
            _status.value = null
        }
    }

    /**
     * Из каких серверов выбирает автопилот в этом режиме (по порядку). Пусто — без ограничений
     * (нет замеров или подходящих серверов: лучше обычный автопилот, чем никакого).
     */
    fun allowed(mode: Mode, nodes: List<Node>): List<String> {
        val q = _quality.value
        val base = pool(nodes).filter { q[it.tag] != null }
        if (base.isEmpty()) return emptyList()
        fun ok(n: Node) = (q[n.tag]?.avg ?: -1) > 0
        val list = when (mode) {
            Mode.Games -> base.filter { ok(it) && q[it.tag]!!.loss == 0 }
                .filter { val s = q[it.tag]!!; s.jitter in 0..maxOf(25, s.avg / 4) }
                .sortedBy { val s = q[it.tag]!!; s.avg + 2 * s.jitter }
                .take(5)
            Mode.Speed -> base.filter { (q[it.tag]?.mbps ?: -1.0) > 0 }
                .sortedByDescending { q[it.tag]!!.mbps }
                .take(2)
            Mode.Stable, Mode.Calls -> base.filter { ok(it) && q[it.tag]!!.loss == 0 && q[it.tag]!!.fails == 0 }
                .sortedBy { val s = q[it.tag]!!; s.jitter.coerceAtLeast(0) * 3 + s.avg }
                .take(5)
            else -> emptyList()
        }
        return list.map { it.tag }
    }

    /** Подпись режима для экрана: «🎮 Игры · 5 серверов». */
    fun summary(mode: Mode, nodes: List<Node>): String {
        if (!mode.measured) return "${mode.emoji} ${mode.title}"
        val n = allowed(mode, nodes).size
        return if (n > 0) "${mode.emoji} ${mode.title} · подходящих серверов: $n" else "${mode.emoji} ${mode.title} · подбираю серверы"
    }
}
