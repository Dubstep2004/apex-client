package com.apexvpn.client.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.SettingsBackup
import com.apexvpn.client.update.UpdateInfo
import com.apexvpn.client.update.UpdateInstaller
import com.apexvpn.client.update.UpdateManager
import com.apexvpn.client.ui.theme.Apex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 3.7. Вернуть прошлую версию. Android не ставит старую версию поверх новой — только после удаления,
 * а удаление стирает данные. Поэтому по шагам: копия настроек → файл прошлой версии → удалить → установить → загрузить копию.
 */
@Composable
internal fun RollbackPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<UpdateInfo>?>(null) }
    var chosen by remember { mutableStateOf<UpdateInfo?>(null) }
    var backupDone by remember { mutableStateOf(false) }
    var apkSaved by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { versions = runCatching { UpdateManager.previousVersions() }.getOrDefault(emptyList()) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SettingsBackup.MIME)) { uri ->
        if (uri != null) {
            backupDone = SettingsBackup.export(ctx, uri)
            viewModel.showMessage(if (backupDone) "Копия настроек сохранена" else "Не удалось сохранить файл")
        }
    }
    val apkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        val info = chosen
        if (uri != null && info != null) scope.launch {
            val apk = info.apkUrl; val sha = info.shaUrl
            if (apk == null || sha == null) { progress = "У этой версии нет контрольной суммы — скачайте её на странице версии"; return@launch }
            val (file, err) = UpdateInstaller.fetchVerified(ctx, apk, sha, "rollback-${info.versionName}.apk") { progress = "Скачиваем ${info.versionName}: $it%" }
            if (file == null) { progress = err; return@launch }
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }; file.delete() }.isSuccess
            }
            progress = null
            apkSaved = if (ok) info.versionName else null
            if (!ok) viewModel.showMessage("Не удалось сохранить файл")
        }
    }

    PageScaffold("Вернуть прошлую версию", onBack) {
        Caption("Android не ставит старую версию поверх новой — только после удаления приложения, а удаление стирает настройки. Поэтому по шагам: сохраняем копию, скачиваем прошлую версию, удаляем, ставим и загружаем копию. Сейчас установлена ${BuildConfig.VERSION_NAME}.")
        Spacer(Modifier.height(12.dp))

        SectionLabel("1. Копия настроек и подписки")
        Group {
            ListRow(
                if (backupDone) "✅ Копия сохранена" else "Сохранить копию в файл",
                "Подписка, серверы, списки, оформление — всё в одном файле",
                onClick = { backupLauncher.launch(SettingsBackup.fileName()) },
            ) { Tag(if (backupDone) "Ещё раз" else "Сохранить") }
        }

        SectionLabel("2. Прошлая версия")
        val list = versions
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Apex.Accent) }
            list.isEmpty() -> Caption("Прошлых версий на GitHub не нашлось.")
            else -> Group {
                list.forEachIndexed { i, v ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        (if (apkSaved == v.versionName) "✅ " else "") + "APEX VPN ${v.versionName}",
                        if (apkSaved == v.versionName) "Файл сохранён — шаг 3" else "Сохранить файл apex-vpn-${v.versionName}.apk",
                        onClick = { chosen = v; progress = null; apkLauncher.launch("apex-vpn-${v.versionName}.apk") },
                    ) { Tag("Скачать") }
                }
            }
        }
        progress?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Apex.Text, fontSize = 13.sp) }

        SectionLabel("3. Удалить и поставить")
        Group {
            ListRow(
                "Удалить APEX VPN",
                if (backupDone && apkSaved != null) "Затем откройте сохранённый apex-vpn-$apkSaved.apk в «Файлах» и установите"
                else "Сначала сохраните копию и файл прошлой версии",
                titleColor = if (backupDone && apkSaved != null) Apex.Bad else Apex.TextDim,
                onClick = {
                    if (backupDone && apkSaved != null) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${ctx.packageName}"))) }
                    } else viewModel.showMessage("Сначала шаги 1 и 2 — иначе настройки потеряются")
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("4. После установки откройте APEX VPN → Настройки → «Резервная копия» → «Восстановить из файла» и выберите сохранённую копию.",
            color = Apex.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        Spacer(Modifier.height(24.dp))
    }
}
