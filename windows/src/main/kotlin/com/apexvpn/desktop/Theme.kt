package com.apexvpn.desktop

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Цвета дизайна. accent/accent2 — фирменные, но их можно заменить своим цветом. */
data class Palette(
    val base: Color, val surface: Color, val surfaceHigh: Color, val outline: Color,
    val text: Color, val dim: Color, val accent: Color, val accent2: Color, val light: Boolean = false,
)

/** Дизайны Windows 1.3 — те же, что на Android. */
enum class Design(val id: String, val title: String, val subtitle: String, val palette: Palette, val deco: String) {
    Fluent("fluent", "Windows 11", "Нейтральный, как сама Windows: ровный фон и голубой акцент",
        Palette(Color(0xFF1C1C1C), Color(0xFF2B2B2B), Color(0xFF353535), Color(0x1AFFFFFF), Color(0xFFFFFFFF), Color(0xFFC5C5C5), Color(0xFF4CC2FF), Color(0xFF0091F8)), "mica"),
    FluentLight("fluent_light", "Windows 11 светлый", "Светлая тема Windows",
        Palette(Color(0xFFF3F3F3), Color(0xFFFBFBFB), Color(0xFFF0F0F0), Color(0x14000000), Color(0xFF1B1B1B), Color(0xFF5F5F5F), Color(0xFF005FB8), Color(0xFF0091F8), light = true), "mica"),
    Apex("apex", "APEX", "Фирменный: мягкое свечение и плавающие пятна",
        Palette(Color(0xFF08080E), Color(0xFF13131C), Color(0xFF1C1C28), Color(0x14FFFFFF), Color(0xFFF4F4F7), Color(0xFF8E8E9A), Color(0xFF7B6CFF), Color(0xFF4FA8F2)), "glow"),
    Classic("classic", "Классика", "Строгий тёмный, без украшений",
        Palette(Color(0xFF0B0B0F), Color(0xFF16161C), Color(0xFF202028), Color(0x14FFFFFF), Color(0xFFF2F2F5), Color(0xFF8A8A96), Color(0xFF3B82F6), Color(0xFF60A5FA)), "none"),
    Cosmos("cosmos", "Космос", "Звёздное небо и туманность",
        Palette(Color(0xFF05040F), Color(0xCC12102A), Color(0xFF1E1A3E), Color(0x22B4A8FF), Color(0xFFF1EEFF), Color(0xFF9C95C9), Color(0xFF8B5CF6), Color(0xFFEC4899)), "stars"),
    Aurora("aurora", "Аврора", "Северное сияние переливается за окном",
        Palette(Color(0xFF040B12), Color(0xCC0E1A24), Color(0xFF16283A), Color(0x2234D399), Color(0xFFEAFBF4), Color(0xFF8FB3A8), Color(0xFF34D399), Color(0xFF818CF8)), "aurora"),
    Neon("neon", "Неон", "Киберпанк: сетка и розово-голубое свечение",
        Palette(Color(0xFF07020F), Color(0xCC140A24), Color(0xFF221238), Color(0x33FF2BD6), Color(0xFFFDF4FF), Color(0xFFB79AD0), Color(0xFFFF2BD6), Color(0xFF00F0FF)), "grid"),
    Steampunk("steampunk", "Стимпанк", "Медь, латунь и вращающиеся шестерёнки",
        Palette(Color(0xFF140D07), Color(0xCC24170C), Color(0xFF332212), Color(0x33D4A24C), Color(0xFFF7E7C8), Color(0xFFBFA27A), Color(0xFFD4A24C), Color(0xFFB8732E)), "gears"),
    Terminal("terminal", "Терминал", "Зелёный текст на чёрном, как в консоли",
        Palette(Color(0xFF020602), Color(0xFF071207), Color(0xFF0D1E0D), Color(0x3339FF6A), Color(0xFFB8FFC8), Color(0xFF4FA864), Color(0xFF39FF6A), Color(0xFF22C55E)), "scan"),
    Japan("japan", "Японский минимализм", "Светлый: бумага, красное солнце, тушь",
        Palette(Color(0xFFF6F1E7), Color(0xFFFFFCF5), Color(0xFFEDE5D6), Color(0x1A1B1B1B), Color(0xFF1B1B1B), Color(0xFF7A7166), Color(0xFFC8102E), Color(0xFF1B1B1B), light = true), "sun"),
    Pastel("pastel", "Пастель", "Светлый и мягкий: персик, лаванда, небо",
        Palette(Color(0xFFF6F0FF), Color(0xFFFFFFFF), Color(0xFFEFE8FB), Color(0x1A5B4B8A), Color(0xFF2A2340), Color(0xFF7D7596), Color(0xFF8B5CF6), Color(0xFFF472B6), light = true), "glow"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Fluent
    }
}

