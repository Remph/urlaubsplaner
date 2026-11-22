// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.PhoneClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class MidnightAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    // Glance's own update needs a real broadcast (goAsync); it is not what is tested here, and its
    // background job would fail with an uncaught exception that lands in a later test.
    private fun receiver() =
        object : WidgetReceiver() {
            override val glanceAppWidget = UrlaubsplanerWidget()

            override fun onUpdate(
                context: Context,
                appWidgetManager: AppWidgetManager,
                appWidgetIds: IntArray,
            ) = Unit
        }

    private fun cancelAlarm() =
        context
            .getSystemService(
                AlarmManager::class.java,
            ).cancel(MidnightAlarm.pendingIntent(context))

    private fun clock(
        at: String,
        zone: String,
    ) = Clock.fixed(Instant.parse(at), ZoneId.of(zone))

    @Test
    fun `the alarm is a minute after the next local midnight`() {
        // 20:00 in Berlin on 10 March 2027 (CET): the next midnight is 23:00 UTC.
        val trigger = MidnightAlarm.nextTrigger(clock("2027-03-10T19:00:00Z", "Europe/Berlin"))

        assertEquals(Instant.parse("2027-03-10T23:01:00Z"), trigger)
    }

    @Test
    fun `just after midnight the next alarm is a day later`() {
        val trigger = MidnightAlarm.nextTrigger(clock("2027-03-10T23:00:30Z", "Europe/Berlin"))

        assertEquals(Instant.parse("2027-03-11T23:01:00Z"), trigger)
    }

    @Test
    fun `the day of the change to summer time is an hour short`() {
        // 27 March 2027, 22:00 in Berlin; the 28th starts at 23:00 UTC and has 23 hours.
        val first = MidnightAlarm.nextTrigger(clock("2027-03-27T21:00:00Z", "Europe/Berlin"))
        assertEquals(Instant.parse("2027-03-27T23:01:00Z"), first)

        // 28 March, 12:00 CEST: midnight of the 29th is 22:00 UTC, not 23:00.
        val next = MidnightAlarm.nextTrigger(clock("2027-03-28T10:00:00Z", "Europe/Berlin"))
        assertEquals(Instant.parse("2027-03-28T22:01:00Z"), next)
    }

    @Test
    fun `a moved time zone moves the alarm`() {
        val berlin = MidnightAlarm.nextTrigger(clock("2027-03-10T19:00:00Z", "Europe/Berlin"))
        // The same moment in New York is 14:00, so its midnight is nine hours later (UTC-5).
        val newYork = MidnightAlarm.nextTrigger(clock("2027-03-10T19:00:00Z", "America/New_York"))

        assertEquals(Instant.parse("2027-03-11T05:01:00Z"), newYork)
        assertTrue(newYork > berlin)
    }

    // Robolectric's ScheduledAlarm marks its fields as deprecated, but has no other way to read them.
    @Suppress("DEPRECATION")
    @Test
    fun `scheduling sets one inexact alarm on the wall clock that a second call replaces`() {
        val clock = clock("2027-03-10T19:00:00Z", "Europe/Berlin")
        val alarm = MidnightAlarm(context, clock)

        alarm.schedule()
        alarm.schedule()

        val set = alarms.scheduledAlarms
        assertEquals(1, set.size)
        assertEquals(AlarmManager.RTC, set.single().type)
        assertEquals(Instant.parse("2027-03-10T23:01:00Z").toEpochMilli(), set.single().triggerAtMs)
    }

    @Test
    fun `the receiver sets the next alarm`() {
        MidnightReceiver().onReceive(context, Intent(context, MidnightReceiver::class.java))

        val set = alarms.scheduledAlarms
        assertEquals(1, set.size)
        val expected = MidnightAlarm.nextTrigger(PhoneClock()).toEpochMilli()
        // The test ends within a day of the start, so both reach the same midnight.
        assertEquals(expected, set.single().triggerAtMs)
    }

    @Test
    fun `a change of time zone or time sets the alarm again, a boot sets none itself`() {
        for (action in listOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
            cancelAlarm()

            receiver().onReceive(context, Intent(action))

            assertEquals(action, 1, alarms.scheduledAlarms.size)
        }

        // The start of the process does it (UrlaubsplanerApp.onCreate); the receiver adds nothing.
        cancelAlarm()
        receiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
