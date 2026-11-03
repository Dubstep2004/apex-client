package com.apexvpn.client.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.apexvpn.client.data.Design
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ─── 3.13. Новые дизайны: Карбон, Сакура, Матрица, Люкс, Океан и «Свой дизайн» ───

/** Настройки «Своего дизайна». */
data class CustomSpec(
    val base: Long = 0xFF0E0F14,
    val accent: Long = 0xFF7B6CFF,
    val accent2: Long = 0xFF22D3EE,
    val radius: Int = 24,
    val glass: Boolean = false,
    val border: Boolean = true,
    val glow: Boolean = false,
)

/** Настройки «Своего дизайна» из настроек приложения. */
fun com.apexvpn.client.data.Settings.customSpec() = CustomSpec(cdBase, cdAccent, cdAccent2, cdRadius, cdGlass, cdBorder, cdGlow)

private fun pal(
    dark: Boolean, base: Color, surface: Color, text: Color, textDim: Color, accent: Color,
    brand1: Color, brand2: Color, radius: Int, backdrop: Backdrop, design: Design,
    border: Color = Color.Transparent, glow: Boolean = false, mono: Boolean = false, serif: Boolean = false,
) = Palette(
    dark = dark,
    bg = if (backdrop == Backdrop.Solid) base else Color.Transparent,
    base = base,
    surface = surface,
    // Стекло (полупрозрачные карточки): приглушённые элементы тоже полупрозрачные, а не белые полосы
    surfaceHigh = if (surface.alpha < 0.5f) (if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f))
        else lerp(surface.copy(alpha = 1f), text, 0.08f),
    outline = lerp(base, text, 0.16f),
    divider = text.copy(alpha = if (dark) 0.09f else 0.08f),
    text = text,
    textDim = textDim,
    accent = accent,
    accentSoft = accent.copy(alpha = if (dark) 0.2f else 0.16f),
    accentText = lerp(accent, text, if (dark) 0.35f else 0.2f),
    selectedCard = lerp(base, accent, 0.12f),
    heroCard = if (surface.alpha < 0.5f) accent.copy(alpha = 0.10f) else lerp(surface.copy(alpha = 1f), accent, 0.1f).copy(alpha = surface.alpha),
    brand1 = brand1, brand2 = brand2,
    radius = radius.dp,
    cardBorder = border,
    glow = glow,
    backdrop = backdrop,
    design = design,
    mono = mono,
    serif = serif,
)

/** Карбон — тёмный графит с текстурой углеволокна, серебро и красный акцент. */
internal fun carbonPalette() = pal(
    true, Color(0xFF0D0E10), Color(0xFF17191C).copy(alpha = 0.92f), Color(0xFFEDEFF2), Color(0xFF8D939C),
    Color(0xFFE5383B), Color(0xFFB8BEC6), Color(0xFFE5383B), 14, Backdrop.Carbon, Design.Carbon, border = Color.White.copy(alpha = 0.07f),
)

/** Сакура — светлая розовая бумага, падающие лепестки. */
internal fun sakuraPalette() = pal(
    false, Color(0xFFFFF4F6), Color.White.copy(alpha = 0.82f), Color(0xFF2B1B22), Color(0xFF8A6A76),
    Color(0xFFE0457B), Color(0xFFF7A1C0), Color(0xFFE0457B), 26, Backdrop.Sakura, Design.Sakura, border = Color(0xFFF6D3DE),
)

/** Матрица — чёрный экран и зелёный «дождь» символов. */
internal fun matrixPalette() = pal(
    true, Color.Black, Color(0xFF020A04).copy(alpha = 0.86f), Color(0xFFB8FFC6), Color(0xFF4FA866),
    Color(0xFF00FF66), Color(0xFF00C853), Color(0xFF69F0AE), 8, Backdrop.Matrix, Design.Matrix,
    border = Color(0xFF00FF66).copy(alpha = 0.3f), glow = true, mono = true,
)

/** Люкс — чёрный бархат и золото. */
internal fun luxePalette() = pal(
    true, Color(0xFF07070A), Color(0xFF111015).copy(alpha = 0.94f), Color(0xFFF6EFD9), Color(0xFFA79D84),
    Color(0xFFD4AF37), Color(0xFFB8860B), Color(0xFFF3D98B), 20, Backdrop.Luxe, Design.Luxe,
    border = Color(0xFFD4AF37).copy(alpha = 0.35f), serif = true,
)

/** Океан — глубокая синева и медленные волны. */
internal fun oceanPalette() = pal(
    true, Color(0xFF031526), Color(0xFF0A2238).copy(alpha = 0.8f), Color(0xFFE6F4FF), Color(0xFF86A8C4),
    Color(0xFF2EC4F1), Color(0xFF1E6FD9), Color(0xFF2EE6C9), 24, Backdrop.Ocean, Design.Ocean,
    border = Color.White.copy(alpha = 0.06f),
)

/** «Свой дизайн»: фон, два цвета, скругление, стекло, рамка и свечение — как выберете. */
internal fun customPalette(c: CustomSpec): Palette {
    val base = Color(c.base)
    val dark = base.luminance() < 0.5f
    val text = if (dark) Color(0xFFF4F4F7) else Color(0xFF15151A)
    val surface = if (c.glass) (if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.6f))
    else lerp(base, text, if (dark) 0.07f else 0.04f).let { if (!dark) lerp(it, Color.White, 0.6f) else it }
    val accent = Color(c.accent)
    return pal(
        dark, base, surface, text, lerp(text, base, 0.45f), accent, accent, Color(c.accent2), c.radius.coerceIn(0, 36),
        if (c.glass) Backdrop.Custom else Backdrop.Solid, Design.Custom,
        border = if (c.border) accent.copy(alpha = if (c.glow) 0.5f else 0.22f) else Color.Transparent,
        glow = c.glow,
    )
}

