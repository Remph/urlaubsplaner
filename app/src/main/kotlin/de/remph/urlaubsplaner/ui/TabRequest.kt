// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import android.content.Intent

/**
 * A request from outside the app, the widget, to open one tab. It is a new object for every
 * request, so asking for the tab that is already open still counts as a new request.
 */
class TabRequest(
    val tab: Tab,
) {
    companion object {
        // The tab is in the action, not in an extra: a PendingIntent treats intents that differ
        // only in their extras as the same, so two widget taps would share one tab.
        private const val ACTION_PREFIX = "de.remph.urlaubsplaner.action.OPEN_"

        fun action(tab: Tab): String = ACTION_PREFIX + tab.name

        fun from(intent: Intent?): TabRequest? {
            val action = intent?.action ?: return null
            return Tab.entries.firstOrNull { action(it) == action }?.let { TabRequest(it) }
        }
    }
}
