package com.apexvpn.client.vpn

import android.util.Log
import com.apexvpn.client.data.Prefs
import com.apexvpn.client.data.Subscription
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.17.1. Какой сервер на самом деле выбран в работающем ядре (группа «proxy» в Clash API).
 * Бывало, что в приложении выбран один сервер, а ядро шло через автопилот: тогда ручной выбор
 * (например, LTE-обход) не действовал. После запуска и перезагрузки сверяем и при расхождении
 * переключаем ядро на выбранный сервер.
 */
object CoreSelector {
    private const val TAG = "ApexSelector"

    private fun open(method: String): HttpURLConnection =
        (URL("http://127.0.0.1:${Prefs.clashApiPort}/proxies/proxy").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 1500
            readTimeout = 2500
            setRequestProperty("Authorization", "Bearer ${Prefs.xrayAuth}")
        }

    /** Текущий сервер ядра или null (ядро не отвечает). Не вызывать из главного потока. */
    fun now(): String? = runCatching {
        val c = open("GET")
        try {
            if (c.responseCode != 200) null
            else JSONObject(c.inputStream.bufferedReader().readText()).optString("now").ifEmpty { null }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Ядро идёт не через выбранный сервер — переключить. true — переключили. */
    fun ensure(): Boolean {
        val want = Prefs.selectedTag
        if (want.isEmpty()) return false
        if (want != com.apexvpn.client.data.AUTO_TAG && Subscription.state.value.nodes.none { it.tag == want }) return false
        val cur = now() ?: return false
        if (cur == want) return false
        return runCatching {
            val c = open("PUT")
            try {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(JSONObject().put("name", want).toString().toByteArray()) }
                (c.responseCode in 200..299).also { Log.w(TAG, "core was on $cur, switched to $want: $it") }
            } finally {
                c.disconnect()
            }
        }.getOrDefault(false)
    }
}
