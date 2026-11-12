package com.apexvpn.client.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Фон экранов для выбранного дизайна: стеклянные пятна, северное сияние,
 * неоновая сетка, строки терминала или пастельный градиент.
 */
@Composable
fun DesignBackground(modifier: Modifier = Modifier) {
    val p = Apex.palette
    // Размытие стекла (Android 12+): пятна фона становятся мягче
    val blurred = Apex.feel.blur && android.os.Build.VERSION.SDK_INT >= 31 &&
        p.backdrop in setOf(Backdrop.Glass, Backdrop.Aurora, Backdrop.Pastel, Backdrop.Neon)
    val modifier = if (blurred) modifier.blur(36.dp) else modifier
    when (p.backdrop) {
        Backdrop.Solid -> Canvas(modifier.fillMaxSize()) { drawRect(p.base) }
        Backdrop.Glass -> Canvas(modifier.fillMaxSize()) { drawGlass(p) }
        Backdrop.Aurora -> if (Apex.feel.smooth) Canvas(modifier.fillMaxSize()) { drawAurora(p, 0f) } else AuroraBackground(modifier, p)
        Backdrop.Neon -> Canvas(modifier.fillMaxSize()) { drawNeon(p) }
        Backdrop.Terminal -> Canvas(modifier.fillMaxSize()) { drawTerminal(p) }
        Backdrop.Pastel -> Canvas(modifier.fillMaxSize()) { drawPastel(p) }
        Backdrop.Apex2 -> Canvas(modifier.fillMaxSize()) { drawApex2(p) }
        Backdrop.Steampunk -> SteampunkBackground(modifier, p)
        Backdrop.Synthwave -> SynthwaveBackground(modifier, p)
        Backdrop.Cosmos -> CosmosBackground(modifier, p)
        Backdrop.Japan -> Canvas(modifier.fillMaxSize()) { drawJapan(p) }
        Backdrop.Carbon, Backdrop.Sakura, Backdrop.Matrix, Backdrop.Luxe, Backdrop.Ocean, Backdrop.Custom -> NewBackdrop(p.backdrop, modifier, p)
        Backdrop.Sunset, Backdrop.Forest, Backdrop.Arctic, Backdrop.Mocha -> NewBackdrop3(p.backdrop, modifier, p)
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

private fun DrawScope.drawGlass(p: Palette) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.55f else 0.45f
    // 313. Пятна за стеклом чуть смещаются при наклоне — объём
    val px = Tilt.x * w * 0.06f
    val py = Tilt.y * h * 0.03f
    blob(p.accent.copy(alpha = a), Offset(w * 0.1f - px, h * 0.12f - py), w * 0.75f)
    blob(Color(0xFF22D3EE).copy(alpha = a * 0.8f), Offset(w * 0.95f + px, h * 0.38f), w * 0.7f)
    blob(Color(0xFFF472B6).copy(alpha = a * 0.7f), Offset(w * 0.25f - px * 0.5f, h * 0.85f + py), w * 0.8f)
}

@Composable
private fun AuroraBackground(modifier: Modifier, p: Palette) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.fillMaxSize()) { drawAurora(p, phase) }
}

private fun DrawScope.drawAurora(p: Palette, phase: Float) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.6f else 0.45f
    val colors = listOf(Color(0xFF34D399), Color(0xFF818CF8), Color(0xFFF472B6), Color(0xFF22D3EE))
    colors.forEachIndexed { i, c ->
        val ph = phase + i * 1.6f
        val center = Offset(
            w * (0.5f + 0.38f * cos(ph)),
            h * (0.25f + 0.18f * sin(ph * 0.7f) + i * 0.17f),
        )
        blob(c.copy(alpha = a), center, w * 0.7f)
    }
}

