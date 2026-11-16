// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry

/**
 * A widget of this app on a launcher of the test's own: it is bound the way a launcher binds it
 * and shown in an [AppWidgetHostView], so [texts] is what the user would read on the home screen.
 * That includes the part the JVM tests cannot reach: the receiver, Glance's session and the
 * RemoteViews the system hands to the host.
 *
 * The setup screen ([WidgetConfigActivity]) is not part of binding; a launcher starts it
 * separately, and the widget then draws with the default style. Close it after the test.
 */
class PlacedWidget(
    receiver: Class<out AppWidgetProvider>,
) : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)
    private val view: AppWidgetHostView
    val id: Int = host.allocateAppWidgetId()

    init {
        // Binding needs a permission a launcher has; the shell may hand it to this package.
        grantBind()
        val bound = manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, receiver))
        check(bound) { "The system did not allow binding the widget ${receiver.simpleName}" }
        host.startListening()
        var created: AppWidgetHostView? = null
        instrumentation.runOnMainSync {
            created = host.createView(context, id, manager.getAppWidgetInfo(id))
        }
        view = created!!
    }

    /** Every text on the widget as it is drawn now. */
    fun texts(): List<String> {
        val found = mutableListOf<String>()
        instrumentation.runOnMainSync { collect(view, found) }
        return found
    }

    /**
     * Waits until [accept] likes the texts and returns them. The widget redraws a moment after
     * the data changes (500 ms debounce, then Glance), so this polls. Fails with everything that
     * was on the widget at the end, which is what one needs to know why.
     */
    fun awaitTexts(
        timeoutMillis: Long = TIMEOUT_MILLIS,
        accept: (List<String>) -> Boolean,
    ): List<String> {
        val end = System.currentTimeMillis() + timeoutMillis
        var last = texts()
        while (!accept(last)) {
            if (System.currentTimeMillis() > end) {
                throw AssertionError("The widget did not show the expected text, it shows: $last")
            }
            Thread.sleep(POLL_MILLIS)
            last = texts()
        }
        return last
    }

    fun awaitText(text: String): List<String> = awaitTexts { text in it }

    override fun close() {
        host.stopListening()
        host.deleteAppWidgetId(id)
        revokeBind()
    }

    // Android 11 (our minSdk) has no `cmd appwidget`, only the old `appwidget` program; newer
    // images may have it the other way round. The one that does not exist only prints a message.
    private fun grantBind() {
        shell("cmd appwidget grantbind --package ${context.packageName}")
        shell("appwidget grantbind --package ${context.packageName}")
    }

    private fun revokeBind() {
        shell("cmd appwidget revokebind --package ${context.packageName}")
        shell("appwidget revokebind --package ${context.packageName}")
    }

    private fun collect(
        view: View,
        into: MutableList<String>,
    ) {
        when (view) {
            is TextView -> {
                view.text
                    ?.toString()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(into::add)
            }

            is ViewGroup -> {
                (0 until view.childCount).forEach { collect(view.getChildAt(it), into) }
            }
        }
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val HOST_ID = 0x7e57
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 250L
    }
}
