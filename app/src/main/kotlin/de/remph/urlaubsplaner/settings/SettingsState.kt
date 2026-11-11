package de.remph.urlaubsplaner.settings

import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.backup.BackupSummary
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.settings.UserSettings
import java.time.LocalDate
import java.time.MonthDay

/**
 * What the settings page shows. The amounts of the year form come as values, not text, because
 * the text depends on the language (12,5 or 12.5) and the screen formats it; a field the user
 * has typed in carries its text instead.
 */
data class SettingsUiState(
    val settings: UserSettings,
    val year: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val form: YearForm,
    /** How many days are marked in [year], for the clear action. */
    val markedDays: Int,
    val pendingImport: ImportPlan?,
    val notice: Notice?,
    /** The day a backup was last saved on this phone, null if never. */
    val lastBackup: LocalDate? = null,
)

/**
 * The form for one year. [base] is what is stored for the year, or the suggestion from the year
 * before ([suggestedFrom]), or null when there is neither. A typed field is a text that has
 * not been saved; an error flag says the typed text cannot be used.
 */
data class YearForm(
    val base: Base?,
    val suggestedFrom: Int?,
    val typedDays: String?,
    val typedCarryOver: String?,
    val typedExpiry: String?,
    val typedExpiryYears: Int?,
    val daysError: Boolean,
    val carryOverError: Boolean,
    val expiryError: Boolean,
    val canSave: Boolean,
) {
    data class Base(
        val days: VacationDays,
        val carryOver: VacationDays,
        val expiry: MonthDay,
        /** How many years after the entitlement's year the Resturlaub expires, 0 or 1. */
        val expiryYears: Int = 0,
    )

    val isSuggestion: Boolean get() = suggestedFrom != null
}

/** What an import would do, for the confirm dialog. Nothing has been changed yet. */
data class ImportPlan(
    val current: BackupSummary,
    val file: BackupSummary,
    internal val data: BackupData,
)

/** What happened after an action. The screen picks the text; a detail is shown as is. */
sealed interface Notice {
    data object EntitlementSaved : Notice

    data object Exported : Notice

    data object CalendarSaved : Notice

    data object Imported : Notice

    data class YearCleared(
        val year: Int,
    ) : Notice

    data object ExportFailed : Notice

    /** The file could not be opened or read at all. */
    data object ImportUnreadable : Notice

    data object ImportNotABackup : Notice

    data class ImportNewerVersion(
        val found: Int,
        val supported: Int,
    ) : Notice

    data class ImportDamaged(
        val detail: String,
    ) : Notice
}
