package com.apexvpn.client.data

/**
 * Наши ссылки (Настройки → О приложении). Telegram пока пустой — строка не показывается;
 * когда появится канал, впишите ссылку https://t.me/… сюда и в wear/…/WearActivity.kt (Links).
 */
object Links {
    const val GITHUB = "https://github.com/Dubstep2004/apex-client"
    const val TELEGRAM = ""
    /** Поддержка в Telegram */
    const val SUPPORT = "https://t.me/Apexx_support"
    const val SUPPORT_NAME = "@Apexx_support"
    /** 3.19.5: сайт-визитка */
    const val SITE = "https://dubstep2004.github.io"

    /** 3.18: ссылки можно поменять из веб-админки (docs/config/app.json → links), без новой версии. */
    fun support(): String = AppConfig.value.support.ifBlank { SUPPORT }
    fun channel(): String = AppConfig.value.channel.ifBlank { TELEGRAM }
    /** 3.19.5: ссылка «Сайт» из пульта (раньше читалась, но в приложении не показывалась). */
    fun site(): String = AppConfig.value.site.ifBlank { SITE }
    /** 3.22.1: в бота поддержки — сразу с версией приложения (t.me/бот?start=v3_22_1); у чата с человеком Telegram просто откроет переписку. */
    fun withVersion(url: String): String =
        if (Regex("^https://t\\.me/[A-Za-z0-9_]{4,}/?$").matches(url)) url.trimEnd('/') + "?start=v" + com.apexvpn.client.BuildConfig.VERSION_NAME.replace(Regex("[^0-9.]"), "").replace('.', '_') else url
    fun supportName(): String = support().takeIf { it.startsWith("https://t.me/") }?.let { "@" + it.removePrefix("https://t.me/").trimEnd('/') } ?: SUPPORT_NAME
}
