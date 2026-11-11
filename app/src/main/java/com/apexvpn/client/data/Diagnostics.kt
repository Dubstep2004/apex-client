package com.apexvpn.client.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.vpn.VpnState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 24. Отчёт для поддержки: версия, телефон, настройки, ошибки и журнал ядра.
 * Без ссылки подписки, адресов серверов и IP — их маскируем.
 */
object Diagnostics {
    private const val MAX_LOG = 512 * 1024L

    fun coreLog(context: Context) = File(context.filesDir, "core.log")

    /** Журнал не растёт бесконечно: при каждом подключении оставляем хвост. */
    fun trimCoreLog(context: Context) = runCatching {
        val f = coreLog(context)
        if (f.length() > MAX_LOG) f.writeText(f.readText().takeLast((MAX_LOG / 4).toInt()))
    }

    private val ipv4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.\d{1,3}\.\d{1,3}\b""")
    private val ipv6 = Regex("""\b([0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\b""")
    private val urls = Regex("""https?://\S+""")

    private fun mask(text: String, hosts: Collection<String>): String {
        var t = text
        hosts.filter { it.length > 3 }.forEach { t = t.replace(it, "<сервер>") }
        t = urls.replace(t, "<ссылка>")
        t = ipv4.replace(t) { "${it.groupValues[1]}.${it.groupValues[2]}.x.x" }
        t = ipv6.replace(t, "<ipv6>")
        return t
    }

    fun build(context: Context): String {
        val s = AppSettings.value
        val sub = Subscription.state.value
        val sel = sub.nodes.find { it.tag == sub.selectedTag }
        val hosts = sub.nodes.map { it.server }.toSet() + listOf(sub.url)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val b = StringBuilder()
        b.appendLine("APEX VPN — отчёт для поддержки")
        b.appendLine("Время: $now")
        b.appendLine("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Xray ${BuildConfig.XRAY_VERSION}")
        b.appendLine("Сборка: ${when (Integrity.status(context)) {
            Integrity.Status.Official -> "официальная"
            Integrity.Status.Test -> "тестовая"
            Integrity.Status.Unofficial -> "НЕОФИЦИАЛЬНАЯ (${Integrity.fingerprint(context)}, ${context.packageName})"
        }}")
        b.appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        b.appendLine()
        b.appendLine("── Состояние")
        b.appendLine("VPN: ${VpnState.status.value}, пинг: ${VpnState.ping.value} мс")
        VpnState.error.value?.let { b.appendLine("Ошибка: ${mask(it, hosts)}") }
        b.appendLine("Подписка: «${sub.title}», серверов: ${sub.nodes.size}")
        b.appendLine("Сервер: " + (sel?.let { "${it.name} · ${it.category} · ${it.detail}${if (it.xrayOnly && !it.detail.contains("Xray")) " · Xray" else ""}" } ?: "Автопилот"))
        sel?.let { n ->
            // Без полного адреса: первые две части IP или «домен» — чтобы сравнить с другим приложением
            val ip = Regex("""^(\d+\.\d+)\.\d+\.\d+$""").find(n.server)?.groupValues?.get(1)
            b.appendLine("Адрес сервера: " + (if (ip != null) "$ip.x.x" else "домен") + ", порт ${n.port}")
        }
        if (VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started) {
            // Что выбрано в самом ядре — может не совпасть с приложением (отдельный поток: сеть)
            var cur: String? = null
            Thread { cur = com.apexvpn.client.vpn.CoreSelector.now() }.apply { start(); join(3000) }
            val name = cur?.let { t -> if (t == AUTO_TAG) "Автопилот" else sub.nodes.find { it.tag == t }?.name ?: t } ?: "нет ответа"
            b.appendLine("Ядро сейчас: $name")
            if (com.apexvpn.client.vpn.XrayCore.crashed()) b.appendLine("Xray: остановился — ${mask(com.apexvpn.client.vpn.XrayCore.lastError(), hosts)}")
        }
        val byCat = sub.nodes.groupingBy { it.category }.eachCount().entries.joinToString { "${it.key} ${it.value}" }
        b.appendLine("Протоколы: $byCat")
        b.appendLine()
        b.appendLine("── Настройки")
        b.appendLine("Ядро: ${s.core.title}, режим: ${s.mode.title}, профиль: ${s.profile.title}, DNS: ${s.dns.title}")
        b.appendLine("Приложения: ${s.appsMode}, банки скрыты: ${s.hideFromBanks}, реклама: ${s.adBlock}, семейный: ${s.kids}")
        b.appendLine("Для опытных: правил ${s.userRules.size}, стратегия ${s.domainStrategy}, FakeDNS ${s.fakeDns}, WARP ${s.warp}, " +
            "удалённый DNS ${if (s.remoteDns.isSet) s.remoteDns.type else "—"}, домашний DNS ${if (s.directDns.isSet) s.directDns.type else "—"}")
        b.appendLine("Аварийный выключатель: ${s.killSwitch}")
        b.appendLine()
        val crashes = File(context.filesDir, "crashes").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        b.appendLine("── Сбои (${crashes.size})")
        crashes.take(3).forEach { f ->
            b.appendLine("• ${f.name}")
            b.appendLine(mask(f.readText().take(1500), hosts).prependIndent("  "))
        }
        b.appendLine()
        b.appendLine("── Журнал ядра (последние строки)")
        val log = coreLog(context)
        if (log.exists()) {
            compactLog(log.readLines().takeLast(400).map { mask(it, hosts) }).takeLast(60).forEach { b.appendLine(it) }
        } else {
            b.appendLine("пусто")
        }
        return b.toString()
    }

    /**
     * 3.17.3: одинаковые ошибки (отличаются только временем, номером и длительностью) —
     * одной строкой с числом повторов. Раньше 40 копий одной ошибки вытесняли из отчёта всё
     * остальное. Порядок — по последнему появлению.
     */
    internal fun compactLog(lines: List<String>): List<String> {
        val head = Regex("""^[+-]\d{4} \d{4}-\d\d-\d\d (\d\d:\d\d:\d\d) (\w+) (\[\d+ [^\]]*\] )?""")
        val last = LinkedHashMap<String, Pair<String, Int>>()
        for (l in lines) {
            val m = head.find(l)
            val body = if (m != null) l.substring(m.range.last + 1) else l
            val key = (m?.groupValues?.get(2).orEmpty()) + " " + body.replace(Regex("""\d+(\.\d+)?(ms|s)\b"""), "#")
            val n = (last.remove(key)?.second ?: 0) + 1
            val shown = if (m != null) "${m.groupValues[1]} ${m.groupValues[2]} $body" else l
            last[key] = shown to n
        }
        return last.values.map { (l, n) -> if (n > 1) "$l  (×$n)" else l }
    }

    /** Отчёт в буфер обмена — вставить в любой чат. Telegram принимает до 4096 символов. */
    fun copy(context: Context) {
        // Обрезаем по целым строкам: шапка целиком, из журнала — самые свежие строки, что влезут
        val text = build(context).let { full ->
            if (full.length <= 4000) return@let full
            val cut = full.indexOf("── Журнал ядра")
            val headPart = if (cut > 0) full.substring(0, cut) else ""
            val logLines = (if (cut > 0) full.substring(cut) else full).lines()
            val title = logLines.first()
            val tail = ArrayList<String>()
            var size = headPart.length + title.length + 1
            for (l in logLines.drop(1).asReversed()) {
                if (size + l.length + 1 > 4000) break
                tail.add(0, l); size += l.length + 1
            }
            headPart + title + "\n" + tail.joinToString("\n")
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("APEX VPN — отчёт", text))
    }

    /**
     * 3.17.9. Отчёт одной кнопкой в Telegram: текст сразу уходит в Telegram (выбрать чат
     * поддержки), без копирования. Нет Telegram — обычное окно «Поделиться».
     */
    fun sendTelegram(context: Context) {
        copy(context) // заодно в буфере — если Telegram откроется не в том чате
        val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString() ?: build(context).take(4000)
        val pm = context.packageManager
        val tg = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "tw.nekomimi.nekogram", "nekox.messenger")
            .firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (tg != null) {
            context.startActivity(send.setPackage(tg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            context.startActivity(Intent.createChooser(send, "Отправить отчёт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Окно «Поделиться» с файлом отчёта — например, в Telegram поддержки. */
    fun share(context: Context) {
        val dir = File(context.cacheDir, "support").apply { mkdirs() }
        val file = File(dir, "apex-vpn-report.txt")
        file.writeText(build(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            clipData = android.content.ClipData.newRawUri("report", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "APEX VPN ${BuildConfig.VERSION_NAME}: отчёт")
            putExtra(Intent.EXTRA_TEXT, "Отчёт APEX VPN ${BuildConfig.VERSION_NAME}. Опишите проблему:")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Отправить отчёт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
