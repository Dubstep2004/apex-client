package com.apexvpn.client.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AUTO_TAG
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.ui.theme.Apex
import com.apexvpn.client.vpn.VpnState
import com.apexvpn.client.vpn.VpnStatus
import com.apexvpn.client.vpn.formatSpeed

/** Телевизор с Android TV / Google TV. */
fun isTv(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/**
 * 343. Android TV 2.0: большой глобус слева, щит в центре, справа — страны крупными
 * карточками. Всё управляется пультом: выделенная карточка увеличивается и светится.
 */
@Composable
fun TvHomeScreen(viewModel: MainViewModel, onToggle: () -> Unit) {
    val status by VpnState.status.collectAsStateWithLifecycle()
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val pings by viewModel.pings.collectAsStateWithLifecycle()
    val speed by VpnState.speed.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val me = remember { userCountry(context) }
    LaunchedEffect(Unit) { viewModel.pingAll() }

    val selected = sub.nodes.find { it.tag == sub.selectedTag }
    val auto = sub.selectedTag == AUTO_TAG || selected == null
    // Страны: по одной карточке на сервер-группу (без рекламных)
    val groups = remember(sub.nodes) { sub.nodes.filter { !it.ads }.distinctBy { ServerInfo.title(it.name) } }
    val target = if (auto) groups.mapNotNull { n -> pings[n.tag]?.takeIf { it > 0 }?.let { n to it } }.minByOrNull { it.second }?.first else selected
    val shieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { shieldFocus.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        // Глобус
        Column(Modifier.weight(1.15f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            ApexWordmark()
            Spacer(Modifier.height(12.dp))
            Globe(
                Modifier.fillMaxWidth().aspectRatio(1.15f),
                from = WorldMap.coords[me],
                to = target?.let { flagCode(ServerInfo.flag(it.name)) }?.let { WorldMap.coords[it] },
                connected = status == VpnStatus.Started,
                busy = status == VpnStatus.Starting,
                spin = target == null,
            )
        }
        // Щит
        Column(Modifier.weight(0.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.focusRequester(shieldFocus)) { ShieldButton(status, onToggle, diameter = 220.dp) }
            Spacer(Modifier.height(12.dp))
            Text(
                when (status) {
                    VpnStatus.Started -> "Защищено"
                    VpnStatus.Starting -> "Подключение…"
                    VpnStatus.Stopping -> "Отключение…"
                    VpnStatus.Paused -> "Пауза"
                    VpnStatus.Blocked -> "Интернет заблокирован"
                    VpnStatus.Stopped -> "Не подключено"
                },
                color = Apex.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (auto) "Автопилот" + (target?.let { " · " + ServerInfo.title(it.name) } ?: "") else ServerInfo.title(selected!!.name),
                color = Apex.TextDim, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (status == VpnStatus.Started) {
                Spacer(Modifier.height(8.dp))
                Text("↓ ${formatSpeed(speed.first)}   ↑ ${formatSpeed(speed.second)}", color = Apex.Good, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            // 3.18.1: баннер из пульта для телевизоров
            val tvCfg by com.apexvpn.client.data.AppConfig.state.collectAsStateWithLifecycle()
            tvCfg.bannerFor("tv")?.let { b ->
                Spacer(Modifier.height(12.dp))
                Text(
                    b.text, color = Apex.Text, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background((when (b.level) { "alert" -> Apex.Bad; "warn" -> Apex.Warn; else -> Apex.Accent }).copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("OK на пульте — подключить · → страны", color = Apex.TextDim, fontSize = 14.sp)
        }
        // Страны
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Страны", color = Apex.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    TvCountry(null, "Автопилот", "Самый быстрый", auto) { viewModel.select(AUTO_TAG) }
                }
                items(groups, key = { it.tag }) { n ->
                    val p = pings[n.tag]
                    TvCountry(
                        ServerInfo.flag(n.name), ServerInfo.title(n.name),
                        if (p != null && p > 0) "$p мс" else n.category,
                        !auto && ServerInfo.title(selected!!.name) == ServerInfo.title(n.name),
                    ) { viewModel.select(n.tag) }
                }
            }
        }
    }
}

@Composable
private fun TvCountry(flag: String?, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tvScale")
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) Apex.SurfaceHigh else if (selected) Apex.SelectedCard else Apex.Surface)
            .border(3.dp, if (focused) Apex.Accent else if (selected) Apex.Accent.copy(alpha = 0.4f) else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flag == null) {
            IconTile(Apex.AccentSoft, 48.dp) { Icon(Icons.Rounded.Bolt, null, tint = Apex.AccentText) }
        } else {
            FlagCircle(flag, 48.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Apex.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Apex.TextDim, fontSize = 15.sp)
        }
        if (selected) Text("✓", color = Apex.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
