// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.remph.urlaubsplaner.UrlaubsplanerApp
import de.remph.urlaubsplaner.appearance.UrlaubsplanerTheme
import kotlinx.coroutines.launch

/**
 * The setup screen of one widget. Android shows it when the widget is placed (and from Android 12
 * again on a long press, `reconfigurable`). Cancelling it, with Back, leaves the widget unplaced.
 * An AppCompatActivity like the main one, so the app language applies here too.
 */
class WidgetConfigActivity : AppCompatActivity() {
    private var style by mutableStateOf(WidgetStyle())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        // Without an OK result the launcher drops the widget it was about to place.
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val app = application as UrlaubsplanerApp
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val widget = widgetFor(this, appWidgetId)
        if (savedInstanceState == null) {
            // Setting up again shows what the widget has now, not the defaults.
            lifecycleScope.launch {
                val saved =
                    widget.getAppWidgetState<Preferences>(
                        this@WidgetConfigActivity,
                        glanceId,
                    )
                style = WidgetStyle.from(saved)
            }
        }
        setContent {
            val appearance by app.appearanceStore.state.collectAsStateWithLifecycle()
            UrlaubsplanerTheme(appearance) {
                WidgetSetupScreen(
                    style = style,
                    onChange = { style = it },
                    onDone = {
                        lifecycleScope.launch {
                            updateAppWidgetState(
                                this@WidgetConfigActivity,
                                glanceId,
                            ) { style.writeTo(it) }
                            widget.update(this@WidgetConfigActivity, glanceId)
                            app.widgetRefresher.start()
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }
}
