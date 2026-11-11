package com.apexvpn.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.ConnLog
import com.apexvpn.client.data.Links
import com.apexvpn.client.data.RulesUpdater
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 3.10. «Помощник» — чат с готовыми ответами. Это не ИИ: он разбирает вопрос по ключевым словам
 * и отвечает по состоянию VPN, подписке, журналу соединений и правилам маршрута. Работает без интернета.
 */
object Helper {
    data class Action(val title: String, val page: Page? = null, val run: (() -> Unit)? = null, val link: String? = null)
    data class Msg(val fromUser: Boolean, val text: String, val actions: List<Action> = emptyList())

    val suggestions = listOf(
        "Не работает YouTube", "Не открывается банк", "Медленный интернет", "VPN не подключается",
        "Высокий пинг в играх", "Когда кончится подписка", "Много рекламы", "Мобильный интернет не работает",
    )

    private val sites = mapOf(
        "youtube" to "youtube.com", "ютуб" to "youtube.com", "instagram" to "instagram.com", "инстаграм" to "instagram.com",
        "инст" to "instagram.com", "telegram" to "telegram.org", "телеграм" to "telegram.org", "chatgpt" to "chatgpt.com",
        "чатгпт" to "chatgpt.com", "gpt" to "chatgpt.com", "netflix" to "netflix.com", "нетфликс" to "netflix.com",
        "discord" to "discord.com", "дискорд" to "discord.com", "twitter" to "x.com", "твиттер" to "x.com",
        "facebook" to "facebook.com", "фейсбук" to "facebook.com", "spotify" to "spotify.com", "tiktok" to "tiktok.com",
        "тикток" to "tiktok.com", "whatsapp" to "whatsapp.com", "ватсап" to "whatsapp.com", "gemini" to "gemini.google.com",
        "claude" to "claude.ai", "twitch" to "twitch.tv", "твич" to "twitch.tv", "steam" to "steampowered.com",
    )

    private fun has(t: String, vararg w: String) = w.any { it in t }

    private fun statusLine(): String = when (VpnState.status.value) {
        VpnStatus.Started -> "VPN включён."
        VpnStatus.Starting -> "VPN подключается."
        VpnStatus.Blocked -> "VPN упал, аварийный выключатель закрыл интернет — приложение переподключается."
        VpnStatus.Paused -> "VPN на паузе (доверенная сеть Wi-Fi)."
        else -> "VPN сейчас выключен."
    }

