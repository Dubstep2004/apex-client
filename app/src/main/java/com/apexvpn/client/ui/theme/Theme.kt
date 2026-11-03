package com.apexvpn.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.data.AccentColor
import com.apexvpn.client.data.Design
import com.apexvpn.client.data.ThemeMode

/** Чем закрашен фон экранов. */
enum class Backdrop { Solid, Glass, Aurora, Neon, Terminal, Pastel, Apex2, Steampunk, Synthwave, Cosmos, Japan, Carbon, Sakura, Matrix, Luxe, Ocean, Custom }

/** Всё, что определяет внешний вид: цвета, форма карточек, фон, шрифт, раскладка главного экрана. */
data class Palette(
    val dark: Boolean,
    /** Фон экрана; прозрачный, если фон рисует [backdrop] */
    val bg: Color,
    /** Непрозрачная основа — для шторок, заставки и страниц */
    val base: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val divider: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val selectedCard: Color,
    val heroCard: Color,
    /** Градиент щита и фирменных элементов */
    val brand1: Color = Color(0xFF7C3AED),
    val brand2: Color = Color(0xFF22D3EE),
    /** Скругление карточек */
    val radius: Dp = 24.dp,
    /** Рамка карточек (стекло, неон) */
    val cardBorder: Color = Color.Transparent,
    /** Неоновое свечение карточек */
    val glow: Boolean = false,
    val backdrop: Backdrop = Backdrop.Solid,
    val mono: Boolean = false,
    /** Главный экран из плиток */
    val bento: Boolean = false,
    val design: Design = Design.Classic,
    /** 212. Крупные карточки и строки (APEX 2.0) */
    val roomy: Boolean = false,
    /** 431. Шрифт с засечками (Японский минимализм) */
    val serif: Boolean = false,
)

private fun darkPalette(accent: Color, amoled: Boolean) = Palette(
    dark = true,
    bg = if (amoled) Color.Black else Color(0xFF0A0A0D),
    base = if (amoled) Color.Black else Color(0xFF0A0A0D),
    surface = if (amoled) Color(0xFF0E0E11) else Color(0xFF16161B),
    surfaceHigh = if (amoled) Color(0xFF19191E) else Color(0xFF202027),
    outline = Color(0xFF26262E),
    divider = if (amoled) Color(0xFF1A1A1F) else Color(0xFF232329),
    text = Color(0xFFF4F4F7),
    textDim = Color(0xFF8E8E9A),
    accent = accent,
    accentSoft = lerp(Color(0xFF16161B), accent, 0.22f),
    accentText = lerp(accent, Color.White, 0.45f),
    selectedCard = lerp(if (amoled) Color.Black else Color(0xFF0A0A0D), accent, 0.12f),
    heroCard = lerp(Color(0xFF16161B), accent, 0.14f),
)

private fun lightPalette(accent: Color) = Palette(
    dark = false,
    bg = Color(0xFFF4F4F7),
    base = Color(0xFFF4F4F7),
    surface = Color.White,
    surfaceHigh = Color(0xFFEDEDF2),
    outline = Color(0xFFDDDDE4),
    divider = Color(0xFFEAEAF0),
    text = Color(0xFF121216),
    textDim = Color(0xFF6B6B77),
    accent = accent,
    accentSoft = lerp(Color.White, accent, 0.14f),
    accentText = lerp(accent, Color.Black, 0.25f),
    selectedCard = lerp(Color.White, accent, 0.08f),
    heroCard = lerp(Color.White, accent, 0.10f),
)

/** Д1. Liquid Glass — полупрозрачные карточки со светлой кромкой поверх цветных пятен. */
private fun glassPalette(accent: Color, dark: Boolean): Palette {
    val base = if (dark) darkPalette(accent, false) else lightPalette(accent)
    return base.copy(
        bg = Color.Transparent,
        base = if (dark) Color(0xFF0B0B14) else Color(0xFFEFF1F8),
        surface = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f),
        surfaceHigh = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.8f),
        divider = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
        heroCard = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.6f),
        accentSoft = accent.copy(alpha = 0.22f),
        cardBorder = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.9f),
        radius = 28.dp,
        backdrop = Backdrop.Glass,
        design = Design.Glass,
    )
}

