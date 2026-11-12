package com.apexvpn.client.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.R
import com.apexvpn.client.ui.theme.Apex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Бренд ──────────────────────────────────────────────────────────────

/** Значок приложения (адаптивную иконку Compose не рисует, собираем из слоёв). */
@Composable
fun ApexLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(size))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size))
    }
}

/**
 * «Живое» название APEX CLIENT: по буквам пробегает блик, «VPN» переливается цветами бренда,
 * логотип мягко «дышит». В плавном режиме — неподвижное.
 */
@Composable
fun ApexWordmark(modifier: Modifier = Modifier) {
    val live = !Apex.feel.smooth
    val textColor = Apex.Text
    val c1 = Apex.Cyan
    val c2 = Apex.Accent
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wordmark")
    val sweep by t.animateFloat(
        initialValue = -1f, targetValue = 2.5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "sweep",
    )
    val hue by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "hue",
    )
    val breath by t.animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = with(density) { 70.dp.toPx() }
    val apexBrush = if (live) {
        val x = sweep * width
        Brush.linearGradient(
            listOf(textColor, textColor, Color.White, c1, textColor, textColor),
            start = androidx.compose.ui.geometry.Offset(x - width * 0.6f, 0f),
            end = androidx.compose.ui.geometry.Offset(x + width * 0.6f, 0f),
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(textColor)
    }
    val vpnBrush = if (live) {
        Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.lerp(c1, c2, hue), androidx.compose.ui.graphics.lerp(c2, c1, hue)))
    } else {
        androidx.compose.ui.graphics.SolidColor(c1)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            if (live) {
                Box(
                    Modifier
                        .size(32.dp)
                        .graphicsLayer { scaleX = breath * 1.15f; scaleY = breath * 1.15f; alpha = (breath - 1f) * 6f }
                        .background(Brush.radialGradient(listOf(c2.copy(alpha = 0.55f), Color.Transparent)), CircleShape),
                )
            }
            ApexLogo(32.dp, Modifier.graphicsLayer { if (live) { scaleX = breath; scaleY = breath } })
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "APEX",
            style = TextStyle(brush = apexBrush, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 2.sp),
        )
        Text(
            " CLIENT",
            style = TextStyle(brush = vpnBrush, fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = 1.5.sp),
        )
    }
}

// ─── Строительные блоки экранов ─────────────────────────────────────────

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = Apex.Text, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 10.dp)) {
        Text(text, color = Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Apex.TextDim, fontSize = 14.sp)
    }
}

@Composable
fun Caption(text: String) {
    Text(text, color = Apex.TextDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

/** Скруглённая группа строк, как в настройках iOS/Android 16. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().apexCard(), content = content)
}

/** Карточка в стиле текущего дизайна: скругление, фон, рамка (стекло/неон), свечение. */
@Composable
fun Modifier.apexCard(color: Color = Apex.Surface, radius: Dp = Apex.Radius): Modifier {
    val shape = RoundedCornerShape(radius)
    val glow = Apex.palette.glow
    val glowColor = Apex.Cyan
    return this
        // Тень-свечение — только у непрозрачных карточек: сквозь стекло она видна тёмным прямоугольником
        .then(if (glow && color.alpha >= 0.99f) Modifier.shadow(14.dp, shape, ambientColor = glowColor, spotColor = glowColor) else Modifier)
        .then(if (glow && color.alpha < 0.99f) Modifier.border(2.dp, glowColor.copy(alpha = 0.35f), shape) else Modifier)
        .clip(shape)
        .background(color)
        .then(if (Apex.palette.design == com.apexvpn.client.data.Design.Glass) Modifier.glassGlare() else Modifier)
        .then(if (Apex.CardBorder != Color.Transparent) Modifier.border(1.dp, Apex.CardBorder, shape) else Modifier)
}

/** 313. Liquid Glass 2.0: по стеклу карточки проходит блик, который двигается при наклоне телефона. */
private fun Modifier.glassGlare(): Modifier = this.drawWithContent {
    drawContent()
    val tx = com.apexvpn.client.ui.theme.Tilt.x
    val ty = com.apexvpn.client.ui.theme.Tilt.y
    val c = androidx.compose.ui.geometry.Offset(size.width * (0.5f + 0.55f * tx), size.height * (0.3f + 0.6f * ty))
    val d = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.8f) * 0.7f
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.42f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.14f),
            0.58f to Color.Transparent,
            1f to Color.Transparent,
            start = c - d,
            end = c + d,
        ),
    )
    // Светлая кромка сверху — как у толстого стекла
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f + 0.06f * (1f - ty).coerceIn(0f, 1f)), Color.Transparent), 0f, 18f),
        size = androidx.compose.ui.geometry.Size(size.width, 18f),
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 76.dp, end = 16.dp), thickness = 1.dp, color = Apex.Divider)
}

