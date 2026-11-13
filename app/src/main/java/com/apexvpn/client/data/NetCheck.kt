package com.apexvpn.client.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.21. «Проверка сети»: что открывается напрямую, что глушат, и что приложение уже сделало.
 * Этапы: российские сайты и зарубежные сайты (напрямую, мимо VPN), серверы APEX (TCP),
 * протоколы (настоящий запрос через каждый протокол — только при включённом VPN).
 * Итог — один из вердиктов [Kind]: всё работает, нет интернета, белые списки, серверы
 * недоступны, глушат часть протоколов, глушат VPN.
 * Ничего не отправляет наружу: проверка только на телефоне, история — в настройках телефона.
 */
object NetCheck {
    enum class Kind { Ok, NoInternet, WhiteList, ServersBlocked, JamPartial, Jammed }
    enum class Mark { Wait, Run, Ok, Slow, Fail }

    data class Item(val title: String, val mark: Mark = Mark.Wait, val detail: String = "", val ms: Int = -1)
    data class Stage(val title: String, val items: List<Item> = emptyList(), val note: String = "")

    data class Result(
        val at: Long,
        val kind: Kind,
        val network: String,
        val okNodes: Int,
        val allNodes: Int,
        val ping: Int,
        val protoOk: Int,
        val protoAll: Int,
        /** Что приложение уже сделало само (или пусто) */
        val fixed: String = "",
    )