/** Д2. Material 3 Expressive — акцент из обоев (Android 12+), крупные скругления. */
private fun expressivePalette(accent: Color, dark: Boolean): Palette {
    val base = if (dark) darkPalette(accent, false) else lightPalette(accent)
    return base.copy(
        surface = lerp(base.surface, accent, if (dark) 0.10f else 0.06f),
        surfaceHigh = lerp(base.surfaceHigh, accent, if (dark) 0.18f else 0.12f),
        heroCard = lerp(base.surface, accent, if (dark) 0.28f else 0.18f),
        brand1 = accent,
        brand2 = lerp(accent, if (dark) Color.White else Color(0xFF22D3EE), 0.45f),
        radius = 32.dp,
        design = Design.Expressive,
    )
}

/** Д3. Neon Cyber — чёрный, неоновая рамка и свечение. */
private fun neonPalette(): Palette {
    val pink = Color(0xFFFF2BD6)
    val cyan = Color(0xFF00F0FF)
    return darkPalette(cyan, amoled = true).copy(
        bg = Color.Transparent,
        base = Color(0xFF050509),
        surface = Color(0xFF0B0B14),
        surfaceHigh = Color(0xFF14142A),
        textDim = Color(0xFF8C8CB8),
        accentText = cyan,
        accentSoft = cyan.copy(alpha = 0.16f),
        heroCard = Color(0xFF10102A),
        brand1 = pink,
        brand2 = cyan,
        cardBorder = cyan.copy(alpha = 0.45f),
        glow = true,
        radius = 18.dp,
        backdrop = Backdrop.Neon,
        design = Design.Neon,
    )
}

/** Д4. Минимализм — чёрно-белый. */
private fun minimalPalette(dark: Boolean): Palette {
    val ink = if (dark) Color.White else Color.Black
    val base = if (dark) darkPalette(ink, amoled = true) else lightPalette(ink)
    return base.copy(
        surface = if (dark) Color(0xFF0D0D0D) else Color.White,
        surfaceHigh = if (dark) Color(0xFF1C1C1C) else Color(0xFFEFEFEF),
        accentSoft = if (dark) Color(0xFF222222) else Color(0xFFE6E6E6),
        accentText = ink,
        heroCard = if (dark) Color(0xFF0D0D0D) else Color.White,
        brand1 = if (dark) Color(0xFFBDBDBD) else Color(0xFF222222),
        brand2 = ink,
        cardBorder = if (dark) Color(0xFF262626) else Color(0xFFE2E2E2),
        radius = 12.dp,
        design = Design.Minimal,
    )
}

/** Д5. Aurora — переливающийся фон, полупрозрачные карточки. */
private fun auroraPalette(accent: Color, dark: Boolean): Palette =
    glassPalette(accent, dark).copy(
        surface = if (dark) Color(0xFF0B0B14).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f),
        surfaceHigh = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f),
        cardBorder = Color.Transparent,
        radius = 26.dp,
        brand1 = Color(0xFF34D399),
        brand2 = Color(0xFF818CF8),
        backdrop = Backdrop.Aurora,
        design = Design.Aurora,
    )

/**
 * 212. APEX 2.0 — новый облик 2026: глубокий фон с мягким свечением акцента,
 * крупные скругления и строки, тонкая светлая кромка карточек.
 */
