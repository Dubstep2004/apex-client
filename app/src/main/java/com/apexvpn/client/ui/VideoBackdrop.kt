package com.apexvpn.client.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Живые видео на фоне главного экрана: готовые шаблоны (в приложении, ~0,1–0,3 МБ каждый,
 * бесшовная петля) или своё видео из галереи. Звук выключен, на паузе, когда приложение свёрнуто.
 * В настройках: shieldBackdrop = "video:<id>" (шаблон) или "video" (своё).
 */
object LiveVideos {
    data class Template(val id: String, val title: String)

    val templates = listOf(
        Template("aurora", "Сияние"),
        Template("cosmos", "Космос"),
        Template("liquid", "Жидкое стекло"),
        Template("synth", "Синтвейв"),
        Template("ocean", "Океан"),
        Template("rain", "Ночной дождь"),
    )

    fun isVideo(backdrop: String) = backdrop == "video" || backdrop.startsWith("video:")

    fun ownFile(context: Context) = File(context.filesDir, "backdrop_video.mp4")

    /** Копирует видео из галереи к себе (до 150 МБ). */
    suspend fun saveOwn(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "backdrop_video.tmp")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 150L * 1024 * 1024) error("Видео больше 150 МБ")
                        out.write(buf, 0, n)
                    }
                }
            }
            check(tmp.length() > 0)
            tmp.renameTo(ownFile(context))
        }.getOrDefault(false)
    }

    fun clearOwn(context: Context) {
        runCatching { ownFile(context).delete() }
    }

    /** Подготовить плеер для источника; null — видео нет. */
    fun player(context: Context, backdrop: String): MediaPlayer? = runCatching {
        val mp = MediaPlayer()
        if (backdrop == "video") {
            val f = ownFile(context)
            if (!f.exists()) return null
            mp.setDataSource(f.absolutePath)
        } else if (backdrop.startsWith("video:net:")) {
            // 3.19.7. Живые обои из интернета — скачанный файл
            val f = com.apexvpn.client.data.Wallpapers.file(context, backdrop.removePrefix("video:net:"))
            if (!f.exists()) return null
            mp.setDataSource(f.absolutePath)
        } else {
            val id = backdrop.removePrefix("video:")
            if (templates.none { it.id == id }) return null
            context.assets.openFd("videos/$id.mp4").use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        }
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp
    }.getOrNull()
}

@Composable
fun VideoBackdrop(backdrop: String, stamp: Long, dim: Int, speedPct: Int = 100) {
    val speed = (speedPct.coerceIn(50, 200)) / 100f
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember(backdrop, stamp) { arrayOfNulls<MediaPlayer>(1) }
    // start() до окончания подготовки переводит MediaPlayer в ошибку — ждём onPrepared
    val ready = remember(backdrop, stamp) { BooleanArray(1) }
    val base = Apex.Base
    val d = dim.coerceIn(0, 90) / 100f

    DisposableEffect(backdrop, stamp, lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            val mp = holder[0] ?: return@LifecycleEventObserver
            if (!ready[0]) return@LifecycleEventObserver
            runCatching {
                when (e) {
                    Lifecycle.Event.ON_PAUSE -> if (mp.isPlaying) mp.pause()
                    Lifecycle.Event.ON_RESUME -> mp.start()
                    else -> {}
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            holder[0]?.let { runCatching { it.stop() }; it.release() }
            holder[0] = null
            ready[0] = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.key(backdrop, stamp, speed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            private var surface: Surface? = null

                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                val mp = LiveVideos.player(ctx, backdrop) ?: return
                                holder[0]?.release()
                                holder[0] = mp
                                ready[0] = false
                                surface?.release()
                                mp.setSurface(Surface(st).also { surface = it })
                                mp.setOnPreparedListener { p ->
                                    ready[0] = true
                                    centerCrop(this@apply, p.videoWidth, p.videoHeight)
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                        // 3.20. Скорость живых обоев (Android 6+); setPlaybackParams сам запускает видео
                                        if (speed != 1f && android.os.Build.VERSION.SDK_INT >= 23) runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
                                        p.start()
                                    }
                                }
                                mp.setOnErrorListener { _, _, _ -> true }
                                runCatching { mp.prepareAsync() }
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                holder[0]?.takeIf { ready[0] }?.let { runCatching { centerCrop(this@apply, it.videoWidth, it.videoHeight) } }
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                holder[0]?.let { runCatching { it.stop() }; it.release() }
                                holder[0] = null
                                ready[0] = false
                                surface?.release(); surface = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
            )
        }
        // Затемнение: сверху и снизу сильнее, чтобы читались заголовок и кнопки
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        base.copy(alpha = (d + 0.1f).coerceAtMost(0.95f)),
                        base.copy(alpha = d * 0.45f),
                        base.copy(alpha = (d + 0.3f).coerceAtMost(0.95f)),
                    ),
                ),
            ),
        )
    }
}

/** Видео заполняет экран без искажений (лишнее обрезается по краям). */
private fun centerCrop(view: TextureView, vw: Int, vh: Int) {
    val w = view.width.toFloat()
    val h = view.height.toFloat()
    if (vw <= 0 || vh <= 0 || w <= 0f || h <= 0f) return
    val scale = maxOf(w / vw, h / vh)
    val sx = vw * scale / w
    val sy = vh * scale / h
    val m = android.graphics.Matrix()
    m.setScale(sx, sy, w / 2, h / 2)
    view.setTransform(m)
}
