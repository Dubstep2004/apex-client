package com.apexvpn.client.data

import android.net.NetworkCapabilities
import android.util.Base64
import com.apexvpn.client.ApexApp
import com.apexvpn.client.vpn.NetworkMonitor
import com.apexvpn.client.vpn.WifiInfoReader
import io.nekohasekai.apexcore.Apexcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Авто-выбор DNS. Меряем, как быстро отвечают Cloudflare, Google, Quad9, Mullvad, OpenDNS и Control D,
 * причём разными способами: DNS-over-HTTPS, DNS-over-TLS и DNS-over-QUIC (516) — где-то быстрее один,
 * где-то другой. Если VPN включён — через VPN, как ходит и сам DNS.
 * Лучший запоминается отдельно для каждой сети (499): домашний Wi-Fi, рабочий Wi-Fi, мобильная сеть.
 * AdGuard, Яндекс, Comss и NextDNS в выбор не входят: они меняют поведение (фильтры, геолокация).
 */
object DnsAuto {
    /** Вариант: DNS, способ и адреса для замера. */
    data class Variant(val choice: DnsChoice, val proto: String, val doh: String = "", val ip: String = "", val sni: String = "") {
        val key get() = "${choice.id}/$proto"
        val protoTitle get() = when (proto) { "tls" -> "DoT"; "quic" -> "DoQ"; else -> "DoH" }
    }

    data class Result(val variant: Variant, val ms: Int)

    val variants = listOf(
        Variant(DnsChoice.Cloudflare, "https", doh = "https://1.1.1.1/dns-query"),
        Variant(DnsChoice.Cloudflare, "tls", ip = "1.1.1.1", sni = "cloudflare-dns.com"),
        Variant(DnsChoice.Google, "https", doh = "https://dns.google/dns-query"),
        Variant(DnsChoice.Google, "tls", ip = "8.8.8.8", sni = "dns.google"),
        Variant(DnsChoice.Quad9, "https", doh = "https://dns.quad9.net/dns-query"),
        Variant(DnsChoice.Quad9, "tls", ip = "9.9.9.9", sni = "dns.quad9.net"),
        Variant(DnsChoice.Mullvad, "https", doh = "https://dns.mullvad.net/dns-query"),
        Variant(DnsChoice.Mullvad, "tls", ip = "194.242.2.2", sni = "dns.mullvad.net"),
        Variant(DnsChoice.OpenDns, "https", doh = "https://doh.opendns.com/dns-query"),
        Variant(DnsChoice.ControlD, "https", doh = "https://freedns.controld.com/p0"),
        Variant(DnsChoice.ControlD, "tls", ip = "76.76.2.0", sni = "p0.freedns.controld.com"),
        Variant(DnsChoice.ControlD, "quic", ip = "76.76.2.0", sni = "p0.freedns.controld.com"),
    )

    /** Последний замер шёл через сервер VPN (время включает путь до сервера). */
    @Volatile var lastViaVpn: Boolean = false

    private val _results = MutableStateFlow<List<Result>>(emptyList())
    val results = _results.asStateFlow()

