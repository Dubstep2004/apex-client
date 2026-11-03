package com.apexvpn.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.LinearOutSlowInEasing
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnStatus

/** Контур щита в координатах 100×100. */
private fun shieldPath(scale: Float) = Path().apply {
    moveTo(50f * scale, 6f * scale)
    lineTo(86f * scale, 20f * scale)
    lineTo(86f * scale, 48f * scale)
    cubicTo(86f * scale, 70f * scale, 70f * scale, 86f * scale, 50f * scale, 94f * scale)
    cubicTo(30f * scale, 86f * scale, 14f * scale, 70f * scale, 14f * scale, 48f * scale)
    lineTo(14f * scale, 20f * scale)
    close()
}

private fun checkPath(scale: Float) = Path().apply {
    moveTo(33f * scale, 50f * scale)
    lineTo(45f * scale, 62f * scale)
    lineTo(68f * scale, 38f * scale)
}

/**
 * Большая кнопка-щит. Выключено — пустой щит со значком питания,
 * подключение — бегущий контур, подключено — щит с градиентом и галочкой.
 */
@Composable
fun ShieldButton(status: VpnStatus, onClick: () -> Unit, diameter: androidx.compose.ui.unit.Dp = 240.dp) {
    val haptics = LocalHapticFeedback.current
    val active = status == VpnStatus.Started
    val paused = status == VpnStatus.Paused || status == VpnStatus.Blocked
    val busy = status == VpnStatus.Starting || status == VpnStatus.Stopping

    val transition = rememberInfiniteTransition(label = "shield")
    val glow by transition.animateFloat(
        0.85f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val runner by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "runner",
    )
    val feel = Apex.feel
    // 47. Android TV: на пульте видно, что щит выбран
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val check = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    // 147. «Тоннель» при подключении: вспышка, кольца и лучи наружу
    val burst = remember { Animatable(1f) }
    // 196. «Щит закрывается» при отключении: галочка стирается, щит сжимается, кольцо сходится к нему
    val close = remember { Animatable(1f) }
    var wasActive by remember { androidx.compose.runtime.mutableStateOf(active) }
    LaunchedEffect(active) {
        val animate = !feel.smooth
        if (active) {
            // Вибрация при подключении — в ConnectFeedback (работает и из плитки, и из виджета)
            if (animate && !wasActive) {
                burst.snapTo(0f)
                launch { burst.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
            }
            fill.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            check.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        } else {
            if (animate && wasActive) {
                close.snapTo(0f)
                launch { close.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                check.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                fill.animateTo(0f, tween(420))
            } else {
                check.snapTo(0f)
                fill.animateTo(0f, tween(300))
            }
        }
        wasActive = active
    }

    val surface = Apex.SurfaceHigh
    val dim = Apex.TextDim
    val accent = Apex.Accent

    Box(
        Modifier
            .size(diameter)
            // 429. 3D-щит: поворачивается вслед за наклоном телефона (чтение наклона — только при отрисовке)
            .graphicsLayer {
                if (feel.shield3d && !feel.smooth) {
                    rotationY = com.apexvpn.client.ui.theme.Tilt.x * 22f
                    rotationX = -com.apexvpn.client.ui.theme.Tilt.y * 16f
                    cameraDistance = 10f * density
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = status != VpnStatus.Stopping,
            ) {
                if (feel.vibration) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            val s = size.minDimension / 100f
            val shield = shieldPath(s)
            if (focused) {
                drawCircle(accent, radius = size.minDimension * 0.49f, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            }

            // Свечение
            if (active || busy || paused) {
                val r = size.minDimension * 0.48f * (if (active && !feel.smooth) glow else 1f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = if (active) 0.45f else 0.2f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
            }

            // 147. Тоннель: два кольца и лучи разлетаются от щита
            val b = burst.value
            if (b < 1f) {
                val fade = 1f - b
                for (k in 0..1) {
                    val p = (b * 1.2f - k * 0.2f).coerceIn(0f, 1f)
                    drawCircle(
                        Apex.Cyan.copy(alpha = fade * 0.7f),
                        radius = size.minDimension * (0.32f + p * 0.3f),
                        style = Stroke((3f - k).dp.toPx() * fade + 1f),
                    )
                }
                val rays = 14
                for (i in 0 until rays) {
                    val a = (i * 360f / rays) * (Math.PI / 180f).toFloat()
                    val r0 = size.minDimension * (0.30f + b * 0.12f)
                    val r1 = r0 + size.minDimension * 0.12f * fade
                    drawLine(
                        Color.White.copy(alpha = fade * 0.75f),
                        Offset(center.x + kotlin.math.cos(a) * r0, center.y + kotlin.math.sin(a) * r0),
                        Offset(center.x + kotlin.math.cos(a) * r1, center.y + kotlin.math.sin(a) * r1),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            // 196. Кольцо сходится к щиту при отключении
            val c = close.value
            if (c < 1f) {
                drawCircle(
                    accent.copy(alpha = (1f - c) * 0.6f),
                    radius = size.minDimension * (0.62f - c * 0.28f),
                    style = Stroke(2.5.dp.toPx()),
                )
            }
            val squeeze = if (c < 1f) 1f - 0.07f * kotlin.math.sin(c * Math.PI.toFloat()) else 1f
            val pop = if (b < 1f) 1f + 0.05f * kotlin.math.sin(b * Math.PI.toFloat()) else 1f

            // Тело щита
            scale(0.82f * squeeze * pop, center) {
                drawPath(shield, surface)
                if (fill.value > 0f) {
                    drawPath(
                        shield,
                        Brush.linearGradient(listOf(Apex.Violet, Apex.Cyan), Offset(0f, size.height), Offset(size.width, 0f)),
                        alpha = fill.value,
                    )
                }
                // Вспышка в начале «тоннеля»
                if (b < 0.35f) drawPath(shield, Color.White, alpha = (0.35f - b) * 1.6f)
                // Контур
                if (busy) {
                    drawRunner(shield, runner, accent)
                } else {
                    drawPath(
                        shield,
                        if (active) Color.White.copy(alpha = 0.35f) else if (paused) accent else Apex.Outline,
                        style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round),
                    )
                }

                // Значок: галочка при подключении, иначе «питание»
                if (active || check.value > 0f) {
                    val path = checkPath(s)
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * check.value, part, true)
                    drawPath(part, Color.White, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    // 3.13.5: в выключенном состоянии значок ярче — на тёмном стекле серый терялся
                    drawPower(s, if (paused) accent else androidx.compose.ui.graphics.lerp(dim, Apex.Text, 0.6f))
                }
            }
        }
    }
}

/** Бегущий отрезок по контуру щита. */
private fun DrawScope.drawRunner(shield: Path, progress: Float, color: Color) {
    drawPath(shield, color.copy(alpha = 0.18f), style = Stroke(width = 3.dp.toPx()))
    val measure = PathMeasure().apply { setPath(shield, true) }
    val len = measure.length
    val start = progress * len
    val seg = len * 0.28f
    val part = Path()
    if (start + seg <= len) {
        measure.getSegment(start, start + seg, part, true)
    } else {
        measure.getSegment(start, len, part, true)
        measure.getSegment(0f, start + seg - len, part, true)
    }
    drawPath(part, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
}

/** Значок питания в центре щита. */
private fun DrawScope.drawPower(s: Float, color: Color) {
    val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
    val c = Offset(50f * s, 52f * s)
    val r = 16f * s
    rotate(0f, c) {
        drawArc(
            color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
        )
    }
    drawLine(color, Offset(c.x, c.y - r - 4f * s), Offset(c.x, c.y - 2f * s), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
}