private fun DrawScope.drawNeon(p: Palette) {
    drawRect(p.base)
    val step = 32.dp.toPx()
    val line = p.brand2.copy(alpha = 0.06f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    blob(p.brand1.copy(alpha = 0.28f), Offset(size.width * 0.1f, 0f), size.width * 0.8f)
    blob(p.brand2.copy(alpha = 0.22f), Offset(size.width, size.height), size.width * 0.9f)
}

private fun DrawScope.drawTerminal(p: Palette) {
    drawRect(p.base)
    // Строки развёртки старого монитора
    val step = 3.dp.toPx()
    val line = p.accent.copy(alpha = 0.035f)
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    blob(p.accent.copy(alpha = 0.08f), Offset(size.width / 2, size.height / 3), size.width)
}

/** 212. APEX 2.0: мягкое свечение акцента сверху и бирюзы снизу. */
private fun DrawScope.drawApex2(p: Palette) {
    drawRect(p.base)
    val w = size.width
    val h = size.height
    val a = if (p.dark) 0.30f else 0.18f
    blob(p.brand1.copy(alpha = a), Offset(w * 0.5f, -h * 0.05f), w * 1.0f)
    blob(p.brand2.copy(alpha = a * 0.55f), Offset(w * 1.05f, h * 0.95f), w * 0.85f)
}

private fun DrawScope.drawPastel(p: Palette) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1E8), Color(0xFFF6EEFF), Color(0xFFEFF7FF))))
    blob(p.brand1.copy(alpha = 0.35f), Offset(size.width * 0.9f, size.height * 0.1f), size.width * 0.7f)
    blob(p.brand2.copy(alpha = 0.30f), Offset(0f, size.height * 0.6f), size.width * 0.8f)
}

// ─── 351. Стимпанк: медленно вращающиеся латунные шестерёнки ───────────

@Composable
private fun SteampunkBackground(modifier: Modifier, p: Palette) {
    val angle = if (Apex.feel.smooth) 0f else {
        val t = rememberInfiniteTransition(label = "gears")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "gearAngle").value
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1E140B), p.base, Color(0xFF0E0905))))
        val w = size.width
        val h = size.height
        val brass = Color(0xFFD4A24C)
        val copper = Color(0xFFB8732E)
        // Пары сцепленных шестерёнок крутятся в разные стороны
        gear(Offset(w * 0.86f, h * 0.10f), w * 0.30f, 16, angle, brass.copy(alpha = 0.10f))
        gear(Offset(w * 0.40f, h * 0.06f), w * 0.17f, 10, -angle * 1.7f + 9f, copper.copy(alpha = 0.10f))
        gear(Offset(w * 0.06f, h * 0.62f), w * 0.26f, 14, -angle * 1.1f, brass.copy(alpha = 0.08f))
        gear(Offset(w * 0.95f, h * 0.88f), w * 0.22f, 12, angle * 1.3f, copper.copy(alpha = 0.09f))
        gear(Offset(w * 0.62f, h * 0.97f), w * 0.13f, 8, -angle * 2.1f, brass.copy(alpha = 0.08f))
        // Тёплая лампа сверху и виньетка
        blob(Color(0xFFFFB45A).copy(alpha = 0.10f), Offset(w * 0.5f, 0f), w * 0.9f)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(w / 2, h / 2), maxOf(w, h) * 0.75f))
    }
}

private fun DrawScope.gear(c: Offset, r: Float, teeth: Int, angleDeg: Float, color: Color) {
    val path = androidx.compose.ui.graphics.Path()
    val inner = r * 0.84f
    val steps = teeth * 4
    for (i in 0..steps) {
        val a = Math.toRadians((angleDeg + i * 360.0 / steps)).toFloat()
        val rr = if ((i % 4) == 1 || (i % 4) == 2) r else inner
        val pt = Offset(c.x + rr * cos(a), c.y + rr * sin(a))
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.05f)
    drawPath(path, color, style = stroke)
    drawCircle(color, r * 0.62f, c, style = stroke)
    drawCircle(color, r * 0.16f, c, style = stroke)
    for (k in 0 until 5) {
        val a = Math.toRadians((angleDeg + k * 72.0)).toFloat()
        drawLine(color, Offset(c.x + r * 0.16f * cos(a), c.y + r * 0.16f * sin(a)), Offset(c.x + r * 0.62f * cos(a), c.y + r * 0.62f * sin(a)), r * 0.045f)
    }
}

// ─── 387. Синтвейв: неоновое солнце и бегущая сетка ────────────────────

