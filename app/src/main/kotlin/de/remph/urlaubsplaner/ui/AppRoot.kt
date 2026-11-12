package de.remph.urlaubsplaner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.planner.CalendarMode
import de.remph.urlaubsplaner.planner.CalendarScreen
import de.remph.urlaubsplaner.planner.OverviewScreen
import de.remph.urlaubsplaner.planner.PlannerViewModel
import de.remph.urlaubsplaner.settings.SettingsHost
import de.remph.urlaubsplaner.settings.SettingsPage
import de.remph.urlaubsplaner.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The three tabs along the bottom (the maintainer, 2026-10-25). */
enum class Tab(
    val label: Int,
    val icon: ImageVector,
) {
    OVERVIEW(R.string.nav_overview, Icons.Filled.Home),
    CALENDAR(R.string.nav_calendar, Icons.Filled.DateRange),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

/**
 * The frame of the app: the bottom navigation and the one place messages (snackbars) appear.
 * It starts on the overview; until a Land is chosen that points to the settings.
 */
@Composable
fun AppRoot(
    settingsViewModel: SettingsViewModel,
    plannerViewModel: PlannerViewModel,
    appearanceStore: AppearanceStore,
    request: TabRequest? = null,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }
    // Messages run in this scope, not in the screen that asked for them: a tab that is left takes
    // its effects with it, so a snackbar from the settings vanished at once (and its notice came
    // back on the next visit), and the undo after Entfernen would go with the closing sheet.
    val messages = rememberCoroutineScope()
    val showMessage: (String) -> Unit =
        remember(messages, snackbar) { { text -> messages.launch { snackbar.showSnackbar(text) } } }
    RemovalUndo(plannerViewModel, snackbar, messages)
    // Leaving a tab drops its composables; this keeps what each tab remembered (the open settings
    // page, scroll positions) for the way back.
    val tabState = rememberSaveableStateHolder()
    // A button or the widget that says "settings" means the list, not the page left open earlier;
    // only the tab bar brings back where the user was.
    // The old settings screen is still composed while it fades out and saves its state when it
    // goes, so removing the state is not enough; a new key makes the next settings screen start fresh.
    var settingsGeneration by rememberSaveable { mutableIntStateOf(0) }
    // The page a fresh settings screen opens on: the list, or the backup page from the overview's hint.
    var settingsStart by rememberSaveable { mutableStateOf(SettingsPage.LIST) }
    // Counts the taps on the Einstellungen tab while it is already open; the settings go back to
    // their list for each one.
    var settingsHomeTaps by rememberSaveable { mutableIntStateOf(0) }
    val show = { target: Tab ->
        if (target == Tab.SETTINGS && tab != Tab.SETTINGS) {
            settingsStart = SettingsPage.LIST
            tabState.removeState(stateKey(Tab.SETTINGS, settingsGeneration))
            settingsGeneration++
        }
        tab = target
    }
    // A tap on the widget asks for a tab; it wins over where the user was.
    LaunchedEffect(request) { request?.let { show(it.tab) } }
    // A phone keeps the app in memory for days; the status must follow the date.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { plannerViewModel.refreshToday() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                val calendarMode by plannerViewModel.calendarMode.collectAsStateWithLifecycle()
                val jumpLabel =
                    stringResource(
                        if (calendarMode == CalendarMode.YEAR) {
                            R.string.nav_calendar_this_year
                        } else {
                            R.string.nav_calendar_this_month
                        },
                    )
                for (entry in Tab.entries) {
                    val label = stringResource(entry.label)
                    // A second tap on the open Kalender tab goes back to today, on the open
                    // Einstellungen tab to the settings list; Übersicht does nothing then.
                    val backToToday = tab == Tab.CALENDAR && entry == Tab.CALENDAR
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            if (backToToday) plannerViewModel.showToday()
                            if (tab == Tab.SETTINGS && entry == Tab.SETTINGS) settingsHomeTaps++
                            tab = entry
                        },
                        // TalkBack says what the tap will do instead of a plain "activate".
                        modifier =
                            if (backToToday) {
                                Modifier.semantics {
                                    onClick(label = jumpLabel) {
                                        plannerViewModel.showToday()
                                        true
                                    }
                                }
                            } else {
                                Modifier
                            },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        // One line, shrunk when it does not fit: at a large system font
                        // "Einstellungen" broke in the middle of the word.
                        label = { Text(label, maxLines = 1, autoSize = LabelAutoSize) },
                    )
                }
            }
        },
    ) { padding ->
        // The tabs slide a short way in the direction of the tab bar, with a fade.
        val axis = rememberSharedAxis<Tab>()
        AnimatedContent(
            tab,
            transitionSpec = {
                axis(targetState.ordinal > initialState.ordinal).using(SizeTransform(clip = false))
            },
            label = "tab",
        ) { shown ->
            tabState.SaveableStateProvider(stateKey(shown, settingsGeneration)) {
                when (shown) {
                    Tab.OVERVIEW -> {
                        OverviewScreen(
                            plannerViewModel,
                            padding,
                            onOpenSettings = { show(Tab.SETTINGS) },
                            onOpenBackup = {
                                show(Tab.SETTINGS)
                                settingsStart = SettingsPage.BACKUP
                            },
                            onOpenMonth = {
                                plannerViewModel.showMode(CalendarMode.MONTH)
                                plannerViewModel.showMonth(it)
                                tab = Tab.CALENDAR
                            },
                            onShowDate = {
                                plannerViewModel.showDate(it)
                                tab = Tab.CALENDAR
                            },
                        )
                    }

                    Tab.CALENDAR -> {
                        CalendarScreen(
                            plannerViewModel,
                            padding,
                            onOpenSettings = { show(Tab.SETTINGS) },
                        )
                    }

                    Tab.SETTINGS -> {
                        SettingsHost(
                            settingsViewModel,
                            appearanceStore,
                            showMessage,
                            padding,
                            settingsStart,
                            settingsHomeTaps,
                        )
                    }
                }
            }
        }
    }
}

/**
 * After Entfernen in the day sheet: how many days went, and Rückgängig, which puts back each day's
 * mark and granted flag. Marking says nothing (the tile and a haptic tick are enough).
 */
@Composable
private fun RemovalUndo(
    viewModel: PlannerViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
) {
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val undo = stringResource(R.string.undo)
    LaunchedEffect(removed) {
        val days = removed ?: return@LaunchedEffect
        val count = days.marks.size
        val text = resources.getQuantityString(R.plurals.removed_days, count, count)
        scope.launch {
            // A newer removal replaces the older offer instead of waiting behind it.
            snackbar.currentSnackbarData?.dismiss()
            val result =
                snackbar.showSnackbar(text, actionLabel = undo, duration = SnackbarDuration.Long)
            // Only once it is over: a rotation cancels this scope first, and the new screen then
            // offers the undo again instead of losing it.
            viewModel.removalDone(days)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemoval(days)
        }
    }
}

private val LabelAutoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp)

private fun stateKey(
    tab: Tab,
    settingsGeneration: Int,
): String {
    val generation = if (tab == Tab.SETTINGS) settingsGeneration.toString() else ""
    return tab.name + generation
}
