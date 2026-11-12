package com.apexvpn.client.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnStatus
import java.util.Locale

/**
 * Главный экран «Карта»: точечная карта мира, дуга от вас до сервера.
 * «Живая карта» — дышащие кольца и бегущий по дуге свет.
 */
@Composable
fun WorldMapCard(status: VpnStatus, serverCountry: String?, serverTitle: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val me = remember { userCountry(context) }
    val from = WorldMap.coords[me] ?: WorldMap.coords.getValue("ru")
    val to = serverCountry?.let { WorldMap.coords[it] }
    val feel = Apex.feel
    val connected = status == VpnStatus.Started
    val busy = status == VpnStatus.Starting || status == VpnStatus.Stopping
    val live = feel.liveMap && !feel.smooth

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
        if (prev != null && to != null && prev != to && !feel.smooth) {
            flyFrom = prev
            fly.snapTo(0f)
            fly.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }
    }
    val dot = Apex.TextDim
    val accent = Apex.Accent
    val accent2 = Apex.Cyan
    val ratio = WorldMap.W.toFloat() / WorldMap.H
    Column(modifier.fillMaxWidth().apexCard().padding(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            // Слой точек можно размыть — «Размытие стекла» (Android 12+)
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(ratio)
                    .then(if (feel.blur && android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(1.5.dp) else Modifier),
            ) { drawDots(dot.copy(alpha = if (Apex.isDark) 0.32f else 0.28f)) }
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
            Text(countryName(me), color = Apex.TextDim, fontSize = 13.sp)
            Text("  →  ", color = Apex.TextDim, fontSize = 13.sp)
            Text(serverTitle, color = Apex.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Страна пользователя: сеть оператора, SIM, язык системы. */
fun userCountry(context: Context): String = runCatching {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    listOfNotNull(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
        .map { it.lowercase(Locale.ROOT) }
        .firstOrNull { it in WorldMap.coords }
}.getOrNull() ?: "ru"

internal fun countryName(code: String): String =
    Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale("ru")).ifBlank { code.uppercase(Locale.ROOT) }

object WorldMap {
    const val W = 96
    const val H = 40
    const val N = 78f
    const val S = -56f

    /** Суша мира 96×40 (равнопромежуточная проекция, 78° с. ш. … 56° ю. ш.). */
    val rows = listOf(
        "................####.######..##############....................#.#......######.......#.#........",
        "..............######.##.####.....#########....................#.....##############....##........",
        "....#################.########....########...........#####....##################################",
        "##..######################.####...#####...##.......#############################################",
        "....####################.#..###...###.....#......###############################################",
        "....###################....####..................####.#######################################...",
        "............##############.#####..............##...##################################....###....",
        ".............#############.######.............##.#####################################...##.....",
        "..............####################.............#######################################..........",
        "...............#################...............#######################################..........",
        "...............###############................##########...##.######################.##.........",
        "...............#############.................###....#########.####################...#..........",
        "...............#############..................#####.....###########################.##..........",
        "................###########..................#######..#..########################.##............",
        ".................##########..................####################################...............",
        "..................####....#.................####################################................",
        "...................###.....................#####################..#############.................",
        "....................#####..#...............###############.#####...####..####...................",
        ".....................####..................###################.....###...####...#...............",
        "........................##.................#################........##....###...................",
        ".........................#.#####............##################......##......#....#..............",
        "...........................#######...........################.............##...#.#..............",
        "...........................########...............###########.............##.###................",
        "..........................##########..............##########..............##.####...............",
        "..........................############.............########................##.###..####.........",
        "..........................#############............########.................##.......###........",
        "...........................############............########.....................................",
        "............................##########.............########.##...................######.........",
        ".............................#########.............#######.###..................#######.........",
        ".............................########..............#######.##..................#########........",
        ".............................#######................#####...#.................###########.......",
        ".............................######.................#####.....................###########.......",
        ".............................######.................####......................###########.......",
        ".............................#####...................##........................##...#####.......",
        "............................#####....................................................###......#.",
        "............................###........................................................#......#.",
        "............................###.............................................................##..",
        "............................##..................................................................",
        "............................##..................................................................",
        "............................##..................................................................",
    )

    /** Примерные координаты (широта, долгота) городов, где обычно стоят серверы. */
    val coords: Map<String, Pair<Float, Float>> = mapOf(
        "ru" to (55.75f to 37.62f),
        "ua" to (50.45f to 30.52f),
        "by" to (53.9f to 27.56f),
        "kz" to (43.24f to 76.95f),
        "uz" to (41.3f to 69.24f),
        "kg" to (42.87f to 74.59f),
        "am" to (40.18f to 44.51f),
        "ge" to (41.72f to 44.78f),
        "az" to (40.41f to 49.87f),
        "tr" to (41.0f to 28.97f),
        "de" to (50.11f to 8.68f),
        "nl" to (52.37f to 4.9f),
        "fi" to (60.17f to 24.94f),
        "se" to (59.33f to 18.07f),
        "no" to (59.91f to 10.75f),
        "dk" to (55.68f to 12.57f),
        "ee" to (59.44f to 24.75f),
        "lv" to (56.95f to 24.1f),
        "lt" to (54.69f to 25.28f),
        "pl" to (52.23f to 21.01f),
        "cz" to (50.08f to 14.44f),
        "at" to (48.21f to 16.37f),
        "ch" to (47.38f to 8.54f),
        "fr" to (48.86f to 2.35f),
        "gb" to (51.51f to -0.13f),
        "ie" to (53.35f to -6.26f),
        "es" to (40.42f to -3.7f),
        "pt" to (38.72f to -9.14f),
        "it" to (45.46f to 9.19f),
        "ro" to (44.43f to 26.1f),
        "bg" to (42.7f to 23.32f),
        "md" to (47.01f to 28.86f),
        "hu" to (47.5f to 19.04f),
        "rs" to (44.79f to 20.45f),
        "gr" to (37.98f to 23.73f),
        "il" to (32.08f to 34.78f),
        "ae" to (25.2f to 55.27f),
        "in" to (19.08f to 72.88f),
        "sg" to (1.35f to 103.82f),
        "hk" to (22.32f to 114.17f),
        "jp" to (35.68f to 139.69f),
        "kr" to (37.57f to 126.98f),
        "cn" to (31.23f to 121.47f),
        "tw" to (25.03f to 121.57f),
        "vn" to (10.82f to 106.63f),
        "th" to (13.76f to 100.5f),
        "id" to (-6.2f to 106.85f),
        "my" to (3.14f to 101.69f),
        "au" to (-33.87f to 151.21f),
        "nz" to (-36.85f to 174.76f),
        "us" to (40.71f to -74.0f),
        "ca" to (43.65f to -79.38f),
        "mx" to (19.43f to -99.13f),
        "br" to (-23.55f to -46.63f),
        "ar" to (-34.6f to -58.38f),
        "cl" to (-33.45f to -70.67f),
        "za" to (-26.2f to 28.05f),
        "eg" to (30.04f to 31.24f),
        "ng" to (6.52f to 3.38f),
        "is" to (64.15f to -21.94f),
        "lu" to (49.61f to 6.13f),
        "be" to (50.85f to 4.35f),
        "cy" to (35.17f to 33.36f),
        "mn" to (47.89f to 106.91f),
        "tj" to (38.56f to 68.79f),
        "tm" to (37.96f to 58.33f),
        "sa" to (24.71f to 46.68f),
    )
}