private fun apex2Palette(accent: Color, dark: Boolean, amoled: Boolean): Palette {
    val base = if (dark) darkPalette(accent, amoled) else lightPalette(accent)
    return if (dark) base.copy(
        bg = Color.Transparent,
        base = if (amoled) Color.Black else Color(0xFF08080E),
        surface = if (amoled) Color(0xFF0F0F16) else Color(0xFF13131C),
        surfaceHigh = if (amoled) Color(0xFF1A1A24) else Color(0xFF1E1E2A),
        divider = Color.White.copy(alpha = 0.06f),
        heroCard = lerp(Color(0xFF13131C), accent, 0.18f),
        accentSoft = lerp(Color(0xFF13131C), accent, 0.26f),
        brand1 = accent,
        brand2 = lerp(accent, Color(0xFF22D3EE), 0.6f),
        cardBorder = Color.White.copy(alpha = 0.07f),
        radius = 30.dp,
        backdrop = Backdrop.Apex2,
        design = Design.Apex2,
        roomy = true,
    ) else base.copy(
        bg = Color.Transparent,
        base = Color(0xFFF3F3F9),
        surface = Color.White,
        surfaceHigh = Color(0xFFEDEDF5),
        heroCard = lerp(Color.White, accent, 0.12f),
        brand1 = accent,
        brand2 = lerp(accent, Color(0xFF22D3EE), 0.6f),
        cardBorder = Color.Black.copy(alpha = 0.05f),
        radius = 30.dp,
        backdrop = Backdrop.Apex2,
        design = Design.Apex2,
        roomy = true,
    )
}

/** 351. Стимпанк — латунь и медь на тёмном дереве, шестерёнки на фоне. */
private fun steampunkPalette(): Palette {
    val brass = Color(0xFFD4A24C)
    return Palette(
        dark = true,
        bg = Color.Transparent,
        base = Color(0xFF140D07),
        surface = Color(0xFF241810).copy(alpha = 0.92f),
        surfaceHigh = Color(0xFF34241A),
        outline = Color(0xFF5A4027),
        divider = Color(0xFF3A2A1C),
        text = Color(0xFFF3E3C3),
        textDim = Color(0xFFB59B74),
        accent = brass,
        accentSoft = Color(0xFF4A3418),
        accentText = Color(0xFFF0C878),
        selectedCard = Color(0xFF2E2012),
        heroCard = Color(0xFF2C1E12),
        brand1 = Color(0xFFB8732E),
        brand2 = Color(0xFFE8C36A),
        radius = 14.dp,
        cardBorder = brass.copy(alpha = 0.35f),
        backdrop = Backdrop.Steampunk,
        design = Design.Steampunk,
    )
}

/** 430. Космос — глубокий космос, звёзды и туманность; вокруг щита летают планеты. */
private fun cosmosPalette(): Palette {
    val accent = Color(0xFF8B9CFF)
    return Palette(
        dark = true,
        bg = Color.Transparent,
        base = Color(0xFF04050D),
        surface = Color(0xFF0E1124).copy(alpha = 0.78f),
        surfaceHigh = Color(0xFF1A1F3D),
        outline = Color(0xFF2A3060),
        divider = Color(0xFF1C2147),
        text = Color(0xFFEFF1FF),
        textDim = Color(0xFF9AA0C8),
        accent = accent,
        accentSoft = accent.copy(alpha = 0.2f),
        accentText = Color(0xFFC2CBFF),
        selectedCard = Color(0xFF161A38),
        heroCard = Color(0xFF12152E),
        brand1 = Color(0xFF6D5BFF),
        brand2 = Color(0xFFFF9E57),
        radius = 24.dp,
        cardBorder = Color.White.copy(alpha = 0.08f),
        backdrop = Backdrop.Cosmos,
        design = Design.Cosmos,
    )
}

/** 431. Японский минимализм — бумага, чёрная тушь, красный акцент, шрифт с засечками. */
private fun japanPalette(): Palette {
    val red = Color(0xFFC8102E)
    return Palette(
        dark = false,
        bg = Color.Transparent,
        base = Color(0xFFF3EEE3),
        surface = Color(0xFFFBF8F2),
        surfaceHigh = Color(0xFFEDE6D8),
        outline = Color(0xFFD9CFBE),
        divider = Color(0xFFE6DDCC),
        text = Color(0xFF1B1B1B),
        textDim = Color(0xFF6E6658),
        accent = red,
        accentSoft = Color(0xFFF3D6D2),
        accentText = Color(0xFFA30D25),
        selectedCard = Color(0xFFF6E7E1),
        heroCard = Color(0xFFFBF3EC),
        brand1 = red,
        brand2 = Color(0xFFE25A3C),
        radius = 6.dp,
        cardBorder = Color(0xFFD9CFBE),
        backdrop = Backdrop.Japan,
        design = Design.Japan,
        serif = true,
    )
}

