package com.apexvpn.client.data

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 262. Перенос на новый телефон одним QR-кодом: настройки, свои списки, избранное,
 * подписка и выбранный сервер — то же, что в файле резервной копии, только сжато.
 * Данные не уходят в интернет: QR сканируется камерой нового телефона.
 */
object Transfer {
    private const val PREFIX = "APEXT1:"

    /** Ёмкость QR-кода (версия 40, низкая коррекция) — 2953 байта. */
    private const val MAX = 2900

    sealed interface Result {
        data class Ok(val text: String) : Result
        data class TooBig(val bytes: Int) : Result
    }

    fun encode(): Result {
        val json = SettingsBackup.payload().toString().toByteArray(Charsets.UTF_8)
        val text = PREFIX + Base64.encodeToString(deflate(json), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return if (text.length > MAX) Result.TooBig(text.length) else Result.Ok(text)
    }

    fun isTransfer(text: String?) = text?.trim()?.startsWith(PREFIX) == true

    /** null — QR повреждён или это не перенос APEX CLIENT (внутренняя метка в QR осталась «APEX VPN» — для совместимости). */
    fun decode(text: String): JSONObject? = runCatching {
        val raw = Base64.decode(text.trim().removePrefix(PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(inflate(raw).toString(Charsets.UTF_8)).takeIf { it.optString("app") == "APEX VPN" }
    }.getOrNull()

    /** Сколько всего переносится — для подтверждения на новом телефоне. */
    fun summary(o: JSONObject): String {
        val s = o.optJSONObject("settings")
        val lists = s?.optJSONArray("customLists")?.length() ?: 0
        val fav = s?.optJSONArray("favorites")?.length() ?: 0
        val rules = s?.optJSONArray("userRules")?.length() ?: 0
        return buildList {
            add("все настройки")
            if (o.optString("subscription").isNotBlank()) add("подписка")
            if (lists > 0) add("свои списки: $lists")
            if (fav > 0) add("избранное: $fav")
            if (rules > 0) add("правила: $rules")
        }.joinToString(", ")
    }

    fun qrBitmap(text: String, size: Int = 900): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            px[y * m.width + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val i = Inflater()
        i.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!i.finished()) {
            val n = i.inflate(buf)
            if (n == 0 && (i.needsInput() || i.needsDictionary())) break
            out.write(buf, 0, n)
            check(out.size() < 1_000_000) { "слишком большой" }
        }
        i.end()
        return out.toByteArray()
    }
}