    /** Ключ сети (499): «mobile», «wifi:<имя сети>» или «wifi». */
    fun networkKey(): String = runCatching {
        val cm = ApexApp.connectivity
        val net = NetworkMonitor.defaultNetwork ?: cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "mobile"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                WifiInfoReader.currentSsid(ApexApp.instance)?.let { "wifi:$it" } ?: "wifi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "other"
        }
    }.getOrDefault("other")

    fun networkTitle(key: String = networkKey()): String = when {
        key == "mobile" -> "мобильная сеть"
        key.startsWith("wifi:") -> "Wi-Fi «${key.removePrefix("wifi:")}»"
        key == "wifi" -> "Wi-Fi"
        key == "ethernet" -> "кабель"
        else -> "эта сеть"
    }

    private fun saved(): JSONObject = runCatching { JSONObject(Prefs.autoDnsBest.takeIf { it.startsWith("{") } ?: "{}") }.getOrDefault(JSONObject())

    /** Лучший вариант для текущей сети; по умолчанию — Cloudflare DoH. */
    fun bestVariant(key: String = networkKey()): Variant {
        val k = saved().optString(key).ifBlank { saved().optString("any") }
        val v = variants.firstOrNull { it.key == k } ?: variants.first()
        // Сохранённый DoQ не применяем на мобильной сети — берём тот же DNS по DoT
        if (key == "mobile" && v.proto == "quic") return variants.firstOrNull { it.choice == v.choice && it.proto == "tls" } ?: variants.first()
        return v
    }

    /** Совместимость: id DNS для ядра. */
    val best: String get() = bestVariant().choice.id

    /** Что сейчас применено в ядре — чтобы при смене сети понять, нужна ли перезагрузка. */
    @Volatile var applied: String = ""

    /** Замер; возвращает true, если лучший DNS для этой сети сменился. */
    suspend fun measure(): Boolean = withContext(Dispatchers.IO) {
        val key = networkKey()
        // На мобильной сети без DoQ: UDP через VPN на LTE теряется, DNS по TCP надёжнее
        val pool = if (key == "mobile") variants.filter { it.proto != "quic" } else variants
        // Не больше двух замеров одновременно: раньше все 12 шли разом через один сервер,
        // мешали друг другу, и время ответа выходило в разы больше настоящего
        val gate = kotlinx.coroutines.sync.Semaphore(2)
        val res = coroutineScope {
            pool.map { v -> async { gate.withPermit { Result(v, probe(v)) } } }.awaitAll()
        }.sortedBy { if (it.ms < 0) Int.MAX_VALUE else it.ms }
        lastViaVpn = viaVpn()
        _results.value = res
        val win = res.firstOrNull { it.ms >= 0 } ?: return@withContext false
        val cur = bestVariant(key)
        val old = res.firstOrNull { it.variant == cur }?.ms ?: -1
        // Меняем, только если новый заметно быстрее — чтобы DNS не «прыгал»
        val change = win.variant != cur && (old < 0 || old - win.ms > 25)
        if (change || saved().optString(key).isBlank()) {
            val o = saved().put(key, win.variant.key).put("any", win.variant.key)
            Prefs.autoDnsBest = o.toString()
        }
        // Перезагрузка нужна, если в ядре сейчас не тот DNS, что лучший для этой сети
        bestVariant(key).key != applied
    }

    /**
     * Замер в ядре: при включённом VPN — через сервер (локальный SOCKS ядра), как и ходит DNS.
     * Раньше DoH мерился запросом самого приложения, и на мобильной сети с белыми списками
     * все DNS показывали «нет ответа», хотя через VPN работали.
     */
    private fun probe(v: Variant): Int = runCatching {
        if (v.proto == "https") Apexcore.dnsProbeHTTPS(v.doh, 4000) else Apexcore.dnsProbe(v.proto, v.ip, v.sni, 4000)
    }.getOrDefault(-1).let { if (it < 0 && v.proto == "https" && !viaVpn()) probeDoh(v.doh) else it }

    private fun viaVpn() = com.apexvpn.client.vpn.VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Started

    /** Ни один DNS не ответил на последнем замере. */
    val allFailed: Boolean get() = _results.value.isNotEmpty() && _results.value.none { it.ms >= 0 }

    /** Лучшее из трёх запросов, мс; -1 — не ответил. */
    private fun probeDoh(url: String): Int {
        val q = Base64.encodeToString(query(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        var bestMs = -1
        repeat(3) {
            runCatching {
                val t = System.nanoTime()
                val c = URL("$url?dns=$q").openConnection() as HttpURLConnection
                c.connectTimeout = 3000
                c.readTimeout = 3000
                c.setRequestProperty("Accept", "application/dns-message")
                try {
                    if (c.responseCode == 200) {
                        c.inputStream.use { it.readBytes() }
                        val ms = ((System.nanoTime() - t) / 1_000_000).toInt()
                        if (bestMs < 0 || ms < bestMs) bestMs = ms
                    }
                } finally {
                    c.disconnect()
                }
            }
        }
        return bestMs
    }

    /** DNS-запрос A для www.example.com в формате RFC 1035. */
    private fun query(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in "www.example.com".split('.')) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        return out.toByteArray()
    }
}
