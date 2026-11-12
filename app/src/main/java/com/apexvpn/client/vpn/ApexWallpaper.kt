package com.apexvpn.client.vpn

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.apexvpn.client.data.AppSettings
import com.apexvpn.client.data.ServerInfo
import com.apexvpn.client.data.Subscription
import com.apexvpn.client.ui.WorldMap
import com.apexvpn.client.ui.flagCode
import com.apexvpn.client.ui.userCountry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 43. Живые обои APEX: точечная карта мира, дуга до сервера и статус VPN.
 * Анимация идёт только когда обои видны и VPN включён — батарею не тратит.
 */
class ApexWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = ApexEngine()

    inner class ApexEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var visible = false
        private var status = VpnStatus.Stopped
        private var serverCc: String? = null
        private var serverTitle = "Автопилот"
        private val me by lazy { userCountry(applicationContext) }
        private val start = System.currentTimeMillis()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (visible && status == VpnStatus.Started && !AppSettings.value.smoothMode) handler.postDelayed(this, 40)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            scope.launch {
                combine(VpnState.status, Subscription.state) { st, sub -> st to sub }.collect { (st, sub) ->
                    status = st
                    val n = sub.nodes.find { it.tag == sub.selectedTag }
                    serverCc = n?.let { flagCode(ServerInfo.flag(it.name)) }
                    serverTitle = n?.let { ServerInfo.title(it.name) } ?: "Автопилот"
                    restart()
                }
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            restart()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            restart()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            scope.cancel()
            super.onDestroy()
        }

        private fun restart() {
            handler.removeCallbacks(tick)
            if (visible) handler.post(tick)
        }

        private fun draw() {
            val holder = surfaceHolder
            val c: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                render(c)
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        private fun render(c: Canvas) {
            val w = c.width.toFloat()
            val h = c.height.toFloat()
            val accent = AppSettings.value.accentArgb.toInt()
            val cyan = 0xFF22D3EE.toInt()
            val on = status == VpnStatus.Started
            val t = (System.currentTimeMillis() - start) / 1000f

            paint.shader = LinearGradient(0f, 0f, w, h, 0xFF0B0B16.toInt(), 0xFF05050A.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = RadialGradient(w * 0.15f, h * 0.12f, w * 0.9f, (accent and 0x00FFFFFF) or 0x55000000, 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = RadialGradient(w, h * 0.9f, w * 0.9f, (cyan and 0x00FFFFFF) or (if (on) 0x44000000 else 0x22000000), 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            // Карта по ширине экрана, по центру
            val mw = w * 1.0f
            val mh = mw / (WorldMap.W.toFloat() / WorldMap.H)
            val mx = 0f
            val my = h * 0.42f - mh / 2
            val cw = mw / WorldMap.W
            val ch = mh / WorldMap.H
            paint.color = 0x2EFFFFFF
            WorldMap.rows.forEachIndexed { j, row ->
                row.forEachIndexed { i, ch2 ->
                    if (ch2 == '#') c.drawCircle(mx + (i + 0.5f) * cw, my + (j + 0.5f) * ch, minOf(cw, ch) * 0.3f, paint)
                }
            }
            fun proj(cc: String?): Pair<Float, Float>? {
                val (lat, lon) = WorldMap.coords[cc ?: return null] ?: return null
                return mx + (lon + 180f) / 360f * mw to my + (WorldMap.N - lat) / (WorldMap.N - WorldMap.S) * mh
            }
            val a = proj(me) ?: proj("ru")!!
            val b = proj(serverCc)
            val d = resources.displayMetrics.density
            if (b != null) {
                val ctrlX = (a.first + b.first) / 2
                val ctrlY = minOf(a.second, b.second) - kotlin.math.abs(a.first - b.first) * 0.4f
                val path = Path().apply { moveTo(a.first, a.second); quadTo(ctrlX, ctrlY, b.first, b.second) }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = if (on) accent else 0x66FFFFFF
                c.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                if (on) {
                    val p = (t / 2.6f) % 1f
                    val u = 1 - p
                    val px = u * u * a.first + 2 * u * p * ctrlX + p * p * b.first
                    val py = u * u * a.second + 2 * u * p * ctrlY + p * p * b.second
                    paint.shader = RadialGradient(px, py, 10 * d, intArrayOf(0xEEFFFFFF.toInt(), (cyan and 0x00FFFFFF) or 0x88000000.toInt(), 0), null, Shader.TileMode.CLAMP)
                    c.drawCircle(px, py, 10 * d, paint)
                    paint.shader = null
                }
                pin(c, b.first, b.second, if (on) cyan else 0x88FFFFFF.toInt(), on, t, d)
            }
            pin(c, a.first, a.second, accent, on, t + 1.1f, d)

            // Статус
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = 26 * d
            val label = when (status) {
                VpnStatus.Started -> "Защищено"
                VpnStatus.Starting -> "Подключение…"
                VpnStatus.Paused -> "Пауза"
                VpnStatus.Blocked -> "Интернет заблокирован"
                else -> "VPN выключен"
            }
            c.drawText(label, w / 2, my + mh + 64 * d, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 15 * d
            paint.color = 0x99FFFFFF.toInt()
            c.drawText("APEX CLIENT · $serverTitle", w / 2, my + mh + 90 * d, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        private fun pin(c: Canvas, x: Float, y: Float, color: Int, rings: Boolean, t: Float, d: Float) {
            if (rings) {
                for (k in 0..1) {
                    val f = ((t / 2.2f) + k * 0.5f) % 1f
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.5f * d
                    paint.color = (color and 0x00FFFFFF) or (((1 - f) * 140).toInt() shl 24)
                    c.drawCircle(x, y, (4 + f * 18) * d, paint)
                }
                paint.style = Paint.Style.FILL
            }
            paint.color = (color and 0x00FFFFFF) or 0x44000000
            c.drawCircle(x, y, 8 * d, paint)
            paint.color = color
            c.drawCircle(x, y, 4.5f * d, paint)
            paint.color = 0xFFFFFFFF.toInt()
            c.drawCircle(x, y, 1.8f * d, paint)
        }
    }
}
