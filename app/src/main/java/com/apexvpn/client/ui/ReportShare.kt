package com.apexvpn.client.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.apexvpn.client.data.MonthStats
import com.apexvpn.client.ui.theme.Apex
import java.io.File

/** 3.20. Отчёт за месяц картинкой 1080×1350 — поделиться в Telegram, сторис и т. п. Только цифры, без адресов и подписки. */
object ReportShare {
    fun render(m: MonthStats.Month, title: String): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Apex.palette
        val b1 = p.brand1.toArgb(); val b2 = p.brand2.toArgb()
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B0C14.toInt(), 0xFF14121F.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        c.drawCircle(w * 0.9f, 0f, w * 0.8f, Paint().apply { shader = RadialGradient(w * 0.9f, 0f, w * 0.8f, (b1 and 0x00FFFFFF) or 0x66000000, 0, Shader.TileMode.CLAMP) })
        c.drawCircle(0f, h.toFloat(), w * 0.8f, Paint().apply { shader = RadialGradient(0f, h.toFloat(), w * 0.8f, (b2 and 0x00FFFFFF) or 0x55000000, 0, Shader.TileMode.CLAMP) })

        fun text(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val white = 0xFFF4F4F8.toInt(); val dim = 0xFF9A9AB0.toInt()
        c.drawText("APEX CLIENT", 80f, 140f, text(44f, white, true))
        c.drawText("Мой месяц с VPN · " + title, 80f, 200f, text(36f, dim))

        val tiles = listOf(
            Triple("Трафик через VPN", formatBytes(m.down + m.up), "↓ ${formatBytes(m.down)}   ↑ ${formatBytes(m.up)}"),
            Triple("Реклама и трекеры", "%,d".format(m.ads).replace(',', ' '), "заблокировано"),
            Triple("Под защитой", hoursText(m.seconds), "${m.connects} подключений"),
            Triple("Средний пинг", if (m.avgPing > 0) "${m.avgPing} мс" else "—", "через VPN"),
        )
        val tw = (w - 80f * 2 - 30f) / 2; val th = 380f
        tiles.forEachIndexed { i, (k, v, sub) ->
            val x = 80f + (i % 2) * (tw + 30f); val y = 280f + (i / 2) * (th + 30f)
            c.drawRoundRect(RectF(x, y, x + tw, y + th), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF })
            c.drawText(k, x + 40f, y + 80f, text(34f, dim))
            val big = text(if (v.length > 7) 84f else 104f, white, true)
            big.shader = LinearGradient(x, 0f, x + tw, 0f, b1, b2, Shader.TileMode.CLAMP)
            c.drawText(v, x + 40f, y + 230f, big)
            c.drawText(sub, x + 40f, y + 310f, text(30f, dim))
        }
        val top = m.servers.maxByOrNull { it.value }?.key
        if (top != null) c.drawText("Любимый сервер: " + top.take(32), 80f, 1160f, text(36f, white))
        c.drawText("dubstep2004.github.io", 80f, 1250f, text(32f, dim))
        return bmp
    }

    private fun hoursText(sec: Long) = when {
        sec >= 3600 -> "${sec / 3600} ч"
        else -> "${sec / 60} мин"
    }

    fun share(context: Context, m: MonthStats.Month, title: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "apex-report-${m.key}.png")
        f.outputStream().use { render(m, title).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "Мой месяц с APEX CLIENT — dubstep2004.github.io")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Поделиться отчётом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