/** Свой цвет акцента поверх дизайна. */
enum class AccentChoice(val id: String, val title: String, val c1: Color?, val c2: Color?) {
    Design("design", "Как в дизайне", null, null),
    Violet("violet", "Фиолетовый", Color(0xFF7B6CFF), Color(0xFF4FA8F2)),
    Blue("blue", "Синий", Color(0xFF3B82F6), Color(0xFF22D3EE)),
    Mint("mint", "Мята", Color(0xFF10B981), Color(0xFF6EE7B7)),
    Sunset("sunset", "Закат", Color(0xFFFF6B4A), Color(0xFFFFB347)),
    Rose("rose", "Роза", Color(0xFFF43F5E), Color(0xFFF472B6)),
    Gold("gold", "Золото", Color(0xFFEAB308), Color(0xFFF59E0B)),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Design
    }
}

/** Текущие цвета: читаются в Compose, поэтому смена дизайна сразу перекрашивает всё. */
object C {
    var palette by mutableStateOf(Design.Apex.palette)
    var design by mutableStateOf(Design.Apex)
    var animations by mutableStateOf(true)
    /** Вид Windows 11: меньше скругления, мелкие значки, текст как в «Параметрах» Windows */
    var fluent by mutableStateOf(true)

    fun apply(s: Settings) {
        design = s.design
        val p = s.design.palette
        val a = s.accent
        palette = if (a.c1 != null && a.c2 != null) p.copy(accent = a.c1, accent2 = a.c2) else p
        animations = s.animations
        fluent = s.layout == "windows"
    }

    val Base get() = palette.base
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Outline get() = palette.outline
    val Text get() = palette.text
    val Dim get() = palette.dim
    val Accent get() = palette.accent
    val Cyan get() = palette.accent2
    val Light get() = palette.light
    val AccentSoft get() = palette.accent.copy(alpha = if (palette.light) 0.14f else 0.20f)
    val AccentText get() = if (palette.light) palette.accent else palette.accent.copy(red = (palette.accent.red + 0.25f).coerceAtMost(1f), green = (palette.accent.green + 0.25f).coerceAtMost(1f), blue = (palette.accent.blue + 0.15f).coerceAtMost(1f))
    val Radius get() = when {
        fluent -> 8.dp
        design == Design.Classic || design == Design.Terminal -> 18.dp
        else -> 26.dp
    }
    val Good = Color(0xFF22C55E)
    val Warn = Color(0xFFE5A23A)
    val Bad = Color(0xFFEF4444)
}

