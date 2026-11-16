package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the widgets current. The app process is the only writer of the database, so while it
 * lives it watches the three tables and draws the widgets again after a change. Without that a
 * marked day would show on the home screen only at the next periodic update.
 */
@OptIn(FlowPreview::class)
class WidgetRefresher(
    private val context: Context,
    private val database: UrlaubsplanerDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null
    private val renderer = WidgetRenderer()

    private val _revision = MutableStateFlow(0)

    /** Rises with every [invalidate]; a running widget session reads its data again when it moves. */
    val revision: StateFlow<Int> = _revision

    /** Whether the user has put the widget on a home screen. */
    fun hasWidgets(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WIDGET_RECEIVERS.any {
            manager
                .getAppWidgetIds(
                    ComponentName(context, it),
                ).isNotEmpty()
        }
    }

    /** Starts watching the data; calling it again does nothing. */
    fun start() {
        if (watching != null) return
        watching =
            scope.launch {
                // The first value comes at once, so the widgets are also drawn when the app starts.
                database.invalidationTracker
                    .createFlow("settings", "year_entitlement", "marked_day")
                    .debounce(DEBOUNCE_MILLIS)
                    .collect { redraw() }
            }
    }

    /**
     * Draws the widgets again now. For changes the database watch may not see: the language, the
     * phone's light or dark mode, and a restored backup. It also starts the watch, which the app
     * start skips when it finds no widget (parked item 7: after "Clear data" it must not depend
     * on that one look).
     */
    fun refresh() {
        if (!hasWidgets()) return
        start()
        scope.launch { redraw() }
    }

    /** Marks what the widgets show as outdated, for a session that is still running. */
    fun invalidate() {
        _revision.update { it + 1 }
    }

    /**
     * Draws every placed widget again, by the ids the system reports (`updateAll` asks Glance,
     * which keeps its own list of receivers in a file of the app; after "Clear data" that list is
     * gone and is only rebuilt on some paths, parked item 7). A widget that fails is logged and
     * skipped: an exception must not end the watch in [start], or no widget would be drawn again
     * until the process dies.
     */
    private suspend fun redraw() {
        invalidate()
        val manager = AppWidgetManager.getInstance(context)
        for (receiver in WIDGET_RECEIVERS) {
            val ids =
                try {
                    manager.getAppWidgetIds(ComponentName(context, receiver)).toList()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not list the widgets of ${receiver.simpleName}", e)
                    continue
                }
            updateEach(ids, onError = { id, e -> Log.w(TAG, "Widget $id not drawn", e) }) { id ->
                drawNow(id)
            }
        }
    }

    /**
     * Draws one placed widget now, in this process (see [WidgetRenderer]). Used when the app has
     * just changed what the widget shows, for example the colour in the setup screen. A session
     * that Glance still has running reads the new state through [revision] ([invalidate] first).
     * If composing fails the old route is the fallback: Glance's `update()` queues the draw for
     * its session, which may come late but comes.
     */
    suspend fun drawNow(appWidgetId: Int) {
        val started = SystemClock.elapsedRealtime()
        try {
            val widget = widgetFor(context, appWidgetId)
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val snapshot = widget.snapshot(context, glanceId)
            val views =
                renderer.render(context, widgetSize(context, appWidgetId), snapshot, widget.layout)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            Log.i(TAG, "Widget $appWidgetId drawn in ${SystemClock.elapsedRealtime() - started} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget $appWidgetId not drawn directly, asking Glance", e)
            try {
                val widget = widgetFor(context, appWidgetId)
                widget.update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                Log.w(TAG, "Widget $appWidgetId not drawn by Glance either", e2)
            }
        }
    }

    private companion object {
        const val TAG = "WidgetRefresher"

        // Marking a range writes many rows; one redraw after they are all in is enough.
        const val DEBOUNCE_MILLIS = 500L
    }
}

/**
 * Runs [update] for every id; one that throws is reported to [onError] and the rest still run.
 * Cancellation is not an error and passes through.
 */
internal suspend fun updateEach(
    ids: Collection<Int>,
    onError: (Int, Exception) -> Unit,
    update: suspend (Int) -> Unit,
) {
    for (id in ids) {
        try {
            update(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(id, e)
        }
    }
}
