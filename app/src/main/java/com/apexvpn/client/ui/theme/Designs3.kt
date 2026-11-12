package com.apexvpn.client.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.apexvpn.client.data.Design
import kotlin.random.Random

// ─── 3.19.7. Новые дизайны: Закат, Лес, Арктика, Мокко. Фоны статичные — не тратят батарею ───

private fun pal3(
    dark: Boolean, base: Color, surface: Color, text: Color, textDim: Color, accent: Color,
    brand1: Color, brand2: Color, radius: Int, backdrop: Backdrop, design: Design, border: Color,
) = Palette(
    dark = dark,
    bg = Color.Transparent,
    base = base,
    surface = surface,
    surfaceHigh = lerp(surface.copy(alpha = 1f), text, 0.08f),
    outline = lerp(base, text, 0.16f),
    divider = text.copy(alpha = if (dark) 0.09f else 0.08f),
    text = text,
    textDim = textDim,
    accent = accent,
    accentSoft = accent.copy(alpha = if (dark) 0.2f else 0.15f),
    accentText = lerp(accent, text, if (dark) 0.3f else 0.25f),
    selectedCard = lerp(base, accent, 0.12f),
    heroCard = lerp(surface.copy(alpha = 1f), accent, 0.1f).copy(alpha = surface.alpha),
    brand1 = brand1, brand2 = brand2,
    radius = radius.dp,
    cardBorder = border,
    backdrop = backdrop,
    design = design,
)

/** Закат — сливовое небо, тёплый горизонт и низкое солнце. */
internal fun sunsetPalette() = pal3(
    true, Color(0xFF1A0E26), Color(0xFF26142F).copy(alpha = 0.9f), Color(0xFFFFF1E8), Color(0xFFC9A3AE),
    Color(0xFFFF7A59), Color(0xFFFF7A59), Color(0xFFFFB547), 22, Backdrop.Sunset, Design.Sunset, Color(0xFFFF7A59).copy(alpha = 0.18f),
)

/** Лес — глубокая хвоя, туман и янтарный свет. */
internal fun forestPalette() = pal3(
    true, Color(0xFF08140F), Color(0xFF10231A).copy(alpha = 0.9f), Color(0xFFEAF5EC), Color(0xFF8FAF98),
    Color(0xFF5CC98A), Color(0xFF2F9E66), Color(0xFFE9B44C), 20, Backdrop.Forest, Design.Forest, Color.White.copy(alpha = 0.06f),
)

/** Арктика — светлый лёд, морозное стекло и холодный синий. */
internal fun arcticPalette() = pal3(
    false, Color(0xFFF1F6FB), Color.White.copy(alpha = 0.86f), Color(0xFF0F1E2E), Color(0xFF5B7085),
    Color(0xFF2F7BEF), Color(0xFF2F7BEF), Color(0xFF5FD0F5), 24, Backdrop.Arctic, Design.Arctic, Color(0xFFD3E2F0),
)

/** Мокко — тёплый кофе, сливки и карамель. */
internal fun mochaPalette() = pal3(
    true, Color(0xFF16100C), Color(0xFF221913).copy(alpha = 0.94f), Color(0xFFF6EBDD), Color(0xFFB09A85),
    Color(0xFFD9A066), Color(0xFFB9763F), Color(0xFFF0D2A8), 18, Backdrop.Mocha, Design.Mocha, Color(0xFFD9A066).copy(alpha = 0.16f),
)

/** Фоны новых дизайнов. Вызывается из [DesignBackground]. */
@Composable
internal fun NewBackdrop3(backdrop: Backdrop, modifier: Modifier, p: Palette) {
    Canvas(modifier.fillMaxSize()) {
        when (backdrop) {
            Backdrop.Sunset -> drawSunset(p)
            Backdrop.Forest -> drawForest(p)
            Backdrop.Arctic -> drawArctic(p)
            Backdrop.Mocha -> drawMocha(p)
            else -> drawRect(p.base)
        }
    }
}

private fun DrawScope.glow(c: Color, center: Offset, r: Float) =
    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, r), r, center)

