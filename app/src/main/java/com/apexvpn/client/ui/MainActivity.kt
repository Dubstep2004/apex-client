package com.apexvpn.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.apexvpn.client.ui.theme.customSpec
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.Changelog
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.ui.theme.ApexTheme
import com.apexvpn.client.ui.theme.applyPalette
import com.apexvpn.client.ui.theme.paletteFor
import com.apexvpn.client.vpn.ApexVpnService
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.vpn.VpnToggle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class Tab(val title: String, val icon: ImageVector) {
    Home("VPN", Icons.Rounded.PowerSettingsNew),
    Mode("Режим", Icons.Rounded.Public),
    Settings("Настройки", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {

    companion object {
        /** Из уведомления «обновление готово» — сразу открыть установщик */
        const val EXTRA_INSTALL_UPDATE = "install_update"
        /** 3.5: открыть страницу по имени (Page) — из уведомления VPN */
        const val EXTRA_OPEN_PAGE = "open_page"
    }

    private val viewModel: MainViewModel by viewModels()

    /** Ссылка подписки из QR-кода, «Поделиться» или apexvpn:// — ждёт подтверждения. */
    private val pendingImport = MutableStateFlow<String?>(null)
    private val pendingPage = MutableStateFlow<Page?>(null)

    /** 601. Сайт из «Поделиться» в браузере — ждёт выбора «Через VPN» или «Напрямую». */
    private val pendingSite = MutableStateFlow<String?>(null)

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            ApexVpnService.start(this)
        } else {
            viewModel.showMessage("Без разрешения на VPN подключиться нельзя")
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { connectNow() }

    /** 262. QR переноса с другого телефона — ждёт подтверждения. */
    private val pendingTransfer = MutableStateFlow<org.json.JSONObject?>(null)

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        if (com.apexvpn.client.data.Transfer.isTransfer(text)) {
            val o = com.apexvpn.client.data.Transfer.decode(text)
            if (o != null) pendingTransfer.value = o else viewModel.showMessage("QR-код переноса повреждён — попробуйте ещё раз")
            return@registerForActivityResult
        }
        val link = Subscription.extractLink(text)
        if (link != null) pendingImport.value = link else viewModel.showMessage("В QR-коде нет ссылки на ${com.apexvpn.client.data.SubHosts.primary()}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 3.9.1: открылись после обновления — уведомление «Обновлено, открыть» больше не нужно
        runCatching { getSystemService(android.app.NotificationManager::class.java)?.cancel(6) }
        // Тема: палитра и цвет значков строки состояния меняются вместе с настройками
        lifecycleScope.launch {
            // 588. Пока VPN включён на автопилоте, щит в цветах режима (🎮 красный, 🚀 голубой…)
            kotlinx.coroutines.flow.combine(
                AppSettings.state,
                com.apexvpn.client.vpn.VpnState.status,
                com.apexvpn.client.data.Subscription.state,
            ) { s, st, sub -> Triple(s, st == com.apexvpn.client.vpn.VpnStatus.Started, sub.selectedTag == com.apexvpn.client.data.AUTO_TAG) }
                .distinctUntilChanged()
                .collect { (s, on, auto) ->
                val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                // Material Expressive: акцент из обоев (Android 12+)
                val wallpaper = if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_400)) }.getOrNull()
                } else null
                val base = paletteFor(s.design, s.theme, s.accent, systemDark, wallpaper, customArgb = s.customAccent, customArgb2 = s.customAccent2, custom = s.customSpec())
                val mode = com.apexvpn.client.data.AutoModes.Mode.of(s.autoMode)
                val p = if (s.modeColors && on && auto && mode.c1 != 0L) {
                    base.copy(brand1 = androidx.compose.ui.graphics.Color(mode.c1), brand2 = androidx.compose.ui.graphics.Color(mode.c2))
                } else base
                applyPalette(p)
                com.apexvpn.client.ui.theme.applyFeel(
                    com.apexvpn.client.ui.theme.Feel(s.vibration, s.smoothMode, s.glassBlur, s.liveMap, s.shield3d),
                )
                val style = if (p.dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        // «Подключать при открытии»: только при настоящем запуске (не при повороте экрана),
        // если есть серверы и разрешение на VPN уже дано
        if (savedInstanceState == null && AppSettings.value.connectOnOpen &&
            com.apexvpn.client.vpn.VpnState.status.value == com.apexvpn.client.vpn.VpnStatus.Stopped &&
            com.apexvpn.client.data.Subscription.state.value.nodes.isNotEmpty() &&
            VpnService.prepare(this) == null
        ) {
            ApexVpnService.start(this)
        }

        setContent {
            ApexTheme {
                Surface(Modifier.fillMaxSize(), color = Apex.Base) {
                    val sub by viewModel.subscription.collectAsStateWithLifecycle()
                    val message by viewModel.message.collectAsStateWithLifecycle()
                    val importLink by pendingImport.collectAsStateWithLifecycle()
                    val transfer by pendingTransfer.collectAsStateWithLifecycle()
                    val snackbar = remember { SnackbarHostState() }
                    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                    var page by rememberSaveable { mutableStateOf<Page?>(null) }
                    var serversOpen by rememberSaveable { mutableStateOf(false) }
                    var splash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                    val requested by pendingPage.collectAsStateWithLifecycle()
                    LaunchedEffect(requested) {
                        requested?.let { page = it; splash = false; pendingPage.value = null }
                    }

                    LaunchedEffect(message) {
                        message?.let {
                            snackbar.showSnackbar(it)
                            viewModel.consumeMessage()
                        }
                    }

                    val st by viewModel.settings.collectAsStateWithLifecycle()
                    val tv = remember { isTv(this@MainActivity) }
                    // 313. Блики Liquid Glass 2.0 следуют за наклоном телефона
                    com.apexvpn.client.ui.theme.TiltSensor((st.design == com.apexvpn.client.data.Design.Glass || st.shield3d ||
                        (st.shieldBackdrop == "photo" && st.photoParallax)) && !st.smoothMode)
                    var onboarded by remember { mutableStateOf(com.apexvpn.client.data.Prefs.onboarded) }
                    ThemeRevealHost {
                        // Фон рисуем внутри — чтобы «волна» меняла и его
                        com.apexvpn.client.ui.theme.DesignBackground()
                        // 3.8. Сезонное оформление (выключается в «Оформлении»)
                        val powerSave = remember { (getSystemService(android.os.PowerManager::class.java))?.isPowerSaveMode == true }
                        if (st.seasonal && !st.smoothMode && !powerSave && st.design != com.apexvpn.client.data.Design.Sakura && st.design != com.apexvpn.client.data.Design.Matrix) com.apexvpn.client.ui.theme.SeasonalOverlay()
                        if (sub.url.isEmpty() && !onboarded) {
                            // 320. Знакомство при первом запуске
                            Onboarding {
                                com.apexvpn.client.data.Prefs.onboarded = true
                                onboarded = true
                            }
                        } else if (sub.url.isEmpty()) {
                            SetupScreen(viewModel, onScanQr = { scanQr() })
                        } else {
                            BackHandler(enabled = page == null && tab != Tab.Home) { tab = Tab.Home }
                            Column(Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = tab,
                                    // 212. Вкладки «выезжают» в сторону нажатой, а не просто растворяются
                                    transitionSpec = {
                                        if (Apex.feel.smooth) {
                                            fadeIn(androidx.compose.animation.core.tween(150)) togetherWith fadeOut(androidx.compose.animation.core.tween(150))
                                        } else {
                                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                            val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                            (slideInHorizontally(spec) { w -> dir * w / 5 } + fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60))) togetherWith
                                                (slideOutHorizontally(spec) { w -> -dir * w / 5 } + fadeOut(androidx.compose.animation.core.tween(180)))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = "tab",
                                ) { current ->
                                    when (current) {
                                        Tab.Home -> if (tv) {
                                            // 343. Android TV 2.0
                                            TvHomeScreen(viewModel, onToggle = { toggleVpn() })
                                        } else HomeScreen(
                                            viewModel = viewModel,
                                            onToggle = { toggleVpn() },
                                            onOpenServers = { serversOpen = true },
                                            onOpenMode = { tab = Tab.Mode },
                                            onOpenPage = { page = it },
                                        )
                                        Tab.Mode -> ModeScreen(viewModel, onOpenPage = { page = it })
                                        Tab.Settings -> SettingsScreen(viewModel, onOpenPage = { page = it }, onScanQr = { scanQr() })
                                    }
                                }
                                BottomBar(tab) { tab = it }
                            }
                            // Страницы поверх вкладок
                            AnimatedVisibility(
                                visible = page != null,
                                enter = slideInHorizontally(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
                                    fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.96f),
                                exit = slideOutHorizontally(androidx.compose.animation.core.tween(260)) { it } + fadeOut() +
                                    androidx.compose.animation.scaleOut(targetScale = 0.96f),
                            ) {
                                val current = page
                                if (current != null) {
                                    BackHandler { page = null }
                                    PageHost(current, viewModel, onOpen = { page = it }) { page = null }
                                }
                            }
                            if (serversOpen) {
                                ServerSheet(viewModel) { serversOpen = false }
                            }
                        }
                        SnackbarHost(
                            snackbar,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
                        )
                        // 382. Конфетти
                        ConfettiOverlay { text -> viewModel.showMessage(text) }
                        // 63. Анимированная заставка
                        AnimatedVisibility(visible = splash, exit = fadeOut()) {
                            SplashScreen { splash = false }
                        }
                    }

                    val siteToAdd by pendingSite.collectAsStateWithLifecycle()
                    siteToAdd?.let { site ->
                        AlertDialog(
                            onDismissRequest = { pendingSite.value = null },
                            containerColor = Apex.Dialog,
                            title = { Text("Сайт $site", color = Apex.Text) },
                            text = { Text("Как открывать этот сайт? Его можно убрать в «Режим → Свои сайты».", color = Apex.TextDim) },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingSite.value = null
                                    viewModel.showMessage(viewModel.addDomain(site, direct = false) ?: "$site теперь через VPN")
                                }) { Text("Через VPN", color = Apex.Accent) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    pendingSite.value = null
                                    viewModel.showMessage(viewModel.addDomain(site, direct = true) ?: "$site теперь напрямую")
                                }) { Text("Напрямую", color = Apex.TextDim) }
                            },
                        )
                    }
                    importLink?.let { link ->
                        AlertDialog(
                            onDismissRequest = { pendingImport.value = null },
                            containerColor = Apex.Dialog,
                            title = { Text("Добавить подписку?", color = Apex.Text) },
                            text = { Text(maskLink(link), color = Apex.TextDim) },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingImport.value = null
                                    viewModel.setSubscription(link) { err -> viewModel.showMessage(err ?: "Подписка добавлена") }
                                }) { Text("Добавить", color = Apex.Accent) }
                            },
                            dismissButton = { TextButton(onClick = { pendingImport.value = null }) { Text("Отмена", color = Apex.TextDim) } },
                        )
                    }
                    transfer?.let { o ->
                        AlertDialog(
                            onDismissRequest = { pendingTransfer.value = null },
                            containerColor = Apex.Dialog,
                            title = { Text("Перенести с другого телефона?", color = Apex.Text) },
                            text = {
                                Text(
                                    "Перенесётся: " + com.apexvpn.client.data.Transfer.summary(o) + ". Текущие настройки на этом телефоне заменятся.",
                                    color = Apex.TextDim,
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    pendingTransfer.value = null
                                    val restored = com.apexvpn.client.data.SettingsBackup.restore(o)
                                    if (restored == null) {
                                        viewModel.showMessage("Не удалось применить настройки")
                                    } else {
                                        viewModel.applyRestored(restored, "Всё перенесено")
                                    }
                                }) { Text("Перенести", color = Apex.Accent) }
                            },
                            dismissButton = { TextButton(onClick = { pendingTransfer.value = null }) { Text("Отмена", color = Apex.TextDim) } },
                        )
                    }
                    // Защита от подделок: при каждом запуске неофициальной копии — предупреждение
                    var cloneWarning by rememberSaveable {
                        mutableStateOf(com.apexvpn.client.data.Integrity.status(this@MainActivity) == com.apexvpn.client.data.Integrity.Status.Unofficial)
                    }
                    if (cloneWarning && !splash) {
                        AlertDialog(
                            onDismissRequest = { },
                            containerColor = Apex.Dialog,
                            title = { Text("⚠ Неофициальная копия", color = Apex.Bad) },
                            text = {
                                Text(
                                    "Это приложение похоже на APEX VPN, но подписано не автором. В копии может быть чужой код: " +
                                        "реклама, слежка или кража ссылки подписки.\n\n" +
                                        "Оригинал APEX VPN скачивается только со страницы github.com/Dubstep2004/apex-vpn.",
                                    color = Apex.TextDim,
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(com.apexvpn.client.data.Integrity.OFFICIAL_URL)))
                                    }
                                }) { Text("Скачать оригинал", color = Apex.Accent, fontWeight = FontWeight.SemiBold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { cloneWarning = false }) { Text("Понимаю риск", color = Apex.TextDim) }
                            },
                        )
                    }
                    // «Что нового» один раз после обновления приложения
                    var whatsNew by remember { mutableStateOf(Changelog.unseen(BuildConfig.VERSION_CODE)) }
                    if (whatsNew.isNotEmpty() && !splash && sub.url.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { Changelog.markSeen(BuildConfig.VERSION_CODE); whatsNew = emptyList() },
                            containerColor = Apex.Dialog,
                            title = { Text("Обновлено до ${BuildConfig.VERSION_NAME}", color = Apex.Text) },
                            text = {
                                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    whatsNew.forEach { r ->
                                        Text("Версия ${r.version}", color = Apex.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        com.apexvpn.client.ui.ChangeList(r.changes, textColor = Apex.TextDim)
                                        Spacer(Modifier.height(10.dp))
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { Changelog.markSeen(BuildConfig.VERSION_CODE); whatsNew = emptyList() }) {
                                    Text("Отлично", color = Apex.Accent)
                                }
                            },
                        )
                    }
                    UpdateDialog(viewModel)
                    // 3.18. Админка отозвала эту версию — предложить вернуться на прошлую
                    val adminCfg by com.apexvpn.client.data.AppConfig.state.collectAsStateWithLifecycle()
                    var revokedShown by remember { mutableStateOf(false) }
                    if (!revokedShown && BuildConfig.VERSION_CODE in adminCfg.revoked) {
                        AlertDialog(
                            onDismissRequest = { revokedShown = true },
                            containerColor = Apex.Dialog,
                            title = { Text("В этой версии нашли ошибку", color = Apex.Text) },
                            text = { Text("Версия ${BuildConfig.VERSION_NAME} отозвана. Лучше вернуться на прошлую или дождаться исправления — оно придёт обновлением.", color = Apex.TextDim) },
                            confirmButton = {
                                TextButton(onClick = { revokedShown = true; pendingPage.value = com.apexvpn.client.ui.Page.Rollback }) { Text("Вернуть прошлую", color = Apex.Accent) }
                            },
                            dismissButton = { TextButton(onClick = { revokedShown = true }) { Text("Позже", color = Apex.TextDim) } },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 3.18: открыли приложение — свежие настройки из админки (не чаще раза в 5 минут)
        lifecycleScope.launch {
            if (runCatching { com.apexvpn.client.data.AppConfig.refresh() }.getOrDefault(false) &&
                VpnState.status.value == VpnStatus.Started
            ) ApexVpnService.reload(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 32. apexvpn://import?url=…, «Поделиться» ссылкой, запуск из плитки/виджета. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_OPEN_PAGE)?.let { name ->
            intent.removeExtra(EXTRA_OPEN_PAGE)
            Page.entries.firstOrNull { it.name == name }?.let { pendingPage.value = it }
        }
        if (intent.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) {
            intent.removeExtra(EXTRA_INSTALL_UPDATE)
            // 3.4: из уведомления «Доступна версия» — сразу скачиваем и ставим (диалог покажет ход)
            lifecycleScope.launch {
                com.apexvpn.client.update.UpdateManager.check(silent = true)
                val info = (com.apexvpn.client.update.UpdateManager.state.value as? com.apexvpn.client.update.UpdateState.Available)?.info
                when {
                    info == null -> runCatching { com.apexvpn.client.update.UpdateManager.openPage(this@MainActivity, null) }
                    info.apkUrl != null && info.shaUrl != null -> {
                        com.apexvpn.client.data.Prefs.dismissedUpdateCode = 0
                        com.apexvpn.client.update.UpdateInstaller.updateNow(this@MainActivity, info)
                    }
                    else -> runCatching { com.apexvpn.client.update.UpdateManager.openPage(this@MainActivity, info) }
                }
            }
        }
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val link = Subscription.extractLink(text)
        if (link != null) {
            pendingImport.value = link
        } else if (text != null) {
            // 601. «Поделиться» ссылкой на сайт из браузера — добавить сайт в свой список
            val url = Regex("""https?://[^\s]+""").find(text)?.value ?: text.trim().takeIf { !it.contains(' ') }
            val site = url?.let { AppSettings.normalizeDomain(it) }
            if (site != null) pendingSite.value = site
            else viewModel.showMessage("Поддерживаются ссылки на сайты и подписки ${com.apexvpn.client.data.SubHosts.primary()}")
        }
        // 9/16. Ярлыки и голосовые команды
        when (intent.action) {
            com.apexvpn.client.vpn.Shortcuts.ACTION_CONNECT -> {
                connectFromShortcut(intent.getStringExtra(com.apexvpn.client.vpn.Shortcuts.EXTRA_SERVER))
                intent.action = null
                return
            }
            com.apexvpn.client.vpn.Shortcuts.ACTION_DISCONNECT -> {
                ApexVpnService.stop(this)
                intent.action = null
                return
            }
            com.apexvpn.client.vpn.Shortcuts.ACTION_FEATURE -> {
                val feature = intent.getStringExtra("feature").orEmpty().lowercase()
                if (listOf("выкл", "откл", "disconnect", "стоп").any { it in feature }) ApexVpnService.stop(this) else connectFromShortcut(null)
                intent.action = null
                return
            }
        }
        if (intent.getBooleanExtra(VpnToggle.EXTRA_CONNECT, false) && VpnState.status.value == VpnStatus.Stopped &&
            Subscription.state.value.nodes.isNotEmpty()
        ) {
            intent.removeExtra(VpnToggle.EXTRA_CONNECT)
            toggleVpn()
        }
    }

    /** Подключиться к серверу по названию из ярлыка ("auto" — автопилот, null — текущий). */
    private fun connectFromShortcut(server: String?) {
        val nodes = Subscription.state.value.nodes
        if (nodes.isEmpty()) return
        when {
            server == null -> Unit
            server == com.apexvpn.client.data.AUTO_TAG -> viewModel.select(com.apexvpn.client.data.AUTO_TAG)
            else -> nodes.firstOrNull { com.apexvpn.client.data.ServerInfo.title(it.name) == server }
                ?.let { viewModel.select(it.tag) }
                ?: viewModel.showMessage("Сервера «$server» больше нет в подписке — подключаю автопилот")
        }
        if (VpnState.status.value == VpnStatus.Stopped) toggleVpn()
    }

    private fun scanQr() {
        qrScanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код подписки или переноса")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }

    private fun toggleVpn() {
        when (VpnState.status.value) {
            VpnStatus.Started, VpnStatus.Starting, VpnStatus.Paused, VpnStatus.Blocked -> ApexVpnService.stop(this)
            VpnStatus.Stopping -> Unit
            VpnStatus.Stopped -> {
                VpnState.clearError()
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    connectNow()
                }
            }
        }
    }

    private fun connectNow() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else ApexVpnService.start(this)
    }
}

