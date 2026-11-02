package com.apexvpn.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** Windows: права администратора, системный прокси, автозапуск, ссылки. */
object WinSys {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    internal interface WinInet : StdCallLibrary {
        fun InternetSetOptionW(hInternet: Pointer?, option: Int, buffer: Pointer?, length: Int): Boolean

        companion object {
            val I: WinInet by lazy { Native.load("wininet", WinInet::class.java, W32APIOptions.DEFAULT_OPTIONS) }
        }
    }

    private const val INET = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    private const val RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val RUN_NAME = "APEX VPN"
    private const val BYPASS = "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>"

    internal interface Shell32Ex : StdCallLibrary {
        fun IsUserAnAdmin(): Boolean

        companion object {
            val I: Shell32Ex by lazy { Native.load("shell32", Shell32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS) }
        }
    }

    internal interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: com.sun.jna.platform.win32.WinDef.HWND, attr: Int, value: com.sun.jna.ptr.IntByReference, size: Int): Int

        companion object {
            val I: Dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
        }
    }

    /** Тёмный или светлый заголовок окна (DWMWA_USE_IMMERSIVE_DARK_MODE). */
    fun darkTitleBar(handle: Long, dark: Boolean) {
        if (!isWindows || handle == 0L) return
        runCatching {
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(handle))
            val v = com.sun.jna.ptr.IntByReference(if (dark) 1 else 0)
            if (Dwm.I.DwmSetWindowAttribute(hwnd, 20, v, 4) != 0) Dwm.I.DwmSetWindowAttribute(hwnd, 19, v, 4)
        }
    }

    fun isAdmin(): Boolean = isWindows && runCatching { Shell32Ex.I.IsUserAnAdmin() }.getOrDefault(false)

    /** Перезапустить программу от администратора (окно UAC). */
    fun relaunchAsAdmin(): Boolean {
        val exe = ProcessHandle.current().info().command().orElse(null) ?: return false
        return runCatching {
            ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                "Start-Process -FilePath '${exe.replace("'", "''")}' -Verb RunAs").start()
            true
        }.getOrDefault(false)
    }

    fun setProxy(address: String) {
        if (!isWindows) return
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyServer", address)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyOverride", BYPASS)
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyEnable", 1)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "Software\\APEX VPN", "ProxyOwned", address)
        refresh()
    }

    fun clearProxy() {
        if (!isWindows) return
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyEnable", 0)
        runCatching { Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, "Software\\APEX VPN", "ProxyOwned") }
        refresh()
    }

    /** Программа закрылась аварийно, а системный прокси остался на нашем порту — убираем, иначе нет интернета. */
    fun cleanupStaleProxy() {
        if (!isWindows) return
        runCatching {
            val owned = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Software\\APEX VPN", "ProxyOwned")
            val now = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyServer")
            val on = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, INET, "ProxyEnable") == 1
            if (on && owned == now) clearProxy()
        }
    }

    private fun refresh() {
        runCatching {
            WinInet.I.InternetSetOptionW(null, 39, null, 0) // INTERNET_OPTION_SETTINGS_CHANGED
            WinInet.I.InternetSetOptionW(null, 37, null, 0) // INTERNET_OPTION_REFRESH
        }
    }

    /** Автозапуск включён (переключателем в программе или галочкой в установщике). */
    fun autostartEnabled(): Boolean = isWindows && runCatching {
        Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, RUN_NAME)
    }.getOrDefault(false)

    fun setAutostart(on: Boolean) {
        if (!isWindows) return
        runCatching {
            if (on) {
                val exe = ProcessHandle.current().info().command().orElse(null) ?: return
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, RUN)
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN, RUN_NAME, "\"$exe\" --tray")
            } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN, RUN_NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN, RUN_NAME)
            }
        }
    }

    fun open(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure { runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start() } }
    }

    fun openFolder(dir: File) {
        runCatching { Desktop.getDesktop().open(dir) }
    }
}

object AppInfo {
    const val VERSION = "2.0.1"
    const val REPO = "Dubstep2004/apex-vpn"
    const val SUPPORT = "https://t.me/Apexx_support"
}

/** Новая версия для Windows на GitHub (файл apex-vpn-*-windows.exe в последнем релизе). */
object Updates {
    data class Info(val tag: String, val url: String, val page: String)

    /** Страница релизов для Windows (отдельные от Android: теги windows-v…). */
    const val PAGE = "https://github.com/${AppInfo.REPO}/releases?q=windows&expanded=true"

    /** Последний релиз windows-v<версия>; Info — если он новее установленной. */
    suspend fun check(): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URI("https://api.github.com/repos/${AppInfo.REPO}/releases?per_page=30").toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "APEX VPN Windows ${AppInfo.VERSION}")
            val list = try { org.json.JSONArray(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
            val rel = (0 until list.length()).map { list.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") && it.optString("tag_name").startsWith("windows-v") }
                .maxWithOrNull { a, b -> if (newer(a.getString("tag_name").removePrefix("windows-v"), b.getString("tag_name").removePrefix("windows-v"))) 1 else -1 }
                ?: return@runCatching null
            val win = rel.getString("tag_name").removePrefix("windows-v")
            val assets = rel.getJSONArray("assets")
            val exe = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".exe") }
            if (newer(win, AppInfo.VERSION)) Info(win, exe?.getString("browser_download_url") ?: rel.getString("html_url"), rel.getString("html_url")) else null
        }.getOrNull()
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