@Composable
private fun SynthwaveBackground(modifier: Modifier, p: Palette) {
    val scroll = if (Apex.feel.smooth) 0f else {
        val t = rememberInfiniteTransition(label = "grid")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "gridScroll").value
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f
        drawRect(Brush.verticalGradient(0f to Color(0xFF0B0020), 0.5f to Color(0xFF3A0A5E), 1f to Color(0xFFB0186E), endY = horizon), size = androidx.compose.ui.geometry.Size(w, horizon))
        // Солнце с полосами
        val sunC = Offset(w / 2, horizon - w * 0.02f)
        val sunR = w * 0.30f
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFF7A3D), Color(0xFFFF2E97)), sunC.y - sunR, sunC.y), sunR, sunC)
        var y = sunC.y - sunR * 0.35f
        var gap = sunR * 0.035f
        while (y < sunC.y) {
            drawRect(Color(0xFF8A1068), Offset(sunC.x - sunR, y), androidx.compose.ui.geometry.Size(sunR * 2, gap))
            y += gap * 3.2f
            gap *= 1.25f
        }
        // Пол с сеткой
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFF07000F)), horizon, h), Offset(0f, horizon), androidx.compose.ui.geometry.Size(w, h - horizon))
        val line = p.brand1.copy(alpha = 0.55f)
        for (k in -10..10) {
            val xBottom = w / 2 + k * w * 0.18f
            drawLine(line, Offset(w / 2 + k * w * 0.02f, horizon), Offset(xBottom, h), 1.5f)
        }
        for (k in 0 until 12) {
            val f = ((k + scroll) / 12f)
            val yy = horizon + (h - horizon) * f * f
            drawLine(line.copy(alpha = 0.55f * f + 0.1f), Offset(0f, yy), Offset(w, yy), 1.5f)
        }
        drawLine(Color(0xFFFF8AC6), Offset(0f, horizon), Offset(w, horizon), 2f)
    }
}

// ─── 430. Космос: мерцающие звёзды и туманность ────────────────────────

private val stars: List<FloatArray> by lazy {
    val r = java.util.Random(42)
    List(170) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.6f, r.nextFloat() * 6.28f) }
}

@Composable
private fun CosmosBackground(modifier: Modifier, p: Palette) {
    val phase = if (Apex.feel.smooth) 0f else {
        val t = rememberInfiniteTransition(label = "stars")
        t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "twinkle").value
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(p.base)
        val w = size.width
        val h = size.height
        blob(Color(0xFF3B2A9E).copy(alpha = 0.35f), Offset(w * 0.15f, h * 0.2f), w * 0.8f)
        blob(Color(0xFFB04A8F).copy(alpha = 0.18f), Offset(w * 0.9f, h * 0.55f), w * 0.7f)
        blob(Color(0xFF1E6FA8).copy(alpha = 0.2f), Offset(w * 0.4f, h * 0.95f), w * 0.8f)
        for (st in stars) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(phase + st[3]))
            drawCircle(Color.White.copy(alpha = a * 0.8f), st[2] * density * 0.6f, Offset(st[0] * w, st[1] * h))
        }
    }
}

// ─── 431. Японский минимализм: бумага, тушь и солнце ───────────────────

private val fibers: List<FloatArray> by lazy {
    val r = java.util.Random(7)
    List(260) { floatArrayOf(r.nextFloat(), r.nextFloat(), (r.nextFloat() - 0.5f) * 0.6f, 6f + r.nextFloat() * 18f) }
}

private fun DrawScope.drawJapan(p: Palette) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF7F2E8), p.base, Color(0xFFEFE8DA))))
    val w = size.width
    val h = size.height
    // Волокна рисовой бумаги
    for (f in fibers) {
        val x = f[0] * w
        val y = f[1] * h
        val len = f[3] * density
        drawLine(Color(0xFF8A7B60).copy(alpha = 0.07f), Offset(x, y), Offset(x + len * cos(f[2]), y + len * sin(f[2])), 1f)
    }
    // Красное солнце, наполовину за краем
    drawCircle(p.accent.copy(alpha = 0.10f), w * 0.42f, Offset(w * 1.02f, h * 0.12f))
    // Мазок тушью снизу
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(-20f, h * 0.9f)
        cubicTo(w * 0.3f, h * 0.84f, w * 0.6f, h * 0.95f, w + 20f, h * 0.87f)
    }
    drawPath(path, Color(0xFF1B1B1B).copy(alpha = 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 26f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}
