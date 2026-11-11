package de.remph.urlaubsplaner

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle

/**
 * Phones stay upright; tablets rotate (the maintainer, 2026-11-10). The manifest cannot tell the
 * two apart (its `screenOrientation` is one value for every device), so the app does it at the
 * start of each of its activities.
 */
object OrientationPolicy {
    /** The same 600 dp that the Android resource qualifier `sw600dp` calls a tablet. */
    const val TABLET_SMALLEST_WIDTH_DP = 600

    fun requestedFor(smallestScreenWidthDp: Int): Int =
        if (smallestScreenWidthDp < TABLET_SMALLEST_WIDTH_DP) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    /**
     * Applies the policy to every activity of the app, the main one and the widget setup screen,
     * before its own `onCreate`, so a phone held sideways does not first build a landscape screen
     * (a lifecycle callback, not code in each activity, so a new activity cannot forget it).
     */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    activity.requestedOrientation =
                        requestedFor(activity.resources.configuration.smallestScreenWidthDp)
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
