package com.apexvpn.client.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * «Ощущения»: звук и вибрация при подключении VPN.
 * Звуки синтезируются на лету — без файлов в APK. На беззвучном телефон молчит.
 */
object ConnectFeedback {
    data class Sound(val id: String, val title: String, val subtitle: String)

    /** 271. Наборы звуков 2.0: у каждого свой звук подключения и отключения. */
    val sounds = listOf(
        Sound("signal", "Сигнал", "Два коротких тона"),
        Sound("crystal", "Кристалл", "Звонкий и чистый"),
        Sound("sphere", "Сфера", "Мягкий низкий"),
        Sound("chime", "Звон", "Три ноты вверх"),
        Sound("space", "Космос", "Взлёт с плавным подъёмом тона"),
        Sound("retro", "Ретро 8-бит", "Как в старых приставках"),
        Sound("drop", "Капля", "Короткий «бульк»"),
        Sound("harp", "Арфа", "Быстрый перебор струн"),
        Sound("pulse", "Пульс", "Два мягких удара сердца"),
    )

    fun title(id: String) = sounds.firstOrNull { it.id == id }?.title ?: sounds[0].title

    /** Вызывается после отключения VPN. */
    fun onDisconnected(context: Context) {
        val s = AppSettings.value
        if (s.connectSound && s.disconnectSound) play(context, s.sound, off = true)
    }

    /** Вызывается при переходе в «Защищено». */
    fun onConnected(context: Context) {
        val s = AppSettings.value
        if (s.vibration) vibrate(context)
        if (s.connectSound) play(context, s.sound)
    }

    fun vibrate(context: Context) = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return@runCatching
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 40), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, 70, 40), -1)
        }
    }

    /** [force] — проба звука в настройках (громкость всё равно системная). */
    fun play(context: Context, id: String, force: Boolean = false, off: Boolean = false) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!force && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        Thread {
            runCatching {
                val pcm = synth(id, off)
                val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
                // Раньше: «системные звуки» (USAGE_ASSISTANCE_SONIFICATION) и статический буфер — на Xiaomi,
                // Samsung и др. громкость системных звуков часто 0, а статический режим на части
                // телефонов молчит. Теперь — громкость уведомлений и потоковый режим.
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(min)
                    .build()
                try {
                    track.play()
                    // Тишина в конце, чтобы последняя нота не обрывалась при остановке
                    val padded = pcm + ShortArray(RATE / 10)
                    track.write(padded, 0, padded.size)
                    Thread.sleep(200)
                    track.stop()
                } finally {
                    track.release()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private const val RATE = 44100

    /** Ноты: частота, начало и длина в секундах, затухание. */
    /**
     * [glide] — во сколько раз меняется частота к концу ноты (взлёт/капля),
     * [square] — «квадратная» волна для 8-бит.
     */
    private class Note(
        val f: Double, val start: Double, val len: Double, val decay: Double, val harm: Double = 0.0,
        val glide: Double = 1.0, val square: Boolean = false,
    )

    private fun connectNotes(id: String): List<Note> = when (id) {
        "crystal" -> listOf(Note(1760.0, 0.0, 0.9, 5.0, 0.35), Note(2637.0, 0.06, 0.8, 6.0, 0.2))
        "sphere" -> listOf(Note(330.0, 0.0, 0.8, 3.5, 0.15), Note(494.0, 0.12, 0.7, 4.0, 0.1))
        "chime" -> listOf(Note(1047.0, 0.0, 0.5, 6.0, 0.25), Note(1319.0, 0.12, 0.5, 6.0, 0.25), Note(1568.0, 0.24, 0.7, 5.0, 0.25))
        "space" -> listOf(Note(220.0, 0.0, 0.7, 2.5, 0.3, glide = 4.0), Note(880.0, 0.45, 0.6, 4.0, 0.2))
        "retro" -> listOf(Note(523.0, 0.0, 0.08, 4.0, square = true), Note(659.0, 0.08, 0.08, 4.0, square = true), Note(784.0, 0.16, 0.08, 4.0, square = true), Note(1047.0, 0.24, 0.25, 6.0, square = true))
        "drop" -> listOf(Note(600.0, 0.0, 0.25, 9.0, glide = 2.2))
        "harp" -> listOf(523.0, 659.0, 784.0, 988.0, 1175.0).mapIndexed { i, f -> Note(f, i * 0.055, 0.7, 5.0, 0.3) }
        "pulse" -> listOf(Note(110.0, 0.0, 0.25, 10.0, 0.5, glide = 0.7), Note(110.0, 0.28, 0.3, 9.0, 0.5, glide = 0.7))
        else -> listOf(Note(880.0, 0.0, 0.12, 14.0), Note(1320.0, 0.16, 0.2, 10.0)) // signal
    }

    /** Отключение — тот же набор, но вниз и тише по характеру. */
    private fun disconnectNotes(id: String): List<Note> = when (id) {
        "space" -> listOf(Note(880.0, 0.0, 0.7, 3.0, 0.3, glide = 0.25))
        "drop" -> listOf(Note(1300.0, 0.0, 0.25, 9.0, glide = 0.45))
        "pulse" -> listOf(Note(110.0, 0.0, 0.35, 8.0, 0.5, glide = 0.6))
        else -> {
            // Ноты подключения в обратном порядке
            val up = connectNotes(id)
            val ordered = up.sortedByDescending { it.f }
            var t = 0.0
            ordered.mapIndexed { i, n ->
                val gap = if (i == 0) 0.0 else (up.getOrNull(1)?.start ?: 0.12).coerceAtLeast(0.06)
                t += gap
                Note(n.f * 0.85, t, n.len, n.decay * 1.2, n.harm, square = n.square)
            }
        }
    }

    private fun synth(id: String, off: Boolean = false): ShortArray {
        val notes = if (off) disconnectNotes(id) else connectNotes(id)
        val total = notes.maxOf { it.start + it.len }
        val out = DoubleArray((total * RATE).toInt() + 1)
        for (n in notes) {
            val s0 = (n.start * RATE).toInt()
            val count = (n.len * RATE).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / RATE
                val attack = minOf(1.0, t / 0.006)
                val env = attack * exp(-n.decay * t)
                // Частота плавно меняется от f до f×glide: фаза — интеграл частоты
                val k0 = if (n.glide == 1.0) 0.0 else kotlin.math.ln(n.glide) / n.len
                val phase = if (k0 == 0.0) n.f * t else n.f * (exp(k0 * t) - 1) / k0
                val base = sin(2 * PI * phase)
                val w = (if (n.square) (if (base >= 0) 0.6 else -0.6) else base) + n.harm * sin(2 * PI * phase * 2.01)
                val k = s0 + i
                if (k < out.size) out[k] += w * env
            }
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(out.size) { (out[it] / peak * 0.6 * Short.MAX_VALUE).toInt().toShort() }
    }
}