/** 387. Синтвейв — секретная тема: неоновый закат, розовый и бирюзовый. */
private fun synthwavePalette(): Palette {
    val pink = Color(0xFFFF2E97)
    return Palette(
        dark = true,
        bg = Color.Transparent,
        base = Color(0xFF12002A),
        surface = Color(0xFF1C0638).copy(alpha = 0.82f),
        surfaceHigh = Color(0xFF2B0B52),
        outline = Color(0xFF4B1D7A),
        divider = Color(0xFF2E0F55),
        text = Color(0xFFFFF0FA),
        textDim = Color(0xFFC79BDB),
        accent = pink,
        accentSoft = pink.copy(alpha = 0.2f),
        accentText = Color(0xFFFF8AC6),
        selectedCard = Color(0xFF2A0A4A),
        heroCard = Color(0xFF240844),
        brand1 = pink,
        brand2 = Color(0xFF2DE2E6),
        radius = 20.dp,
        cardBorder = pink.copy(alpha = 0.35f),
        glow = true,
        backdrop = Backdrop.Synthwave,
        design = Design.Synthwave,
    )
}

/** Д7. Ретро-терминал — зелёный моноширинный текст на чёрном. */
private fun terminalPalette(): Palette {
    val green = Color(0xFF39FF6A)
    return Palette(
        dark = true,
        bg = Color.Transparent,
        base = Color.Black,
        surface = Color(0xFF020A04),
        surfaceHigh = Color(0xFF07170B),
        outline = Color(0xFF14501F),
        divider = Color(0xFF0D2E14),
        text = green,
        textDim = Color(0xFF2DAE4C),
        accent = green,
        accentSoft = Color(0xFF0B2A12),
        accentText = green,
        selectedCard = Color(0xFF07170B),
        heroCard = Color(0xFF041008),
        brand1 = Color(0xFF0E7A2A),
        brand2 = green,
        radius = 4.dp,
        cardBorder = Color(0xFF14501F),
        backdrop = Backdrop.Terminal,
        mono = true,
        design = Design.Terminal,
    )
}

/** Д8. Пастель — светлые тёплые цвета. */
private fun pastelPalette(): Palette {
    val peach = Color(0xFFFF8A65)
    return lightPalette(peach).copy(
        bg = Color.Transparent,
        base = Color(0xFFFFF6EF),
        surface = Color(0xFFFFFFFF).copy(alpha = 0.85f),
        surfaceHigh = Color(0xFFFFEDE2),
        outline = Color(0xFFF3D9CB),
        divider = Color(0xFFF7E6DC),
        text = Color(0xFF3B2A2A),
        textDim = Color(0xFF9A7F78),
        accentSoft = Color(0xFFFFE1D3),
        accentText = Color(0xFFD9583A),
        heroCard = Color(0xFFFFEFE6),
        brand1 = Color(0xFFFFB199),
        brand2 = Color(0xFFB8A6FF),
        radius = 28.dp,
        backdrop = Backdrop.Pastel,
        design = Design.Pastel,
    )
}

/**
 * Цвета и форма APEX VPN. Свойства читают текущую палитру из состояния Compose,
 * поэтому смена дизайна мгновенно перерисовывает все экраны.
 */
object Apex {
    var palette by mutableStateOf(darkPalette(Color(AccentColor.Violet.argb), amoled = false))
        internal set

    /** Ощущения и плавность: вибрация, плавный режим, размытие, живая карта. */
    var feel by mutableStateOf(Feel())
        internal set

