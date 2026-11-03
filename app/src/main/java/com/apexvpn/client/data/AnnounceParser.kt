package com.apexvpn.client.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 434. Объявление из панели с картинкой и кнопкой.
 * В тексте: ![](https://…/banner.jpg) — картинка, [Продлить](https://…) — кнопка.
 */
object AnnounceParser {
    /**
     * 3.13.5. Шаблон панели иногда пишет для бессрочной безлимитной подписки «Истекает: 26538»
     * (дни до условного «никогда») и «Осталось ГБ: 0». Показываем по-человечески.
     */
    fun tidy(text: String, expireAt: Long, total: Long): String = text.lines().joinToString("\n") { line ->
        val l = line.lowercase()
        when {
            "истека" in l && (expireAt == 0L || Regex("""\d{4,}""").containsMatchIn(line)) && !Regex("""\d{1,2}[./]\d{1,2}[./]\d{2,4}""").containsMatchIn(line) ->
                Regex("""(истека\p{L}*\s*:?\s*).*""", RegexOption.IGNORE_CASE).replace(line) { m -> m.groupValues[1] + "никогда" }
            "осталось" in l && total <= 0L && ("гб" in l || "gb" in l || "трафик" in l) && Regex("""\d""").containsMatchIn(line) ->
                Regex("""(осталось).*""", RegexOption.IGNORE_CASE).replace(line) { m -> m.groupValues[1] + ": безлимит" }
            else -> line
        }
    }

    data class Rich(val text: String, val image: String, val button: String, val url: String)

    private val imageRe = Regex("""!\[[^\]]*]\((https://[^\s)]+)\)""")
    private val linkRe = Regex("""\[([^\]]{1,40})]\(((?:https://|tg://)[^\s)]+)\)""")

    fun parse(raw: String): Rich {
        var t = raw
        val img = imageRe.find(t)?.groupValues?.get(1).orEmpty()
        t = imageRe.replace(t, "")
        val link = linkRe.find(t)
        t = linkRe.replace(t, "")
        return Rich(
            text = t.replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim(),
            image = img,
            button = link?.groupValues?.get(1)?.trim().orEmpty(),
            url = link?.groupValues?.get(2).orEmpty(),
        )
    }

    fun safeImage(u: String?) = u != null && u.startsWith("https://") && u.length < 600
    fun safeLink(u: String?) = u != null && (u.startsWith("https://") || u.startsWith("tg://")) && u.length < 600

    /** Картинка объявления: загружается один раз и хранится в кэше. */
    suspend fun image(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!safeImage(url)) return@withContext null
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(context.cacheDir, "announce-$name.img")
        runCatching {
            if (!file.exists() || file.length() == 0L) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                try {
                    check(c.responseCode == 200)
                    val bytes = c.inputStream.use { it.readBytes() }
                    check(bytes.size < 5_000_000) { "слишком большая картинка" }
                    file.writeBytes(bytes)
                } finally {
                    c.disconnect()
                }
            }
            // Уменьшаем до ширины экрана, чтобы не тратить память
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1280) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
