package de.remph.urlaubsplaner.planner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Composes the real overview in German and English and looks at what it says. */
@RunWith(AndroidJUnit4::class)
class OverviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var fixture: PlannerFixture

    @Before
    fun open() {
        fixture = PlannerFixture()
    }

    @After
    fun close() = fixture.close()

    private val shown = mutableListOf<LocalDate>()
    private var backupOpened = 0

    private fun show(waitFor: String) {
        compose.setContent {
            OverviewScreen(
                fixture.viewModel(),
                PaddingValues(),
                onOpenSettings = {},
                onOpenBackup = { backupOpened++ },
                onOpenMonth = {},
                onShowDate = { shown += it },
            )
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "de")
    fun `without a Land the overview asks for it and offers the settings`() {
        show("Wähle zuerst")

        compose.onNodeWithText("Einstellungen öffnen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `with a Land but no days entered it points to the settings`() {
        runBlocking { fixture.repository.saveSettings(UserSettings(land = Bundesland.BAYERN)) }
        show("Für 2027 sind noch keine Urlaubstage eingetragen.")

        compose.onNodeWithText("Urlaubstage eintragen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the days left come first, then taken and planned`() {
        fixture.fill()
        show("Tage übrig 2027")

        compose.onNodeWithText("33").assertIsDisplayed()
        compose.onNode(hasText("2") and hasText("Genommen")).assertIsDisplayed()
        compose.onNode(hasText("5") and hasText("Geplant")).assertIsDisplayed()
        compose.onNode(hasText("40") and hasText("Anspruch")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `an old backup shows one quiet line that opens the backup page`() {
        fixture.fill()
        // 2027-03-10 is today: five whole months.
        fixture.backupStatus.recordBackup(LocalDate.of(2026, 10, 1))
        show("Letzte Sicherung vor 5 Monaten")

        compose
            .onNodeWithText(
                "Letzte Sicherung vor 5 Monaten",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNode(
                hasText("Letzte Sicherung vor 5 Monaten") and hasClickAction(),
            ).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Letzte Sicherung vor 5 Monaten").performClick()

        assertEquals(1, backupOpened)
    }

    @Test
    @Config(qualifiers = "en")
    fun `a day-marking user who never saved a backup is reminded after 14 days, in English`() {
        fixture.fill()
        // The app first saw marked days on 24 February: 14 days before today.
        fixture.backupStatus.noteMarkedDays(LocalDate.of(2027, 2, 24))
        show("No backup saved yet")

        compose.onNodeWithText("No backup saved yet").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `a recent backup or a young start shows no line`() {
        fixture.fill()
        fixture.backupStatus.recordBackup(LocalDate.of(2027, 2, 1))
        show("Tage übrig 2027")

        compose.onAllNodesWithText("Sicherung", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `the first marked days start the wait, they do not show the line at once`() {
        fixture.fill()
        show("Tage übrig 2027")
        // The view model noted today as the start; nothing is due before 14 days have passed.
        compose.waitUntil(5_000) { fixture.backupStatus.state.value.marksSince != null }

        assertEquals(LocalDate.of(2027, 3, 10), fixture.backupStatus.state.value.marksSince)
        compose.onAllNodesWithText("Sicherung", substring = true).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "de")
    fun `open Resturlaub before its expiry shows a warning`() {
        fixture.fill()
        show("verfallen am")

        // 10 Resturlaub, 7 days marked before 31 March: 3 are still open.
        compose
            .onNodeWithText("3 Tage Resturlaub verfallen am 31.03.2027", substring = true)
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `no warning once the Resturlaub is used up`() {
        fixture.fill()
        runBlocking {
            fixture.repository.markAll(
                listOf("2027-03-22", "2027-03-23", "2027-03-24").map(fixture::date),
                DayMark.FULL,
            )
        }
        show("Tage übrig 2027")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("30").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("verfallen am", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the next trip and the next holiday are named`() {
        fixture.fill()
        show("Nächster Urlaub")

        compose.onNodeWithText("15.03.2027 bis 19.03.2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("kostet 5 Urlaubstage").assertIsDisplayed()
        compose.onNodeWithText("Karfreitag").assertIsDisplayed()
        compose.onNodeWithText("26.03.2027, in 16 Tagen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the next holiday is a button that shows its day in the calendar`() {
        fixture.fill()
        show("Nächster Feiertag")

        val button = compose.onNode(hasClickAction() and hasText("Karfreitag"))
        button.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertEquals(
            "Feiertag im Kalender zeigen",
            button.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        button.performClick()

        assertEquals(listOf(LocalDate.parse("2027-03-26")), shown)
    }

    @Test
    @Config(qualifiers = "de")
    fun `without future marks it says no vacation is planned`() {
        runBlocking {
            fixture.repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            fixture.repository.saveEntitlement(YearEntitlement(2027, VacationDays.of(30)))
        }
        show("Noch kein Urlaub geplant")

        // The days left and the entitlement are both 30.
        compose.onAllNodesWithText("30").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "de")
    fun `more marked days than the entitlement says so`() {
        runBlocking {
            fixture.repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
            fixture.repository.saveEntitlement(
                YearEntitlement(2027, VacationDays.ONE),
            )
            fixture.repository.markAll(
                listOf("2027-03-15", "2027-03-16").map(fixture::date),
                DayMark.FULL,
            )
        }
        show("Mehr Urlaub eingetragen")

        compose.onNodeWithText("-1").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English overview has the English texts and date format`() {
        fixture.fill()
        show("Days left 2027")

        compose.onNodeWithText("33").assertIsDisplayed()
        compose
            .onNodeWithText("3 carried-over days expire on Mar 31, 2027", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Mar 15, 2027 to Mar 19, 2027").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("costs 5 vacation days").assertIsDisplayed()
        compose.onNodeWithText("Good Friday").assertIsDisplayed()
        compose.onNodeWithText("Mar 26, 2027, in 16 days").assertIsDisplayed()
    }

    private fun grantedDaysOn() =
        runBlocking {
            fixture.repository.saveSettings(
                UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN, grantedDays = true),
            )
        }

    @Test
    @Config(qualifiers = "de")
    fun `the overview names how many planned days are granted, once there are some`() {
        fixture.fill()
        grantedDaysOn()
        runBlocking {
            fixture.repository.setGranted(
                listOf("2027-03-15", "2027-03-16").map(fixture::date),
                true,
            )
        }
        show("Tage übrig 2027")

        compose.onNodeWithText("Davon genehmigt: 2 Tage").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the granted line reads right in English for one day`() {
        fixture.fill()
        grantedDaysOn()
        runBlocking { fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true) }
        show("Days left 2027")

        compose.onNodeWithText("Of those, granted: 1 day").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de")
    fun `no granted line while nothing is granted or the setting is off`() {
        fixture.fill()
        grantedDaysOn()
        show("Tage übrig 2027")
        compose.onNodeWithText("Davon genehmigt", substring = true).assertDoesNotExist()

        runBlocking {
            fixture.repository.setGranted(listOf(fixture.date("2027-03-15")), true)
            fixture.repository.saveSettings(UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Davon genehmigt", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de")
    fun `the intro counts up without composing the whole overview in every frame`() {
        fixture.fill()
        val composed = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalCompositionProbe provides { composed += it }) {
                OverviewScreen(
                    fixture.viewModel(),
                    PaddingValues(),
                    onOpenSettings = {},
                    onOpenBackup = {},
                    onOpenMonth = {},
                    onShowDate = {},
                )
            }
        }
        // The data comes from Room on its own threads; step frames until the status is there.
        val waitUntil = System.currentTimeMillis() + 5_000
        while ("Status" !in composed && System.currentTimeMillis() < waitUntil) {
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(5)
        }
        assertTrue("Status" in composed)

        // The whole intro (1 s) and a little more, frame by frame.
        repeat(75) { compose.mainClock.advanceTimeByFrame() }

        // Before 0.10.1 the status composed in 63 of 75 frames; now only when its data changes.
        assertTrue(composed.count { it == "Status" } <= 5)
        // The numbers still count up: they compose on their own, many times.
        assertTrue(composed.count { it == "CountUpText" } > 20)
        compose.onNodeWithText("33").assertIsDisplayed()
    }
}
