// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.appwidget.AppWidgetProvider
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.FreshApp
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.balance.AmountText
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationCalculator
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.targetApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Both widgets on a launcher of the test's own, against the app's real database. What this is
 * for: the widget stays stale in some situations that no JVM test reaches (parked item 7, "stays
 * stale after clearing the data and restoring a backup"). A failing test here shows which texts
 * the widget showed instead.
 *
 * Not covered: the setup screen, a real launcher's resizing, and the process dying (a test cannot
 * clear its own app's data).
 */
@RunWith(AndroidJUnit4::class)
class WidgetOnDeviceTest {
    @get:Rule
    val fresh = FreshApp()

    private val app = targetApp
    private val repository = app.repository
    private val year = LocalDate.now(app.clock).year

    // The widget speaks the app language, which is not necessarily the phone's.
    private val language = WidgetLanguage.of(app)

    private fun text(id: Int) = language.context.getString(id)

    private fun number(days: Double) = AmountText.format(VacationDays.of(days), language.locale)

    private fun save(
        total: Int,
        settings: UserSettings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN),
    ) = runBlocking {
        repository.saveSettings(settings)
        repository.saveEntitlement(YearEntitlement(year, VacationDays.of(total)))
    }

    /** A working day of this year that is not a public holiday, so marking it costs a full day. */
    private fun costlyDay(): LocalDate {
        val check = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN).publicHolidayCheck()!!
        val calculator = VacationCalculator(app.clock, isPublicHoliday = check)
        return generateSequence(LocalDate.of(year, 1, 15)) { it.plusDays(1) }
            .first { calculator.cost(it) > VacationDays.ZERO }
    }

    private fun place(receiver: Class<out AppWidgetProvider>) =
        PlacedWidget(receiver).also {
            // The setup screen does this when it is done; a launcher always runs it.
            app.widgetRefresher.start()
        }

    private fun followsTheData(receiver: Class<out AppWidgetProvider>) {
        runBlocking { repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)) }
        place(receiver).use { widget ->
            widget.awaitText(text(R.string.enter_vacation_days))

            save(total = 30)
            widget.awaitText(number(30.0))

            // A second change soon after the first: the case a running Glance session used to miss.
            save(total = 25)
            widget.awaitText(number(25.0))

            runBlocking { repository.mark(costlyDay(), DayMark.FULL) }
            widget.awaitText(number(24.0))

            runBlocking { repository.unmark(costlyDay()) }
            widget.awaitText(number(25.0))
        }
    }

    @Test
    fun `the small widget follows entering days and marking`() =
        followsTheData(UrlaubsplanerWidgetReceiver::class.java)

    @Test
    fun `the big widget follows entering days and marking`() =
        followsTheData(UrlaubsplanerBigWidgetReceiver::class.java)

    @Test
    fun `a widget placed on a fresh app asks for the Bundesland`() {
        place(UrlaubsplanerWidgetReceiver::class.java).use { widget ->
            widget.awaitText(text(R.string.land_choose))
        }
    }

    @Test
    fun `a widget placed on a fresh app shows the numbers after a backup is restored`() {
        // The backup goes through a real file, as the picker's file would. Not through
        // BackupFileStore: it accepts only the picker's content:// addresses, and a test has no picker.
        val file = File(app.cacheDir, "widget-restore.json")
        val backup =
            BackupData(
                settings = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN),
                entitlements = listOf(YearEntitlement(year, VacationDays.of(28))),
            )
        try {
            file.outputStream().use { BackupFile.writeTo(it, backup, LocalDate.now(app.clock)) }
            place(UrlaubsplanerWidgetReceiver::class.java).use { widget ->
                widget.awaitText(text(R.string.land_choose))

                runBlocking {
                    repository.replaceAll(
                        file.inputStream().use { BackupFile.readFrom(it) },
                    )
                }

                widget.awaitText(number(28.0))
            }
        } finally {
            file.delete()
        }
    }

    private fun choose(
        widget: PlacedWidget,
        colors: WidgetColors,
    ) = runBlocking {
        // What the setup screen does when it is done.
        val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(widget.id)
        updateAppWidgetState(app, glanceId) { WidgetStyle(colors = colors).writeTo(it) }
        app.widgetRefresher.invalidate()
        app.widgetRefresher.drawNow(widget.id)
    }

    @Test
    fun `changing the colour twice in a row shows both at once`() {
        save(total = 30)
        PlacedWidget(UrlaubsplanerWidgetReceiver::class.java).use { widget ->
            app.widgetRefresher.start()
            val number = number(30.0)
            widget.awaitText(number)

            choose(widget, WidgetColors.LIGHT)
            Thread.sleep(SETTLE_MILLIS)
            val light = widget.awaitColor(number) { true }

            choose(widget, WidgetColors.DARK)
            widget.awaitColor(number) { it != light }

            // The second change within the minute Glance keeps its session: it used to show late.
            choose(widget, WidgetColors.LIGHT)
            widget.awaitColor(number) { it == light }
        }
    }

    @Test
    fun `the app knows that a widget is placed`() {
        PlacedWidget(UrlaubsplanerBigWidgetReceiver::class.java).use {
            assertTrue(app.widgetRefresher.hasWidgets())
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
