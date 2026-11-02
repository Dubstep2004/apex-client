package com.apexvpn.client.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.data.AppInfo
import com.apexvpn.client.ui.theme.Apex

/**
 * 3.5. «Приложение не работает?» — из уведомления VPN одной кнопкой: выбрать приложение
 * (недавно открытые — первыми), и оно пойдёт мимо VPN (как «Скрыть VPN»).
 */
object RecentApps {
    fun granted(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService<AppOpsManager>() ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openSettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Пакеты, открытые за последние 3 часа, свежие первыми (без самого APEX). */
    fun recent(ctx: Context): List<String> = runCatching {
        val usm = ctx.getSystemService<UsageStatsManager>() ?: return emptyList()
        val now = System.currentTimeMillis()
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3 * 3_600_000L, now)
            .filter { it.lastTimeUsed > now - 3 * 3_600_000L && it.packageName != ctx.packageName && it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
    }.getOrDefault(emptyList())
}

@Composable
internal fun AppIssuePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val appList by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var granted by remember { mutableStateOf(RecentApps.granted(ctx)) }
    var recent by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { viewModel.loadApps() }
    LaunchedEffect(granted) { if (granted) recent = RecentApps.recent(ctx) }
    // Вернулись из настроек Android — проверяем разрешение снова
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        granted = RecentApps.granted(ctx)
        onPauseOrDispose { }
    }

    fun pick(app: AppInfo) {
        if (app.packageName in s.hiddenApps) {
            viewModel.change { copy(hiddenApps = hiddenApps - app.packageName, hideTemplateSkip = hideTemplateSkip + app.packageName) }
            viewModel.showMessage("«${app.label}» снова идёт через VPN")
        } else {
            viewModel.change { copy(hiddenApps = hiddenApps + app.packageName, hideTemplateSkip = hideTemplateSkip - app.packageName) }
            viewModel.showMessage("«${app.label}» теперь идёт мимо VPN — откройте его заново. Вернуть: Режим → «Скрыть VPN»")
            onBack()
        }
    }

    PageScaffold("Приложение не работает?", onBack, scroll = false) {
        Caption("Банк, госуслуги, маркетплейс или игра не открываются с VPN? Выберите приложение — оно пойдёт мимо VPN и не увидит его. Остальные приложения остаются под защитой.")
        Spacer(Modifier.height(12.dp))
        if (!granted) {
            Group {
                ListRow(
                    "Показывать недавние приложения",
                    "Разрешите APEX видеть, какие приложения вы открывали, — нужное будет первым в списке",
                    onClick = { RecentApps.openSettings(ctx) },
                ) { Tag("Разрешить") }
            }
            Spacer(Modifier.height(10.dp))
        }
        SearchField(query, "Поиск приложения") { query = it }
        Spacer(Modifier.height(10.dp))
        val list = appList
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Apex.Accent) }
        } else {
            val order = remember(recent) { recent.withIndex().associate { it.value to it.index } }
            val filtered = list
                .filter { it.packageName != ctx.packageName }
                .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                .sortedWith(compareBy<AppInfo> { order[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
            LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Apex.Surface)) {
                items(filtered, key = { it.packageName }) { app ->
                    val hidden = app.packageName in s.hiddenApps
                    val icon = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                    ListRow(
                        title = app.label,
                        subtitle = when {
                            hidden -> "Уже мимо VPN · нажмите, чтобы вернуть через VPN"
                            app.packageName in order -> "Недавно открывали"
                            else -> null
                        },
                        leading = { if (icon != null) Image(icon, null, Modifier.size(40.dp)) else GlyphTile(app.label.take(1), Apex.Accent) },
                        onClick = { pick(app) },
                    ) { Tag(if (hidden) "Вернуть" else "Мимо VPN") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
