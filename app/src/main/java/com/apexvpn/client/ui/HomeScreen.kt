package com.apexvpn.client.ui

import androidx.compose.animation.core.animateFloat
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ExpiryAlerts
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.isFavorite
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.vpn.formatSpeed
import kotlinx.coroutines.delay

private val timerOptions = listOf(30, 60, 120, 240)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenMode: () -> Unit,
    onOpenPage: (Page) -> Unit,
) {
    val status by VpnState.status.collectAsStateWithLifecycle()
    val error by VpnState.error.collectAsStateWithLifecycle()
    val connectedAt by VpnState.connectedAt.collectAsStateWithLifecycle()
    val speed by VpnState.speed.collectAsStateWithLifecycle()
    val autoOffAt by VpnState.autoOffAt.collectAsStateWithLifecycle()
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val livePing by viewModel.livePing.collectAsStateWithLifecycle()
    var timerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.startLivePing()
        onDispose { viewModel.stopLivePing() }
    }

    // 157. Своё фото за щитом (из галереи)
    // Живое видео на фоне: шаблон или своё
    // 3.20.5: обои затемняются минимум на 30% — иначе на яркой картинке не видно щит, текст и карточки
    val dimSafe = settings.photoDim.coerceAtLeast(MIN_DIM)
    if (LiveVideos.isVideo(settings.shieldBackdrop)) VideoBackdrop(settings.shieldBackdrop, settings.backdropStamp, dimSafe, settings.liveSpeed)
    if (settings.shieldBackdrop == "photo") PhotoBackdrop(settings.backdropStamp, settings.photoBlur, dimSafe, settings.photoParallax, settings.photoMotion)
    // 354. Фото дня страны сервера (автопилот — ваша страна)
    if (settings.shieldBackdrop == "country") {
        val sel = sub.nodes.find { it.tag == sub.selectedTag }
        val code = sel?.let { flagCode(ServerInfo.flag(it.name)) }?.takeIf { com.apexvpn.client.data.CountryPhoto.supports(it) }
            ?: userCountry(context)
        CountryPhotoBackdrop(code)
    }
    // 3.20. Эффект поверх любых обоев: снег, дождь, сакура, светлячки, звёзды, листья
    if (settings.wallFx != "none") com.apexvpn.client.ui.theme.WallFxOverlay(settings.wallFx)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ApexWordmark()
            Spacer(Modifier.width(8.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = Apex.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Apex.Surface)
                    .clickable { onOpenPage(Page.Changelog) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            StatusPill(status)
        }

        // Защита от подделок: постоянное предупреждение в неофициальной копии
        if (remember { com.apexvpn.client.data.Integrity.status(context) } == com.apexvpn.client.data.Integrity.Status.Unofficial) {
            Spacer(Modifier.height(10.dp))
            Banner(
                Icons.Rounded.WarningAmber, Apex.Bad,
                "Неофициальная копия APEX CLIENT",
                "Её собрал не автор — она может украсть подписку. Нажмите, чтобы скачать оригинал.",
                onClick = { openUrl(context, com.apexvpn.client.data.Integrity.OFFICIAL_URL) },
                onClose = null,
            )
        }

        // 3.18. Баннер из веб-админки: технические работы, сбой, новости
        val cfg by com.apexvpn.client.data.AppConfig.state.collectAsStateWithLifecycle()
        // Расписание баннера: пересчитываем раз в минуту (с 20:00 до 23:00 и т. п.)
        var minute by remember { mutableStateOf(System.currentTimeMillis()) }
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); minute = System.currentTimeMillis() } }
        val device = remember { if (com.apexvpn.client.data.AppConfig.isTv()) "tv" else "phone" }
        val current = remember(cfg, minute) { cfg.bannerFor(device, minute) }
        var bannerHidden by remember(current?.id, current?.text) { mutableStateOf(false) }
        current?.takeIf { !bannerHidden && com.apexvpn.client.data.AppConfig.bannerVisible(it) }?.let { b ->
            Spacer(Modifier.height(10.dp))
            val (icon, color) = when (b.level) {
                "alert" -> Icons.Rounded.WarningAmber to Apex.Bad
                "warn" -> Icons.Rounded.WarningAmber to Apex.Warn
                else -> Icons.Rounded.Info to Apex.Accent
            }
            Banner(
                icon, color, null, b.text,
                onClick = b.url.takeIf { it.startsWith("https://") }?.let { u -> { openUrl(context, u) } },
                onClose = {
                    com.apexvpn.client.data.Prefs.bannerDismissed = (b.id + b.text).hashCode()
                    bannerHidden = true
                },
            )
        }

        // 3.20.10: «Вышла версия … Обновить» — обновиться прямо с главного экрана, без настроек
        UpdatePill(viewModel)

        // 13. Подписка заканчивается
        ExpiryAlerts.warning(sub)?.let { w ->
            // 3.20.11: нажатие — продлить (личный кабинет из подписки, иначе чат поддержки)
            val renew = ExpiryAlerts.renewUrl()
            Spacer(Modifier.height(10.dp))
            Banner(
                Icons.Rounded.WarningAmber, Apex.Warn, null,
                if (renew.startsWith("http") || renew.startsWith("tg:")) "$w · нажмите, чтобы продлить" else w,
                onClick = renew.takeIf { it.startsWith("http") || it.startsWith("tg:") }?.let { u -> { openUrl(context, u) } },
                onClose = null,
            )
        }

        val selected = sub.nodes.find { it.tag == sub.selectedTag }
        val auto = sub.selectedTag == AUTO_TAG || selected == null
        // 12. Свайп по щиту: избранные серверы (если их 2+), иначе все без рекламы
        val cycle = remember(sub.nodes, settings.favorites) {
            // Из каждой избранной группы — лучший по порядку сервер; иначе все без рекламы
            val fav = sub.nodes.filter { it.isFavorite(settings.favorites) }.distinctBy { ServerInfo.title(it.name) }
            listOf(AUTO_TAG) + (if (fav.size >= 2) fav else sub.nodes.filter { !it.ads }).map { it.tag }
        }
        val onSwipe: (Int) -> Unit = { dir ->
            if (cycle.size > 1) {
                val i = cycle.indexOf(if (auto) AUTO_TAG else sub.selectedTag).coerceAtLeast(0)
                val next = cycle[(i + dir + cycle.size) % cycle.size]
                viewModel.select(next)
                val n = sub.nodes.find { it.tag == next }
                viewModel.showMessage(if (n == null) "Автопилот" else ServerInfo.title(n.name))
            }
        }
        if (Apex.palette.bento) {
            // Д6. Бенто: главный экран из плиток
            Spacer(Modifier.height(16.dp))
            BentoGrid(
                status = status,
                connectedAt = connectedAt,
                speed = speed,
                livePing = livePing,
                serverTitle = if (auto) "Автопилот" else ServerInfo.title(selected!!.name),
                serverFlag = if (auto) null else ServerInfo.flag(selected!!.name),
                modeTitle = settings.mode.title,
                timerText = if (autoOffAt > 0) timerLeft(autoOffAt) else "Таймер",
                timerOn = autoOffAt > 0,
                onToggle = onToggle,
                onServers = onOpenServers,
                onMode = onOpenMode,
                onTimer = { timerDialog = true },
                onSpeedTest = { onOpenPage(Page.SpeedTest) },
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Apex.Bad, fontSize = 14.sp, modifier = Modifier.apexCard(Apex.Bad.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 9.dp))
            }
        } else {
        if (settings.homeStyle == "map" || settings.homeStyle == "globe") {
            // Главный экран «Карта»: дуга от вас до сервера
            val pings by viewModel.pings.collectAsStateWithLifecycle()
            val target = if (auto) {
                sub.nodes.filter { !it.ads }.mapNotNull { n -> pings[n.tag]?.takeIf { it > 0 }?.let { n to it } }.minByOrNull { it.second }?.first
            } else selected
            Spacer(Modifier.height(16.dp))
            val mapTitle = when {
                !auto -> ServerInfo.title(selected!!.name)
                target != null -> "Автопилот · ${ServerInfo.title(target.name)}"
                else -> "Автопилот"
            }
            val mapCountry = target?.let { flagCode(ServerInfo.flag(it.name)) }
            if (settings.homeStyle == "globe") {
                // 353. 3D-глобус поворачивается к стране сервера
                GlobeCard(status = status, serverCountry = mapCountry, serverTitle = mapTitle)
            } else {
                WorldMapCard(status = status, serverCountry = mapCountry, serverTitle = mapTitle)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().swipeServers(onSwipe), contentAlignment = Alignment.Center) {
                // 430. Планеты вокруг щита в дизайне «Космос»
                if (Apex.palette.design == com.apexvpn.client.data.Design.Cosmos) CosmosOrbit(Modifier.matchParentSize())
                ShieldAmbient(settings.shieldBackdrop, status == VpnStatus.Started, Modifier.matchParentSize())
                if (settings.modeColors && sub.selectedTag == AUTO_TAG) ModeAura(settings.autoMode, status == VpnStatus.Started, Modifier.matchParentSize())
                ShieldButton(status, onToggle, diameter = 150.dp)
            }
        } else {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().swipeServers(onSwipe), contentAlignment = Alignment.Center) {
                // 430. Планеты вокруг щита в дизайне «Космос»
                if (Apex.palette.design == com.apexvpn.client.data.Design.Cosmos) CosmosOrbit(Modifier.matchParentSize())
                ShieldAmbient(settings.shieldBackdrop, status == VpnStatus.Started, Modifier.matchParentSize())
                if (settings.modeColors && sub.selectedTag == AUTO_TAG) ModeAura(settings.autoMode, status == VpnStatus.Started, Modifier.matchParentSize())
                ShieldButton(status, onToggle)
            }
        }

        val label = when (status) {
            VpnStatus.Started -> "Защищено"
            VpnStatus.Starting -> "Подключение…"
            VpnStatus.Stopping -> "Отключение…"
            VpnStatus.Paused -> "Пауза"
            VpnStatus.Blocked -> "Интернет заблокирован"
            VpnStatus.Stopped -> "Не подключено"
        }
        // 615. Приветствие по времени суток: «Доброе утро! Защищено ✓»
        if (settings.greeting && (status == VpnStatus.Started || status == VpnStatus.Stopped)) {
            Text(greeting(), color = Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
        }
        Text(if (settings.greeting && status == VpnStatus.Started) "$label ✓" else label, color = Apex.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        when {
            status == VpnStatus.Started && connectedAt > 0 -> ConnectionTimer(connectedAt)
            status == VpnStatus.Paused -> Text("Доверенная сеть Wi-Fi — VPN не нужен", color = Apex.TextDim, fontSize = 15.sp)
            status == VpnStatus.Blocked -> Text("VPN переподключается, без него интернет закрыт", color = Apex.Warn, fontSize = 15.sp)
            else -> Text(
                if (status == VpnStatus.Stopped) "Нажмите на щит · свайп — другой сервер" else " ",
                color = Apex.TextDim,
                fontSize = 15.sp,
            )
        }

        // 4. Скорость
        if (status == VpnStatus.Started) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("↓ ${formatSpeed(speed.first)}", color = Apex.Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("↑ ${formatSpeed(speed.second)}", color = Apex.AccentText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Apex.Bad,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Apex.Bad.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }

        // 197. Сколько рекламы заблокировано сегодня
        val adsToday by com.apexvpn.client.data.AdCounter.today.collectAsStateWithLifecycle()
        if (settings.adBlock && adsToday > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "🛡 Заблокировано рекламы сегодня: $adsToday",
                color = Apex.Good,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Apex.Good.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Быстрые действия: таймер (51) и тест скорости (5)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val timerText = if (autoOffAt > 0) timerLeft(autoOffAt) else "Таймер"
            Chip(Icons.Rounded.Timer, timerText, active = autoOffAt > 0) { timerDialog = true }
            Chip(Icons.Rounded.Speed, "Тест скорости") { onOpenPage(Page.SpeedTest) }
        }

        // 49. Живой пинг
        if (status == VpnStatus.Started && livePing.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PingGraph(livePing)
        }

        Spacer(Modifier.height(16.dp))

        // Сервер и режим
        val autoNow by viewModel.autoNow.collectAsStateWithLifecycle()
        val whitelist by com.apexvpn.client.data.WhiteList.state.collectAsStateWithLifecycle()
        val nowNode = autoNow?.let { t -> sub.nodes.find { it.tag == t } }
        Group {
            ListRow(
                title = if (auto) "Автопилот" else ServerInfo.title(selected!!.name),
                // Автопилот показывает, какой сервер он выбрал прямо сейчас
                subtitle = when {
                    !auto -> "Сервер выбран вручную"
                    whitelist && settings.autoWhitelist -> "Белые списки · обход LTE" + (nowNode?.let { " · " + ServerInfo.title(it.name) } ?: "")
                    nowNode != null && status == VpnStatus.Started -> autoModePrefix(settings.autoMode) + "Сейчас: " + ServerInfo.title(nowNode.name)
                    else -> autoModePrefix(settings.autoMode).ifEmpty { "Самый быстрый из ${sub.nodes.size} серверов" }.removeSuffix(" · ")
                },
                leading = {
                    // 383. Флаг переворачивается, как карточка, при смене страны
                    FlipFlag(if (auto) "auto" else (ServerInfo.flag(selected!!.name) ?: "🌐")) { k ->
                        if (k == "auto") {
                            IconTile(Apex.AccentSoft) { Icon(Icons.Rounded.Bolt, null, tint = Apex.AccentText) }
                        } else {
                            FlagCircle(k, 44.dp)
                        }
                    }
                },
                onClick = onOpenServers,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim) }
            GroupDivider()
            ListRow(
                title = settings.mode.title,
                // LTE-обход: весь трафик через VPN (при белых списках напрямую открываются не все сайты)
                subtitle = if (settings.mode != com.apexvpn.client.data.RouteMode.All &&
                    ((whitelist && settings.autoWhitelist) || (!auto && ServerInfo.isLte(selected!!.name)))
                ) "LTE-обход: сейчас весь трафик идёт через VPN" else settings.mode.subtitle,
                leading = { IconTile(Apex.SurfaceHigh) { Icon(Icons.Rounded.Public, null, tint = Apex.Cyan) } },
                onClick = onOpenMode,
            ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Apex.TextDim) }
        }

        }
        Spacer(Modifier.height(12.dp))
        SubscriptionCard(
            sub = sub,
            refreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            onPing = onOpenServers,
            onOpen = { openUrl(context, it) },
        )
        Spacer(Modifier.height(20.dp))
    }

    if (timerDialog) {
        AlertDialog(
            onDismissRequest = { timerDialog = false },
            containerColor = Apex.Dialog,
            title = { Text("Выключить VPN через…", color = Apex.Text) },
            text = {
                Column {
                    timerOptions.forEach { m ->
                        Text(
                            if (m < 60) "$m минут" else "${m / 60} ${if (m == 60) "час" else "часа"}",
                            color = Apex.Text,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { VpnState.setTimer(m); timerDialog = false }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                if (autoOffAt > 0) {
                    TextButton(onClick = { VpnState.setTimer(0); timerDialog = false }) { Text("Отменить таймер", color = Apex.Bad) }
                }
            },
            dismissButton = { TextButton(onClick = { timerDialog = false }) { Text("Закрыть", color = Apex.TextDim) } },
        )
    }
}

@Composable
private fun timerLeft(offAt: Long): String {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(offAt) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val left = ((offAt - now) / 1000).coerceAtLeast(0)
    return if (left >= 3600) "${left / 3600} ч ${(left % 3600) / 60} мин" else "${left / 60}:${"%02d".format(left % 60)}"
}

@Composable
private fun Chip(icon: ImageVector, text: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Apex.AccentSoft else Apex.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) Apex.AccentText else Apex.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = if (active) Apex.AccentText else Apex.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String?,
    text: String,
    onClick: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, color = Apex.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = if (title != null) Apex.TextDim else Apex.Text, fontSize = 14.sp)
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Скрыть", tint = Apex.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** График задержки за последние ~80 секунд. */
@Composable
private fun PingGraph(values: List<Int>) {
    val ok = values.filter { it >= 0 }
    val last = values.lastOrNull() ?: -1
    Column(
        Modifier
            .fillMaxWidth()
            .apexCard()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пинг через VPN", color = Apex.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (last >= 0) "$last мс" else "нет ответа", color = pingColor(last), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        val line = Apex.Accent
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            if (ok.size < 2) return@Canvas
            val max = (ok.maxOrNull() ?: 1).coerceAtLeast(50).toFloat() * 1.15f
            val step = size.width / 39f
            val offset = (40 - values.size) * step
            val path = Path()
            val fill = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v < 0) return@forEachIndexed
                val x = offset + i * step
                val y = size.height - (v / max) * size.height
                if (!started) {
                    path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y); started = true
                } else {
                    path.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(offset + (values.size - 1) * step, size.height)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lx = offset + (values.size - 1) * step
            val lv = values.last()
            if (lv >= 0) drawCircle(line, 4.dp.toPx(), Offset(lx, size.height - (lv / max) * size.height))
        }
        if (ok.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "мин ${ok.min()} · средн ${ok.average().toInt()} · макс ${ok.max()} мс",
                color = Apex.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StatusPill(status: VpnStatus) {
    val on = status == VpnStatus.Started
    val paused = status == VpnStatus.Paused || status == VpnStatus.Blocked
    val color = when {
        on -> Apex.Good
        paused -> Apex.Warn
        else -> Apex.TextDim
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on || paused) color.copy(alpha = 0.14f) else Apex.Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(
            when {
                on -> "VPN включён"
                status == VpnStatus.Blocked -> "Блокировка"
                paused -> "Пауза"
                else -> "VPN выключен"
            },
            fontSize = 13.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Карточка подписки в стиле Happ: название, обновление, трафик, объявление, поддержка. */
@Composable
private fun SubscriptionCard(
    sub: com.apexvpn.client.data.SubscriptionState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .apexCard(Apex.HeroCard)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.title.ifBlank { "Подписка" },
                    color = Apex.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val updated = if (sub.lastUpdated > 0) {
                    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sub.lastUpdated))
                } else {
                    "ещё не обновлялась"
                }
                Text("$updated | Автообновление - ${sub.updateHours} ч.", color = Apex.TextDim, fontSize = 13.sp)
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Apex.Accent)
                } else {
                    Icon(Icons.Rounded.Refresh, "Обновить подписку", tint = Apex.Text)
                }
            }
            IconButton(onClick = onPing) { Icon(Icons.Rounded.Speed, "Проверить пинг", tint = Apex.Text) }
        }
        Spacer(Modifier.height(12.dp))

        // Трафик: «11,3 ГБ / ∞» в полосе, по краям — сайт и поддержка
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpen(sub.webPageUrl) }, enabled = sub.webPageUrl.isNotEmpty()) {
                Icon(Icons.Outlined.Info, "Сайт", tint = if (sub.webPageUrl.isNotEmpty()) Apex.AccentText else Apex.TextDim.copy(alpha = 0.4f))
            }
            val used = sub.used.coerceAtLeast(0)
            val fraction = if (sub.total > 0) (used.toFloat() / sub.total).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Apex.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Apex.Accent.copy(alpha = 0.45f)),
                )
                Text(
                    "${formatBytes(used)} / ${if (sub.total > 0) formatBytes(sub.total) else "∞"}",
                    color = Apex.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { onOpen(sub.supportUrl) }, enabled = sub.supportUrl.isNotEmpty()) {
                val telegram = "t.me" in sub.supportUrl || sub.supportUrl.startsWith("tg:")
                Icon(
                    if (telegram) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Link,
                    "Поддержка",
                    tint = if (sub.supportUrl.isNotEmpty()) Apex.AccentText else Apex.TextDim.copy(alpha = 0.4f),
                )
            }
        }

        // 160. Обратный отсчёт до конца подписки, обновляется раз в 30 секунд
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(sub.expireAt) {
            while (sub.expireAt > 0) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(30_000)
            }
        }
        val expire = when {
            sub.expireAt > 0 -> "Действует до ${formatDate(sub.expireAt)}"
            sub.expireAt == 0L -> "Бессрочно"
            else -> null
        }
        expire?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Apex.TextDim, fontSize = 13.sp)
        }
        if (sub.expireAt > 0) {
            val left = sub.expireAt * 1000 - now
            val (label, color) = countdown(left)
            Spacer(Modifier.height(6.dp))
            Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        // Объявление от сервиса (заголовок announce из Remnawave)
        if (sub.announce.isNotBlank() || sub.announceImage.isNotBlank() || sub.announceUrl.isNotBlank()) {
            // 434. Картинка над текстом
            if (sub.announceImage.isNotBlank()) {
                val ctx = LocalContext.current
                val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, sub.announceImage) {
                    value = com.apexvpn.client.data.AnnounceParser.image(ctx, sub.announceImage)
                }
                bmp?.let { b ->
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.Image(
                        remember(b) { b.asImageBitmap() }, null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .aspectRatio((b.width.toFloat() / b.height).coerceIn(1f, 3f))
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
            if (sub.announce.isNotBlank()) Spacer(Modifier.height(12.dp))
            if (sub.announce.isNotBlank()) Text(
                com.apexvpn.client.data.AnnounceParser.tidy(sub.announce, sub.expireAt, sub.total),
                color = Apex.Text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // 434. Кнопка из объявления
            if (sub.announceUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = { onOpen(sub.announceUrl) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
                ) { Text(sub.announceButton.ifBlank { "Подробнее" }, fontWeight = FontWeight.SemiBold) }
            }
        }

    }
}

/** Д6. Плитки главного экрана: щит, скорость, пинг, сервер, режим, таймер. */
@Composable
private fun BentoGrid(
    status: VpnStatus,
    connectedAt: Long,
    speed: Pair<Long, Long>,
    livePing: List<Int>,
    serverTitle: String,
    serverFlag: String?,
    modeTitle: String,
    timerText: String,
    timerOn: Boolean,
    onToggle: () -> Unit,
    onServers: () -> Unit,
    onMode: () -> Unit,
    onTimer: () -> Unit,
    onSpeedTest: () -> Unit,
) {
    val gap = 10.dp
    val label = when (status) {
        VpnStatus.Started -> "Защищено"
        VpnStatus.Starting -> "Подключение…"
        VpnStatus.Stopping -> "Отключение…"
        VpnStatus.Paused -> "Пауза"
        VpnStatus.Blocked -> "Интернет заблокирован"
        VpnStatus.Stopped -> "Не подключено"
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(IntrinsicSize.Min)) {
            // Большая плитка: щит и состояние
            Column(
                Modifier.weight(1.25f).fillMaxHeight().apexCard(Apex.HeroCard).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShieldButton(status, onToggle, 150.dp)
                Text(label, color = Apex.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                if (status == VpnStatus.Started && connectedAt > 0) ConnectionTimer(connectedAt)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                BentoTile("Скорость", Modifier.weight(1f)) {
                    Text("↓ ${formatSpeed(speed.first)}", color = Apex.Good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("↑ ${formatSpeed(speed.second)}", color = Apex.AccentText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                BentoTile("Пинг", Modifier.weight(1f)) {
                    val last = livePing.lastOrNull { it >= 0 }
                    Text(if (status == VpnStatus.Started && last != null) "$last мс" else "—", color = pingColor(last ?: -1), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile("Сервер", Modifier.weight(1.4f), onClick = onServers) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (serverFlag != null) {
                        FlagCircle(serverFlag, 26.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(serverTitle, color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            BentoTile("Режим", Modifier.weight(1f), onClick = onMode) {
                Text(modeTitle, color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            BentoTile("Таймер", Modifier.weight(1f), onClick = onTimer) {
                Text(timerText, color = if (timerOn) Apex.AccentText else Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            BentoTile("Тест", Modifier.weight(1f), onClick = onSpeedTest) {
                Text("Скорость сети", color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BentoTile(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .apexCard()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(title, color = Apex.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ConnectionTimer(connectedAt: Long) {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(connectedAt) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    Text(formatDuration(now - connectedAt), color = Apex.TextDim, fontSize = 16.sp, letterSpacing = 1.sp)
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 160. «Осталось 12 дн. 4 ч», в последний день — часы и минуты. */
@Composable
private fun countdown(leftMs: Long): Pair<String, androidx.compose.ui.graphics.Color> {
    if (leftMs <= 0) return "Подписка закончилась" to Apex.Bad
    val minutes = leftMs / 60_000
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    val text = when {
        days >= 1 -> "Осталось ${days} ${plural(days, "день", "дня", "дней")} ${hours} ч"
        hours >= 1 -> "Осталось ${hours} ч ${mins} мин"
        else -> "Осталось ${mins} мин"
    }
    val color = when {
        days >= 7 -> Apex.Good
        days >= 2 -> Apex.Warn
        else -> Apex.Bad
    }
    return text to color
}

private fun plural(n: Long, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1L && m100 != 11L -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

/**
 * 157/563. Фото из галереи на весь экран. Размытие и затемнение настраиваются,
 * а с «Движением» фото чуть сдвигается при наклоне телефона (как обои на iPhone).
 */
@Composable
fun PhotoBackdrop(stamp: Long, blurDp: Int = 6, dim: Int = 55, parallax: Boolean = true, motion: Boolean = false) {
    val context = LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, stamp) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.apexvpn.client.data.Backdrop.load(context)?.asImageBitmap()
        }
    }
    val img = bitmap ?: return
    val base = Apex.Base
    // Размытие есть только на Android 12+
    val blur = blurDp > 0 && android.os.Build.VERSION.SDK_INT >= 31
    val move = parallax && !Apex.feel.smooth
    val d = dim.coerceIn(0, 90) / 100f
    // 3.20. «Живое фото»: медленное приближение и сдвиг по кругу (40 с)
    val live = motion && !Apex.feel.smooth
    val kb = if (live) androidx.compose.animation.core.rememberInfiniteTransition(label = "kb").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(40_000, easing = androidx.compose.animation.core.LinearEasing)), label = "kbT",
    ) else null
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            img, null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (kb != null) {
                        val a = kb.value * 2f * Math.PI.toFloat()
                        val sc = 1.14f + 0.06f * kotlin.math.sin(a)
                        scaleX = sc; scaleY = sc
                        translationX = kotlin.math.cos(a) * size.width * 0.035f
                        translationY = kotlin.math.sin(a * 2f) * size.height * 0.02f
                    }
                    if (move && kb == null) {
                        // Чуть увеличиваем, чтобы при сдвиге не показывались края
                        scaleX = 1.1f; scaleY = 1.1f
                        translationX = -com.apexvpn.client.ui.theme.Tilt.x * size.width * 0.045f
                        translationY = -com.apexvpn.client.ui.theme.Tilt.y * size.height * 0.03f
                    }
                }
                .then(if (blur) Modifier.blur(blurDp.dp) else Modifier),
        )
        // Затемнение: сверху и снизу сильнее, чтобы читались заголовок и кнопки
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        base.copy(alpha = (d + 0.1f).coerceAtMost(0.95f)),
                        base.copy(alpha = d * 0.45f),
                        base.copy(alpha = (d + 0.3f).coerceAtMost(0.95f)),
                    ),
                ),
            ),
        )
    }
}

/** 354. Фото дня страны на весь экран, с затемнением и подписью. */
@Composable
private fun CountryPhotoBackdrop(code: String) {
    val context = LocalContext.current
    val photo by androidx.compose.runtime.produceState<com.apexvpn.client.data.CountryPhoto.Photo?>(null, code) {
        value = com.apexvpn.client.data.CountryPhoto.load(context, code)
    }
    val p = photo ?: return
    val img = remember(p) { p.bitmap.asImageBitmap() }
    val base = Apex.Base
    // Плавное появление
    val alpha = remember(p) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(p) { alpha.animateTo(1f, androidx.compose.animation.core.tween(700)) }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        androidx.compose.foundation.Image(
            img, null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(base.copy(alpha = 0.6f), base.copy(alpha = 0.3f), base.copy(alpha = 0.9f)),
                ),
            ),
        )
        Text(
            "📷 " + p.caption,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}



/** «🎮 Игры · » перед подписью автопилота (для обычного режима — пусто). */
private fun autoModePrefix(id: String): String {
    val m = com.apexvpn.client.data.AutoModes.Mode.of(id)
    return if (m == com.apexvpn.client.data.AutoModes.Mode.Balance) "" else "${m.emoji} ${m.title} · "
}


/** 615. «Доброе утро!» / «Добрый день!» / «Добрый вечер!» / «Доброй ночи!» по часам телефона. */
private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "☀️ Доброе утро!"
    in 12..17 -> "🌤 Добрый день!"
    in 18..22 -> "🌆 Добрый вечер!"
    else -> "🌙 Доброй ночи!"
}

/** 3.20.5. Минимальное затемнение обоев (фото и видео) на главном экране — чтобы щит и текст было видно. */
const val MIN_DIM = 30


/**
 * 3.20.10. Плашка «Вышла версия X · Обновить» вверху главного экрана.
 * Видна, когда есть новая версия, а окно обновления закрыли кнопкой «Позже».
 * Нажатие — сразу скачать, проверить и установить (окно снова покажет ход загрузки).
 */
@Composable
private fun UpdatePill(viewModel: MainViewModel) {
    val state by viewModel.update.collectAsStateWithLifecycle()
    val later by com.apexvpn.client.update.UpdateManager.laterCode.collectAsStateWithLifecycle()
    val info = (state as? com.apexvpn.client.update.UpdateState.Available)?.info ?: return
    if (info.mandatory || later != info.versionCode) return
    val context = LocalContext.current
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Apex.Surface)
            .border(1.dp, Apex.Text.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable {
                com.apexvpn.client.update.UpdateManager.reopen()
                if (info.apkUrl != null && info.shaUrl != null) viewModel.installUpdate(info)
                else runCatching { com.apexvpn.client.update.UpdateManager.openPage(context, info) }
            }
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Apex.Text), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = Apex.Surface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Вышла версия ${info.versionName}" + if (info.beta) " (бета)" else "",
            color = Apex.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("Обновить", color = Apex.TextDim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