/** Фоны новых дизайнов. Вызывается из [DesignBackground]. */
@Composable
internal fun NewBackdrop(backdrop: Backdrop, modifier: Modifier, p: Palette) {
    when (backdrop) {
        Backdrop.Carbon -> Canvas(modifier.fillMaxSize()) { drawCarbon(p) }
        Backdrop.Luxe -> Canvas(modifier.fillMaxSize()) { drawLuxe(p) }
        Backdrop.Custom -> Canvas(modifier.fillMaxSize()) { drawCustomGlass(p) }
        Backdrop.Sakura -> Box(modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF6F8), Color(0xFFFDE4EC))))
                drawCircle(Color(0xFFF7A1C0).copy(alpha = 0.25f), size.width * 0.6f, Offset(size.width, 0f))
            }
            if (!Apex.feel.smooth) SeasonalOverlay(Season.Spring)
        }
        Backdrop.Matrix -> MatrixRain(modifier, p)
        Backdrop.Ocean -> OceanWaves(modifier, p)
        else -> Canvas(modifier.fillMaxSize()) { drawRect(p.base) }
    }
}

private fun DrawScope.drawCarbon(p: Palette) {
    drawRect(p.base)
    // Плетение углеволокна: шахматка из полосок с бликом
    val cell = 14.dp.toPx()
    val dark = Color(0xFF121316); val light = Color(0xFF1B1D21)
    var y = 0f; var row = 0
    while (y < size.height) {
        var x = 0f; var col = 0
        while (x < size.width) {
            val vertical = (row + col) % 2 == 0
            drawRect(
                Brush.linearGradient(listOf(light, dark), start = Offset(x, y), end = if (vertical) Offset(x + cell, y) else Offset(x, y + cell)),
                Offset(x, y), Size(cell, cell),
            )
            x += cell; col++
        }
        y += cell; row++
    }
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.15f), size.width))
}

private fun DrawScope.drawLuxe(p: Palette) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF15120A), p.base, p.base)))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFD4AF37).copy(alpha = 0.18f), Color.Transparent), Offset(size.width * 0.5f, 0f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.5f, 0f))
    // Тонкие золотые линии ар-деко
    val gold = Color(0xFFD4AF37).copy(alpha = 0.12f)
    val step = 46.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(gold, Offset(x, size.height), Offset(x + size.height * 0.5f, 0f), 1f)
        x += step
    }
}

private fun DrawScope.drawCustomGlass(p: Palette) {
    drawRect(p.base)
    // Мягкие размытые пятна (без жёстких краёв): радиальный градиент к прозрачному
    fun blob(c: Color, center: Offset, r: Float) =
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.32f), c.copy(alpha = 0.12f), Color.Transparent), center, r), r, center)
    blob(p.brand1, Offset(size.width * 0.1f, size.height * 0.12f), size.width * 0.9f)
    blob(p.brand2, Offset(size.width * 0.95f, size.height * 0.62f), size.width * 0.85f)
}

@Composable
private fun MatrixRain(modifier: Modifier, p: Palette) {
    val t = if (Apex.feel.smooth) 0f else {
        val tr = rememberInfiniteTransition(label = "matrix")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "rain").value
    }
    val cols = remember {
        val r = Random(7)
        List(28) { Triple(r.nextFloat(), 0.4f + r.nextFloat() * 0.8f, r.nextFloat()) } // x, скорость, сдвиг
    }
    val paint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
    }
    val glyphs = "アイウエオカキクケコサシスセソ0123456789APEX"
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color.Black)
        val step = 16.dp.toPx()
        paint.textSize = 13.dp.toPx()
        val canvas = drawContext.canvas.nativeCanvas
        cols.forEachIndexed { i, (cx, speed, off) ->
            val x = cx * size.width
            val head = ((t * speed * 3f + off) % 1f) * (size.height + step * 14)
            for (k in 0 until 14) {
                val y = head - k * step
                if (y < 0 || y > size.height) continue
                val a = if (k == 0) 1f else (1f - k / 14f) * 0.55f
                paint.color = (if (k == 0) Color(0xFFCCFFDD) else Color(0xFF00FF66)).copy(alpha = a * 0.7f).toArgbCompat()
                canvas.drawText(glyphs[(i * 7 + k + (t * 40).toInt()) % glyphs.length].toString(), x, y, paint)
            }
        }
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun OceanWaves(modifier: Modifier, p: Palette) {
    val t = if (Apex.feel.smooth) 0f else {
        val tr = rememberInfiniteTransition(label = "ocean")
        tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "waves").value
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B3A5C), p.base, Color(0xFF020C16))))
        listOf(Triple(0.72f, 0.10f, Color(0xFF1E6FD9)), Triple(0.80f, 0.08f, Color(0xFF2EC4F1)), Triple(0.88f, 0.06f, Color(0xFF2EE6C9))).forEachIndexed { i, (level, alpha, c) ->
            val path = Path()
            val baseY = size.height * level
            val amp = 18.dp.toPx() - i * 4.dp.toPx()
            path.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) {
                path.lineTo(x, baseY + sin(x / size.width * 4 * PI.toFloat() + t + i) * amp)
                x += 8f
            }
            path.lineTo(size.width, size.height); path.close()
            drawPath(path, c.copy(alpha = alpha * 2.2f))
        }
    }
}
