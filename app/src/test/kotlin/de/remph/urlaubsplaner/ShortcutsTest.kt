package de.remph.urlaubsplaner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.planner.CalendarShortcut
import de.remph.urlaubsplaner.ui.Tab
import de.remph.urlaubsplaner.ui.TabRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** One entry of res/xml/shortcuts.xml, with every resource reference already resolved. */
private class StaticShortcut(
    val id: String,
    val shortLabel: String,
    val longLabel: String,
    val iconResource: Int,
    val action: String,
    val targetPackage: String,
    val targetClass: String,
)

/** The launcher shortcuts on the app icon: what the file says, and that the app understands it. */
@RunWith(AndroidJUnit4::class)
class ShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun XmlResourceParser.text(name: String): String {
        val resource = getAttributeResourceValue(androidNs, name, 0)
        return if (resource !=
            0
        ) {
            context.getString(resource)
        } else {
            getAttributeValue(androidNs, name)
        }
    }

    private fun shortcuts(): List<StaticShortcut> {
        val found = mutableListOf<StaticShortcut>()
        val parser = context.resources.getXml(R.xml.shortcuts)
        var open: Map<String, String>? = null
        var icon = 0
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType != XmlResourceParser.START_TAG) continue
            when (parser.name) {
                "shortcut" -> {
                    icon = parser.getAttributeResourceValue(androidNs, "icon", 0)
                    open =
                        mapOf(
                            "id" to parser.text("shortcutId"),
                            "short" to parser.text("shortcutShortLabel"),
                            "long" to parser.text("shortcutLongLabel"),
                        )
                }

                "intent" -> {
                    val shortcut = checkNotNull(open) { "an intent outside a shortcut" }
                    found +=
                        StaticShortcut(
                            shortcut.getValue("id"),
                            shortcut.getValue("short"),
                            shortcut.getValue("long"),
                            icon,
                            parser.text("action"),
                            // Raw, as the launcher service reads it: a "@string/..." reference would come
                            // out as "@<number>" here and make the system start a package of that name.
                            parser.getAttributeValue(androidNs, "targetPackage"),
                            parser.text("targetClass"),
                        )
                }
            }
        }
        return found
    }

    @Test
    @Config(qualifiers = "de")
    fun `the app has the three shortcuts of the plan in the order named`() {
        assertEquals(listOf("calendar", "year", "mark_today"), shortcuts().map { it.id })
        assertEquals(
            listOf("Kalender", "Jahresansicht", "Heute eintragen"),
            shortcuts().map { it.shortLabel },
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun `the English labels are there and differ from the German ones`() {
        val english = shortcuts().map { it.shortLabel }

        assertEquals(listOf("Calendar", "Year view", "Add today"), english)
    }

    @Test
    @Config(qualifiers = "de")
    fun `every label is filled and the long one fits what launchers show`() {
        for (shortcut in shortcuts()) {
            assertTrue(shortcut.shortLabel.isNotBlank())
            // Android documents 25 characters for the long label.
            assertTrue("${shortcut.id}: ${shortcut.longLabel}", shortcut.longLabel.length in 1..25)
            assertTrue(shortcut.shortLabel.length <= shortcut.longLabel.length)
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `every English label is filled and the long one fits what launchers show`() {
        for (shortcut in shortcuts()) {
            assertTrue(shortcut.shortLabel.isNotBlank())
            assertTrue("${shortcut.id}: ${shortcut.longLabel}", shortcut.longLabel.length in 1..25)
            assertTrue(shortcut.shortLabel.length <= shortcut.longLabel.length)
        }
    }

    @Test
    fun `every shortcut has its own icon and its own action that the app reads`() {
        val all = shortcuts()
        assertEquals(all.size, all.map { it.iconResource }.toSet().size)
        assertTrue(all.all { it.iconResource != 0 })
        assertEquals(all.size, all.map { it.action }.toSet().size)

        val read = all.map { TabRequest.from(Intent(it.action)) }

        assertTrue(read.all { it?.tab == Tab.CALENDAR })
        assertEquals(CalendarShortcut.entries.toSet(), read.mapNotNull { it?.shortcut }.toSet())
    }

    @Test
    fun `a shortcut targets this app and the main activity`() {
        // The debug build has its own application ID: a shortcut with another package than the
        // app's own is dropped by the launcher service, silently.
        for (shortcut in shortcuts()) {
            assertEquals(context.packageName, shortcut.targetPackage)
            assertEquals(MainActivity::class.java.name, shortcut.targetClass)
        }
    }

    @Test
    fun `the main activity points at the shortcut file`() {
        val activity =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                PackageManager.GET_META_DATA,
            )

        assertEquals(R.xml.shortcuts, activity.metaData.getInt("android.app.shortcuts"))
        assertNotEquals(0, activity.metaData.getInt("android.app.shortcuts"))
    }
}
