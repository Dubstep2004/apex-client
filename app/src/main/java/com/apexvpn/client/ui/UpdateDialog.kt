package com.apexvpn.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.update.InstallState
import com.apexvpn.client.update.UpdateInfo
import com.apexvpn.client.update.UpdateInstaller
import com.apexvpn.client.update.UpdateManager
import com.apexvpn.client.update.UpdateState
import com.apexvpn.client.ui.theme.Apex

/** 3.4. Диалог обновления: одна кнопка «Обновить» — скачать, проверить и установить. */
@Composable
fun UpdateDialog(viewModel: MainViewModel) {
    val state by viewModel.update.collectAsStateWithLifecycle()
    val install by UpdateInstaller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Failed -> s.info ?: return
        else -> return
    }
    // «Позже» запоминаем для этой версии, кроме обязательных обновлений
    if (state is UpdateState.Available && !info.mandatory && Prefs.dismissedUpdateCode == info.versionCode) return

    val busy = install is InstallState.Downloading || install is InstallState.Verifying || install is InstallState.Installing
    val canDismiss = !info.mandatory && !busy
    val oneClick = info.apkUrl != null && info.shaUrl != null

    fun later() {
        Prefs.dismissedUpdateCode = info.versionCode
        UpdateInstaller.reset()
        UpdateManager.dismiss()
    }

    AlertDialog(
        onDismissRequest = { if (canDismiss) later() },
        properties = DialogProperties(dismissOnBackPress = canDismiss, dismissOnClickOutside = canDismiss),
        containerColor = Apex.Dialog,
        title = { Text("Доступна версия ${info.versionName}", color = Apex.Text) },
        text = {
            Column {
                Text(
                    "Установлена версия ${BuildConfig.VERSION_NAME}" +
                        if (info.mandatory) ". Это обновление обязательно." else "",
                    color = Apex.TextDim,
                    fontSize = 13.sp,
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        info.changelog,
                        color = Apex.Text,
                        fontSize = 14.sp,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                Spacer(Modifier.height(10.dp))
                when (val i = install) {
                    is InstallState.Downloading -> {
                        Text("Загрузка обновления ${i.percent}%", color = Apex.Text, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { i.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Apex.Accent)
                    }
                    InstallState.Verifying -> Text("Проверяем файл (SHA-256 и подпись)…", color = Apex.Text, fontSize = 13.sp)
                    InstallState.Installing -> {
                        Text("Устанавливаем новую версию… Если Android спросит — нажмите «Обновить». APEX закроется на пару секунд и откроется снова, VPN включится сам.", color = Apex.Text, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Apex.Accent)
                    }
                    InstallState.NeedPermission -> Text(
                        "Разрешите установку обновлений из APEX CLIENT (откроются настройки Android), вернитесь и нажмите «Обновить» ещё раз.",
                        color = Apex.Accent, fontSize = 13.sp,
                    )
                    is InstallState.Failed -> Text(i.message, color = Apex.Bad, fontSize = 13.sp)
                    InstallState.Idle -> Text(
                        if (oneClick) "Приложение скачает новую версию с GitHub, проверит её и установит — настройки и подписка сохранятся."
                        else "Откроется страница версии на GitHub — скачайте там файл apex-client-${info.versionName}.apk и установите.",
                        color = Apex.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    if (oneClick) viewModel.installUpdate(info)
                    else {
                        runCatching { UpdateManager.openPage(context, info) }
                            .onFailure { viewModel.showMessage("Не удалось открыть браузер") }
                        if (!info.mandatory) later()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
            ) { Text(if (install is InstallState.Installing) "Установка…" else if (busy) "Загрузка…" else if (oneClick) "Обновить" else "Открыть на GitHub") }
        },
        dismissButton = {
            if (canDismiss) {
                TextButton(onClick = {
                    if (install is InstallState.Failed && oneClick) {
                        runCatching { UpdateManager.openPage(context, info) }
                    } else later()
                }) { Text(if (install is InstallState.Failed && oneClick) "GitHub" else "Позже", color = Apex.TextDim) }
            }
        },
    )
}
