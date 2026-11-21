// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The setup screen is exported, so any app can start it with any widget id. Glance throws for an id
 * that is not one of ours, which would show "Urlaubsplaner keeps stopping".
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun start(appWidgetId: Int): WidgetConfigActivity {
        val intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return Robolectric.buildActivity(WidgetConfigActivity::class.java, intent).create().get()
    }

    private fun bind(
        id: Int,
        provider: ComponentName,
    ) = shadowOf(AppWidgetManager.getInstance(context))
        .addBoundWidget(id, AppWidgetProviderInfo().apply { this.provider = provider })

    private fun assertCancelled(activity: Activity) {
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(true, activity.isFinishing)
    }

    @Test
    fun `an id that no widget has closes the screen with a cancelled result`() {
        assertCancelled(start(12345))
    }

    @Test
    fun `an id that belongs to another app's widget closes the screen too`() {
        bind(21, ComponentName("com.example.other", "com.example.other.TheirWidgetReceiver"))

        assertCancelled(start(21))
    }

    @Test
    fun `a widget of this app with a different class name is not ours either`() {
        bind(22, ComponentName(context, "de.remph.urlaubsplaner.widget.NoSuchReceiver"))

        assertCancelled(start(22))
    }

    @Test
    fun `no id at all closes the screen`() {
        assertCancelled(start(AppWidgetManager.INVALID_APPWIDGET_ID))
    }

    @Test
    fun `a placed widget of ours opens the setup screen`() {
        bind(23, ComponentName(context, UrlaubsplanerWidgetReceiver::class.java))
        bind(24, ComponentName(context, UrlaubsplanerBigWidgetReceiver::class.java))

        for (id in listOf(23, 24)) {
            val activity = start(id)
            assertFalse("widget $id should stay open", activity.isFinishing)
        }
    }
}
