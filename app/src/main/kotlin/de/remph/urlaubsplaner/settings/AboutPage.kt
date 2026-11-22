// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import de.remph.urlaubsplaner.R
import de.remph.urlaubsplaner.core.schoolholidays.SchoolHolidayData

/** Where the source lives; the F-Droid metadata and the README point to the same place. */
internal const val SOURCE_URL = "https://github.com/Remph/urlaubsplaner"

/** The licence texts the app carries (`assets/licenses/`), so they travel with the APK. */
internal enum class LicenceText(
    val asset: String,
    val titleRes: Int,
) {
    GPL("GPL-3.0.txt", R.string.about_text_gpl),
    APACHE("Apache-2.0.txt", R.string.about_text_apache),
    BSD("BSD-3-Clause-protobuf.txt", R.string.about_text_bsd),
}

/** The author's website, linked from the about page. */
internal const val WEBSITE_URL = "https://remph.de"

/** The version name from the manifest, e.g. "0.1.0". */
fun appVersion(context: Context): String =
    context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        .orEmpty()

/**
 * Version, what the app does with data, where the source is, and the credits and licences the
 * licences of the bundled data and libraries ask for (docs/data-sources.md, the ODbL credit).
 */
@Composable
fun AboutPage(
    outerPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<LicenceText?>(null) }
    val noBrowser = stringResource(R.string.about_no_browser)

    SettingsPageFrame(stringResource(R.string.section_about), outerPadding, onBack) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.about_version, appVersion(context)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.about_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.about_source),
                value = SOURCE_URL.removePrefix("https://"),
                onClick = { openLink(context, SOURCE_URL, noBrowser) },
            )
            SettingsRow(
                title = stringResource(R.string.about_website),
                value = WEBSITE_URL.removePrefix("https://"),
                onClick = { openLink(context, WEBSITE_URL, noBrowser) },
            )
            SettingsRow(
                title = stringResource(R.string.about_licence),
                value = stringResource(R.string.about_licence_value),
                onClick = { shown = LicenceText.GPL },
                showDivider = false,
            )
        }
        Section(stringResource(R.string.about_data_title), SchoolHolidayData.CREDIT)
        Section(
            stringResource(R.string.about_libraries_title),
            stringResource(R.string.about_libraries_body),
        )
        Text(
            stringResource(R.string.about_texts_title),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingsGroup {
            val texts = LicenceText.entries
            texts.forEachIndexed { index, text ->
                SettingsRow(
                    title = stringResource(text.titleRes),
                    onClick = { shown = text },
                    showDivider = index != texts.lastIndex,
                )
            }
        }
    }

    shown?.let { text ->
        val title = stringResource(text.titleRes)
        val missing = stringResource(R.string.about_text_missing)
        val body = remember(text) { readLicence(context, text, missing) }
        AlertDialog(
            onDismissRequest = { shown = null },
            title = { Text(title) },
            text = {
                Text(
                    body,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { shown = null }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun Section(
    title: String,
    body: String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun readLicence(
    context: Context,
    text: LicenceText,
    missing: String,
): String =
    runCatching {
        context.assets
            .open("licenses/${text.asset}")
            .bufferedReader()
            .use { it.readText() }
    }.getOrDefault(missing)

private fun openLink(
    context: Context,
    url: String,
    noBrowser: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
    }
}
