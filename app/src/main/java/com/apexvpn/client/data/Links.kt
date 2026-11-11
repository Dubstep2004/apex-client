package com.apexvpn.client.data

/**
 * Наши ссылки (Настройки → О приложении). Telegram пока пустой — строка не показывается;
 * когда появится канал, впишите ссылку https://t.me/… сюда и в wear/…/WearActivity.kt (Links).
 */
object Links {
    const val GITHUB = "https://github.com/Dubstep2004/apex-vpn"
    const val TELEGRAM = ""
    /** Поддержка в Telegram */
    const val SUPPORT = "https://t.me/Apexx_support"
    const val SUPPORT_NAME = "@Apexx_support"

    /** 3.18: ссылки можно поменять из веб-админки (docs/config/app.json → links), без новой версии. */
    fun support(): String = AppConfig.value.support.ifBlank { SUPPORT }
    fun channel(): String = AppConfig.value.channel.ifBlank { TELEGRAM }
    fun supportName(): String = support().takeIf { it.startsWith("https://t.me/") }?.let { "@" + it.removePrefix("https://t.me/").trimEnd('/') } ?: SUPPORT_NAME
}
