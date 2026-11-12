package com.apexvpn.client.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.apexvpn.client.R

/** 20. Иконка приложения на выбор: каждая — отдельный ярлык запуска (activity-alias). */
object AppIcons {
    data class Icon(val id: String, val title: String, val alias: String, val background: Int, val foreground: Int)

    val all = listOf(
        Icon("classic", "APEX", "com.apexvpn.client.ui.LauncherClassic", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
        Icon("neon", "Неон", "com.apexvpn.client.ui.LauncherNeon", R.drawable.ic_launcher_neon_background, R.drawable.ic_launcher_neon_foreground),
        Icon("light", "Светлая", "com.apexvpn.client.ui.LauncherLight", R.drawable.ic_launcher_light_background, R.drawable.ic_launcher_light_foreground),
        Icon("gold", "Золото", "com.apexvpn.client.ui.LauncherGold", R.drawable.ic_launcher_gold_background, R.drawable.ic_launcher_gold_foreground),
        Icon("terminal", "Терминал", "com.apexvpn.client.ui.LauncherTerminal", R.drawable.ic_launcher_terminal_background, R.drawable.ic_launcher_terminal_foreground),
        // 154. Новые иконки
        Icon("cosmos", "Космос", "com.apexvpn.client.ui.LauncherCosmos", R.drawable.ic_launcher_cosmos_background, R.drawable.ic_launcher_cosmos_foreground),
        Icon("minimal", "Минимал", "com.apexvpn.client.ui.LauncherMinimal", R.drawable.ic_launcher_minimal_background, R.drawable.ic_launcher_minimal_foreground),
        Icon("gradient", "Градиент", "com.apexvpn.client.ui.LauncherGradient", R.drawable.ic_launcher_gradient_background, R.drawable.ic_launcher_gradient_foreground),
        // 3.20. Под новые дизайны
        Icon("sunset", "Закат", "com.apexvpn.client.ui.LauncherSunset", R.drawable.ic_launcher_sunset_background, R.drawable.ic_launcher_sunset_foreground),
        Icon("forest", "Лес", "com.apexvpn.client.ui.LauncherForest", R.drawable.ic_launcher_forest_background, R.drawable.ic_launcher_forest_foreground),
        Icon("arctic", "Арктика", "com.apexvpn.client.ui.LauncherArctic", R.drawable.ic_launcher_arctic_background, R.drawable.ic_launcher_arctic_foreground),
        Icon("mocha", "Мокко", "com.apexvpn.client.ui.LauncherMocha", R.drawable.ic_launcher_mocha_background, R.drawable.ic_launcher_mocha_foreground),
    )

    /**
     * Включает выбранный ярлык и выключает остальные. Приложение при этом не закрывается.
     * [vpnOn] — 20. вариант той же иконки с зелёной точкой (если включено «Иконка показывает статус»).
     */
    fun apply(context: Context, id: String, vpnOn: Boolean = false) {
        val pm = context.packageManager
        val chosen = all.firstOrNull { it.id == id } ?: all.first()
        val target = if (vpnOn) chosen.alias + "On" else chosen.alias
        val every = all.flatMap { listOf(it.alias, it.alias + "On") }
        runCatching {
            val current = ComponentName(context.packageName, target)
            if (pm.getComponentEnabledSetting(current) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                every.filter { it != target }.none {
                    pm.getComponentEnabledSetting(ComponentName(context.packageName, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
            ) return
            // Сначала включаем новый — чтобы на мгновение не остаться без иконки
            pm.setComponentEnabledSetting(current, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            every.filter { it != target }.forEach {
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, it),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    /** 20. Обновить иконку по состоянию VPN. */
    fun syncStatus(context: Context, vpnOn: Boolean) {
        val s = AppSettings.value
        apply(context, s.appIcon, vpnOn = s.iconStatus && vpnOn)
    }
}
