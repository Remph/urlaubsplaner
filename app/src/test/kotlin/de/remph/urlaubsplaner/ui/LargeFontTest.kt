package de.remph.urlaubsplaner.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.planner.PlannerFixture
import de.remph.urlaubsplaner.settings.CalendarExporter
import de.remph.urlaubsplaner.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The M5 UI review found words broken in the middle at a 150 % system font on a 360 dp phone. */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class LargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixture = PlannerFixture()

    @After
    fun close() = fixture.close()

    private fun lineCount(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        compose
            .onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach {
                it.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(results)
            }
        return results.single().lineCount
    }

    @Test
    @Config(qualifiers = "de-w360dp-h800dp-xxhdpi")
    fun `the bottom bar labels stay on one line at a 150 percent font`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings =
            SettingsViewModel(
                fixture.repository,
                BackupFileStore(context.contentResolver),
                CalendarExporter(fixture.repository, CalendarFileStore(context), fixture.clock),
                fixture.clock,
                fixture.backupStatus,
            )
        val appearance = AppearanceStore(context.getSharedPreferences("large-font-test", 0))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                AppRoot(settings, fixture.viewModel(), appearance, null)
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Einstellungen").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(1, lineCount("Einstellungen"))
    }
}
