// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.core.widget.WidgetStatus
import de.remph.urlaubsplaner.settings.LanguageChoice
import de.remph.urlaubsplaner.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

// 2027: 10 March is a Wednesday, 15 March a Monday, 19 March a Friday.
@RunWith(AndroidJUnit4::class)
class WidgetModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val nrw = UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)

    private fun clockOn(date: String) =
        Clock.fixed(Instant.parse("${date}T10:00:00Z"), ZoneOffset.UTC)

    private fun marks(vararg dates: String): Map<LocalDate, DayMark> =
        dates.associate { LocalDate.parse(it) to DayMark.FULL }

    // Taken: two days before today. Planned: the week of 15 March. Resturlaub 10 until 31 March,
    // 7 of it used by those days, so 3 are still open.
    private val march =
        marks(
            "2027-03-08",
            "2027-03-09",
            "2027-03-15",
            "2027-03-16",
            "2027-03-17",
            "2027-03-18",
            "2027-03-19",
        )
    private val thirty =
        YearEntitlement(2027, VacationDays.of(30), VacationDays.of(10), MonthDay.of(3, 31))

    private fun status(
        today: String = "2027-03-10",
        settings: UserSettings = nrw,
        entitlement: YearEntitlement? = thirty,
        marks: Map<LocalDate, DayMark> = march,
    ) = WidgetStatus.of(clockOn(today), settings, listOfNotNull(entitlement), marks)

    private fun model(
        status: WidgetStatus,
        choice: LanguageChoice,
    ): WidgetModel {
        val language = WidgetLanguage.of(context, choice)
        return WidgetModel.of(status, language.context, language.locale)
    }

    private fun ready(
        status: WidgetStatus,
        choice: LanguageChoice = LanguageChoice.GERMAN,
    ) = model(status, choice) as WidgetModel.Status

    @Test
    fun `the normal state in German`() {
        val model = ready(status())

        assertEquals("33", model.number)
        assertFalse(model.overbooked)
        assertEquals("Tage übrig", model.unit)
        assertEquals("Tage übrig 2027", model.unitWithYear)
        assertEquals("Nächster Urlaub 15.03.", model.summary)
        assertEquals("Nächster Urlaub", model.tripLabel)
        assertEquals("15.–19. März", model.tripWhen)
        assertEquals("5 Urlaubstage", model.tripCost)
        assertEquals(listOf("Genommen 2", "Geplant 5", "Anspruch 40"), model.figures)
        assertEquals("3 Tage Resturlaub verfallen am 31.03.", model.warning)
        assertEquals(Tab.OVERVIEW, model.opens)
    }

    @Test
    fun `the normal state in English`() {
        val model = ready(status(), LanguageChoice.ENGLISH)

        assertEquals("days left", model.unit)
        assertEquals("days left 2027", model.unitWithYear)
        assertEquals("Next vacation 15.03.", model.summary)
        assertEquals("Next vacation", model.tripLabel)
        assertEquals("15–19 March", model.tripWhen)
        assertEquals("5 vacation days", model.tripCost)
        assertEquals(listOf("Taken 2", "Planned 5", "Entitlement 40"), model.figures)
        assertEquals("3 days of carry-over expire on 31.03.", model.warning)
    }

    @Test
    fun `half days use the language's decimal mark`() {
        val half =
            status(
                entitlement = YearEntitlement(2027, VacationDays.ofHalves(61)),
                marks = emptyMap(),
            )

        assertEquals("30,5", ready(half).number)
        assertEquals("30.5", ready(half, LanguageChoice.ENGLISH).number)
    }

    @Test
    fun `on vacation now the trip ends today or later`() {
        val model = ready(status(today = "2027-03-16"))

        assertEquals("Im Urlaub bis 19.03.", model.summary)
        assertEquals("Du bist gerade im Urlaub", model.tripLabel)
        assertEquals("bis 19. März", model.tripWhen)
        assertNull(model.tripCost)

        val english = ready(status(today = "2027-03-16"), LanguageChoice.ENGLISH)
        assertEquals("On vacation until 19.03.", english.summary)
        assertEquals("You are on vacation now", english.tripLabel)
        assertEquals("until 19 March", english.tripWhen)
    }

    @Test
    fun `without a planned trip it says so`() {
        val model = ready(status(marks = emptyMap()))

        assertEquals("Kein Urlaub geplant", model.summary)
        assertEquals("Noch kein Urlaub geplant", model.tripWhen)
        assertNull(model.tripCost)
        assertEquals(
            "No vacation planned",
            ready(status(marks = emptyMap()), LanguageChoice.ENGLISH).summary,
        )
    }

    @Test
    fun `the Resturlaub warning is gone after its Verfall day`() {
        val model = ready(status(today = "2027-04-01"))

        assertNull(model.warning)
    }

    @Test
    fun `one day of Resturlaub is singular`() {
        val one =
            status(
                entitlement =
                    YearEntitlement(
                        2027,
                        VacationDays.of(30),
                        VacationDays.of(1),
                        MonthDay.of(3, 31),
                    ),
                marks = emptyMap(),
            )

        assertEquals("1 Tag Resturlaub verfällt am 31.03.", ready(one).warning)
        assertEquals(
            "1 day of carry-over expires on 31.03.",
            ready(one, LanguageChoice.ENGLISH).warning,
        )
    }

    @Test
    fun `more vacation than entitled is a negative number with a real minus`() {
        val over = status(entitlement = YearEntitlement(2027, VacationDays.of(3)), marks = march)

        val model = ready(over)

        assertEquals("−4", model.number)
        assertTrue(model.overbooked)
        assertEquals("überbucht", model.unit)
        assertEquals("überbucht", model.unitWithYear)
        assertEquals("overbooked", ready(over, LanguageChoice.ENGLISH).unit)
    }

    @Test
    fun `a trip in the next year names the year`() {
        val later = status(marks = marks("2028-01-10"))

        assertEquals("Nächster Urlaub 10.01.2028", ready(later).summary)
        assertEquals("10. Januar 2028", ready(later).tripWhen)
    }

    @Test
    fun `without a Land the widget asks for it and a tap opens the settings`() {
        val model =
            model(
                status(settings = UserSettings()),
                LanguageChoice.GERMAN,
            ) as WidgetModel.Prompt

        assertEquals("Bundesland wählen", model.title)
        assertEquals("Dann zeigt das Widget deinen Urlaub.", model.subtitle)
        assertEquals(Tab.SETTINGS, model.opens)

        val english =
            model(
                status(settings = UserSettings()),
                LanguageChoice.ENGLISH,
            ) as WidgetModel.Prompt
        assertEquals("Choose state", english.title)
        assertEquals("Then the widget shows your vacation.", english.subtitle)
    }

    @Test
    fun `without this year's days the widget asks for them and a tap opens the settings`() {
        val model = model(status(entitlement = null), LanguageChoice.GERMAN) as WidgetModel.Prompt

        assertEquals("Urlaubstage eintragen", model.title)
        assertEquals("Für 2027 fehlen sie noch.", model.subtitle)
        assertEquals(Tab.SETTINGS, model.opens)
        assertEquals(
            "They are still missing for 2027.",
            (
                model(
                    status(entitlement = null),
                    LanguageChoice.ENGLISH,
                ) as WidgetModel.Prompt
            ).subtitle,
        )
    }

    @Test
    fun `the system language that is neither German nor English gives German`() {
        val turkish = android.content.res.Configuration(context.resources.configuration)
        turkish.setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag("tr")))
        val turkishContext = context.createConfigurationContext(turkish)

        val language = WidgetLanguage.of(turkishContext, LanguageChoice.SYSTEM)

        assertEquals("de", language.locale.language)
        assertEquals(
            "Tage übrig",
            language.context.getString(de.remph.urlaubsplaner.R.string.widget_days_left),
        )
    }

    @Test
    fun `the first German or English language in the phone's list decides`() {
        val mixed = android.content.res.Configuration(context.resources.configuration)
        mixed.setLocales(android.os.LocaleList.forLanguageTags("tr,en-GB,de"))

        val language =
            WidgetLanguage.of(context.createConfigurationContext(mixed), LanguageChoice.SYSTEM)

        assertEquals("en", language.locale.language)
        assertEquals(
            "days left",
            language.context.getString(de.remph.urlaubsplaner.R.string.widget_days_left),
        )
    }

    @Test
    fun `the system language English stays English`() {
        val english = android.content.res.Configuration(context.resources.configuration)
        english.setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag("en-GB")))

        val language =
            WidgetLanguage.of(
                context.createConfigurationContext(english),
                LanguageChoice.SYSTEM,
            )

        assertEquals("en", language.locale.language)
        assertEquals(
            "days left",
            language.context.getString(de.remph.urlaubsplaner.R.string.widget_days_left),
        )
    }
}
