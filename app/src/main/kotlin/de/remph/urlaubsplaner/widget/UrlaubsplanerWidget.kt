// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import de.remph.urlaubsplaner.UrlaubsplanerApp
import de.remph.urlaubsplaner.core.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The home screen widget. There are two in the launcher's picker (the maintainer, 2026-10-26): the small one
 * (2x1) and [UrlaubsplanerBigWidget] (4x2), each with its own layout, so the text always has the
 * room it was made for. Each placed widget keeps its own [WidgetStyle] in Glance's state.
 */
open class UrlaubsplanerWidget(
    internal val layout: WidgetLayout = WidgetLayout.SMALL,
) : GlanceAppWidget() {
    // The cell the launcher gives is the size of the layout; nothing to choose between.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val refresher = (context.applicationContext as UrlaubsplanerApp).widgetRefresher
        // Taken before the first read, so a change in between reads the data again.
        val seen = refresher.revision.value
        val first = snapshot(context, id)
        provideContent {
            // Not `currentState`: that arrives as an event in Glance's session and waits for the
            // session's background job (see [WidgetRefresher.drawNow]). The style is read from the
            // same place, so the revision brings both.
            val (model, style) =
                liveModel(
                    first,
                    seen,
                    refresher.revision,
                ) { snapshot(context, id) }
            WidgetContent(model, style, layout)
        }
    }

    /** What the widget shows now: the data and the style the user set up for this widget. */
    internal suspend fun snapshot(
        context: Context,
        id: GlanceId,
    ): WidgetSnapshot {
        val app = context.applicationContext as UrlaubsplanerApp
        val language = WidgetLanguage.of(context)
        val data = withContext(Dispatchers.IO) { app.repository.readAll() }
        val status = WidgetStatus.of(app.clock, data.settings, data.entitlements, data.marks)
        val style = WidgetStyle.from(getAppWidgetState<Preferences>(context, id))
        return WidgetSnapshot(WidgetModel.of(status, language.context, language.locale), style)
    }
}

/** The data and the style of one widget, read together. */
internal data class WidgetSnapshot(
    val model: WidgetModel,
    val style: WidgetStyle,
)

/**
 * The model of a widget whose data stays current. Glance keeps a session alive for about a minute
 * after a draw, and while it lives an update only changes the widget's state:
 * [UrlaubsplanerWidget.provideGlance] does not run again. So a second change within that minute
 * (marking two days one after the other) would show the numbers of the first. Each
 * [WidgetRefresher.invalidate] raises [revision]; the model is read again when it moves past
 * [seen], the value [first] was read at.
 */
@Composable
internal fun <T> liveModel(
    first: T,
    seen: Int,
    revision: StateFlow<Int>,
    load: suspend () -> T,
): T {
    val current by revision.collectAsState()
    val model by produceState(first, current) {
        if (current != seen) value = load()
    }
    return model
}

/** The big widget, 4x2: the layout with the next trip, its cost and the three figures. */
class UrlaubsplanerBigWidget : UrlaubsplanerWidget(WidgetLayout.BIG)

/** Both receivers keep their widget current the same way. */
abstract class WidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // A running Glance session would keep showing the old date or language (see [liveModel]).
        if (intent.action in DAY_CHANGES || intent.action == Intent.ACTION_LOCALE_CHANGED) {
            (context.applicationContext as UrlaubsplanerApp).widgetRefresher.invalidate()
        }
        if (intent.action in DAY_CHANGES) {
            // The date is what "next vacation" and "days left" depend on. Glance already redraws
            // on a language change; the same path serves these.
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            onUpdate(context, manager, ids)
        } else {
            super.onReceive(context, intent)
        }
    }

    private companion object {
        val DAY_CHANGES =
            setOf(
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
            )
    }
}

/**
 * The small widget. Its class name is what widgets placed before there were two sizes belong to,
 * so it must not change; those keep working and show the small layout.
 */
class UrlaubsplanerWidgetReceiver : WidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UrlaubsplanerWidget()
}

class UrlaubsplanerBigWidgetReceiver : WidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UrlaubsplanerBigWidget()
}

/** The receivers of both widgets, to find the placed ones. */
internal val WIDGET_RECEIVERS =
    listOf(UrlaubsplanerWidgetReceiver::class.java, UrlaubsplanerBigWidgetReceiver::class.java)

/** The widget a placed widget id belongs to, by its provider; the small one if it is unknown. */
internal fun widgetFor(
    context: Context,
    appWidgetId: Int,
): UrlaubsplanerWidget {
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
    return if (provider?.className == UrlaubsplanerBigWidgetReceiver::class.java.name) {
        UrlaubsplanerBigWidget()
    } else {
        UrlaubsplanerWidget()
    }
}
