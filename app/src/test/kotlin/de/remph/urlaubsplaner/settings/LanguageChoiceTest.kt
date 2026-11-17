// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LanguageChoiceTest {
    @Test
    fun `no stored language means the system language`() {
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTags(""))
    }

    @Test
    fun `German and English are found with or without a region`() {
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.fromTags("de"))
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.fromTags("de-DE"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTags("en"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTags("en-GB,de"))
    }

    @Test
    fun `any other language counts as system, not as a guess`() {
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTags("fr"))
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTags("dev"))
    }

    @Test
    fun `the tag written is the tag read`() {
        for (choice in LanguageChoice.entries) {
            assertEquals(choice, LanguageChoice.fromTags(choice.tag))
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Below Android 13 the widget has no other way to know the language chosen in the app.
    @Test
    @Config(sdk = [32])
    fun `below Android 13 the stored choice is the one applied`() {
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.stored(context))

        LanguageChoice.apply(context, LanguageChoice.ENGLISH)
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.stored(context))

        LanguageChoice.apply(context, LanguageChoice.SYSTEM)
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.stored(context))
    }

    // From Android 13 the system keeps it; the app may be asked outside any activity.
    @Test
    @Config(sdk = [34])
    fun `from Android 13 the stored choice is read from the system`() {
        // What AppCompatDelegate does in the app, minus the activity this test does not have.
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("de")

        assertEquals(LanguageChoice.GERMAN, LanguageChoice.stored(context))
    }
}
