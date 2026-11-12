package com.apexvpn.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * 3.9. Android TV: подписку вводить пультом неудобно. Телевизор показывает QR-код со ссылкой
 * на свою маленькую страницу в домашней сети; телефон открывает её, вставляет ссылку подписки —
 * и телевизор подключается. Работает только в домашней сети, ссылка с одноразовым ключом,
 * сервер закрывается сразу после получения подписки или при выходе с экрана.
 */
object TvPairing {
    @Volatile private var server: ServerSocket? = null

    private fun lanIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("tun") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    /** Запустить; ответ — адрес страницы для QR или null (нет домашней сети). */
    fun start(onUrl: (String) -> Unit): String? {
        stop()
        val ip = lanIp() ?: return null
        val token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = ss
        Thread {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                runCatching { handle(s, token, onUrl) }
                runCatching { s.close() }
            }
        }.apply { isDaemon = true }.start()
        return "http://$ip:${ss.localPort}/$token"
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(s: Socket, token: String, onUrl: (String) -> Unit) {
        s.soTimeout = 15_000
        val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val line = r.readLine() ?: return
        val parts = line.split(' ')
        val method = parts.getOrNull(0) ?: return
        val path = parts.getOrNull(1) ?: return
        var length = 0
        while (true) {
            val h = r.readLine() ?: break
            if (h.isEmpty()) break
            if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toIntOrNull()?.coerceIn(0, 4096) ?: 0
        }
        if (path.trimEnd('/') != "/$token") return reply(s, 404, "<h3>Ссылка устарела — отсканируйте QR-код на телевизоре ещё раз</h3>")
        if (method == "POST") {
            val body = CharArray(length).let { buf -> var n = 0; while (n < length) { val k = r.read(buf, n, length - n); if (k < 0) break; n += k }; String(buf, 0, n) }
            val url = body.split('&').firstOrNull { it.startsWith("url=") }?.removePrefix("url=")
                ?.let { URLDecoder.decode(it, "UTF-8").trim() }.orEmpty()
            val host = runCatching { java.net.URL(url).host }.getOrNull()
            if (!url.startsWith("https://") || !com.apexvpn.client.data.SubHosts.isAllowed(host)) {
                return reply(s, 200, page("Нужна ссылка вида https://${com.apexvpn.client.data.SubHosts.primary()}/…", ""))
            }
            reply(s, 200, "<h2>✅ Готово</h2><p>Телевизор загружает подписку. Эту страницу можно закрыть.</p>")
            onUrl(url)
            stop()
        } else {
            reply(s, 200, page(null, ""))
        }
    }

    private fun page(error: String?, value: String) = """
        <h2>APEX CLIENT → телевизор</h2>
        <p>Вставьте ссылку подписки (https://${com.apexvpn.client.data.SubHosts.primary()}/…) и нажмите «Отправить».</p>
        ${if (error != null) "<p style='color:#ef4444'>$error</p>" else ""}
        <form method='post'><input name='url' value='$value' placeholder='https://${com.apexvpn.client.data.SubHosts.primary()}/…' autofocus
        style='width:100%;font-size:18px;padding:12px;border-radius:12px;border:1px solid #555;box-sizing:border-box'>
        <p><button style='font-size:18px;padding:12px 24px;border-radius:12px;border:0;background:#6366f1;color:#fff'>Отправить</button></p></form>
    """.trimIndent()

    private fun reply(s: Socket, code: Int, html: String) {
        val body = ("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>APEX CLIENT</title></head><body style='font-family:sans-serif;background:#0b0b14;color:#eee;padding:24px;max-width:560px;margin:auto'>$html</body></html>")
            .toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
    }
}

/** Карточка на экране первого запуска телевизора: QR-код для телефона. */
@Composable
fun TvPairingCard(viewModel: MainViewModel) {
    var status by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        url = TvPairing.start { sub ->
            status = "Подписка получена — загружаем…"
            viewModel.setSubscription(sub) { err -> status = err ?: "Готово" }
        }
        onDispose { TvPairing.stop() }
    }
    val qr = remember(url) { url?.let { runCatching { com.apexvpn.client.data.Transfer.qrBitmap(it, 600).asImageBitmap() }.getOrNull() } }
    Column(
        Modifier.clip(RoundedCornerShape(24.dp)).background(Apex.Surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("С телефона — без пульта", color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (qr != null) {
            Image(qr, null, Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
            Spacer(Modifier.height(10.dp))
            Text("Наведите камеру телефона на QR-код, вставьте ссылку подписки и нажмите «Отправить». Телефон и телевизор — в одной сети Wi-Fi.",
                color = Apex.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
            url?.let { Text(it, color = Apex.TextDim, fontSize = 12.sp) }
        } else {
            Text("Телевизор не в домашней сети — подключите его к Wi-Fi или введите ссылку пультом.", color = Apex.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        status?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Apex.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}
