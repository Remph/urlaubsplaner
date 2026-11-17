// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.settings.UserSettings
import de.remph.urlaubsplaner.planner.fullText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The launcher shortcuts on a real system: that Android accepts the three static shortcuts of the
 * manifest (it drops one silently when its package is not the app's own, as in a debug build with
 * the release ID), and that each shortcut's own intent opens the right screen when the app is
 * started by it and when it is already running.
 *
 * Not covered: how a launcher draws them (order, icon shape, label length). That is a look at a
 * real home screen.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutsOnDeviceTest {
    private val fresh = FreshApp()
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fresh).around(compose)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = targetApp

    private fun string(id: Int) = context.getString(id)

    private fun tab(id: Int) =
        hasText(string(id)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun shortcutIntent(id: String): Intent =
        context
            .getSystemService(ShortcutManager::class.java)
            .manifestShortcuts
            .first { it.id == id }
            .intent!!

    private fun withLand() =
        runBlocking {
            app.repository.saveSettings(
                UserSettings(land = Bundesland.NORDRHEIN_WESTFALEN),
            )
        }

    private fun waitUntil(condition: () -> Boolean) =
        compose.waitUntil(TIMEOUT_MILLIS) { condition() }

    private fun yearGrid() =
        hasContentDescription(
            string(R.string.year_grid_description).substringBefore("%1\$d").trim(),
            substring = true,
        )

    private fun hasNode(matcher: SemanticsMatcher) =
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Android accepts the three shortcuts of the manifest`() {
        val ids =
            context
                .getSystemService(ShortcutManager::class.java)
                .manifestShortcuts
                .map { it.id }

        // A set, not a list: the launcher decides the order (Android 17 returns it reversed). The
        // size check keeps a duplicate from hiding behind the set.
        assertEquals(setOf("calendar", "year", "mark_today"), ids.toSet())
        assertEquals(3, ids.size)
    }

    @Test
    fun `the Kalender shortcut starts the app on the month view`() {
        withLand()

        ActivityScenario.launch<MainActivity>(shortcutIntent("calendar")).use {
            waitUntil {
                hasNode(
                    tab(R.string.nav_calendar) and
                        SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
                )
            }
            waitUntil { !hasNode(hasText(string(R.string.mark_full))) }
            waitUntil { !hasNode(yearGrid()) }
        }
    }

    @Test
    fun `the Jahresansicht shortcut starts the app on the year view`() {
        withLand()

        ActivityScenario.launch<MainActivity>(shortcutIntent("year")).use {
            waitUntil { hasNode(yearGrid()) }
        }
    }

    @Test
    fun `the Heute eintragen shortcut starts the app with the sheet of today`() {
        withLand()
        val locale = context.resources.configuration.locales[0]
        val today = LocalDate.now(app.clock)

        ActivityScenario.launch<MainActivity>(shortcutIntent("mark_today")).use {
            waitUntil { hasNode(hasText(today.fullText(locale))) }
        }
    }

    @Test
    fun `a shortcut also reaches an app that is already running`() {
        withLand()
        val locale = context.resources.configuration.locales[0]
        val today = LocalDate.now(app.clock)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil { hasNode(tab(R.string.nav_overview)) }

            // MainActivity is singleTask: the new start is handed to the running one.
            context.startActivity(
                shortcutIntent("mark_today").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )

            waitUntil { hasNode(hasText(today.fullText(locale))) }
            compose.onNodeWithText(today.fullText(locale)).assertExists()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
