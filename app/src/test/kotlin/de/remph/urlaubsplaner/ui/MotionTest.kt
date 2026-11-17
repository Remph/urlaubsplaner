// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.ui

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionTest {
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun scale(value: Float) =
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)

    @Test
    fun `animations count as removed only when the animator scale is zero`() {
        scale(0f)
        assertTrue(animationsRemoved(resolver))

        scale(0.5f)
        assertFalse(animationsRemoved(resolver))

        scale(1f)
        assertFalse(animationsRemoved(resolver))
    }
}
