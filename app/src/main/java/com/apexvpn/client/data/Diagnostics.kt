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
            log.readLines().takeLast(150).forEach { b.appendLine(mask(it, hosts)) }
        } else {
            b.appendLine("пусто")
        }
        return b.toString()
    }

    /** Отчёт в буфер обмена — вставить в любой чат. Telegram принимает до 4096 символов — берём хвост. */
    fun copy(context: Context) {
        val text = build(context).let { if (it.length > 4000) it.take(1500) + "\n…\n" + it.takeLast(2400) else it }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("APEX VPN — отчёт", text))
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