/** Живой фон окна: у каждого дизайна свой (свечение, звёзды, сияние, сетка, шестерёнки…). */
@Composable
fun Backdrop(connected: Boolean) {
    val d = C.design
    val p = C.palette
    val anim = C.animations
    val t = rememberInfiniteTransition()
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (connected) 9000 else 16000, easing = LinearEasing)))
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse))
    val ph = if (anim) phase else 0.2f
    val pu = if (anim) pulse else 0.5f
    val stars = remember { List(140) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(p.base)
        val w = size.width; val h = size.height
        val a1 = p.accent; val a2 = p.accent2
        val glow = if (connected) 0.30f else 0.16f
        when (d.deco) {
            // Windows 11: почти ровный фон с лёгким оттенком акцента (как Mica)
            "mica" -> {
                drawRect(Brush.linearGradient(listOf(a1.copy(alpha = if (p.light) 0.05f else 0.07f), Color.Transparent, a2.copy(alpha = if (p.light) 0.04f else 0.05f)), Offset(0f, 0f), Offset(w, h)))
                if (connected) blob(Offset(w * 0.2f, h * 0.15f), w * 0.4f, a1, 0.06f + 0.02f * pu)
            }
            "glow" -> {
                blob(Offset(w * (0.15f + 0.08f * sin(2 * PI * ph).toFloat()), h * 0.05f), w * 0.55f, a1, glow + 0.06f * pu)
                blob(Offset(w * 0.95f, h * (0.85f + 0.06f * cos(2 * PI * ph).toFloat())), w * 0.45f, a2, glow * 0.8f)
            }
            "stars" -> {
                blob(Offset(w * 0.25f, h * 0.3f), w * 0.5f, a1, glow)
                blob(Offset(w * 0.8f, h * 0.75f), w * 0.4f, a2, glow * 0.7f)
                stars.forEach { (x, y, k) ->
                    val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(2 * PI * (ph * 3 + k)).toFloat())
                    drawCircle(Color.White.copy(alpha = tw * 0.8f), 0.6f + k * 1.4f, Offset(x * w, ((y + ph * 0.05f * k) % 1f) * h))
                }
            }
            "aurora" -> {
                stars.take(60).forEach { (x, y, k) -> drawCircle(Color.White.copy(alpha = 0.3f + 0.3f * k), 0.5f + k, Offset(x * w, y * h * 0.6f)) }
                for (i in 0 until 3) {
                    val c = listOf(a1, a2, Color(0xFF22D3EE))[i]
                    val path = Path()
                    val base = h * (0.28f + i * 0.08f)
                    path.moveTo(0f, base)
                    var x = 0f
                    while (x <= w) {
                        val y = base + h * 0.06f * sin(2 * PI * (x / w * (1.3 + i * 0.4) + ph + i * 0.3)).toFloat()
                        path.lineTo(x, y); x += 12f
                    }
                    path.lineTo(w, h); path.lineTo(0f, h); path.close()
                    drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = glow * 0.9f), Color.Transparent), startY = base - h * 0.08f, endY = base + h * 0.35f))
                }
            }
            "grid" -> {
                val step = 48f
                val off = (ph * step) % step
                var y = h * 0.55f + off
                while (y < h) { drawLine(a1.copy(alpha = 0.18f * ((y - h * 0.55f) / (h * 0.45f))), Offset(0f, y), Offset(w, y), 1.2f); y += step * (1 + (y - h * 0.55f) / h) }
                var x = -w
                while (x < 2 * w) { drawLine(a2.copy(alpha = 0.10f), Offset(w / 2 + (x - w / 2) * 0.2f, h * 0.55f), Offset(x, h), 1f); x += 70f }
                blob(Offset(w * 0.1f, 0f), w * 0.5f, a1, glow)
                blob(Offset(w, h), w * 0.45f, a2, glow * 0.8f)
            }
            "gears" -> {
                blob(Offset(w * 0.5f, h * 0.4f), w * 0.5f, a1, glow * 0.6f)
                gear(Offset(w * 0.92f, h * 0.12f), h * 0.22f, 16, ph * 360f / 4, a1.copy(alpha = 0.16f))
                gear(Offset(w * 0.06f, h * 0.7f), h * 0.18f, 14, -ph * 360f / 3.5f, a2.copy(alpha = 0.14f))
                gear(Offset(w * 0.96f, h * 0.92f), h * 0.14f, 12, ph * 360f / 3, a1.copy(alpha = 0.12f))
            }
            "scan" -> {
                var y = 0f
                while (y < h) { drawLine(a1.copy(alpha = 0.04f), Offset(0f, y), Offset(w, y), 1f); y += 3f }
                val sy = ph * h
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, a1.copy(alpha = 0.06f), Color.Transparent), startY = sy - 60f, endY = sy + 60f))
            }
            "sun" -> {
                drawCircle(a1.copy(alpha = 0.10f + 0.03f * pu), h * 0.32f, Offset(w * 0.9f, h * 0.15f))
                val path = Path().apply {
                    moveTo(-10f, h * 0.9f)
                    cubicTo(w * 0.3f, h * (0.86f + 0.01f * sin(2 * PI * ph).toFloat()), w * 0.6f, h * 0.95f, w + 10f, h * 0.88f)
                }
                drawPath(path, Color(0xFF1B1B1B).copy(alpha = 0.06f), style = Stroke(w * 0.05f))
            }
        }
    }
}

private fun DrawScope.blob(c: Offset, r: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), c, r), r, c)
}

private fun DrawScope.gear(c: Offset, r: Float, teeth: Int, angle: Float, color: Color) {
    rotate(angle, c) {
        val path = Path()
        val n = teeth * 4
        for (i in 0..n) {
            val a = 2 * PI * i / n
            val rr = if (i % 4 == 1 || i % 4 == 2) r else r * 0.84f
            val x = c.x + rr * cos(a).toFloat(); val y = c.y + rr * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = Stroke(r * 0.05f))
        drawCircle(color, r * 0.55f, c, style = Stroke(r * 0.05f))
    }
}