    data class State(
        val running: Boolean = false,
        val stages: List<Stage> = emptyList(),
        val result: Result? = null,
        /** Сервер «Обход LTE», который стоит выбрать (белые списки, а автопилот выключен) */
        val lteTag: String? = null,
        /** Выбран вручную сервер, чей протокол глушат — предложить автопилот */
        val suggestAuto: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val ru = listOf("Яндекс" to "https://ya.ru", "Госуслуги" to "https://www.gosuslugi.ru", "ВКонтакте" to "https://vk.com")
    private val world = listOf("Google" to "https://www.google.com/generate_204", "Cloudflare" to "https://1.1.1.1", "Telegram" to "https://telegram.org")

    private fun stage(i: Int, s: Stage) {
        val list = _state.value.stages.toMutableList(); list[i] = s
        _state.value = _state.value.copy(stages = list)
    }

    /** Открыть адрес напрямую — по настоящей сети, мимо VPN. -1 — не открылся. */
    private fun direct(url: String): Int = runCatching {
        val u = URL(url)
        val net = if (VpnState.status.value == VpnStatus.Started) NetworkMonitor.defaultNetwork else null
        val c = (net?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000; c.requestMethod = "HEAD"; c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val t = System.nanoTime()
        try { c.responseCode } finally { c.disconnect() }
        ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
    }.getOrDefault(-1)

    private suspend fun sites(list: List<Pair<String, String>>): List<Item> = coroutineScope {
        list.map { (name, url) ->
            async(Dispatchers.IO) {
                val ms = direct(url)
                Item(name, if (ms > 0) (if (ms > 1500) Mark.Slow else Mark.Ok) else Mark.Fail, if (ms > 0) "открывается" else "не открывается напрямую", ms)
            }
        }.awaitAll()
    }

    /** Wi-Fi / мобильный интернет и оператор — для заголовка и истории. */
    fun networkName(context: Context): String = runCatching {
        val cm = context.getSystemService<ConnectivityManager>()
        val net = NetworkMonitor.defaultNetwork ?: cm?.activeNetwork
        val caps = net?.let { cm?.getNetworkCapabilities(it) }
        when {
            caps == null -> "Нет сети"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val op = context.getSystemService<TelephonyManager>()?.networkOperatorName.orEmpty().trim()
                "Мобильный интернет" + if (op.isNotEmpty()) " · $op" else ""
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Кабель"
            else -> "Сеть"
        }
    }.getOrDefault("Сеть")

    suspend fun run(context: Context) {
        if (_state.value.running) return
        try { runSteps(context) } finally { if (_state.value.running) _state.value = _state.value.copy(running = false) }
    }

    private suspend fun runSteps(context: Context) {
        val vpnOn = VpnState.status.value == VpnStatus.Started
        _state.value = State(
            running = true,
            stages = listOf(Stage("Российские сайты"), Stage("Зарубежные сайты"), Stage("Серверы APEX"), Stage("Протоколы")),
        )
        // 1–2. Сайты напрямую
        val ruItems = sites(ru); stage(0, Stage("Российские сайты", ruItems))
        val worldItems = sites(world); stage(1, Stage("Зарубежные сайты", worldItems))
        // 3. Серверы: обычные и «Обход LTE» отдельно
        val nodes = Subscription.state.value.nodes.filter { !it.ads }
        val normal = nodes.filter { !ServerInfo.isSpecial(it.name) }
        val lte = nodes.filter { ServerInfo.isLte(it.name) }
        val pings = runCatching { Pinger.pingAll(nodes, save = false, type = "tcp") }.getOrDefault(emptyMap())
        val alive = { list: List<Node> -> list.filter { (pings[it.tag] ?: -1) > 0 } }
        val normalOk = alive(normal); val lteOk = alive(lte)
        val serverItems = buildList {
            if (normal.isNotEmpty()) add(
                Item(
                    "Обычные серверы", if (normalOk.isEmpty()) Mark.Fail else if (normalOk.size < normal.size) Mark.Slow else Mark.Ok,
                    if (normalOk.isEmpty()) "${normal.size} не отвечают" else "отвечают ${normalOk.size} из ${normal.size}",
                    normalOk.mapNotNull { pings[it.tag] }.minOrNull() ?: -1,
                ),
            )
            if (lte.isNotEmpty()) add(
                Item(
                    "Обход LTE", if (lteOk.isEmpty()) Mark.Fail else Mark.Ok,
                    if (lteOk.isEmpty()) "не отвечает" else "работает",
                    lteOk.mapNotNull { pings[it.tag] }.minOrNull() ?: -1,
                ),
            )
        }
        stage(2, Stage("Серверы APEX", serverItems, if (nodes.isEmpty()) "Нет подписки — добавьте ссылку подписки" else ""))
        // 4. Протоколы: настоящий запрос через ядро по 3 живых сервера каждого протокола
        val proto = mutableListOf<Item>()
        if (vpnOn) {
            val groups = (normalOk + lteOk).groupBy { it.category }
            proto += coroutineScope {
                groups.map { (cat, list) ->
                    async(Dispatchers.IO) {
                        val ms = list.take(3).map { n ->
                            runCatching { io.nekohasekai.apexcore.Apexcore.proxyPing(n.tag, "", "HEAD", "default", 6000) }.getOrDefault(-1)
                        }.filter { it > 0 }.minOrNull() ?: -1
                        Item(cat, if (ms > 0) (if (ms > 2500) Mark.Slow else Mark.Ok) else Mark.Fail, if (ms > 0) (if (ms > 2500) "медленно — возможно, режут скорость" else "работает") else "обрыв после начала — глушат", ms)
                    }
                }.awaitAll()
            }.sortedBy { it.mark != Mark.Fail }
        }
        stage(3, Stage("Протоколы", proto, if (!vpnOn) "Включите VPN, чтобы проверить, какие протоколы глушат" else if (proto.isEmpty()) "Нет отвечающих серверов для проверки" else ""))

        val kind = verdict(
            ruOk = ruItems.any { it.ms > 0 }, worldOk = worldItems.any { it.ms > 0 },
            normalOk = normalOk.isNotEmpty(), lteOk = lteOk.isNotEmpty(), hasNodes = nodes.isNotEmpty(),
            protoOk = proto.count { it.mark != Mark.Fail }, protoAll = proto.size,
        )
        val auto = Prefs.selectedTag == AUTO_TAG
        val selCat = Subscription.state.value.nodes.find { it.tag == Prefs.selectedTag }?.category
        val fixed = when {
            kind == Kind.WhiteList && WhiteList.active -> "Автопилот уже перешёл на «Обход LTE» — интернет через VPN работает. Когда списки снимут, вернётся сам."
            kind == Kind.JamPartial && auto -> "Автопилот выбирает только рабочие протоколы — ничего делать не нужно."
            else -> ""
        }
        val okPings = (normalOk + lteOk).mapNotNull { pings[it.tag] }.filter { it > 0 }.sorted()
        val res = Result(
            at = System.currentTimeMillis(), kind = kind, network = networkName(context),
            okNodes = normalOk.size + lteOk.size, allNodes = normal.size + lte.size,
            ping = okPings.getOrNull(okPings.size / 2) ?: -1,
            protoOk = proto.count { it.mark != Mark.Fail }, protoAll = proto.size, fixed = fixed,
        )
        save(res)
        _state.value = _state.value.copy(
            running = false, result = res,
            lteTag = if (kind == Kind.WhiteList && !WhiteList.active) lteOk.minByOrNull { pings[it.tag] ?: Int.MAX_VALUE }?.tag else null,
            suggestAuto = !auto && (kind == Kind.Jammed || kind == Kind.JamPartial) && proto.any { it.title == selCat && it.mark == Mark.Fail },
        )
    }

    /** Итог по результатам этапов — отдельно, чтобы проверять автотестами. */
    internal fun verdict(ruOk: Boolean, worldOk: Boolean, normalOk: Boolean, lteOk: Boolean, hasNodes: Boolean, protoOk: Int, protoAll: Int): Kind = when {
        !ruOk && !worldOk && !normalOk && !lteOk -> Kind.NoInternet
        // Российские открываются, зарубежные — нет. TCP до серверов при белых списках часто проходит, а данные — нет, поэтому серверы тут не смотрим
        ruOk && !worldOk -> Kind.WhiteList
        hasNodes && !normalOk && !lteOk -> Kind.ServersBlocked
        protoAll > 0 && protoOk == 0 -> Kind.Jammed
        protoAll > 0 && protoOk < protoAll -> Kind.JamPartial
        hasNodes && !normalOk && lteOk && worldOk -> Kind.ServersBlocked
        else -> Kind.Ok
    }

    fun title(k: Kind) = when (k) {
        Kind.Ok -> "Всё работает"
        Kind.NoInternet -> "Нет интернета"
        Kind.WhiteList -> "Включены белые списки"
        Kind.ServersBlocked -> "Серверы VPN недоступны"
        Kind.JamPartial -> "Глушат часть протоколов"
        Kind.Jammed -> "Глушат VPN"
    }

    fun explain(k: Kind) = when (k) {
        Kind.Ok -> "Ограничений не видно: сайты и серверы отвечают."
        Kind.NoInternet -> "Не открываются ни российские, ни зарубежные сайты. Похоже на отключение интернета у оператора — VPN тут не поможет. Подождите или подключитесь к Wi-Fi."
        Kind.WhiteList -> "Российские сайты открываются, а зарубежные и обычные серверы VPN — нет. Так работают белые списки у оператора."
        Kind.ServersBlocked -> "Интернет есть, но обычные серверы VPN не отвечают — их адреса могут быть заблокированы или идут техработы. Попробуйте другой сервер или Wi-Fi."
        Kind.JamPartial -> "Интернет работает, но часть протоколов обрывается сразу после начала — так блокируют по «отпечатку» соединения."
        Kind.Jammed -> "Серверы отвечают, но любое VPN-соединение обрывается сразу после начала. Попробуйте «Обход LTE», другую сеть или Wi-Fi."
    }

    // ── История: последние 20 проверок (только на телефоне)
    private fun save(r: Result) = runCatching {
        val arr = JSONArray(Prefs.netHistory)
        arr.put(JSONObject().put("at", r.at).put("kind", r.kind.name).put("net", r.network).put("ok", r.okNodes).put("all", r.allNodes)
            .put("ping", r.ping).put("pok", r.protoOk).put("pall", r.protoAll).put("fixed", r.fixed))
        val keep = JSONArray(); val from = (arr.length() - 20).coerceAtLeast(0)
        for (i in from until arr.length()) keep.put(arr.get(i))
        Prefs.netHistory = keep.toString()
    }

    fun history(): List<Result> = runCatching {
        val arr = JSONArray(Prefs.netHistory)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Result(o.getLong("at"), runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.Ok), o.optString("net"),
                o.optInt("ok"), o.optInt("all"), o.optInt("ping", -1), o.optInt("pok"), o.optInt("pall"), o.optString("fixed"))
        }.reversed()
    }.getOrDefault(emptyList())

