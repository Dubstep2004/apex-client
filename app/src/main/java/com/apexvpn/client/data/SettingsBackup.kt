package com.apexvpn.client.data

import android.content.Context
import android.net.Uri
import com.apexvpn.client.BuildConfig
import org.json.JSONObject

/**
 * Резервная копия настроек в файл: все настройки, свои списки, правила, избранное
 * и ссылка подписки. Файл личный — в нём ссылка подписки.
 */
object SettingsBackup {
    const val MIME = "application/json"

    fun fileName() = "apex-client-settings.json"

    /** Всё для переноса: настройки, подписка, выбранный сервер. */
    fun payload(): JSONObject = JSONObject()
        .put("app", "APEX VPN")
        .put("format", 1)
        .put("version", BuildConfig.VERSION_NAME)
        .put("settings", AppSettings.exportJson())
        .put("subscription", Prefs.subscriptionUrl)
        .put("selected", Prefs.selectedTag)

    fun export(context: Context, uri: Uri, password: String = ""): Boolean = runCatching {
        val o = if (password.isNotEmpty()) encrypt(payload(), password) else payload()
        // «wt» — перезаписать файл целиком; некоторые папки его не понимают, тогда обычная запись
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
        out?.use { it.write(o.toString(2).toByteArray()) } ?: error("нет доступа к файлу")
        true
    }.getOrDefault(false)

    /** Что восстановили: ссылка подписки (может быть пустой) и выбранный сервер. */
    data class Restored(val subscription: String, val selected: String)

    /** Читает файл и применяет настройки; null — файл не подошёл. */
    fun import(context: Context, uri: Uri): Restored? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        restore(JSONObject(text))
    }.getOrNull()

    /** Прочитать файл копии как JSON (зашифрованный или нет); null — не JSON. */
    fun read(context: Context, uri: Uri): JSONObject? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        JSONObject(text)
    }.getOrNull()

    // ─── 3.8. Копия с паролем: AES-256-GCM, ключ из пароля (PBKDF2, 120 000 повторов) ───

    fun isEncrypted(o: JSONObject) = o.optString("app") == "APEX VPN" && o.optInt("encrypted") == 1

    private const val ITER = 120_000

    private fun key(password: String, salt: ByteArray, iter: Int): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iter, 256)
        val raw = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
    private fun unb64(s: String) = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)

    fun encrypt(plain: JSONObject, password: String): JSONObject {
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key(password, salt, ITER), javax.crypto.spec.GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().put("app", "APEX VPN").put("encrypted", 1).put("format", 1)
            .put("version", BuildConfig.VERSION_NAME)
            .put("kdf", "PBKDF2WithHmacSHA256").put("iter", ITER)
            .put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(data))
    }

    /** Расшифровать копию; null — неверный пароль или файл повреждён. */
    fun decrypt(o: JSONObject, password: String): JSONObject? = runCatching {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, key(password, unb64(o.getString("salt")), o.optInt("iter", ITER)),
            javax.crypto.spec.GCMParameterSpec(128, unb64(o.getString("iv"))))
        JSONObject(String(c.doFinal(unb64(o.getString("data"))), Charsets.UTF_8))
    }.getOrNull()

    /** Применить копию (из файла или QR-кода); null — это не копия APEX VPN. */
    fun restore(o: JSONObject): Restored? = runCatching {
        if (o.optString("app") != "APEX VPN") return null
        val settings = o.optJSONObject("settings") ?: return null
        if (!AppSettings.importJson(settings)) return null
        val selected = o.optString("selected")
        selected.takeIf { it.isNotBlank() }?.let { Prefs.selectedTag = it }
        Restored(o.optString("subscription"), selected)
    }.getOrNull()
}
