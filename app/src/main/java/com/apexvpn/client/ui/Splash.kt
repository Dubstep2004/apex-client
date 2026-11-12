package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 217. Заставка 2.0: щит APEX сам рисуется светящейся линией, заливается градиентом,
 * появляется галочка и волна света, затем по буквам — «APEX CLIENT» и метка «2.0».
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val smooth = Apex.feel.smooth
    val outline = remember { Animatable(if (smooth) 1f else 0f) }
    val fill = remember { Animatable(if (smooth) 1f else 0f) }
    val check = remember { Animatable(if (smooth) 1f else 0f) }
    val wave = remember { Animatable(if (smooth) 1f else 0f) }
    val pop = remember { Animatable(if (smooth) 1f else 0.85f) }
    val letters = remember { Animatable(if (smooth) 1f else 0f) }
    val badge = remember { Animatable(if (smooth) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (smooth) {
            delay(500)
            onFinished()
            return@LaunchedEffect
        }
        outline.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        launch { fill.animateTo(1f, tween(320)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        launch { wave.animateTo(1f, tween(700, easing = LinearOutSlowInEasing)) }
        check.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        launch { letters.animateTo(1f, tween(420)) }
        delay(260)
        badge.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        delay(320)
        onFinished()
    }

    val c1 = Apex.Violet
    val c2 = Apex.Cyan
    Box(Modifier.fillMaxSize().background(Apex.Base), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(220.dp).scale(pop.value)) {
                val s = size.minDimension / 100f
                val shield = Path().apply {
                    moveTo(50f * s, 14f * s)
                    lineTo(78f * s, 25f * s)
                    lineTo(78f * s, 49f * s)
                    cubicTo(78f * s, 66f * s, 66f * s, 79f * s, 50f * s, 86f * s)
                    cubicTo(34f * s, 79f * s, 22f * s, 66f * s, 22f * s, 49f * s)
                    lineTo(22f * s, 25f * s)
                    close()
                }
                // Волна света от щита
                val w = wave.value
                if (w in 0.01f..0.99f) {
                    drawCircle(c2.copy(alpha = (1f - w) * 0.55f), radius = size.minDimension * (0.3f + w * 0.2f), style = Stroke(3.dp.toPx() * (1f - w) + 1f))
                }
                // Мягкое свечение
                drawCircle(
                    Brush.radialGradient(listOf(c1.copy(alpha = 0.38f * fill.value), Color.Transparent), center, size.minDimension / 2),
                    radius = size.minDimension / 2,
                )
                if (fill.value > 0f) {
                    drawPath(shield, Brush.linearGradient(listOf(c1, c2), Offset(0f, size.height), Offset(size.width, 0f)), alpha = fill.value)
                }
                // Контур рисуется линией
                val measure = PathMeasure().apply { setPath(shield, true) }
                val part = Path()
                measure.getSegment(0f, measure.length * outline.value, part, true)
                drawPath(part, if (fill.value > 0.5f) Color.White.copy(alpha = 0.4f) else c2, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Галочка
                if (check.value > 0f) {
                    val tick = Path().apply {
                        moveTo(38f * s, 50f * s)
                        lineTo(47f * s, 59f * s)
                        lineTo(63f * s, 41f * s)
                    }
                    val m = PathMeasure().apply { setPath(tick, false) }
                    val t = Path()
                    m.getSegment(0f, m.length * check.value, t, true)
                    drawPath(t, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                "APEX CLIENT".forEachIndexed { i, ch ->
                    // Буквы появляются по очереди
                    val p = ((letters.value * 8f) - i * 0.7f).coerceIn(0f, 1f)
                    Text(
                        ch.toString(),
                        color = if (i >= 5) c2 else Apex.Text,
                        fontSize = 30.sp,
                        fontWeight = if (i >= 5) FontWeight.Medium else FontWeight.Black,
                        letterSpacing = 3.sp,
                        modifier = Modifier.alpha(p).offset(y = ((1f - p) * 10f).dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    com.apexvpn.client.BuildConfig.VERSION_NAME.split('.').take(2).joinToString("."),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .scale(badge.value)
                        .alpha(badge.value.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(c1, c2)))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
