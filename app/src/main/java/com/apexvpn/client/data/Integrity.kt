package com.apexvpn.client.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.apexvpn.client.BuildConfig
import java.security.MessageDigest

/**
 * Защита от подделок: официальные сборки APEX VPN подписаны ключом автора.
 * Если кто-то перепаковал APK (вставил свой код, рекламу, кражу подписок) и подписал
 * своим ключом — подпись не совпадёт, и приложение честно предупредит пользователя.
 *
 * Отпечаток SHA-256 сертификата официальных сборок опубликован в README.
 */
object Integrity {
    /** SHA-256 сертификата подписи официальных релизов (CN=APEX VPN). */
    private const val OFFICIAL_CERT = "6115D655A08DFE7C970BE7DFC11E06E35F7EC8B8BD78FF5AB782F65145B100D4"
    private const val OFFICIAL_PACKAGE = "com.apexvpn.client"

    /** Откуда скачивать оригинал — не из BuildConfig, чтобы копия не подменила ссылку сборкой. */
    const val OFFICIAL_URL = "https://github.com/Dubstep2004/apex-client/releases/latest"

    enum class Status {
        /** Подпись автора — всё хорошо */
        Official,
        /** Тестовая сборка (debug) — предупреждать не нужно */
        Test,
        /** Чужая подпись или другое имя пакета — копия */
        Unofficial,
    }

    @Volatile private var cached: Status? = null

    fun status(context: Context): Status = cached ?: compute(context).also { cached = it }

    /** Короткий отпечаток подписи этой копии — для «О приложении» и отчёта. */
    fun fingerprint(context: Context): String = certs(context).firstOrNull()?.chunked(2)?.take(8)?.joinToString(":") ?: "—"

    private fun compute(context: Context): Status {
        val pkg = context.packageName
        val certs = certs(context)
        val samePackage = pkg == OFFICIAL_PACKAGE || pkg == "$OFFICIAL_PACKAGE.debug"
        return when {
            samePackage && OFFICIAL_CERT in certs -> Status.Official
            BuildConfig.DEBUG && pkg == "$OFFICIAL_PACKAGE.debug" -> Status.Test
            else -> Status.Unofficial
        }
    }

    @Suppress("DEPRECATION")
    private fun certs(context: Context): List<String> = runCatching {
        val pm = context.packageManager
        val raw: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                ?: return@runCatching emptyList()
            // При смене ключа (ротации) учитываем и историю подписей
            val list = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            list.map { it.toByteArray() }
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty().map { it.toByteArray() }
        }
        raw.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
        }
    }.getOrDefault(emptyList())
}
