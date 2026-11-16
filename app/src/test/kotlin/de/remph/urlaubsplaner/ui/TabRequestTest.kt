package de.remph.urlaubsplaner.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.planner.CalendarShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabRequestTest {
    @Test
    fun `every tab has its own action, so the widget's two taps stay two different intents`() {
        val actions = Tab.entries.map { TabRequest.action(it) }

        assertEquals(actions.size, actions.toSet().size)
        assertNotEquals(TabRequest.action(Tab.OVERVIEW), TabRequest.action(Tab.SETTINGS))
    }

    @Test
    fun `the tab comes back out of the intent`() {
        for (tab in Tab.entries) {
            val intent = Intent(TabRequest.action(tab))

            assertEquals(tab, TabRequest.from(intent)?.tab)
        }
    }

    @Test
    fun `the launcher's own start or a stranger's intent asks for nothing`() {
        assertNull(TabRequest.from(null))
        assertNull(TabRequest.from(Intent(Intent.ACTION_MAIN)))
        assertNull(TabRequest.from(Intent("de.remph.urlaubsplaner.action.OPEN_NOWHERE")))
    }

    @Test
    fun `every shortcut opens the calendar tab and says which view`() {
        for (shortcut in CalendarShortcut.entries) {
            val request = TabRequest.from(Intent(TabRequest.action(shortcut)))

            assertEquals(Tab.CALENDAR, request?.tab)
            assertEquals(shortcut, request?.shortcut)
        }
    }

    @Test
    fun `the widget's taps ask for no shortcut`() {
        for (tab in Tab.entries) {
            assertNull(TabRequest.from(Intent(TabRequest.action(tab)))?.shortcut)
        }
    }

    @Test
    fun `the shortcuts and the tabs never share an action`() {
        val actions =
            Tab.entries.map { TabRequest.action(it) } +
                CalendarShortcut.entries.map { TabRequest.action(it) }

        assertEquals(actions.size, actions.toSet().size)
    }

    @Test
    fun `the actions in the shortcut file are the ones the app reads`() {
        // res/xml/shortcuts.xml spells the actions out; a rename here must not leave it behind.
        assertEquals(
            "de.remph.urlaubsplaner.action.SHORTCUT_MONTH",
            TabRequest.action(CalendarShortcut.MONTH),
        )
        assertEquals(
            "de.remph.urlaubsplaner.action.SHORTCUT_YEAR",
            TabRequest.action(CalendarShortcut.YEAR),
        )
        assertEquals(
            "de.remph.urlaubsplaner.action.SHORTCUT_MARK_TODAY",
            TabRequest.action(CalendarShortcut.MARK_TODAY),
        )
    }
}
