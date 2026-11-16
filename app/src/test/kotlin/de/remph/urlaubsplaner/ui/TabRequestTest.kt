// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
