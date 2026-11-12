package de.remph.urlaubsplaner.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.appearance.AppearanceStore
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.ui.rememberSharedAxis

/** The settings list and the small page behind each row. */
enum class SettingsPage {
    LIST,
    VACATION,
    WORK_DAYS,
    REGIONAL,
    APPEARANCE,
    CALENDAR_EXPORT,
    BACKUP,
    ABOUT,
}

/**
 * Shows the settings list or one of its pages, and the messages and questions that can come up
 * on any of them (the import question, errors, "saved"). The page is remembered over a rotation;
 * the system back button goes from a page to the list.
 */
@Composable
fun SettingsHost(
    viewModel: SettingsViewModel,
    appearanceStore: AppearanceStore,
    showMessage: (String) -> Unit,
    outerPadding: PaddingValues,
    startPage: SettingsPage = SettingsPage.LIST,
    homeTaps: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui = state ?: return
    // [startPage] only counts the first time: a rotation brings back the page the user is on.
    var page by rememberSaveable { mutableStateOf(startPage) }

    val leave = {
        // The list shows the current year's numbers, so the year stepper goes back to it.
        viewModel.selectYear(viewModel.currentYear)
        page = SettingsPage.LIST
    }
    BackHandler(enabled = page != SettingsPage.LIST, onBack = leave)

    // A tap on the open Einstellungen tab goes back to the list, like the back button. The count
    // is remembered so a rotation does not replay an old tap.
    var handledTaps by rememberSaveable { mutableIntStateOf(homeTaps) }
    LaunchedEffect(homeTaps) {
        if (homeTaps != handledTaps) {
            handledTaps = homeTaps
            if (page != SettingsPage.LIST) leave()
        }
    }

    val notice = ui.notice
    val noticeText = notice?.let { noticeText(it) }
    LaunchedEffect(notice) {
        if (notice != null && noticeText != null && !notice.needsDialog()) {
            // Handled once it is handed over; the snackbar runs in the app's frame, so it stays
            // when the tab is left and does not show again on the way back.
            viewModel.dismissNotice()
            showMessage(noticeText)
        }
    }

    val axis = rememberSharedAxis<SettingsPage>()
    AnimatedContent(
        page,
        transitionSpec = {
            axis(
                initialState == SettingsPage.LIST,
            ).using(SizeTransform(clip = false))
        },
        label = "settingsPage",
    ) { shown ->
        when (shown) {
            SettingsPage.LIST -> {
                SettingsListScreen(ui, viewModel, appearanceStore, outerPadding) { page = it }
            }

            SettingsPage.VACATION -> {
                VacationPage(ui, viewModel, outerPadding, leave)
            }

            SettingsPage.WORK_DAYS -> {
                WorkDaysPage(ui, viewModel, outerPadding, leave)
            }

            SettingsPage.REGIONAL -> {
                RegionalPage(ui, viewModel, outerPadding, leave)
            }

            SettingsPage.APPEARANCE -> {
                AppearancePage(appearanceStore, outerPadding, leave)
            }

            SettingsPage.CALENDAR_EXPORT -> {
                CalendarExportPage(viewModel, outerPadding, leave)
            }

            SettingsPage.BACKUP -> {
                BackupPage(ui, viewModel, outerPadding, leave)
            }

            SettingsPage.ABOUT -> {
                AboutPage(outerPadding, leave)
            }
        }
    }

    ui.pendingImport?.let { ImportDialog(it, viewModel) }
    if (notice != null && noticeText != null && notice.needsDialog()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            text = { Text(noticeText) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissNotice) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
internal fun LandDialog(
    selected: Bundesland?,
    onSelect: (Bundesland) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.land_choose)) },
        text = {
            LazyColumn {
                items(Bundesland.entries) { land ->
                    ChoiceRow(stringResource(land.labelRes()), land == selected) { onSelect(land) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun LanguageDialog(
    selected: LanguageChoice,
    onSelect: (LanguageChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.section_language)) },
        text = {
            Column {
                for (option in LanguageChoice.entries) {
                    ChoiceRow(stringResource(option.labelRes()), option == selected) {
                        onSelect(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun Notice.needsDialog() =
    when (this) {
        Notice.ImportNotABackup,
        is Notice.ImportNewerVersion,
        is Notice.ImportDamaged,
        Notice.ImportUnreadable,
        -> true

        else -> false
    }

@Composable
private fun noticeText(notice: Notice): String =
    when (notice) {
        Notice.EntitlementSaved -> {
            stringResource(R.string.notice_saved)
        }

        Notice.Exported -> {
            stringResource(R.string.notice_exported)
        }

        Notice.CalendarSaved -> {
            stringResource(R.string.notice_calendar_saved)
        }

        Notice.Imported -> {
            stringResource(R.string.notice_imported)
        }

        is Notice.YearCleared -> {
            stringResource(R.string.notice_year_cleared, notice.year)
        }

        Notice.ExportFailed -> {
            stringResource(R.string.notice_export_failed)
        }

        Notice.ImportUnreadable -> {
            stringResource(R.string.notice_import_unreadable)
        }

        Notice.ImportNotABackup -> {
            stringResource(R.string.notice_not_a_backup)
        }

        is Notice.ImportNewerVersion -> {
            stringResource(R.string.notice_newer_version, notice.found, notice.supported)
        }

        is Notice.ImportDamaged -> {
            stringResource(R.string.notice_damaged, notice.detail)
        }
    }