private fun DrawScope.drawSunset(p: Palette) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF140A22), p.base, Color(0xFF4A1A3E), Color(0xFF9C3B3B)), startY = 0f, endY = h))
    // Солнце у горизонта и тёплое свечение
    val sun = Offset(w * 0.5f, h * 0.9f)
    glow(Color(0xFFFFB547).copy(alpha = 0.35f), sun, w * 0.9f)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFD08A), Color(0xFFFF7A59)), sun.y - w * 0.18f, sun.y + w * 0.18f), w * 0.18f, sun, alpha = 0.55f)
    // Полосы над солнцем, как на ретро-закате
    var y = sun.y - w * 0.04f; var gap = 4.dp.toPx()
    while (y < h) { drawRect(Color(0xFF4A1A3E).copy(alpha = 0.55f), Offset(0f, y), androidx.compose.ui.geometry.Size(w, gap)); y += gap * 3; gap *= 1.25f }
    // Редкие звёзды наверху
    val r = Random(7)
    repeat(40) { drawCircle(Color.White.copy(alpha = r.nextFloat() * 0.5f), r.nextFloat() * 1.6f + 0.4f, Offset(r.nextFloat() * w, r.nextFloat() * h * 0.45f)) }
}

private fun DrawScope.drawForest(p: Palette) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0E2219), p.base)))
    glow(Color(0xFFE9B44C).copy(alpha = 0.14f), Offset(w * 0.85f, h * 0.08f), w * 0.8f)
    glow(Color(0xFF5CC98A).copy(alpha = 0.10f), Offset(w * 0.1f, h * 0.4f), w * 0.7f)
    // Три ряда елей — дальние светлее (туман)
    val r = Random(11)
    listOf(0.78f to 0.10f, 0.86f to 0.16f, 0.95f to 0.28f).forEach { (line, a) ->
        val tree = Color(0xFF0B1F15).copy(alpha = 1f)
        var x = -20f
        while (x < w + 20f) {
            val th = h * (0.10f + r.nextFloat() * 0.08f); val tw = th * 0.42f
            val path = Path().apply { moveTo(x, h * line - th); lineTo(x - tw / 2, h * line); lineTo(x + tw / 2, h * line); close() }
            drawPath(path, lerp(tree, Color(0xFF8FAF98), a * 0.6f), alpha = 0.55f + a)
            x += tw * (0.55f + r.nextFloat() * 0.5f)
        }
        drawRect(lerp(tree, Color(0xFF8FAF98), a * 0.6f).copy(alpha = 0.55f + a), Offset(0f, h * line), androidx.compose.ui.geometry.Size(w, h * (1f - line)))
    }
}

private fun DrawScope.drawArctic(p: Palette) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), p.base, Color(0xFFDCEAF6))))
    glow(Color(0xFF5FD0F5).copy(alpha = 0.22f), Offset(w * 0.95f, h * 0.05f), w * 0.85f)
    glow(Color(0xFF2F7BEF).copy(alpha = 0.10f), Offset(0f, h * 0.7f), w * 0.9f)
    // Ледяные грани внизу
    val ice = Path().apply {
        moveTo(0f, h); lineTo(0f, h * 0.86f); lineTo(w * 0.22f, h * 0.8f); lineTo(w * 0.4f, h * 0.88f)
        lineTo(w * 0.63f, h * 0.78f); lineTo(w * 0.82f, h * 0.87f); lineTo(w, h * 0.82f); lineTo(w, h); close()
    }
    drawPath(ice, Brush.verticalGradient(listOf(Color(0xFFCFE3F3), Color(0xFFB7D3EA)), h * 0.78f, h), alpha = 0.7f)
    val r = Random(3)
    repeat(46) { drawCircle(Color(0xFF9CC3E4).copy(alpha = 0.25f + r.nextFloat() * 0.3f), r.nextFloat() * 2.2f + 0.8f, Offset(r.nextFloat() * w, r.nextFloat() * h * 0.75f)) }
}

private fun DrawScope.drawMocha(p: Palette) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF241810), p.base, p.base)))
    glow(Color(0xFFD9A066).copy(alpha = 0.16f), Offset(w * 0.2f, 0f), w)
    glow(Color(0xFFF0D2A8).copy(alpha = 0.06f), Offset(w * 0.9f, h * 0.75f), w * 0.8f)
    // Мягкие «завитки» пенки — тонкие дуги
    val cream = Color(0xFFF0D2A8).copy(alpha = 0.05f)
    for (i in 0 until 6) {
        val rr = w * (0.25f + i * 0.12f)
        drawCircle(cream, rr, Offset(w * 0.85f, h * 0.15f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
    }
}
