package com.apexvpn.client.data

import com.apexvpn.client.BuildConfig
import java.net.URLEncoder

/** 210. Ссылка-приглашение на страницу GitHub Pages (docs/invite). Подписка — после #, сервер её не видит. */
object Invite {
    fun pageUrl(): String {
        val (owner, repo) = BuildConfig.GITHUB_REPO.split('/').let { it[0] to it.getOrElse(1) { "apex-client" } }
        return "https://${owner.lowercase()}.github.io/$repo/invite/"
    }

    fun link(subscriptionUrl: String): String =
        pageUrl() + "#" + URLEncoder.encode(subscriptionUrl, "UTF-8").replace("+", "%20")
}
