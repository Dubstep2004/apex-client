package com.apexvpn.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexvpn.client.BuildConfig
import com.apexvpn.client.ui.theme.Apex

/** Первый запуск: ввод ссылки на подписку. */
@Composable
fun SetupScreen(viewModel: MainViewModel, onScanQr: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        ApexLogo(112.dp)
        Spacer(Modifier.height(20.dp))
        Text("APEX CLIENT", color = Apex.Text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте ссылку на вашу подписку, чтобы начать",
            color = Apex.TextDim,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        // 3.9. Телевизор: подписку удобнее прислать с телефона по QR-коду
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (androidx.compose.runtime.remember { isTv(ctx) }) {
            TvPairingCard(viewModel)
            Spacer(Modifier.height(20.dp))
            Text("или введите ссылку пультом", color = Apex.TextDim, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        SubscriptionInput(viewModel, buttonText = "Подключить подписку", onSuccess = {})
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Apex.Outline),
        ) {
            Icon(androidx.compose.material.icons.Icons.Rounded.QrCodeScanner, null, tint = Apex.Text)
            Spacer(Modifier.width(10.dp))
            Text("Сканировать QR-код", color = Apex.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(24.dp))
        Text("Версия ${BuildConfig.VERSION_NAME}", color = Apex.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
    }
}

/** Поле ввода ссылки + кнопка. Используется и на первом экране, и в настройках. */
@Composable
fun SubscriptionInput(viewModel: MainViewModel, buttonText: String, onSuccess: () -> Unit) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    OutlinedTextField(
        value = url,
        onValueChange = { url = it.trim(); error = null },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ссылка на подписку") },
        placeholder = { Text("https://${com.apexvpn.client.data.SubHosts.primary()}/…") },
        singleLine = true,
        isError = error != null,
        supportingText = {
            Text(error ?: "Поддерживается только ${com.apexvpn.client.data.SubHosts.primary()}")
        },
        trailingIcon = {
            IconButton(onClick = {
                clipboard.getText()?.text?.trim()?.let { url = it; error = null }
            }) {
                Icon(Icons.Rounded.ContentPaste, "Вставить", tint = Apex.Accent)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Apex.Accent,
            unfocusedBorderColor = Apex.Outline,
            cursorColor = Apex.Accent,
        ),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            viewModel.setSubscription(url) { err ->
                error = err
                if (err == null) onSuccess()
            }
        },
        enabled = !refreshing && url.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Apex.Accent, contentColor = Apex.OnAccent),
    ) {
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Apex.OnAccent)
        } else {
            Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
