// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import android.content.Intent
import de.remph.urlaubsplaner.planner.CalendarShortcut

/**
 * A request from outside the app to open one tab: a tap on the widget, or a launcher shortcut
 * (then [shortcut] says which calendar view). It is a new object for every request, so asking for
 * the tab that is already open still counts as a new request.
 */
class TabRequest(
    val tab: Tab,
    val shortcut: CalendarShortcut? = null,
) {
    companion object {
        // The tab is in the action, not in an extra: a PendingIntent treats intents that differ
        // only in their extras as the same, so two widget taps would share one tab. The same goes
        // for the shortcuts, which also tell their intents apart by action only.
        private const val ACTION_PREFIX = "de.remph.urlaubsplaner.action.OPEN_"
        private const val SHORTCUT_PREFIX = "de.remph.urlaubsplaner.action.SHORTCUT_"

        fun action(tab: Tab): String = ACTION_PREFIX + tab.name

        /** The action of the launcher shortcut; the same strings are in `res/xml/shortcuts.xml`. */
        fun action(shortcut: CalendarShortcut): String = SHORTCUT_PREFIX + shortcut.name

        fun from(intent: Intent?): TabRequest? {
            val action = intent?.action ?: return null
            Tab.entries.firstOrNull { action(it) == action }?.let { return TabRequest(it) }
            return CalendarShortcut.entries
                .firstOrNull { action(it) == action }
                ?.let { TabRequest(Tab.CALENDAR, it) }
        }
    }
}