    val Bg get() = palette.bg
    val Base get() = palette.base
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Outline get() = palette.outline
    val Divider get() = palette.divider
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft
    val AccentText get() = palette.accentText
    val SelectedCard get() = palette.selectedCard
    val HeroCard get() = palette.heroCard
    val CardBorder get() = palette.cardBorder
    val Radius get() = palette.radius
    val isDark get() = palette.dark
    /** Текст и значки поверх акцента (в чёрно-белом и терминале — тёмные) */
    val OnAccent get() = if (palette.design == Design.Minimal && palette.dark || palette.design == Design.Terminal) Color.Black else Color.White
    /** Непрозрачный фон диалогов */
    val Dialog get() = if (palette.dark) lerp(palette.base, palette.text, 0.07f).copy(alpha = 1f) else Color.White

    // Фирменные цвета: градиент щита зависит от дизайна
    val Cyan get() = palette.brand2
    val CyanLight = Color(0xFFA5F3FC)
    val Violet get() = palette.brand1
    val Good get() = if (palette.design == Design.Terminal) Color(0xFF39FF6A) else if (palette.dark) Color(0xFF5BD69A) else Color(0xFF16A35A)
    val Warn get() = if (palette.dark) Color(0xFFF2B75B) else Color(0xFFC77700)
    val Bad get() = if (palette.dark) Color(0xFFFF6B6B) else Color(0xFFDC2626)

    val accent get() = Brush.linearGradient(listOf(Violet, Cyan))
}

