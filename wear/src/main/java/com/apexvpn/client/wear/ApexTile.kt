package com.apexvpn.client.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture

/**
 * 409. Плитка APEX CLIENT: состояние, сервер и большая кнопка включения.
 * Нажатие на кнопку отправляет телефону «переключить».
 */
class ApexTile : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        if (requestParams.currentState.lastClickableId == "toggle") toggle()
        val st = WatchState.load(this)
        val on = st.status == "started"
        val accent = if (on) 0xFF22D3EE.toInt() else 0xFF8E8E9A.toInt()

        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
            .setText(t)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

        // 3.9. Вид Wear OS 6 (Material 3 Expressive): кольцо состояния по краю экрана,
        // крупный статус, плашка сервера (открывает APEX Watch) и широкая кнопка-«пилюля» снизу
        fun clickable(id: String, action: ActionBuilders.Action) = ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(action).build()
        fun pill(content: LayoutElementBuilders.LayoutElement, color: Int, w: Float, h: Float, click: ModifiersBuilders.Clickable?) =
            LayoutElementBuilders.Box.Builder()
                .setWidth(dp(w)).setHeight(dp(h))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(color))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(h / 2)).build())
                                .build(),
                        )
                        .apply { if (click != null) setClickable(click) }
                        .build(),
                )
                .addContent(content)
                .build()

        val openApp = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.apexvpn.client.wear.WearActivity")
                    .build(),
            )
            .build()

        val ring = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(androidx.wear.protolayout.DimensionBuilders.degrees(0f))
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(androidx.wear.protolayout.DimensionBuilders.degrees(if (on) 360f else 300f))
                    .setThickness(dp(5f))
                    .setColor(argb(if (on) 0xFF7C5CFF.toInt() else 0xFF2A2A33.toInt()))
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("APEX CLIENT", 12f, 0xFF8E8E9A.toInt()))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(text(if (on) "Защищено" else WatchState.label(st.status), 22f, if (on) 0xFFFFFFFF.toInt() else 0xFFC9C9DA.toInt(), bold = true))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(
                pill(
                    text(if (on && st.ping > 0) "${st.server} · ${st.ping} мс" else st.server.ifBlank { "Открыть APEX Watch" }, 12f, accent),
                    0xFF1C1C26.toInt(), 132f, 30f, clickable("open", openApp),
                ),
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
            .addContent(
                pill(
                    text(if (on) "⏻  Выключить" else "⏻  Включить", 15f, 0xFFFFFFFF.toInt(), bold = true),
                    if (on) 0xFF6D4AFF.toInt() else 0xFF3A3A48.toInt(), 150f, 52f,
                    clickable("toggle", ActionBuilders.LoadAction.Builder().build()),
                ),
            )
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(ring)
            .addContent(column)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES)
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { it.set(ResourceBuilders.Resources.Builder().setVersion(RES).build()); "res" }

    private fun toggle() {
        val ctx = this
        Wearable.getNodeClient(ctx).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, "/apex/toggle", ByteArray(0)) }
        }
    }

    private companion object {
        const val RES = "2"
    }
}
