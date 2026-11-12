package com.apexvpn.client.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.apexvpn.client.wear.R

/** 410. Значок на циферблат: щит и «38 мс» / «ВКЛ» / «ВЫКЛ». Нажатие открывает APEX Watch. */
class ApexComplication : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchState.load(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchState.S("started", "Германия", 38))

    private fun build(type: ComplicationType, st: WatchState.S): ComplicationData? {
        val on = st.status == "started"
        val text = when {
            on && st.ping > 0 -> "${st.ping}мс"
            on -> "ВКЛ"
            st.status == "starting" -> "…"
            else -> "ВЫКЛ"
        }
        val icon = MonochromaticImage.Builder(
            Icon.createWithResource(this, if (on) R.drawable.ic_complication_on else R.drawable.ic_complication_off),
        ).build()
        val desc = PlainComplicationText.Builder("APEX CLIENT: ${WatchState.label(st.status)}").build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, WearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), desc)
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(icon, desc)
                .setTapAction(tap)
                .build()
            else -> null
        }
    }
}