    /** Недавняя проверка (2 часа) нашла глушилку — для плашки на главном. */
    fun recentAlert(): Kind? = history().firstOrNull()?.takeIf {
        System.currentTimeMillis() - it.at < 2 * 3600_000L && it.kind in setOf(Kind.Jammed, Kind.JamPartial, Kind.ServersBlocked)
    }?.kind

    /** Текст для «Поделиться»: без адресов серверов и ссылки подписки. */
    fun shareText(): String {
        val s = _state.value; val r = s.result ?: return ""
        val b = StringBuilder("APEX CLIENT · Проверка сети\n${java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("ru")).format(java.util.Date(r.at))} · ${r.network}\n\n")
        b.append(title(r.kind)).append("\n").append(explain(r.kind)).append("\n")
        if (r.fixed.isNotEmpty()) b.append("✅ ").append(r.fixed).append("\n")
        s.stages.forEach { st ->
            if (st.items.isEmpty()) return@forEach
            b.append("\n").append(st.title).append(":\n")
            st.items.forEach { b.append(if (it.mark == Mark.Fail) "✕ " else if (it.mark == Mark.Slow) "~ " else "✓ ").append(it.title).append(" — ").append(it.detail).append(if (it.ms > 0) " · ${it.ms} мс" else "").append("\n") }
        }
        return b.toString().trim()
    }
}
