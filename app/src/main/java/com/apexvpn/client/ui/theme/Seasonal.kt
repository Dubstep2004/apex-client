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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 3.8. Сезонное оформление: снег зимой, лепестки весной, светлячки летом, листья осенью. */
enum class Season(val title: String) {
    Winter("Снег"), Spring("Лепестки"), Summer("Светлячки"), Autumn("Листья");

    companion object {
        fun now(cal: Calendar = Calendar.getInstance()): Season = when (cal.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Winter
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Spring
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Summer
            else -> Autumn
        }
    }
}

private class Particle(var x: Float, var y: Float, val size: Float, val speed: Float, val sway: Float, val phase: Float, val spin: Float, val tint: Int)

/** Лёгкий слой поверх фона: ~36 частиц, рисуется только пока экран открыт. */
@Composable
fun SeasonalOverlay(season: Season = Season.now()) {
    val parts = remember(season) {
        val r = Random(42)
        List(36) {
            Particle(
                x = r.nextFloat(), y = r.nextFloat(),
                size = when (season) { Season.Winter -> 2f + r.nextFloat() * 4f; Season.Summer -> 1.5f + r.nextFloat() * 2.5f; else -> 6f + r.nextFloat() * 7f },
                speed = when (season) { Season.Summer -> 0.006f + r.nextFloat() * 0.01f; Season.Winter -> 0.03f + r.nextFloat() * 0.05f; else -> 0.025f + r.nextFloat() * 0.04f },
                sway = 0.01f + r.nextFloat() * 0.03f, phase = r.nextFloat() * 6.28f,
                spin = (r.nextFloat() - 0.5f) * 120f, tint = r.nextInt(3),
            )
        }
    }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(season) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = it - start }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time / 1_000_000_000f
        parts.forEach { p ->
            val y = when (season) {
                Season.Summer -> ((p.y - t * p.speed) % 1f + 1f) % 1f // светлячки медленно поднимаются
                else -> (p.y + t * p.speed) % 1f
            }
            val x = (p.x + sin(t * 0.6f + p.phase) * p.sway + 1f) % 1f
            val c = Offset(x * size.width, y * size.height)
            when (season) {
                Season.Winter -> drawCircle(Color.White.copy(alpha = 0.55f), p.size, c)
                Season.Summer -> {
                    val glow = (0.35f + 0.35f * sin(t * 2f + p.phase)).coerceIn(0.05f, 0.7f)
                    drawCircle(Color(0xFFFFF59D).copy(alpha = glow * 0.35f), p.size * 4f, c)
                    drawCircle(Color(0xFFFFF59D).copy(alpha = glow), p.size, c)
                }
                Season.Spring, Season.Autumn -> {
                    val colors = if (season == Season.Spring) listOf(Color(0xFFF9A8D4), Color(0xFFFBCFE8), Color(0xFFFDE2EF))
                    else listOf(Color(0xFFF59E0B), Color(0xFFEA580C), Color(0xFFB45309))
                    rotate(degrees = t * p.spin + p.phase * 180f / PI.toFloat(), pivot = c) {
                        drawOval(colors[p.tint].copy(alpha = 0.75f), topLeft = Offset(c.x - p.size, c.y - p.size * 0.55f), size = Size(p.size * 2f, p.size * 1.1f))
                    }
                }
            }
        }
    }
}
