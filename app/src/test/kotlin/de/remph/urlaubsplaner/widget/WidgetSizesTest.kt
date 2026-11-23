// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widgets drawn at the smallest cells the launcher can give (UI audit 06): no text may be cut
 * at the bottom, not even by a pixel (a comma that loses its tail turns "31,5" into "31.5"), and
 * nothing may stand outside the cell. Real font metrics (native graphics), the app's own renderer.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class WidgetSizesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WidgetRenderer()

    private fun model(warned: Boolean) =
        WidgetModel.Status(
            number = "31,5",
            overbooked = false,
            unit = "Tage übrig",
            unitWithYear = "Tage übrig 2027",
            summary = "Nächster Urlaub 30.03.",
            tripLabel = "Nächster Urlaub",
            tripWhen = "30. März – 2. April",
            tripCost = "4 Urlaubstage",
            figures = listOf("Genommen 8", "Geplant 5,5", "Anspruch 26"),
            warning = if (warned) "3,5 Tage Resturlaub verfallen am 31.03." else null,
            warningShort = if (warned) "3,5 Tage verfallen am 31.03." else null,
        )

    private fun cell(
        layout: WidgetLayout,
        width: Int,
        height: Int,
        warned: Boolean = true,
    ): View {
        val density = context.resources.displayMetrics.density
        val views =
            runBlocking {
                renderer.render(
                    context,
                    DpSize(width.dp, height.dp),
                    WidgetSnapshot(model(warned), WidgetStyle()),
                    layout,
                )
            }
        val host = FrameLayout(context)
        host.addView(views.apply(context, host))
        val w = (width * density).toInt()
        val h = (height * density).toInt()
        host.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, w, h)
        return host
    }

    private fun texts(view: View): List<TextView> =
        when (view) {
            is TextView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    /** The texts that need more height than they have, are zero wide, or end outside the cell. */
    private fun clipped(host: View): List<String> =
        texts(host).mapNotNull {
            val layout = it.layout ?: return@mapNotNull "'${it.text}' has no layout"
            val cut = layout.height > it.height - it.paddingTop - it.paddingBottom + 1
            val bottom = IntArray(2).also { place -> it.getLocationInWindow(place) }[1] + it.height
            val outside = it.bottom > host.height || it.right > host.width || it.width == 0
            if (cut || outside || bottom <= 0) "'${it.text}' cut=$cut outside=$outside" else null
        }

    private fun shown(host: View) = texts(host).map { it.text.toString() }

    @Test
    @Config(qualifiers = "de-xxhdpi")
    fun `the wide widget at its minimum is not cut and keeps the short warning`() {
        val host = cell(WidgetLayout.BIG, 250, 110)

        assertEquals(emptyList<String>(), clipped(host))
        assertTrue("3,5 Tage verfallen am 31.03." in shown(host))
        assertFalse("3,5 Tage Resturlaub verfallen am 31.03." in shown(host))
    }

    @Test
    @Config(qualifiers = "de-xxhdpi", fontScale = 1.3f)
    fun `the wide widget at its minimum and a 130 percent font drops the strip, not a line`() {
        val host = cell(WidgetLayout.BIG, 250, 110)

        assertEquals(emptyList<String>(), clipped(host))
        assertEquals(false, shown(host).any { it.contains("verfallen") })
        assertTrue("31,5" in shown(host))
    }

    @Test
    @Config(qualifiers = "de-xxhdpi")
    fun `the wide widget at 320 x 150 still has the whole warning`() {
        val host = cell(WidgetLayout.BIG, 320, 150)

        assertEquals(emptyList<String>(), clipped(host))
        assertTrue("3,5 Tage Resturlaub verfallen am 31.03." in shown(host))
        assertTrue("Tage übrig 2027" in shown(host))
    }

    @Test
    @Config(qualifiers = "de-xxhdpi")
    fun `the small widget at its minimum shows the whole number and its unit`() {
        val host = cell(WidgetLayout.SMALL, 110, 40)

        assertEquals(emptyList<String>(), clipped(host))
        assertEquals(listOf("31,5", "Tage übrig"), shown(host))
    }

    @Test
    @Config(qualifiers = "de-xxhdpi", fontScale = 1.3f)
    fun `the small widget at its minimum and a 130 percent font is not cut either`() {
        val host = cell(WidgetLayout.SMALL, 110, 40)

        assertEquals(emptyList<String>(), clipped(host))
        assertTrue("31,5" in shown(host))
    }

    @Test
    @Config(qualifiers = "de-xxhdpi")
    fun `the small widget with room keeps its next trip line`() {
        val host = cell(WidgetLayout.SMALL, 180, 70)

        assertTrue("Nächster Urlaub 30.03." in shown(host))
    }
}