private fun typography(mono: Boolean, serif: Boolean = false): Typography {
    val family = if (mono) FontFamily.Monospace else if (serif) FontFamily.Serif else FontFamily.Default
    val body = TextStyle(fontFamily = family)
    return Typography(
        displaySmall = body.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        titleLarge = body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = body.copy(fontSize = 16.sp),
        bodyMedium = body.copy(fontSize = 14.sp),
        bodySmall = body.copy(fontSize = 12.sp),
        labelLarge = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = body.copy(fontSize = 13.sp),
        labelSmall = body.copy(fontSize = 11.sp),
        headlineSmall = body.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        titleSmall = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Палитра для настроек. [systemDark] — тёмная тема в системе, [wallpaper] — акцент из обоев
 * (Android 12+) для Material Expressive.
 */
fun paletteFor(
    design: Design,
    theme: ThemeMode,
    accent: AccentColor,
    systemDark: Boolean,
    wallpaper: Color? = null,
    /** 216. Свой цвет, если accent = Custom */
    customArgb: Long? = null,
    /** 564. Второй цвет готовой палитры (градиент щита), 0 — нет */
    customArgb2: Long = 0,
    /** 3.13. «Свой дизайн» */
    custom: CustomSpec = CustomSpec(),
): Palette {
    if (design == Design.Custom) return customPalette(custom)
    val p = basePaletteFor(design, theme, accent, systemDark, wallpaper, customArgb)
    // Палитра из двух цветов — только для дизайнов, где цвет выбирает пользователь
    if (accent != AccentColor.Custom || customArgb2 == 0L || customArgb == null) return p
    return when (design) {
        Design.Apex2, Design.Classic, Design.Glass, Design.Aurora, Design.Bento ->
            p.copy(brand1 = Color(customArgb), brand2 = Color(customArgb2))
        else -> p
    }
}

private fun basePaletteFor(
    design: Design,
    theme: ThemeMode,
    accent: AccentColor,
    systemDark: Boolean,
    wallpaper: Color?,
    customArgb: Long?,
): Palette {
    val accentColor = Color(if (accent == AccentColor.Custom && customArgb != null) customArgb else accent.argb)
    val dark = when (theme) {
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
        ThemeMode.System -> systemDark
    }
    return when (design) {
        Design.Apex2 -> apex2Palette(accentColor, dark, theme == ThemeMode.Amoled)
        Design.Classic -> when (theme) {
            ThemeMode.Light -> lightPalette(accentColor)
            ThemeMode.Dark -> darkPalette(accentColor, amoled = false)
            ThemeMode.Amoled -> darkPalette(accentColor, amoled = true)
            ThemeMode.System -> if (systemDark) darkPalette(accentColor, false) else lightPalette(accentColor)
        }
        Design.Glass -> glassPalette(accentColor, dark)
        Design.Expressive -> expressivePalette(wallpaper ?: accentColor, dark)
        Design.Neon -> neonPalette()
        Design.Minimal -> minimalPalette(dark)
        Design.Aurora -> auroraPalette(accentColor, dark)
        Design.Bento -> (if (dark) darkPalette(accentColor, theme == ThemeMode.Amoled) else lightPalette(accentColor))
            .copy(radius = 26.dp, bento = true, design = Design.Bento)
        Design.Terminal -> terminalPalette()
        Design.Pastel -> pastelPalette()
        Design.Steampunk -> steampunkPalette()
        Design.Synthwave -> synthwavePalette()
        Design.Cosmos -> cosmosPalette()
        Design.Japan -> japanPalette()
        Design.Carbon -> carbonPalette()
        Design.Sakura -> sakuraPalette()
        Design.Matrix -> matrixPalette()
        Design.Luxe -> luxePalette()
        Design.Ocean -> oceanPalette()
        Design.Custom -> customPalette(CustomSpec())
    }
}

/** 313. Наклон телефона (−1…1) для бликов Liquid Glass 2.0. */
object Tilt {
    var x by androidx.compose.runtime.mutableFloatStateOf(0f)
    var y by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** Слушает датчик гравитации, пока включён Liquid Glass и не «Плавный режим». */
@Composable
fun TiltSensor(enabled: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Только пока экран приложения виден — в фоне датчик не тратит батарею
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumed by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
    }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    androidx.compose.runtime.DisposableEffect(enabled && resumed) {
        if (!(enabled && resumed)) {
            Tilt.x = 0f; Tilt.y = 0f
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            // «Привычное» положение телефона медленно подстраивается: в покое щит и блик
            // возвращаются в центр, а реагируют на наклон относительно него
            var baseX = Float.NaN
            var baseY = Float.NaN
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val rx = -e.values[0] / 9.81f
                val ry = e.values[1] / 9.81f
                if (baseX.isNaN()) { baseX = rx; baseY = ry }
                baseX += (rx - baseX) * 0.01f
                baseY += (ry - baseY) * 0.01f
                val tx = ((rx - baseX) * 2.2f).coerceIn(-1f, 1f)
                val ty = ((ry - baseY) * 2.2f).coerceIn(-1f, 1f)
                // Сглаживание, чтобы движение было плавным, а не дрожало
                Tilt.x += (tx - Tilt.x) * 0.15f
                Tilt.y += (ty - Tilt.y) * 0.15f
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm?.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
}

/** Настройки «Ощущения» и «Плавность» для экранов. */
data class Feel(
    val vibration: Boolean = true,
    /** Плавный режим: без бесконечных анимаций и свечения — для слабых телефонов */
    val smooth: Boolean = false,
    val blur: Boolean = true,
    val liveMap: Boolean = true,
    /** 429. 3D-щит от наклона */
    val shield3d: Boolean = false,
)

fun applyFeel(f: Feel) {
    if (Apex.feel != f) Apex.feel = f
}

/** Применить палитру (вызывается вне отрисовки — из Activity). */
fun applyPalette(p: Palette) {
    if (Apex.palette != p) Apex.palette = p
}

@Composable
fun ApexTheme(content: @Composable () -> Unit) {
    val p = Apex.palette
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent,
        onPrimary = if (p.design == Design.Minimal || p.design == Design.Terminal) p.base else Color.White,
        secondary = p.brand2,
        background = p.base,
        onBackground = p.text,
        surface = p.base,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textDim,
        surfaceContainer = p.base,
        surfaceContainerLow = p.base,
        surfaceContainerHigh = lerp(p.base, p.text, 0.06f),
        surfaceContainerHighest = lerp(p.base, p.text, 0.10f),
        outline = p.outline,
        outlineVariant = p.divider,
        error = Apex.Bad,
    )
    MaterialTheme(colorScheme = scheme, typography = typography(p.mono, p.serif), content = content)
}
