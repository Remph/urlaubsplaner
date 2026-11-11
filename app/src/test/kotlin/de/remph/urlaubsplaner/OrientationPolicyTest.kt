package de.remph.urlaubsplaner

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.remph.urlaubsplaner.widget.WidgetConfigActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class OrientationPolicyTest {
    private val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val free = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    @Test
    fun `below 600 dp of smallest width the app is locked to portrait`() {
        assertEquals(portrait, OrientationPolicy.requestedFor(320))
        assertEquals(portrait, OrientationPolicy.requestedFor(411))
        assertEquals(portrait, OrientationPolicy.requestedFor(599))
    }

    @Test
    fun `from 600 dp up the orientation is left to the user`() {
        assertEquals(free, OrientationPolicy.requestedFor(600))
        assertEquals(free, OrientationPolicy.requestedFor(840))
    }

    // Without a widget id the setup screen closes itself at once, which is enough here: the lock
    // is set before that.
    private fun start(type: Class<out Activity>): Activity =
        Robolectric.buildActivity(type, Intent()).create().get()

    // Robolectric's qualifier "w411dp-h891dp" gives sw411dp. The lock is set before onCreate (a
    // lifecycle callback), so the activity never builds a landscape screen first.

    @Test
    @Config(qualifiers = "de-w411dp-h891dp")
    fun `the main activity on a phone is locked to portrait`() {
        assertEquals(portrait, start(MainActivity::class.java).requestedOrientation)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp")
    fun `the widget setup screen on a phone is locked to portrait too`() {
        assertEquals(portrait, start(WidgetConfigActivity::class.java).requestedOrientation)
    }

    @Test
    @Config(qualifiers = "de-w411dp-h891dp-land")
    fun `a phone that is started sideways is locked to portrait`() {
        // Wide 891 high 411 when held sideways: the smallest width is still 411.
        assertEquals(portrait, start(MainActivity::class.java).requestedOrientation)
    }

    @Test
    @Config(qualifiers = "de-w800dp-h1280dp")
    fun `the main activity on a tablet keeps rotating`() {
        assertEquals(free, start(MainActivity::class.java).requestedOrientation)
    }

    @Test
    @Config(qualifiers = "de-w1280dp-h800dp")
    fun `a tablet held sideways keeps rotating`() {
        assertEquals(free, start(MainActivity::class.java).requestedOrientation)
        assertEquals(free, start(WidgetConfigActivity::class.java).requestedOrientation)
    }
}
