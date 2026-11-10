package de.remph.urlaubsplaner

import android.app.Application
import android.content.res.Configuration
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.data.BackupFileStore
import de.remph.urlaubsplaner.data.BackupStatusStore
import de.remph.urlaubsplaner.data.CalendarFileStore
import de.remph.urlaubsplaner.data.SchoolHolidayAsset
import de.remph.urlaubsplaner.data.UrlaubsplanerDatabase
import de.remph.urlaubsplaner.data.UserDataRepository
import de.remph.urlaubsplaner.settings.CalendarExporter
import de.remph.urlaubsplaner.widget.WidgetRefresher
import java.time.Clock

/**
 * Holds the one database and what is built on it. Screens and the ViewModels get their parts from
 * here; there is no dependency injection library because there are only a few parts.
 */
class UrlaubsplanerApp : Application() {
    /** The only place that asks for "now", so every screen shares one clock. */
    val clock: Clock = Clock.systemDefaultZone()

    val database by lazy { UrlaubsplanerDatabase.create(this) }

    val repository by lazy { UserDataRepository(database) }

    val backupStore by lazy { BackupFileStore(contentResolver) }

    val calendarExporter by lazy { CalendarExporter(repository, CalendarFileStore(this), clock) }

    /** The bundled school holiday file, read once the first time a screen needs it. */
    val schoolHolidays = lazy { SchoolHolidayAsset.load(this) }

    val appearanceStore by lazy {
        AppearanceStore(getSharedPreferences(AppearanceStore.FILE_NAME, MODE_PRIVATE))
    }

    val backupStatusStore by lazy {
        BackupStatusStore(getSharedPreferences(BackupStatusStore.FILE_NAME, MODE_PRIVATE))
    }

    val widgetRefresher by lazy { WidgetRefresher(this, database) }

    override fun onCreate() {
        super.onCreate()
        // AppCompat does not remember the light/dark choice, so it is handed over at every start.
        appearanceStore.applyNightMode()
        // The widget is drawn from here too: it lives in this process while the app does.
        if (widgetRefresher.hasWidgets()) widgetRefresher.start()
    }

    // The app language and the phone's light or dark mode arrive as a configuration change; the
    // widget has to be drawn again to follow them.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        widgetRefresher.refresh()
    }
}
