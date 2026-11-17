// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.ics.IcsExport
import de.remph.urlaubsplaner.core.ics.IcsLanguage

/** The vacation as a calendar file: opened in a calendar app, or saved. */
@Composable
fun CalendarExportPage(
    viewModel: SettingsViewModel,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // The texts in the file follow the language the screens show right now.
    val locales = LocalConfiguration.current.locales
    val language = IcsLanguage.of(List(locales.size()) { locales[it] })
    var trips by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { trips = viewModel.calendarTripCount() }

    val openTitle = stringResource(R.string.calendar_export_open_title)
    LaunchedEffect(Unit) {
        viewModel.opens.collect { uri -> context.startActivity(openIntent(uri, openTitle)) }
    }
    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(IcsExport.MIME_TYPE),
        ) { uri: Uri? -> uri?.let { viewModel.saveCalendar(it, language) } }

    SettingsPageFrame(stringResource(R.string.section_calendar_export), outerPadding, onBack) {
        Text(
            stringResource(R.string.calendar_export_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val count = trips
        if (count != null) {
            Text(
                if (count == 0) {
                    stringResource(R.string.calendar_export_none)
                } else {
                    pluralStringResource(R.plurals.calendar_export_trips, count, count)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val hasTrips = (count ?: 0) > 0
        OutlinedButton(
            onClick = { viewModel.openCalendar(language) },
            enabled = hasTrips,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.calendar_export_open)) }
        OutlinedButton(
            onClick = { saveLauncher.launch(viewModel.suggestedCalendarName()) },
            enabled = hasTrips,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.calendar_export_save)) }
    }
}

/**
 * The file shown in a calendar app, which may read only that file. It is "open", not "share":
 * Some phone makers' calendar apps take .ics files that way and are not offered in the share sheet (the maintainer, 2026-10-26).
 */
private fun openIntent(
    file: Uri,
    title: String,
): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(file, IcsExport.MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        title,
    )
