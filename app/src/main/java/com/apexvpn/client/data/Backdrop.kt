package com.apexvpn.client.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** 157. Своё фото за щитом: уменьшенная копия хранится в папке приложения. */
object Backdrop {
    private const val MAX = 1600

    fun file(context: Context) = File(context.filesDir, "backdrop.jpg")

    fun load(context: Context): Bitmap? = runCatching {
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }.getOrNull()

    /** Копирует выбранное фото (уменьшая до 1600 px), true — получилось. */
    fun save(context: Context, uri: Uri): Boolean = save(context) { context.contentResolver.openInputStream(uri) }

    /** 3.19.7. Обои из интернета: уже скачанный файл. */
    fun save(context: Context, file: File): Boolean = save(context) { file.inputStream() }

    private fun save(context: Context, open: () -> java.io.InputStream?): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX * 2 || bounds.outHeight / sample > MAX * 2) sample *= 2
        val bmp = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        val scale = minOf(1f, MAX.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val tmp = File(context.filesDir, "backdrop.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        tmp.renameTo(file(context))
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
    }
}