    /** Как пойдёт сайт: через VPN, напрямую или блок (по тем же правилам, что у ядра). */
    private suspend fun route(ctx: android.content.Context, domain: String): String? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(Apexcore.matchRoute(AppSettings.coreOptions(RulesUpdater.activeDir(ctx)), domain)).optString("outbound") }.getOrNull()
    }

    suspend fun answer(ctx: android.content.Context, question: String, viewModel: MainViewModel): Msg {
        val t = question.lowercase()
        val sub = Subscription.state.value
        val on = VpnState.status.value == VpnStatus.Started
        val support = Action("Написать в поддержку", link = Links.support())

        // Сайт или сервис по названию / адресу
        val domain = sites.entries.firstOrNull { it.key in t }?.value
            ?: Regex("""([a-z0-9-]+\.)+[a-z]{2,}""").find(t)?.value
        if (domain != null && has(t, "не ", "работает", "открыва", "грузит", "тормозит", "почему", "медлен") || domain != null && t.trim() == domain) {
            val r = route(ctx, domain!!)
            val recent = ConnLog.entries.value.filter { it.host.endsWith(domain.removePrefix("www.")) }.takeLast(3)
            val way = when (r) {
                "direct" -> "идёт **напрямую**, мимо VPN (так настроены правила)"
                "block" -> "**заблокирован** вашими правилами или блокировкой рекламы"
                null, "" -> "проверить маршрут не удалось"
                else -> "идёт **через VPN**"
            }
            val text = buildString {
                append("${statusLine()} Сайт $domain $way.")
                if (recent.isNotEmpty()) append("\n\nВ журнале соединений: ${recent.last().route.name.let { if (it == "Vpn") "через VPN" else if (it == "Direct") "напрямую" else "заблокирован" }}, сервер ${recent.last().server.ifBlank { "—" }}.")
                append("\n\nЧто сделать:")
                if (!on) append("\n• Включите VPN.")
                if (r == "direct") append("\n• Добавьте $domain в «Свои сайты через VPN» или выберите маршрут «Весь трафик».")
                if (r == "block") append("\n• Проверьте «Свои правила» и блокировку рекламы.")
                append("\n• Смените сервер — сайт может не открываться в стране сервера.")
                append("\n• Проверьте «Сервисы на серверах»: там видно, где работают YouTube, ChatGPT, Netflix.")
            }
            return Msg(false, text.replace("**", ""), listOf(
                Action("Сервисы на серверах", Page.ServiceCheck), Action("Проверить сайт", Page.SiteCheck), Action("Самопроверка", Page.SelfCheck),
            ))
        }
        return when {
            has(t, "банк", "госуслуг", "маркетплейс", "озон", "ozon", "wildberries", "вайлдберриз", "авито", "приложение не", "просит отключить", "похоже, вы используете") -> Msg(false,
                "Российские банки, госуслуги и маркетплейсы с 2026 года проверяют VPN и могут не открываться с ним.\n\nРешение: пустите приложение мимо VPN — оно перестанет видеть VPN, остальные останутся под защитой. Быстрее всего — кнопка «Приложение не работает?» в уведомлении VPN или здесь ниже. Готовый шаблон «Российские приложения» — в «Скрыть VPN».",
                listOf(Action("Выбрать приложение", Page.AppIssue), Action("Скрыть VPN (шаблон)", Page.HideApps)))
            has(t, "медлен", "скорост", "тормоз", "долго грузит", "буфер") -> Msg(false,
                "${statusLine()}\n\nЧто помогает:\n• Режим автопилота «Скорость» — он выбирает сервер по замеру загрузки.\n• Тест скорости покажет, дело в VPN или в самом интернете (сравните с выключенным VPN).\n• Если медленно только на мобильном — провайдер мог ограничить VPN, попробуйте сервер «LTE-обход».",
                listOf(Action("Тест скорости", Page.SpeedTest), Action("Режим «Скорость»", run = { viewModel.change { copy(autoMode = "speed") } })))
            has(t, "пинг", "игр", "лаг", "задержк") -> Msg(false,
                "Для игр включите режим автопилота «Игры»: он выбирает сервер с ровным пингом и проверяет серверы каждую минуту. Можно и закрепить ближайший сервер вручную.",
                listOf(Action("Режим «Игры»", run = { viewModel.change { copy(autoMode = "games") } }), Action("Настройки пинга", Page.Ping)))
            has(t, "не подключ", "не включ", "ошибк", "не работает vpn", "vpn не", "не соедин") -> {
                val err = VpnState.error.value
                Msg(false, buildString {
                    append(statusLine())
                    if (!err.isNullOrBlank()) append("\nПоследняя ошибка: $err")
                    if (sub.expireAt > 0 && sub.expireAt * 1000 < System.currentTimeMillis()) append("\n\nПодписка закончилась — продлите её, и VPN заработает.")
                    if (sub.total > 0 && sub.used >= sub.total) append("\n\nТрафик по подписке закончился.")
                    append("\n\n«Самопроверка» по шагам проверит интернет, подписку, серверы и сайты и подскажет, что делать.")
                }, listOf(Action("Самопроверка", Page.SelfCheck), support))
            }
            has(t, "подписк", "срок", "закончи", "истек", "продл", "трафик") -> Msg(false, buildString {
                if (sub.expireAt > 0) {
                    val days = (sub.expireAt * 1000 - System.currentTimeMillis()) / 86_400_000L
                    append(if (days >= 0) "Подписка действует ещё $days дн. (до ${java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("ru")).format(java.util.Date(sub.expireAt * 1000))})." else "Подписка закончилась.")
                } else if (sub.expireAt == 0L) append("Подписка бессрочная.") else append("Срок подписки панель не сообщает.")
                if (sub.total > 0) append("\nТрафик: ${formatBytes(sub.used.coerceAtLeast(0))} из ${formatBytes(sub.total)}.")
                append("\n\nПродлить — у того, кто выдал подписку, или в поддержке.")
            }, listOf(support))
            has(t, "реклам", "трекер", "баннер") -> Msg(false,
                "Включите «Блокировку рекламы» — реклама и трекеры перестанут загружаться во всех приложениях. В «Отчёте за месяц» видно, сколько заблокировано.",
                listOf(Action(if (AppSettings.value.adBlock) "Уже включена" else "Включить блокировку", run = { viewModel.change { copy(adBlock = true) } }), Action("Отчёт за месяц", Page.Report)))
            has(t, "мобильн", "lte", "4g", "5g", "белые списки", "белый список", "оператор") -> Msg(false,
                "Если на мобильном интернете открываются только российские сайты — у оператора включены «белые списки». Тогда помогают серверы «LTE-обход»: автопилот переходит на них сам, если включено «Белые списки → LTE-обход».",
                listOf(Action("Самопроверка", Page.SelfCheck)))
            has(t, "dns", "днс") -> Msg(false, "DNS «Авто» сам выбирает самый быстрый DNS для каждой сети. Если сайты открываются через раз — попробуйте другой DNS вручную.", listOf(Action("DNS", Page.Dns)))
            has(t, "батаре", "заряд", "аккумулятор") -> Msg(false,
                "Режим автопилота «Экономный» проверяет серверы раз в 30 минут — меньше расход батареи. Ещё помогает отключить оптимизацию батареи для APEX, чтобы Android не перезапускал VPN.",
                listOf(Action("Режим «Экономный»", run = { viewModel.change { copy(autoMode = "battery") } }), Action("Работа в фоне", Page.KeepAlive)))
            has(t, "привет", "здравств", "хай", "hello") -> Msg(false, "Привет! Я помощник APEX VPN. Напишите, что не работает — например, «не открывается YouTube» или «медленно».")
            else -> Msg(false,
                "Не понял вопрос — я отвечаю по готовым подсказкам, это не ИИ. Попробуйте: «не работает YouTube», «не открывается банк», «медленно», «VPN не подключается», «пинг в играх», «подписка». Или напишите в поддержку.",
                listOf(support))
        }
    }
}

