package de.remph.urlaubsplaner.data

import androidx.room.withTransaction
import de.remph.urlaubsplaner.core.backup.BackupData
import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.MarkedDays
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The user's data as `:core` types. Screens and the widget talk to this and never see the
 * entities. The flows send the current value first and again after every change.
 */
class UserDataRepository(
    private val database: UrlaubsplanerDatabase,
) {
    private val settingsDao = database.settingsDao()
    private val entitlementDao = database.entitlementDao()
    private val markedDayDao = database.markedDayDao()

    fun observeSettings(): Flow<UserSettings> =
        settingsDao.observeSettings().map {
            it.toUserSettings()
        }

    suspend fun saveSettings(settings: UserSettings) = settingsDao.upsert(settings.toEntity())

    /** Null when the user has not entered the numbers for [year] yet. */
    fun observeEntitlement(year: Int): Flow<YearEntitlement?> =
        entitlementDao.observe(year).map { it?.toYearEntitlement() }

    suspend fun saveEntitlement(entitlement: YearEntitlement) =
        entitlementDao.upsert(entitlement.toEntity())

    suspend fun deleteEntitlement(year: Int) = entitlementDao.delete(year)

    /** The marked days from [from] to [to], both included, with their granted flags. */
    fun observeMarkedDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<MarkedDays> =
        markedDayDao.observeBetween(from.toString(), to.toString()).map {
            it.toMarkedDays()
        }

    /** Whether any day, in any year, is marked. */
    fun observeAnyMarkedDay(): Flow<Boolean> = markedDayDao.observeAny()

    /** The same without the granted flags, for the balance and anything that does not show them. */
    fun observeMarks(
        from: LocalDate,
        to: LocalDate,
    ): Flow<Map<LocalDate, DayMark>> = observeMarkedDays(from, to).map { it.marks }

    /**
     * Marking a day that is already marked replaces the mark (full to half and back) and its
     * granted flag, so a caller that only changes the mark passes the flag the day has.
     */
    suspend fun mark(
        date: LocalDate,
        mark: DayMark,
        granted: Boolean = false,
    ) = markedDayDao.upsert(markedDayEntity(date, mark, granted))

    suspend fun unmark(date: LocalDate) = markedDayDao.delete(date.toString())

    /** Marks all [dates] in one go (a range). Days that are already marked are replaced. */
    suspend fun markAll(
        dates: Collection<LocalDate>,
        mark: DayMark,
        granted: Boolean = false,
    ) = markedDayDao.upsertAll(dates.map { markedDayEntity(it, mark, granted) })

    /**
     * Marks all [dates] and keeps the granted flag each of them has (new marks are not granted).
     * For callers that cannot see the flag: with the granted days setting off, changing a day to a
     * half day must not quietly forget that it was granted.
     */
    suspend fun markAllKeepingGranted(
        dates: Collection<LocalDate>,
        mark: DayMark,
    ) = database.withTransaction {
        val granted = markedDayDao.grantedAmong(dates.map { it.toString() }).toSet()
        markedDayDao.upsertAll(dates.map { markedDayEntity(it, mark, it.toString() in granted) })
    }

    /** Grants or un-grants days that are already marked; a day that is not marked stays so. */
    suspend fun setGranted(
        dates: Collection<LocalDate>,
        granted: Boolean,
    ) = markedDayDao.setGranted(dates.map { it.toString() }, granted)

    /**
     * Unmarks all [dates] and returns what they were (each day's mark and granted flag), so that
     * [restore] can put exactly that back. Days among [dates] that were not marked are not in it.
     */
    suspend fun removeAll(dates: Collection<LocalDate>): MarkedDays =
        database.withTransaction {
            val keys = dates.map { it.toString() }
            val removed = markedDayDao.getDates(keys).toMarkedDays()
            markedDayDao.deleteDates(keys)
            removed
        }

    /** Marks [days] again as they were when [removeAll] returned them (the undo). */
    suspend fun restore(days: MarkedDays) =
        markedDayDao.upsertAll(
            days.marks.map { (date, mark) -> markedDayEntity(date, mark, date in days.granted) },
        )

    /** Removes every mark of [year]; other years, the entitlement and the settings stay. */
    suspend fun clearMarks(year: Int) =
        markedDayDao.deleteBetween(
            LocalDate.of(year, 1, 1).toString(),
            LocalDate.of(year, 12, 31).toString(),
        )

    /**
     * Everything the user has entered, for the backup file. A row that cannot be read (a damaged
     * value) is left out here as it is everywhere else in the app.
     */
    suspend fun readAll(): BackupData =
        database.withTransaction {
            val days = markedDayDao.getAll().toMarkedDays()
            BackupData(
                settings = settingsDao.get().toUserSettings(),
                entitlements = entitlementDao.getAll().mapNotNull { it.toYearEntitlement() },
                marks = days.marks,
                granted = days.granted,
            )
        }

    /**
     * Replaces everything with [data] in one transaction: afterwards the app holds exactly what
     * the backup holds, and if anything fails it still holds what it held before. Screens show
     * [readAll]'s summary next to the file's before they call this.
     */
    suspend fun replaceAll(data: BackupData) =
        database.withTransaction {
            settingsDao.upsert(data.settings.toEntity())
            entitlementDao.deleteAll()
            entitlementDao.upsertAll(data.entitlements.map { it.toEntity() })
            markedDayDao.deleteAll()
            markedDayDao.upsertAll(
                data.marks.map { (date, mark) ->
                    markedDayEntity(date, mark, date in data.granted)
                },
            )
        }
}
