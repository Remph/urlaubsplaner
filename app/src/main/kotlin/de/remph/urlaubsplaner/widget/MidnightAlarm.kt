// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.remph.urlaubsplaner.UrlaubsplanerApp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * One alarm a day, just after the next local midnight, that draws the widgets again so they show
 * the new date. It replaces the 30-minute update of the provider as the way the widget notices
 * the day: that one woke the phone 48 times a day, and `DATE_CHANGED`, which would do it, is not
 * delivered to a receiver in the manifest.
 *
 * Inexact and without wake-up (`set` with `RTC`): it needs no permission, and a phone that sleeps
 * through midnight draws the widget when it wakes. [schedule] replaces the alarm that is set, so it
 * can be called as often as needed: at the start of the app, after a change of time or time zone,
 * and by the alarm itself for the next day. A phone forgets its alarms at a reboot; the start of
 * the app process that the system's first widget update (or the boot broadcast) causes sets it
 * again.
 */
class MidnightAlarm(
    private val context: Context,
    private val clock: Clock,
) {
    fun schedule() {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.set(AlarmManager.RTC, nextTrigger(clock).toEpochMilli(), pendingIntent(context))
    }

    companion object {
        // A little after midnight, so a clock that is a few seconds fast or an early delivery still
        // finds the new date.
        private const val MARGIN_SECONDS = 60L

        /** The first minute of the day after today in the clock's current zone (DST days included). */
        fun nextTrigger(clock: Clock): Instant =
            LocalDate
                .now(clock)
                .plusDays(1)
                .atStartOfDay(clock.zone)
                .toInstant()
                .plusSeconds(MARGIN_SECONDS)

        // Explicit and immutable: nothing outside the app can start it or change it.
        internal fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, MidnightReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

/** Receives [MidnightAlarm]: draws the widgets for the new day and sets the next alarm. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as UrlaubsplanerApp
        // The next alarm first: a failure while drawing must not end the daily redraw.
        app.midnightAlarm.schedule()
        // The draw runs on a background thread; the broadcast stays alive until it is done. (goAsync
        // is null outside a real broadcast, as in a test that calls onReceive itself.)
        val pending = goAsync()
        app.widgetRefresher.redrawNow { pending?.finish() }
    }
}
