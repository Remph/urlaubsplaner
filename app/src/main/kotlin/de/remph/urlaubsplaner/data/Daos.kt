package de.remph.urlaubsplaner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SETTINGS_ID}")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SETTINGS_ID}")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface EntitlementDao {
    @Query("SELECT * FROM year_entitlement WHERE year = :year")
    fun observe(year: Int): Flow<YearEntitlementEntity?>

    @Upsert
    suspend fun upsert(entitlement: YearEntitlementEntity)

    @Query("DELETE FROM year_entitlement WHERE year = :year")
    suspend fun delete(year: Int)

    @Query("SELECT * FROM year_entitlement ORDER BY year")
    suspend fun getAll(): List<YearEntitlementEntity>

    @Upsert
    suspend fun upsertAll(entitlements: List<YearEntitlementEntity>)

    @Query("DELETE FROM year_entitlement")
    suspend fun deleteAll()
}

@Dao
interface MarkedDayDao {
    /** Whether any day, in any year, is marked. */
    @Query("SELECT EXISTS(SELECT 1 FROM marked_day)")
    fun observeAny(): Flow<Boolean>

    /** [from] and [to] are ISO dates, which sort like dates; both ends are included. */
    @Query("SELECT * FROM marked_day WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeBetween(
        from: String,
        to: String,
    ): Flow<List<MarkedDayEntity>>

    @Upsert
    suspend fun upsert(day: MarkedDayEntity)

    @Query("DELETE FROM marked_day WHERE date = :date")
    suspend fun delete(date: String)

    /** The days among [dates] that are marked and granted. */
    @Query("SELECT date FROM marked_day WHERE granted = 1 AND date IN (:dates)")
    suspend fun grantedAmong(dates: List<String>): List<String>

    /** Sets the granted flag of the marked days among [dates]; dates that are not marked are ignored. */
    @Query("UPDATE marked_day SET granted = :granted WHERE date IN (:dates)")
    suspend fun setGranted(
        dates: List<String>,
        granted: Boolean,
    )

    @Query("DELETE FROM marked_day WHERE date IN (:dates)")
    suspend fun deleteDates(dates: List<String>)

    /** The marked days among [dates], with their marks and granted flags. */
    @Query("SELECT * FROM marked_day WHERE date IN (:dates) ORDER BY date")
    suspend fun getDates(dates: List<String>): List<MarkedDayEntity>

    @Query("SELECT * FROM marked_day ORDER BY date")
    suspend fun getAll(): List<MarkedDayEntity>

    @Upsert
    suspend fun upsertAll(days: List<MarkedDayEntity>)

    @Query("DELETE FROM marked_day")
    suspend fun deleteAll()

    /** [from] and [to] are ISO dates, both ends are included. */
    @Query("DELETE FROM marked_day WHERE date BETWEEN :from AND :to")
    suspend fun deleteBetween(
        from: String,
        to: String,
    )
}
