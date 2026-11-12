package com.apexvpn.client.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * «Чтобы VPN не отключался»: оболочки Xiaomi, Samsung, Honor и других закрывают фоновые
 * приложения ради батареи — вместе с VPN. Здесь проверка ограничений батареи и переходы
 * в нужные системные экраны (автозапуск у каждой марки свой).
 */
object KeepAlive {
    data class Vendor(val id: String, val title: String, val steps: List<String>)

    val vendors = listOf(
        Vendor(
            "xiaomi", "Xiaomi · Redmi · POCO",
            listOf(
                "Настройки → Приложения → APEX CLIENT → Автозапуск — включить",
                "Там же: Контроль активности (Батарея) → «Нет ограничений»",
                "В недавних приложениях потяните карточку APEX CLIENT вниз и нажмите на замок",
            ),
        ),
        Vendor(
            "samsung", "Samsung",
            listOf(
                "Настройки → Приложения → APEX CLIENT → Батарея → «Без ограничений»",
                "Обслуживание устройства → Батарея → Ограничения в фоновом режиме → уберите APEX CLIENT из «Спящих» и «Глубокого сна»",
            ),
        ),
        Vendor(
            "honor", "Honor · Huawei",
            listOf(
                "Настройки → Батарея → Запуск приложений → APEX CLIENT → выключить «Автоматическое управление»",
                "В окне включите все три: Автозапуск, Косвенный запуск, Работа в фоновом режиме",
            ),
        ),
        Vendor(
            "oppo", "OPPO · realme · OnePlus",
            listOf(
                "Настройки → Батарея → Ещё → Оптимизация батареи → APEX CLIENT → «Не оптимизировать»",
                "Настройки → Приложения → APEX CLIENT → Использование батареи → разрешить работу в фоне и автозапуск",
            ),
        ),
        Vendor(
            "vivo", "vivo · iQOO",
            listOf(
                "i Manager → Приложения → Автозапуск → APEX CLIENT — включить",
                "Настройки → Батарея → Высокое энергопотребление в фоне → APEX CLIENT — разрешить",
            ),
        ),
        Vendor(
            "other", "Другие телефоны",
            listOf(
                "Настройки → Приложения → APEX CLIENT → Батарея → «Без ограничений»",
                "Не закрывайте APEX CLIENT смахиванием из недавних",
            ),
        ),
    )

    /** Марка этого телефона. */
    fun thisVendor(): Vendor {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val id = when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> "xiaomi"
            "samsung" in m -> "samsung"
            listOf("honor", "huawei").any { it in m } -> "honor"
            listOf("oppo", "realme", "oneplus").any { it in m } -> "oppo"
            listOf("vivo", "iqoo").any { it in m } -> "vivo"
            else -> "other"
        }
        return vendors.first { it.id == id }
    }

    /** Без ограничений батареи (Android 6+). */
    fun unrestricted(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(true)

    /** Системный запрос «Разрешить работу в фоне» (одно нажатие), иначе — список приложений. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!start(context, direct)) start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Экран автозапуска своей оболочки; если не нашёлся — настройки приложения. */
    fun openAutostart(context: Context) {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        for (c in candidates) {
            if (start(context, Intent().setComponent(c))) return
        }
        openAppSettings(context)
    }

    fun openAppSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Нет такого экрана или он закрыт — исключение, пробуем следующий
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