/** Строка: плашка-значок, заголовок, подзаголовок, справа — что угодно. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Apex.Text,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (Apex.palette.roomy) 18.dp else 16.dp, vertical = if (Apex.palette.roomy) 15.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                // Подпись целиком: раньше обрезалась на второй строке «…»
                Text(subtitle, color = Apex.TextDim, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Квадратная плашка со значком или символом. */
@Composable
fun IconTile(color: Color, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun GlyphTile(glyph: String, color: Color) {
    IconTile(color) {
        Text(glyph, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ApexSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Apex.OnAccent,
            checkedTrackColor = Apex.Accent,
            checkedBorderColor = Apex.Accent,
            uncheckedThumbColor = Apex.TextDim,
            uncheckedTrackColor = Apex.SurfaceHigh,
            uncheckedBorderColor = Apex.SurfaceHigh,
        ),
    )
}

/** Маленькая плашка-метка: «Быстрый», «LTE», «VLESS». */
@Composable
fun Tag(text: String, color: Color = Apex.AccentText, background: Color = Apex.AccentSoft, outlined: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .then(
                if (outlined) Modifier.background(color.copy(alpha = 0.12f)) else Modifier.background(background),
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * 383. Переворот «карточкой»: старое содержимое поворачивается ребром,
 * на обороте — новое. [key] — что показывать сейчас.
 */
@Composable
fun FlipFlag(key: String, content: @Composable (String) -> Unit) {
    var shown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(key) }
    val rot = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) {
        if (key == shown) return@LaunchedEffect
        if (Apex.feel.smooth) {
            shown = key
            return@LaunchedEffect
        }
        rot.animateTo(90f, androidx.compose.animation.core.tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        shown = key
        rot.snapTo(-90f)
        rot.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f))
    }
    Box(Modifier.graphicsLayer { rotationY = rot.value; cameraDistance = 14f * density }) { content(shown) }
}

/** Круглый флаг страны: картинка из assets/flags, иначе эмодзи. */
@Composable
fun FlagCircle(flag: String?, size: Dp = 40.dp, animated: Boolean = false) {
    val context = LocalContext.current
    val code = remember(flag) { flagCode(flag) }
    val bitmap = remember(code) { code?.let { FlagCache.get(context, it) } }
    Box(
        Modifier.size(size).clip(CircleShape).background(Apex.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            // 94. В списке серверов флаг колышется (у каждой страны — свой сдвиг волны)
            val flagMod = if (animated) Modifier.animatedFlag(((code?.hashCode() ?: 0) and 0xFF) / 255f) else Modifier
            Image(bitmap, null, Modifier.size(size).clip(CircleShape).then(flagMod))
        } else {
            Text(flag ?: "🌐", fontSize = (size.value * 0.55f).sp)
        }
    }
}

/** «🇬🇧» → «gb». */
fun flagCode(flag: String?): String? {
    if (flag == null) return null
    val cps = flag.codePoints().toArray()
    if (cps.size != 2 || cps.any { it !in 0x1F1E6..0x1F1FF }) return null
    return cps.joinToString("") { ('a' + (it - 0x1F1E6)).toString() }
}

private object FlagCache {
    private val cache = HashMap<String, ImageBitmap?>()

    @Synchronized
    fun get(context: Context, code: String): ImageBitmap? = cache.getOrPut(code) {
        runCatching {
            context.assets.open("flags/$code.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }
}

// ─── Форматирование ─────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return if (i == 0) "$bytes Б" else String.format(Locale("ru"), "%.1f %s", v, units[i])
}

fun formatDate(unixSeconds: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(unixSeconds * 1000))

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun formatAgo(time: Long): String {
    if (time <= 0) return "никогда"
    val min = (System.currentTimeMillis() - time) / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 60 * 24 -> "${min / 60} ч назад"
        else -> "${min / (60 * 24)} дн назад"
    }
}

fun pingColor(ms: Int): androidx.compose.ui.graphics.Color {
    // 3.14: пороги задаются в «Пинге» → «Цвет пинга»
    val s = com.apexvpn.client.data.AppSettings.value
    return when {
        ms < 0 -> Apex.TextDim
        ms < s.pingGood -> Apex.Good
        ms < maxOf(s.pingOk, s.pingGood + 10) -> Apex.Warn
        else -> Apex.Bad
    }
}

/**
 * 3.17.9. Кнопка «?» у сложной настройки: по нажатию — объяснение простыми словами.
 */
@Composable
fun InfoHint(title: String, text: String) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        Modifier.padding(end = 6.dp).size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(Apex.SurfaceHigh).clickable { open = true },
        contentAlignment = Alignment.Center,
    ) { Text("?", color = Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Apex.Dialog,
            title = { Text(title, color = Apex.Text) },
            text = { Text(text, color = Apex.TextDim, fontSize = 15.sp, lineHeight = 21.sp) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Понятно", color = Apex.Accent) } },
        )
    }
}
