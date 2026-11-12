package com.apexvpn.client.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/** 3.20. Эффекты поверх любых обоев (картинка, живые, дизайн). */
object WallFx {
    val all = listOf(
        "none" to "Нет", "snow" to "Снег", "rain" to "Дождь", "sakura" to "Сакура",
        "fireflies" to "Светлячки", "stars" to "Звёзды", "leaves" to "Листья",
    )
}

@Composable
fun WallFxOverlay(fx: String) {
    // Плавный режим (экономия) — без анимации поверх
    if (Apex.feel.smooth) return
    when (fx) {
        "snow" -> SeasonalOverlay(Season.Winter)
        "sakura" -> SeasonalOverlay(Season.Spring)
        "fireflies" -> SeasonalOverlay(Season.Summer)
        "leaves" -> SeasonalOverlay(Season.Autumn)
        "rain" -> RainOverlay()
        "stars" -> StarsOverlay()
    }
}

@Composable
private fun frameTime(): Float {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = it - start }
    }
    return time / 1_000_000_000f
}

@Composable
private fun RainOverlay() {
    val drops = remember { val r = Random(3); List(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, 0.5f + r.nextFloat() * 0.5f) } }
    val t = frameTime()
    Canvas(Modifier.fillMaxSize()) {
        val len = size.height * 0.035f
        drops.forEach { (x0, y0, sp, a) ->
            val y = ((y0 + t * sp) % 1f) * (size.height + len) - len
            val x = (x0 + y / size.height * 0.06f) % 1f * size.width
            drawLine(Color(0xFFBFD4FF).copy(alpha = 0.28f * a), Offset(x, y), Offset(x + len * 0.18f, y + len * sp), strokeWidth = 1.6f + a)
        }
    }
}

@Composable
private fun StarsOverlay() {
    val stars = remember { val r = Random(8); List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.8f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f) } }
    val t = frameTime()
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, s, ph) ->
            val a = (0.25f + 0.45f * (0.5f + 0.5f * sin(t * 1.6f + ph))).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = a), s, Offset(x * size.width, y * size.height * 0.85f))
        }
        // Изредка — падающая звезда
        val k = (t / 7f).toInt(); val p = (t % 7f) / 0.9f
        if (p < 1f) {
            val r = Random(k)
            val sx = r.nextFloat() * size.width * 0.7f + size.width * 0.2f; val sy = r.nextFloat() * size.height * 0.3f
            val dx = -size.width * 0.25f * p; val dy = size.height * 0.12f * p
            drawLine(Color.White.copy(alpha = 0.8f * (1f - p)), Offset(sx + dx, sy + dy), Offset(sx + dx + size.width * 0.06f, sy + dy - size.height * 0.03f), strokeWidth = 2.5f)
        }
    }
}
