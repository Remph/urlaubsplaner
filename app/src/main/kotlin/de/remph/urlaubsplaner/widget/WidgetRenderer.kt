// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draws a widget without a Glance session. Glance's `update()` hands the change to a session that
 * runs in a WorkManager job; when that job is stopped or deferred while the app is in the
 * background (it was, on the maintainer's phone, 2026-11-16: the second colour change showed
 * 12 to 40 s late), the widget waits for it. This composes the same [WidgetContent] straight to
 * `RemoteViews` in the app's own process, which is alive whenever the app has just changed
 * something, and takes no job.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
internal class WidgetRenderer {
    // One instance keeps the layout bookkeeping together; one compose at a time.
    private val remoteViews = GlanceRemoteViews()
    private val mutex = Mutex()

    suspend fun render(
        context: Context,
        size: DpSize,
        snapshot: WidgetSnapshot,
        layout: WidgetLayout,
    ): RemoteViews =
        mutex.withLock {
            remoteViews
                .compose(context, size) {
                    WidgetContent(snapshot.model, snapshot.style, layout)
                }.remoteViews
        }
}

/**
 * The size the launcher gives one placed widget, in dp. The content does not depend on it (the
 * layout is chosen by the widget kind and fills the cell); Glance wants a value for it anyway.
 */
internal fun widgetSize(
    context: Context,
    appWidgetId: Int,
): DpSize {
    val manager = AppWidgetManager.getInstance(context)
    val options = manager.getAppWidgetOptions(appWidgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    if (width > 0 && height > 0) return DpSize(width.dp, height.dp)
    val info = manager.getAppWidgetInfo(appWidgetId)
    val density = context.resources.displayMetrics.density
    return DpSize(
        ((info?.minWidth ?: 0) / density).dp,
        ((info?.minHeight ?: 0) / density).dp,
    )
}
