package com.apexvpn.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Главный экран «Карта» — та же точечная карта мира с дугой до сервера, что на Android. */
@Composable
fun FlatMapCard(connected: Boolean, busy: Boolean, serverCountry: String?, serverTitle: String, modifier: Modifier = Modifier) {
    val me = remember { userCountry() }
    val from = WorldMap.coords[me] ?: WorldMap.coords.getValue("ru")
    val to = serverCountry?.let { WorldMap.coords[it] }
    val live = C.animations

    val t = rememberInfiniteTransition(label = "map")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "breath")
    val runner by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (busy) 900 else 2600, easing = LinearEasing)), label = "runner")

    // 433. Самолётик при смене сервера: от старой страны к новой
    var lastTo by remember { androidx.compose.runtime.mutableStateOf(to) }
    var flyFrom by remember { androidx.compose.runtime.mutableStateOf<Pair<Float, Float>?>(null) }
    val fly = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(to) {
        val prev = lastTo
        lastTo = to
        if (prev != null && to != null && prev != to && C.animations) {
            flyFrom = prev
            fly.snapTo(0f)
            fly.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }
    }
    val dot = C.Dim
    val accent = C.Accent
    val accent2 = C.Cyan
    val ratio = WorldMap.W.toFloat() / WorldMap.H
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(C.Radius)).background(C.Surface).border(1.dp, C.Outline, RoundedCornerShape(C.Radius)).padding(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            // Слой точек можно размыть — «Размытие стекла» (Android 12+)
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(ratio),
            ) { drawDots(dot.copy(alpha = if (!C.Light) 0.32f else 0.28f)) }
            Canvas(Modifier.fillMaxWidth().aspectRatio(ratio)) {
                val a = project(from)
                val b = to?.let { project(it) }
                if (b != null && (a - b).getDistance() > 4f) {
                    val ctrl = Offset((a.x + b.x) / 2, minOf(a.y, b.y) - (a - b).getDistance() * 0.35f)
                    val path = Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
                    if (connected) {
                        drawPath(path, Brush.linearGradient(listOf(accent, accent2), a, b), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawPath(
                            path, dot.copy(alpha = 0.6f),
                            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                        )
                    }
                    if ((connected || busy) && live) {
                        // Бегущий по дуге свет
                        val p = bezier(a, ctrl, b, runner)
                        val r = 9.dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent2.copy(alpha = 0.5f), Color.Transparent), p, r), r, p)
                    }
                    pin(b, if (connected) accent2 else dot, rings = connected && live, phase = breath)
                }
                pin(a, accent, rings = live && (connected || busy), phase = (breath + 0.5f) % 1f)
                val ff = flyFrom
                val f = fly.value
                if (ff != null && b != null && f < 1f) {
                    val s0 = project(ff)
                    val ctrl2 = Offset((s0.x + b.x) / 2, minOf(s0.y, b.y) - (s0 - b).getDistance() * 0.45f)
                    var k = 0f
                    while (k < f) {
                        drawCircle(Color.White.copy(alpha = 0.5f), 1.3.dp.toPx(), bezier(s0, ctrl2, b, k))
                        k += 0.05f
                    }
                    val now = bezier(s0, ctrl2, b, f)
                    val next = bezier(s0, ctrl2, b, (f + 0.02f).coerceAtMost(1f))
                    val ang = Math.toDegrees(kotlin.math.atan2((next.y - now.y).toDouble(), (next.x - now.x).toDouble())).toFloat()
                    drawPlane(now, ang, 9.dp.toPx(), Color.White, accent2)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(countryName(me), color = C.Dim, fontSize = 13.sp)
            Text("  →  ", color = C.Dim, fontSize = 13.sp)
            Text(serverTitle, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun DrawScope.project(c: Pair<Float, Float>): Offset {
    val (lat, lon) = c
    return Offset(
        ((lon + 180f) / 360f) * size.width,
        ((WorldMap.N - lat) / (WorldMap.N - WorldMap.S)) * size.height,
    )
}

private fun bezier(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

private fun DrawScope.drawDots(color: Color) {
    val cw = size.width / WorldMap.W
    val ch = size.height / WorldMap.H
    val r = minOf(cw, ch) * 0.32f
    WorldMap.rows.forEachIndexed { j, row ->
        row.forEachIndexed { i, c ->
            if (c == '#') drawCircle(color, r, Offset((i + 0.5f) * cw, (j + 0.5f) * ch))
        }
    }
}

private fun DrawScope.pin(p: Offset, color: Color, rings: Boolean, phase: Float) {
    if (rings) {
        // Дышащие кольца
        for (k in 0..1) {
            val f = (phase + k * 0.5f) % 1f
            drawCircle(color.copy(alpha = (1f - f) * 0.55f), 4.dp.toPx() + f * 16.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
    }
    drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), p)
    drawCircle(color, 4.dp.toPx(), p)
    drawCircle(Color.White, 1.6.dp.toPx(), p)
}

/** Страна пользователя: по языку и региону Windows. */
fun userCountry(): String = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it in WorldMap.coords } ?: "ru"

fun countryName(code: String): String =
    Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale("ru")).ifBlank { code.uppercase(Locale.ROOT) }
