package com.apexvpn.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 3.5. Какие сервисы открываются на каждом сервере: ChatGPT, YouTube Premium, Netflix, Gemini. */
object ServiceCheck {
    data class Result(val chatgpt: String, val youtube: String, val netflix: String, val gemini: String, val country: String, val at: Long)

    val services = listOf("chatgpt" to "ChatGPT", "youtube" to "YouTube Premium", "netflix" to "Netflix", "gemini" to "Gemini")

    private val _results = MutableStateFlow(load())
    val results = _results.asStateFlow()
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running = _running.asStateFlow()

    fun Result.status(id: String) = when (id) { "chatgpt" -> chatgpt; "youtube" -> youtube; "netflix" -> netflix; else -> gemini }

    private fun load(): Map<String, Result> = runCatching {
        val o = JSONObject(Prefs.serviceChecks)
        o.keys().asSequence().associateWith { k ->
            val r = o.getJSONObject(k)
            Result(r.optString("chatgpt"), r.optString("youtube"), r.optString("netflix"), r.optString("gemini"), r.optString("country"), r.optLong("at"))
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        val o = JSONObject()
        _results.value.forEach { (k, r) ->
            o.put(k, JSONObject().put("chatgpt", r.chatgpt).put("youtube", r.youtube).put("netflix", r.netflix)
                .put("gemini", r.gemini).put("country", r.country).put("at", r.at))
        }
        Prefs.serviceChecks = o.toString()
    }

    private fun parse(json: String): Result? = runCatching {
        val o = JSONObject(json)
        if (o.has("error")) return null
        fun st(k: String) = o.optJSONObject(k)?.optString("status").orEmpty().ifBlank { "error" }
        Result(st("chatgpt"), st("youtube"), st("netflix"), st("gemini"), o.optString("country"), System.currentTimeMillis())
    }.getOrNull()

    /** Проверить серверы (VPN должен быть включён): по 3 одновременно. */
    suspend fun check(tags: List<String>) = withContext(Dispatchers.IO) {
        val gate = Semaphore(3)
        coroutineScope {
            tags.map { tag ->
                async {
                    gate.withPermit {
                        _running.value = _running.value + tag
                        try {
                            val r = parse(io.nekohasekai.apexcore.Apexcore.serviceCheck(tag, 8000))
                            if (r != null) _results.value = _results.value + (tag to r)
                        } finally {
                            _running.value = _running.value - tag
                        }
                    }
                }
            }.awaitAll()
        }
        save()
    }
}

private fun mark(status: String) = when (status) {
    "yes" -> "✅"
    "partial" -> "◐"
    "no" -> "❌"
    "" -> "·"
    else -> "⚠"
}

@Composable
internal fun ServiceCheckPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val results by ServiceCheck.results.collectAsStateWithLifecycle()
    val running by ServiceCheck.running.collectAsStateWithLifecycle()
    val status by VpnState.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf<String?>(null) }
    val on = status == VpnStatus.Started
    val nodes = Subscription.state.collectAsStateWithLifecycle().value.nodes

    PageScaffold("Сервисы на серверах", onBack) {
        Caption("Открываются ли ChatGPT, YouTube Premium, Netflix и Gemini через каждый сервер. Проверка идёт через сам сервер — VPN должен быть включён, переключать сервер не нужно.")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<Pair<String?, String>>(null to "Все") + ServiceCheck.services.map { it.first to "Где работает ${it.second}" }).forEach { (id, title) ->
                androidx.compose.foundation.layout.Box(Modifier.clip(RoundedCornerShape(50)).clickable { filter = id }) {
                    Tag(title, outlined = filter != id)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val shown = nodes.filter { n ->
            val f = filter ?: return@filter true
            with(ServiceCheck) { results[n.tag]?.status(f) } in listOf("yes", "partial")
        }.sortedByDescending { n ->
            results[n.tag]?.let { r -> with(ServiceCheck) { ServiceCheck.services.count { r.status(it.first) == "yes" } } } ?: -1
        }
        if (shown.isEmpty()) {
            Caption(if (filter != null) "Пока нет проверенных серверов, где это работает — нажмите «Проверить все»." else "Сначала добавьте подписку.")
        } else {
            Group {
                shown.forEachIndexed { i, n ->
                    if (i > 0) GroupDivider()
                    val r = results[n.tag]
                    val busy = n.tag in running
                    ListRow(
                        listOfNotNull(ServerInfo.flag(n.name), ServerInfo.title(n.name)).joinToString(" "),
                        if (r == null) "Не проверен · нажмите, чтобы проверить"
                        else with(ServiceCheck) { ServiceCheck.services.joinToString("  ") { (id, t) -> "${mark(r.status(id))} ${t.substringBefore(' ')}" } } +
                            "\n" + age(r.at) + (if (r.country.isNotBlank()) " · страна сервера ${r.country}" else ""),
                        onClick = { if (on && !busy) scope.launch { ServiceCheck.check(listOf(n.tag)) } },
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption("✅ работает · ◐ Netflix — только свои сериалы · ❌ недоступно в стране сервера · ⚠ не ответил")
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { scope.launch { ServiceCheck.check(nodes.map { it.tag }) } },
            enabled = on && running.isEmpty() && nodes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
        ) {
            Text(
                when {
                    !on -> "Включите VPN, чтобы проверить"
                    running.isNotEmpty() -> "Проверяем…"
                    else -> "Проверить все серверы"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** «проверено 5 мин назад»; старше недели — «устарело, проверьте ещё раз». */
private fun age(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        at <= 0 -> ""
        m < 1 -> "проверено только что"
        m < 60 -> "проверено $m мин назад"
        m < 24 * 60 -> "проверено ${m / 60} ч назад"
        m < 7 * 24 * 60 -> "проверено ${m / 1440} дн назад"
        else -> "устарело — проверьте ещё раз"
    }
}
