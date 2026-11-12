package de.remph.urlaubsplaner.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.planner.CalendarMode
import de.remph.urlaubsplaner.planner.PlannerFixture
import de.remph.urlaubsplaner.planner.PlannerViewModel
import de.remph.urlaubsplaner.settings.CalendarExporter
import de.remph.urlaubsplaner.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AppRootTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture
    private lateinit var settings: SettingsViewModel
    private lateinit var planner: PlannerViewModel

    // What the widget asks for through MainActivity.
    private var request by mutableStateOf<TabRequest?>(null)

    // A new value builds AppRoot afresh with the same ViewModels, as after a rotation.
    private var screen by mutableIntStateOf(0)

    @Before
    fun open() {
        fixture = PlannerFixture()
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings =
            SettingsViewModel(
                fixture.repository,
                BackupFileStore(context.contentResolver),
                CalendarExporter(fixture.repository, CalendarFileStore(context), fixture.clock),
                fixture.clock,
                fixture.backupStatus,
            )
        val appearance = AppearanceStore(context.getSharedPreferences("app-root-test", 0))
        planner = fixture.viewModel()
        compose.setContent { key(screen) { AppRoot(settings, planner, appearance, request) } }
    }

    @After
    fun close() = fixture.close()

    // The title "Einstellungen" is on the page too, so pick the tab by its role.
    private fun tab(text: String) =
        hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    @Config(qualifiers = "de")
    fun `the app starts on the overview with three tabs and asks for the Land`() {
        waitForText("Einstellungen öffnen")

        compose.onNode(tab("Übersicht")).assertIsSelected()
        compose.onNode(tab("Kalender")).assertIsDisplayed()
        compose.onNode(tab("Einstellungen")).assertIsDisplayed()
        compose.onNodeWithText("Wähle zuerst dein Bundesland", substring = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the button on the overview opens the settings`() {
        waitForText("Einstellungen öffnen")

        compose.onNodeWithText("Einstellungen öffnen").performClick()

        compose.onNode(tab("Einstellungen")).assertIsSelected()
        waitForText("Bundesland wählen")
    }

    @Test
    @Config(qualifiers = "de")
    fun `the backup line opens the backup page, and the next visit starts on the list`() {
        fixture.fill()
        fixture.backupStatus.recordBackup(java.time.LocalDate.of(2026, 10, 1))
        waitForText("Letzte Sicherung vor 5 Monaten")

        compose.onNodeWithText("Letzte Sicherung vor 5 Monaten").performScrollTo().performClick()

        compose.waitForIdle()
        compose.onNode(tab("Einstellungen")).assertIsSelected()
        waitForText("Sicherung wiederherstellen")
        compose.onNodeWithText("Zuletzt gesichert: 01.10.2026").assertIsDisplayed()

        // The tab bar brings back where the user was: still the backup page.
        compose.onNode(tab("Übersicht")).performClick()
        waitForText("Letzte Sicherung vor 5 Monaten")
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Sicherung wiederherstellen")

        // A button or the widget that says "settings" means the list, not the page left open.
        compose.onNode(tab("Übersicht")).performClick()
        waitForText("Letzte Sicherung vor 5 Monaten")
        request = TabRequest(Tab.SETTINGS)
        compose.waitForIdle()
        compose.onNode(tab("Einstellungen")).assertIsSelected()
        compose.onAllNodesWithText("Sicherung wiederherstellen").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `tapping a tab switches to it`() {
        compose.onNode(tab("Kalender")).performClick()

        compose.onNode(tab("Kalender")).assertIsSelected()
        waitForText("Einstellungen öffnen")
    }

    private fun openCalendar(name: String = "Kalender") {
        compose.onNode(tab(name)).performClick()
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a second tap on the open calendar tab goes back to this month`() {
        openCalendar()
        planner.showMonth(YearMonth.of(2027, 9))
        compose.waitForIdle()

        compose.onNode(tab("Kalender")).performClick()
        compose.waitForIdle()

        assertEquals(YearMonth.of(2027, 3), planner.calendarMonth.value)
        assertEquals(CalendarMode.MONTH, planner.calendarMode.value)
        compose.onNode(tab("Kalender")).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `in the year view the second tap goes back to this year and keeps the view`() {
        openCalendar()
        planner.showMode(CalendarMode.YEAR)
        planner.showMonth(YearMonth.of(2028, 6))
        compose.waitForIdle()

        compose.onNode(tab("Kalender")).performClick()
        compose.waitForIdle()

        assertEquals(2027, planner.calendarMonth.value.year)
        assertEquals(CalendarMode.YEAR, planner.calendarMode.value)
    }

    @Test
    @Config(qualifiers = "de")
    fun `the first tap on the calendar tab opens it where it was left`() {
        planner.showMonth(YearMonth.of(2027, 9))

        compose.onNode(tab("Kalender")).performClick()
        compose.waitForIdle()

        assertEquals(YearMonth.of(2027, 9), planner.calendarMonth.value)
    }

    @Test
    @Config(qualifiers = "de")
    fun `a second tap on the other tabs changes nothing`() {
        planner.showMonth(YearMonth.of(2027, 9))
        compose.onNode(tab("Übersicht")).performClick()
        compose.onNode(tab("Einstellungen")).performClick()
        compose.onNode(tab("Einstellungen")).performClick()
        compose.waitForIdle()

        assertEquals(YearMonth.of(2027, 9), planner.calendarMonth.value)
        compose.onNode(tab("Einstellungen")).assertIsSelected()
    }

    private fun clickLabel(tab: SemanticsMatcher) =
        compose
            .onNode(tab)
            .fetchSemanticsNode()
            .config
            .let {
                if (SemanticsActions.OnClick in
                    it
                ) {
                    it[SemanticsActions.OnClick].label
                } else {
                    null
                }
            }

    @Test
    @Config(qualifiers = "de")
    fun `TalkBack hears what a tap on the open calendar tab does`() {
        openCalendar()
        assertEquals("Zum aktuellen Monat", clickLabel(tab("Kalender")))

        planner.showMode(CalendarMode.YEAR)
        compose.waitForIdle()
        assertEquals("Zum aktuellen Jahr", clickLabel(tab("Kalender")))
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English action label says this month`() {
        openCalendar("Calendar")
        assertEquals("Go to this month", clickLabel(tab("Calendar")))
    }

    @Test
    @Config(qualifiers = "de")
    fun `the calendar tab has no action label while another tab is open`() {
        assertEquals(null, clickLabel(tab("Kalender")))
        assertEquals(null, clickLabel(tab("Einstellungen")))
    }

    @Test
    @Config(qualifiers = "de")
    fun `a request from the widget opens its tab`() {
        waitForText("Einstellungen öffnen")

        request = TabRequest(Tab.SETTINGS)

        compose.waitForIdle()
        compose.onNode(tab("Einstellungen")).assertIsSelected()
        waitForText("Bundesland wählen")
    }

    @Test
    @Config(qualifiers = "de")
    fun `a second tap on the widget opens the tab again after the user has left it`() {
        request = TabRequest(Tab.SETTINGS)
        compose.waitForIdle()
        compose.onNode(tab("Übersicht")).performClick()
        compose.onNode(tab("Übersicht")).assertIsSelected()

        request = TabRequest(Tab.SETTINGS)

        compose.waitForIdle()
        compose.onNode(tab("Einstellungen")).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `an old request does not pull the user back after a rotation or a tab change`() {
        request = TabRequest(Tab.SETTINGS)
        compose.waitForIdle()
        compose.onNode(tab("Kalender")).performClick()

        compose.waitForIdle()

        compose.onNode(tab("Kalender")).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a settings page stays open when the user visits another tab and comes back`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")

        compose.onNode(tab("Übersicht")).performClick()
        compose.onNode(tab("Einstellungen")).performClick()

        compose.onNodeWithText("Sicherung wiederherstellen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a second tap on the open settings tab goes back to the list`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")

        compose.onNode(tab("Einstellungen")).performClick()

        waitForText("Bundesland wählen")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Sicherung wiederherstellen").fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(tab("Einstellungen")).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a second tap on the settings list changes nothing`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")

        compose.onNode(tab("Einstellungen")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Datensicherung").assertIsDisplayed()

        // Opening a page afterwards must not be undone by the earlier tap.
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")
        compose.waitForIdle()
        compose.onNodeWithText("Sicherung wiederherstellen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `an earlier tap on the settings tab does not close a page after a tab change`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Bundesland wählen")

        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")
        compose.onNode(tab("Übersicht")).performClick()
        compose.onNode(tab("Einstellungen")).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Sicherung wiederherstellen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the button on the overview opens the settings list, not the page left open`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")
        compose.onNode(tab("Übersicht")).performClick()
        waitForText("Einstellungen öffnen")

        compose.onNodeWithText("Einstellungen öffnen").performClick()

        waitForText("Bundesland wählen")
        compose.onAllNodesWithText("Sicherung wiederherstellen").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `a request from the widget opens the settings list, not the page left open`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.onNodeWithText("Datensicherung").performClick()
        waitForText("Sicherung wiederherstellen")
        compose.onNode(tab("Übersicht")).performClick()

        request = TabRequest(Tab.SETTINGS)

        waitForText("Bundesland wählen")
        compose.onAllNodesWithText("Sicherung wiederherstellen").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `switching tabs fades, so both screens exist half way and only the new one at the end`() {
        waitForText("Einstellungen öffnen")
        compose.mainClock.autoAdvance = false

        compose.onNode(tab("Einstellungen")).performClick()
        compose.mainClock.advanceTimeBy(60)

        compose.onNodeWithText("Einstellungen öffnen").assertExists()
        compose.onNodeWithText("Bundesland wählen").assertExists()

        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("Einstellungen öffnen").assertDoesNotExist()
        compose.onNodeWithText("Bundesland wählen").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a settings page slides in, so both exist half way and only the page at the end`() {
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Datensicherung").performClick()
        compose.mainClock.advanceTimeBy(60)

        compose.onNodeWithText("Bundesland wählen").assertExists()
        compose.onNodeWithText("Sicherung wiederherstellen").assertExists()

        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("Bundesland wählen").assertDoesNotExist()
        compose.onNodeWithText("Sicherung wiederherstellen").assertExists()
    }

    private fun waitUntilGone(text: String) =
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }

    private fun marked(): MarkedDays =
        runBlocking {
            fixture.repository
                .observeMarkedDays(fixture.date("2027-01-01"), fixture.date("2027-12-31"))
                .first()
        }

    @Test
    @Config(qualifiers = "de")
    fun `a settings message stays when the tab is left and does not come back`() {
        fixture.fill()
        compose.onNode(tab("Einstellungen")).performClick()
        waitForText("Datensicherung")

        settings.clearYear(2027)
        waitForText("Urlaubstage von 2027 gelöscht.")
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(tab("Übersicht")).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Urlaubstage von 2027 gelöscht.").assertIsDisplayed()

        waitUntilGone("Urlaubstage von 2027 gelöscht.")
        compose.onNode(tab("Einstellungen")).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)

        compose.onAllNodesWithText("Urlaubstage von 2027 gelöscht.").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `Entfernen says how many days went and Rückgängig puts the day back`() {
        fixture.fill()
        compose.onNode(tab("Kalender")).performClick()
        waitForText("Karfreitag")

        compose
            .onNode(
                hasContentDescription("Montag, 15. März 2027", substring = true),
            ).performClick()
        compose.onNodeWithText("Entfernen").performClick()

        waitForText("1 Tag entfernt")
        compose.waitUntil(5_000) { fixture.date("2027-03-15") !in marked().marks }
        compose.onNodeWithText("Rückgängig").performClick()

        compose.waitUntil(5_000) { fixture.date("2027-03-15") in marked().marks }
        assertEquals(DayMark.FULL, marked().marks[fixture.date("2027-03-15")])
        waitUntilGone("1 Tag entfernt")
    }

    @Test
    @Config(qualifiers = "de")
    fun `the undo restores each mark and flag, also when another month is shown`() {
        fixture.fill()
        runBlocking {
            fixture.repository.mark(fixture.date("2027-03-22"), DayMark.FULL, granted = true)
            fixture.repository.mark(fixture.date("2027-03-23"), DayMark.HALF)
        }
        val before = marked()
        compose.onNode(tab("Kalender")).performClick()
        waitForText("Karfreitag")

        // Removed in March (as a range would), then the user steps on to June before the undo.
        planner.unmark((15..23).map { fixture.date("2027-03-%02d".format(it)) })
        waitForText("7 Tage entfernt")
        planner.showMonth(YearMonth.of(2027, 6))
        waitForText("Juni 2027")
        compose.onNodeWithText("Rückgängig").performClick()

        compose.waitUntil(5_000) { marked() == before }
    }

    @Test
    @Config(qualifiers = "de")
    fun `a rotation while the undo is offered offers it again`() {
        fixture.fill()
        compose.onNode(tab("Kalender")).performClick()
        waitForText("Karfreitag")
        planner.unmark(listOf(fixture.date("2027-03-15")))
        waitForText("1 Tag entfernt")

        screen++
        compose.waitForIdle()

        waitForText("1 Tag entfernt")
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { fixture.date("2027-03-15") in marked().marks }
        // Let the screens read the restored day before the database closes after the test.
        compose.waitUntil(5_000) {
            planner.calendarView(2027).value?.markOn(fixture.date("2027-03-15")) !=
                null
        }
        waitUntilGone("1 Tag entfernt")
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English undo message counts the days`() {
        fixture.fill()
        compose.onNode(tab("Calendar")).performClick()
        waitForText("Good Friday")

        planner.unmark((15..16).map { fixture.date("2027-03-$it") })

        waitForText("2 days removed")
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }
}
