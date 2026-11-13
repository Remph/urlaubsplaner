package de.remph.urlaubsplaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.remph.urlaubsplaner.appearance.UrlaubsplanerTheme
import de.remph.urlaubsplaner.planner.PlannerViewModel
import de.remph.urlaubsplaner.settings.SettingsViewModel
import de.remph.urlaubsplaner.ui.AppRoot
import de.remph.urlaubsplaner.ui.TabRequest

// AppCompatActivity, not ComponentActivity: AppCompatDelegate switches the app language and the
// light/dark mode with it.
class MainActivity : AppCompatActivity() {
    // The tab the widget asked for (singleTask: a tap on the widget reaches a running app here).
    private var tabRequest by mutableStateOf<TabRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TabRequest.from(intent)?.let { tabRequest = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only at a real start: a restart in the new language or after a rotation keeps its tab.
        if (savedInstanceState == null) tabRequest = TabRequest.from(intent)
        enableEdgeToEdge()
        // With three-button navigation the system lays a translucent scrim over its bar by
        // default, which showed as a second tone under our navigation bar (the maintainer, 2026-10-26).
        // Our bar reaches under the system bar, so without the scrim it is one colour.
        window.isNavigationBarContrastEnforced = false
        val app = application as UrlaubsplanerApp
        setContent {
            val appearance by app.appearanceStore.state.collectAsStateWithLifecycle()
            UrlaubsplanerTheme(appearance) {
                AppRoot(
                    settingsViewModel =
                        viewModel {
                            SettingsViewModel(
                                app.repository,
                                app.backupStore,
                                app.calendarExporter,
                                app.clock,
                                app.backupStatusStore,
                                onDataReplaced = { app.widgetRefresher.refresh() },
                            )
                        },
                    plannerViewModel =
                        viewModel {
                            PlannerViewModel(
                                app.repository,
                                app.schoolHolidays,
                                app.clock,
                                app.backupStatusStore,
                            )
                        },
                    appearanceStore = app.appearanceStore,
                    request = tabRequest,
                )
            }
        }
    }
}
