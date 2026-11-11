package de.remph.urlaubsplaner.settings

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.appearance.ThemeMode
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import de.remph.urlaubsplaner.data.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Composes the real settings list and pages on the JVM (Robolectric) and looks at what it shows. It does not replace
 * trying the app on a phone: pickers, the keyboard and TalkBack are not covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: UrlaubsplanerDatabase
    private lateinit var repository: UserDataRepository
    private lateinit var viewModel: SettingsViewModel
    private lateinit var appearance: AppearanceStore
    private lateinit var backupStatus: BackupStatusStore

    @Before
    fun open() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UrlaubsplanerDatabase::class.java).build()
        repository = UserDataRepository(database)
        val clock = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC)
        backupStatus = BackupStatusStore(context.getSharedPreferences("backup-status-test", 0))
        viewModel =
            SettingsViewModel(
                repository,
                BackupFileStore(context.contentResolver),
                CalendarExporter(repository, CalendarFileStore(context), clock),
                clock,
                backupStatus,
            )
        val preferences = context.getSharedPreferences("appearance-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        appearance = AppearanceStore(preferences)
    }

    @After
    fun close() = database.close()

    private fun show(title: String) {
        compose.setContent {
            SettingsHost(viewModel, appearance, showMessage = {}, PaddingValues())
        }
        waitForText(title)
    }

    /** Room reports from its own threads, so the page appears a moment after composing. */
    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun chooseLand(
        land: Bundesland,
        shownAs: String,
    ) {
        runBlocking { repository.saveSettings(UserSettings(land = land)) }
        waitForText(shownAs)
    }

    /** The marked days arrive from Room a moment after the page shows. */
    private fun waitForEnabled(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(text).assertIsEnabled() }.isSuccess
        }

    private fun openRow(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a fresh start in German asks for the Land and shows only what is needed to start`() {
        show("Einstellungen")

        compose
            .onNodeWithText("Welches Bundesland gilt für dich?", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Bundesland wählen").assertIsDisplayed()
        compose.onNodeWithText("Sprache").assertIsDisplayed()
        compose.onNodeWithText("Datensicherung").assertIsDisplayed()
        compose.onNodeWithText("Urlaub 2027").assertDoesNotExist()
        compose.onNodeWithText("Darstellung").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun `a fresh start in English has the English texts`() {
        show("Settings")

        compose.onNodeWithText("Choose state").assertIsDisplayed()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Backup").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the language row says Systemsprache when the phone decides`() {
        show("Einstellungen")

        compose.onNodeWithText("Systemsprache").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `tapping the Bundesland row opens the list of all 16 Länder`() {
        show("Einstellungen")

        compose.onNodeWithText("Bundesland wählen").performClick()

        compose.onNodeWithText("Baden-Württemberg").assertIsDisplayed()
        compose.onNodeWithText("Bayern").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `with a Land the list shows each topic with what is set`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")

        compose.onNodeWithText("Urlaub 2027").assertIsDisplayed()
        compose.onNodeWithText("Noch nicht eingetragen").assertIsDisplayed()
        compose.onNodeWithText("Arbeitstage").assertIsDisplayed()
        compose.onNodeWithText("Mo bis Fr").assertIsDisplayed()
        compose.onNodeWithText("Darstellung").assertIsDisplayed()
        compose.onNodeWithText("Wie das System").assertIsDisplayed()
        // NRW has no regional holiday to decide, so the row is not offered.
        compose.onNodeWithText("Regionale Feiertage").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the Bayern row shows its holiday and the page has the switch`() {
        show("Einstellungen")
        chooseLand(Bundesland.BAYERN, "Bayern")

        compose.onNodeWithText("Regionale Feiertage").assertIsDisplayed()
        compose.onNodeWithText("Mariä Himmelfahrt").assertIsDisplayed()

        openRow("Regionale Feiertage")

        compose
            .onNodeWithText("Mariä Himmelfahrt (15. August) ist bei mir frei")
            .assertIsDisplayed()
            .assertIsOn()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the back arrow on a page returns to the list`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Arbeitstage")
        compose.onNodeWithText("Heiligabend (24.12.)").assertIsDisplayed()

        compose.onNodeWithContentDescription("Zurück").performClick()

        compose.onNodeWithText("Heiligabend (24.12.)").assertDoesNotExist()
        compose.onNodeWithText("Mo bis Fr").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the work day chips use the short weekday names without a full stop`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")

        openRow("Arbeitstage")

        // Android's own short German names are "Mi." and "Do.".
        compose.onNodeWithText("Mi").assertIsDisplayed()
        compose.onNodeWithText("Mi.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `typing days enables Speichern and a wrong amount shows the error`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")
        compose.onNodeWithText("Speichern").performScrollTo().assertIsNotEnabled()
        // The year stepper has descriptions for TalkBack.
        compose.onNodeWithContentDescription("Vorheriges Jahr").assertExists()
        compose.onNodeWithContentDescription("Nächstes Jahr").assertExists()

        compose.onNodeWithText("Urlaubstage im Jahr").performScrollTo().performTextInput("12,3")
        compose.onNodeWithText("Bitte ganze oder halbe Tage", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Speichern").assertIsNotEnabled()

        compose.onNodeWithText("Urlaubstage im Jahr").performTextClearance()
        compose.onNodeWithText("Urlaubstage im Jahr").performTextInput("12,5")
        compose.onNodeWithText("Speichern").assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the Resturlaub expiry year is chosen with two chips and the list shows the full date`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        compose.onNodeWithText("Verfällt am").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Verfällt im Jahr 2027").assertIsDisplayed()
        compose.onNodeWithText("Urlaubstage im Jahr").performScrollTo().performTextInput("30")
        // The field starts with 0, so it is cleared before typing.
        compose.onNodeWithText("Resturlaub").performScrollTo().performTextClearance()
        compose.onNodeWithText("Resturlaub").performTextInput("4")
        compose
            .onNodeWithContentDescription(
                "Verfällt im Jahr 2028",
            ).performScrollTo()
            .performClick()
        compose.onNodeWithText("Speichern").performScrollTo().performClick()

        compose.onNodeWithContentDescription("Zurück").performScrollTo().performClick()
        waitForText("30 Tage, dazu 4 Resturlaub bis 31.03.2028")
    }

    @Test
    @Config(qualifiers = "de")
    fun `the carry-over option shows in German and is off by default`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        compose
            .onNodeWithText("Übrige Tage als Resturlaub vorschlagen")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the carry-over option shows in English and is off by default`() {
        show("Settings")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "North Rhine-Westphalia")
        openRow("Vacation 2027")

        compose
            .onNodeWithText("Suggest unused days as carried-over days")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the backup page offers restoring from the start and saving once there is data`() {
        show("Einstellungen")
        openRow("Datensicherung")

        compose.onNodeWithText("Sicherung wiederherstellen").assertIsDisplayed()
        compose.onNodeWithText("Sicherung speichern").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the backup page says when the last backup was saved, or that there is none`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Datensicherung")

        compose.onNodeWithText("Noch keine Sicherung").assertIsDisplayed()

        backupStatus.recordBackup(LocalDate.of(2026, 11, 3))

        waitForText("Zuletzt gesichert: 03.11.2026")
        compose.onNodeWithText("Noch keine Sicherung").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English backup page says the same`() {
        backupStatus.recordBackup(LocalDate.of(2026, 11, 3))
        show("Settings")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "North Rhine-Westphalia")
        openRow("Backup")

        waitForText("Last saved: Nov 3, 2026")
    }

    @Test
    @Config(qualifiers = "de")
    fun `a fresh install shows no backup line, restoring is its first step`() {
        show("Einstellungen")
        openRow("Datensicherung")

        compose.onNodeWithText("Sicherung wiederherstellen").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Sicherung").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `choosing dark and pure black on the appearance page is stored`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Darstellung")

        compose.onNodeWithText("Dunkel").performClick()
        compose.onNodeWithText("Reines Schwarz").performScrollTo().performClick()

        assertEquals(ThemeMode.DARK, appearance.state.value.mode)
        assertTrue(appearance.state.value.oled)
        compose.onNodeWithText("Reines Schwarz").assertIsOn()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a suggestion from the year that is still running says so`() {
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(30)))
        }
        show("Einstellungen")
        waitForText("Nordrhein-Westfalen")
        openRow("Urlaub 2027")
        compose.onNodeWithContentDescription("Nächstes Jahr").performClick()

        waitForText("Vorschlag aus 2027", substring = true)
        compose
            .onNodeWithText("Das Jahr 2027 läuft noch", substring = true)
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a suggestion from a finished year has no running-year hint`() {
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            repository.saveEntitlement(YearEntitlement(2026, VacationDays.of(30)))
        }
        show("Einstellungen")
        waitForText("Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        waitForText("Vorschlag aus 2026", substring = true)
        compose.onNodeWithText("läuft noch", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the calendar export row only shows with a Land`() {
        show("Einstellungen")
        compose.onNodeWithText("Kalender-Export").assertDoesNotExist()

        chooseLand(Bundesland.BAYERN, "Bayern")

        compose.onNodeWithText("Kalender-Export").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the calendar export page names the trips and offers send and save`() {
        runBlocking {
            repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            repository.markAll(
                listOf(
                    java.time.LocalDate.parse("2027-03-16"),
                    java.time.LocalDate.parse("2027-03-17"),
                ),
                DayMark.FULL,
            )
        }
        show("Einstellungen")
        waitForText("Nordrhein-Westfalen")
        openRow("Kalender-Export")

        waitForText("1 Urlaub in der Datei")
        compose.onNodeWithText("In Kalender öffnen").assertIsEnabled()
        compose.onNodeWithText("Als Datei speichern").assertIsEnabled()
        compose
            .onNodeWithText("Feiertage und Schulferien sind nicht dabei", substring = true)
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the calendar export page in English with no vacation has both buttons off`() {
        runBlocking { repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN)) }
        show("Settings")
        waitForText("North Rhine-Westphalia")
        openRow("Calendar export")

        waitForText("No vacation entered yet", substring = true)
        compose.onNodeWithText("Open in calendar").assertIsNotEnabled()
        compose.onNodeWithText("Save as file").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the clear action is off while the year has no marked days`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        compose
            .onNodeWithText(
                "Urlaubstage von 2027 löschen",
            ).performScrollTo()
            .assertIsNotEnabled()
        compose
            .onNodeWithText(
                "Für 2027 sind keine Tage markiert.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `clearing a year in German asks with the count, then deletes and confirms`() {
        runBlocking {
            repository.mark(LocalDate.of(2027, 1, 4), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 8, 2), DayMark.HALF)
            repository.mark(LocalDate.of(2026, 8, 3), DayMark.FULL)
        }
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        compose.onNodeWithText("Urlaubstage von 2027 löschen").performScrollTo().assertIsEnabled()
        waitForEnabled("Urlaubstage von 2027 löschen")
        compose.onNodeWithText("Urlaubstage von 2027 löschen").performScrollTo().performClick()

        compose.onNodeWithText("Urlaubstage von 2027 löschen?").assertIsDisplayed()
        compose
            .onNodeWithText("Alle 2 markierten Tage von 2027 werden gelöscht.", substring = true)
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Speichere vorher eine Sicherung",
                substring = true,
            ).assertIsDisplayed()
        // Nothing is gone until the confirmation.
        assertEquals(3, runBlocking { repository.readAll() }.marks.size)

        compose.onNodeWithText("Löschen").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.readAll() }.marks.size == 1
        }
        compose.onNodeWithText("Urlaubstage von 2027 löschen?").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `cancelling the clear dialog keeps the marked days`() {
        runBlocking { repository.mark(LocalDate.of(2027, 1, 4), DayMark.FULL) }
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")
        waitForEnabled("Urlaubstage von 2027 löschen")
        compose.onNodeWithText("Urlaubstage von 2027 löschen").performScrollTo().performClick()
        compose
            .onNodeWithText("Der markierte Tag von 2027 wird gelöscht.", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithText("Abbrechen").performClick()

        compose.onNodeWithText("Urlaubstage von 2027 löschen?").assertDoesNotExist()
        assertEquals(1, runBlocking { repository.readAll() }.marks.size)
    }

    @Test
    @Config(qualifiers = "en")
    fun `the clear dialog has the English texts`() {
        runBlocking {
            repository.mark(LocalDate.of(2027, 1, 4), DayMark.FULL)
            repository.mark(LocalDate.of(2027, 1, 5), DayMark.FULL)
        }
        show("Settings")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "North Rhine-Westphalia")
        openRow("Vacation 2027")
        waitForEnabled("Clear vacation days of 2027")

        compose.onNodeWithText("Clear vacation days of 2027").performScrollTo().performClick()

        compose.onNodeWithText("Clear vacation days of 2027?").assertIsDisplayed()
        compose
            .onNodeWithText("All 2 marked days of 2027 will be deleted.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("This cannot be undone.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Clear").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the granted days option shows in German, is off by default and can be turned on`() {
        show("Einstellungen")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "Nordrhein-Westfalen")
        openRow("Urlaub 2027")

        compose
            .onNodeWithText("Genehmigte Urlaubstage")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()

        // The switch follows Room a moment after the value is saved, so wait for the switch.
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Genehmigte Urlaubstage").assertIsOn() }.isSuccess
        }
        assertTrue(runBlocking { repository.observeSettings().first() }.grantedDays)
    }

    @Test
    @Config(qualifiers = "en")
    fun `the granted days option shows in English`() {
        show("Settings")
        chooseLand(Bundesland.NORDRHEIN_WESTFALEN, "North Rhine-Westphalia")
        openRow("Vacation 2027")

        compose
            .onNodeWithText("Granted vacation days")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
    }
}