private fun maskLink(url: String): String {
    val idx = url.indexOf('/', "https://".length)
    if (idx < 0) return url
    val path = url.substring(idx + 1)
    return url.substring(0, idx + 1) + if (path.length > 6) path.take(3) + "•••" + path.takeLast(3) else "•••"
}

/**
 * Нижняя навигация 2.0: плавающая «пилюля», выделение плавно переезжает
 * к нажатой вкладке, значок слегка подпрыгивает.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val smooth = Apex.feel.smooth
    Box(
        Modifier
            .fillMaxWidth()
            .background(Apex.Bg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Apex.Surface)
                .border(1.dp, Apex.CardBorder, RoundedCornerShape(28.dp))
                .padding(6.dp),
        ) {
            val count = Tab.entries.size
            val itemWidth = maxWidth / count
            val offset by androidx.compose.animation.core.animateDpAsState(
                itemWidth * current.ordinal,
                if (smooth) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 420f),
                label = "tabIndicator",
            )
            // Переезжающее выделение
            Box(
                Modifier
                    .offset(x = offset)
                    .width(itemWidth)
                    .height(54.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Apex.SurfaceHigh),
            )
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    val selected = t == current
                    val lift by androidx.compose.animation.core.animateFloatAsState(
                        if (selected && !smooth) 1f else 0f,
                        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
                        label = "tabLift",
                    )
                    val tint by androidx.compose.animation.animateColorAsState(if (selected) Apex.Accent else Apex.TextDim, label = "tabTint")
                    Column(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            t.icon, null, tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    translationY = -lift * 2.dp.toPx()
                                    scaleX = 1f + lift * 0.12f
                                    scaleY = 1f + lift * 0.12f
                                },
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.title,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Apex.Text else Apex.TextDim,
                        )
                    }
                }
            }
        }
    }
}