@Composable
internal fun HelperPage(viewModel: MainViewModel, onBack: () -> Unit, openPage: (Page) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(Helper.Msg(false, "Здравствуйте! Я помощник APEX VPN. Напишите, что не работает, или выберите подсказку ниже. Я отвечаю по состоянию VPN, подписке и журналу соединений — без интернета (это не ИИ)."))
    }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    fun ask(q: String) {
        val text = q.trim().ifEmpty { return }
        messages += Helper.Msg(true, text)
        input = ""
        scope.launch { messages += Helper.answer(ctx, text, viewModel) }
    }

    PageScaffold("Помощник", onBack, scroll = false) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (m.fromUser) Apex.Accent else Apex.Surface).padding(12.dp),
                    ) {
                        Text(m.text, color = if (m.fromUser) Apex.OnAccent else Apex.Text, fontSize = 14.sp, lineHeight = 19.sp)
                        if (m.actions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            m.actions.forEach { a ->
                                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                                    .background(Apex.Accent.copy(alpha = 0.18f)).clickable {
                                    when {
                                        a.page != null -> openPage(a.page)
                                        a.link != null -> runCatching {
                                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                        else -> { a.run?.invoke(); messages += Helper.Msg(false, "Готово ✅") }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                    Text(a.title, color = Apex.Accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Helper.suggestions.forEach { s ->
                Box(
                    Modifier.height(42.dp).clip(RoundedCornerShape(50)).background(Apex.Surface).clickable { ask(s) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(s, color = Apex.Text, fontSize = 15.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        InputWithButton(input, "Что не работает?", Icons.AutoMirrored.Rounded.Send, { input = it }) { ask(input) }
        Spacer(Modifier.height(12.dp))
    }
}
