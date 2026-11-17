// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget drawn without a Glance session (see [WidgetRenderer]): what reaches the launcher is
 * the style the user chose, every time, however often it changes.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WidgetRenderer()

    private val status =
        WidgetModel.Status(
            number = "12,5",
            overbooked = false,
            unit = "Tage übrig",
            unitWithYear = "Tage übrig 2027",
            summary = "Nächster Urlaub 14.04.",
            tripLabel = "Nächster Urlaub",
            tripWhen = "14.–16. April",
            tripCost = "5 Urlaubstage",
            figures = listOf("Genommen 8", "Geplant 5,5", "Anspruch 26"),
            warning = null,
        )

    private fun draw(
        style: WidgetStyle,
        layout: WidgetLayout = WidgetLayout.SMALL,
    ): View {
        val views =
            runBlocking {
                renderer.render(
                    context,
                    DpSize(250.dp, 110.dp),
                    WidgetSnapshot(status, style),
                    layout,
                )
            }
        return views.apply(context, FrameLayout(context))
    }

    private fun texts(view: View): List<TextView> =
        if (view is TextView) {
            listOf(view)
        } else if (view is ViewGroup) {
            (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        } else {
            emptyList()
        }

    private fun numberColor(style: WidgetStyle) =
        texts(draw(style)).first { it.text.toString() == "12,5" }.currentTextColor

    @Test
    fun `the small widget shows the number and the line`() {
        val shown = texts(draw(WidgetStyle())).map { it.text.toString() }

        assertEquals(true, "12,5" in shown)
        assertEquals(true, "Nächster Urlaub 14.04." in shown)
    }

    @Test
    fun `the big widget shows the trip and the three figures`() {
        val shown = texts(draw(WidgetStyle(), WidgetLayout.BIG)).map { it.text.toString() }

        assertEquals(true, "14.–16. April" in shown)
        assertEquals(true, "Anspruch 26" in shown)
    }

    @Test
    fun `the colours of the style decide the text colour, change after change`() {
        val light = numberColor(WidgetStyle(colors = WidgetColors.LIGHT))
        val dark = numberColor(WidgetStyle(colors = WidgetColors.DARK))
        val lightAgain = numberColor(WidgetStyle(colors = WidgetColors.LIGHT))

        assertNotEquals(light, dark)
        // The same renderer drew three times in a row; nothing of the first stays behind.
        assertEquals(light, lightAgain)
    }

    @Test
    fun `a draw survives the trip to the launcher`() {
        val views =
            runBlocking {
                renderer.render(
                    context,
                    DpSize(250.dp, 110.dp),
                    WidgetSnapshot(status, WidgetStyle()),
                    WidgetLayout.SMALL,
                )
            }

        // AppWidgetManager hands the RemoteViews to the launcher as a Parcel, click intents included.
        val parcel = Parcel.obtain()
        val arrived =
            try {
                views.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                RemoteViews.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }

        val shown = texts(arrived.apply(context, FrameLayout(context))).map { it.text.toString() }
        assertEquals(true, "12,5" in shown)
    }
}
