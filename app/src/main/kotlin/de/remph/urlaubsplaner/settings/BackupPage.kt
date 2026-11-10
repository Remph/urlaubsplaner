package de.remph.urlaubsplaner.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.backup.BackupFile
import de.remph.urlaubsplaner.core.backup.BackupSummary
import de.remph.urlaubsplaner.planner.currentLocale
import de.remph.urlaubsplaner.planner.mediumText

/** Save everything to a file the user picks, or replace everything from one. */
@Composable
fun BackupPage(
    ui: SettingsUiState,
    viewModel: SettingsViewModel,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BackupFile.MIME_TYPE),
        ) { uri: Uri? -> uri?.let(viewModel::export) }
    // Any type: a file saved by hand or moved through a cloud drive does not always keep its type.
    // A file that is not a backup is refused with a message before anything is changed.
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let(viewModel::import)
        }
    SettingsPageFrame(stringResource(R.string.section_backup), outerPadding, onBack) {
        Text(
            stringResource(R.string.backup_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Nothing to say on a fresh install, where restoring is the first step.
        if (ui.settings.land != null || ui.lastBackup != null) {
            Text(
                ui.lastBackup?.let {
                    stringResource(R.string.backup_last, it.mediumText(currentLocale()))
                } ?: stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        // Saving is pointless before anything is entered; restoring is how a new phone starts.
        if (ui.settings.land != null) {
            OutlinedButton(
                onClick = { exportLauncher.launch(viewModel.suggestedBackupName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export)) }
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_import)) }
    }
}

@Composable
internal fun ImportDialog(
    plan: ImportPlan,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val body =
        stringResource(
            R.string.backup_import_body,
            summaryText(context, plan.current),
            summaryText(context, plan.file),
        )
    val noLand =
        if (plan.current.landChosen && !plan.file.landChosen) {
            "\n\n" + stringResource(R.string.backup_import_no_land)
        } else {
            ""
        }
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = { Text(body + noLand) },
        confirmButton = {
            TextButton(onClick = viewModel::confirmImport) {
                Text(stringResource(R.string.backup_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** "12 markierte Tage und 2 Jahre mit Urlaubsanspruch", or "keine Einträge" for nothing at all. */
private fun summaryText(
    context: Context,
    summary: BackupSummary,
): String {
    val resources = context.resources
    val marked = summary.markedDays
    val days = resources.getQuantityString(R.plurals.backup_marked_days, marked, marked)
    val years = resources.getQuantityString(R.plurals.backup_years, summary.years, summary.years)
    return when {
        marked == 0 && summary.years == 0 -> context.getString(R.string.backup_summary_empty)
        summary.years == 0 -> days
        marked == 0 -> years
        else -> context.getString(R.string.backup_summary_join, days, years)
    }
}
