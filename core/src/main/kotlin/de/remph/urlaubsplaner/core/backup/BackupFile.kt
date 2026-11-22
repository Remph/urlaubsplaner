// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Remph

package de.remph.urlaubsplaner.core.backup

import de.remph.urlaubsplaner.core.balance.DayMark
import de.remph.urlaubsplaner.core.balance.VacationDays
import de.remph.urlaubsplaner.core.balance.WorkSchedule
import de.remph.urlaubsplaner.core.balance.YearEntitlement
import de.remph.urlaubsplaner.core.holidays.Bundesland
import de.remph.urlaubsplaner.core.holidays.RegionalExtra
import de.remph.urlaubsplaner.core.holidays.RegionalOptOut
import de.remph.urlaubsplaner.core.settings.UserSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeParseException

/**
 * The backup file: one JSON document, UTF-8, version [VERSION].
 *
 * ```
 * { "app": "urlaubsplaner", "formatVersion": 2, "exportedOn": "2026-10-25",
 *   "settings": { "land": "BY" or null, "regionalExtras": [...], "regionalOptOuts": [...],
 *                 "workingDays": ["MONDAY", ...], "specialDays": [{"day": "--12-24", "halfDays": 1}],
 *                 "suggestCarryOver": false, "grantedDays": false },
 *   "entitlements": [{"year": 2026, "halfDays": 60, "carryOverHalfDays": 10, "carryOverExpiry": "--03-31",
 *                    "carryOverExpiryYears": 0}],
 *   "markedDays": [{"date": "2026-07-01", "mark": "full", "granted": false}] }
 * ```
 *
 * Amounts are counted in half days, like in the database. Every value has a name owned by this
 * file (a `when` per enum, so a new enum value does not compile until it has one), which means
 * renaming something in the app cannot break old backups. A change that an older app would lose
 * data on needs a new [VERSION].
 *
 * Version 2 (0.10.0) added `granted` to every marked day: the employer has approved that day. A
 * 0.9.x app would drop the key silently, so the version went up and 0.9.x refuses these files.
 * Version 1 files are still read; their marked days are all not granted (`granted` is required in
 * version 2 and ignored in version 1, which never had it).
 *
 * `suggestCarryOver` was added before the first release and `grantedDays` (the setting that shows
 * granted days) in version 2; they are the optional keys: a file without one reads as off, so
 * files written before them still restore. The per-day `granted` is not optional in version 2,
 * and the other keys are required.
 *
 * Reading is strict: a newer version, or one value that is missing or not understood, refuses the
 * whole file with a [BackupException] before anything is changed. Dropping part of someone's
 * vacation data without saying so is worse than not restoring.
 */
object BackupFile {
    const val VERSION = 2
    const val MIME_TYPE = "application/json"

    /**
     * Ten years of marked days are well below 1 MB. The limit is tight because the JSON tree takes
     * far more memory than the text: a file of 8 MB made of tiny elements needs over 300 MB on a
     * JVM, more than an Android app gets, and an OutOfMemoryError is not caught anywhere.
     */
    const val MAX_BYTES = 2 * 1024 * 1024

    private const val APP = "urlaubsplaner"

    // U+FEFF, written as a number because formatters like to turn the escape into the invisible character.
    private val BYTE_ORDER_MARK = 0xFEFF.toChar()

    // A year has at most 366 days; anything above is a typo, and a sum of such values must not overflow.
    private const val MAX_HALF_DAYS = 2 * 366

    // The screens write 2019 to next year. The range is wide on purpose, but it must stay inside
    // what the rules and the calendar file can handle: the bridge search and the public holidays
    // need years from 2019 on and look a few years around a mark, and the ICS date has four digits
    // (a mark on 9999-12-31 ends on 10000-01-01, which cannot be written). The year arithmetic of
    // the Resturlaub expiry must not overflow either.
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2999

    private val ALLOWED_SPECIAL_DAYS = setOf(WorkSchedule.HEILIGABEND, WorkSchedule.SILVESTER)

    private val json = Json { prettyPrint = true }

    /** The name suggested to the file picker. */
    fun fileName(exportedOn: LocalDate) = "urlaubsplaner-$exportedOn.json"

    fun encode(
        data: BackupData,
        exportedOn: LocalDate,
    ): String {
        val document =
            buildJsonObject {
                put("app", APP)
                put("formatVersion", VERSION)
                put("exportedOn", exportedOn.toString())
                put("settings", encodeSettings(data.settings))
                put(
                    "entitlements",
                    JsonArray(
                        data.entitlements.sortedBy { it.year }.map { entitlement ->
                            buildJsonObject {
                                put("year", entitlement.year)
                                put("halfDays", entitlement.days.halfDays())
                                put("carryOverHalfDays", entitlement.carryOver.halfDays())
                                put("carryOverExpiry", entitlement.carryOverExpiry.toString())
                                put("carryOverExpiryYears", entitlement.carryOverExpiryYears)
                            }
                        },
                    ),
                )
                put(
                    "markedDays",
                    JsonArray(
                        data.marks.entries.sortedBy { it.key }.map { (date, mark) ->
                            buildJsonObject {
                                put("date", date.toString())
                                put("mark", mark.backupName())
                                put("granted", date in data.granted)
                            }
                        },
                    ),
                )
            }
        return json.encodeToString(JsonElement.serializer(), document)
    }

    fun writeTo(
        output: OutputStream,
        data: BackupData,
        exportedOn: LocalDate,
    ) {
        output.write(encode(data, exportedOn).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Reads at most [MAX_BYTES]; a longer stream is not a backup. */
    fun readFrom(input: InputStream): BackupData {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            if (buffer.size() + count > MAX_BYTES) {
                throw BackupException.NotABackup("the file is larger than $MAX_BYTES bytes")
            }
            buffer.write(chunk, 0, count)
        }
        // Not buffer.toString(Charset): that overload is Java 10 and missing below Android 13.
        return decode(buffer.toByteArray().decodeToString())
    }

    /** @throws BackupException when [text] is not a backup this app can restore completely. */
    fun decode(text: String): BackupData {
        // Notepad on Windows writes a byte order mark when a user saves the file by hand.
        val document =
            try {
                Json.parseToJsonElement(text.removePrefix(BYTE_ORDER_MARK.toString()))
            } catch (e: SerializationException) {
                throw BackupException.NotABackup(
                    "it is not valid JSON (${e.message?.lineSequence()?.first()})",
                )
            }
        val root =
            document as? JsonObject ?: throw BackupException.NotABackup("it is not a JSON object")
        if ((root["app"] as? JsonPrimitive)?.takeIf { it.isString }?.content != APP) {
            throw BackupException.NotABackup("the app marker is missing")
        }
        // Before anything else, so a newer file with a different structure still gets the right message.
        val version = root.int("formatVersion", "formatVersion")
        if (version > VERSION) throw BackupException.NewerVersion(version, VERSION)
        if (version < 1) damaged("formatVersion is $version")

        try {
            val (marks, granted) = decodeMarks(root.array("markedDays", "markedDays"), version)
            return BackupData(
                settings = decodeSettings(root.obj("settings", "settings")),
                entitlements = decodeEntitlements(root.array("entitlements", "entitlements")),
                marks = marks,
                granted = granted,
            )
        } catch (e: IllegalArgumentException) {
            // The domain types check their own rules (no negative entitlement, special days between
            // none and a whole day); their message names the broken rule.
            throw BackupException.Damaged(e.message ?: "a value is out of range")
        }
    }

    private fun encodeSettings(settings: UserSettings) =
        buildJsonObject {
            put("land", settings.land?.code)
            putJsonArray("regionalExtras") {
                settings.extras
                    .map { it.backupName() }
                    .sorted()
                    .forEach { add(it) }
            }
            putJsonArray("regionalOptOuts") {
                settings.optOuts
                    .map { it.backupName() }
                    .sorted()
                    .forEach { add(it) }
            }
            putJsonArray(
                "workingDays",
            ) {
                settings.schedule.workingDays
                    .sorted()
                    .forEach { add(it.name) }
            }
            put(
                "specialDays",
                JsonArray(
                    settings.schedule.specialDays.entries.sortedBy { it.key }.map { (day, value) ->
                        buildJsonObject {
                            put("day", day.toString())
                            put("halfDays", value.halfDays())
                        }
                    },
                ),
            )
            put("suggestCarryOver", settings.suggestCarryOver)
            put("grantedDays", settings.grantedDays)
        }

    private fun decodeSettings(settings: JsonObject): UserSettings {
        val land =
            when (val element = settings["land"] ?: damaged("settings.land is missing")) {
                is JsonNull -> {
                    null
                }

                else -> {
                    val code = element.string("settings.land")
                    Bundesland.fromCode(code) ?: damaged("settings.land: unknown Land \"$code\"")
                }
            }
        val specialDays =
            settings.array("specialDays", "settings.specialDays").mapIndexed { index, element ->
                val path = "settings.specialDays[$index]"
                val entry = element.obj(path)
                val day = entry.monthDay("day", path)
                day to VacationDays.ofHalves(entry.int("halfDays", path))
            }
        val days = specialDays.map { it.first }
        if (days.size != days.toSet().size) damaged("settings.specialDays lists a day twice")
        // The screens offer special days for these two only; any other date can never be shown or changed.
        days.firstOrNull { it !in ALLOWED_SPECIAL_DAYS }?.let {
            damaged("settings.specialDays: $it is not one of $ALLOWED_SPECIAL_DAYS")
        }
        val workingDays =
            settings.names("workingDays", "settings") { name ->
                DayOfWeek.entries.find { it.name == name }
            }
        // The screens keep at least one working day; with none, no day costs anything.
        if (workingDays.isEmpty()) damaged("settings.workingDays must have at least one day")
        return UserSettings(
            land = land,
            extras =
                settings.names("regionalExtras", "settings") { name ->
                    RegionalExtra.entries.find { it.backupName() == name }
                },
            optOuts =
                settings.names("regionalOptOuts", "settings") { name ->
                    RegionalOptOut.entries.find { it.backupName() == name }
                },
            schedule =
                WorkSchedule(
                    workingDays = workingDays,
                    specialDays = specialDays.toMap(),
                ),
            suggestCarryOver = settings.optionalBoolean("suggestCarryOver", "settings"),
            grantedDays = settings.optionalBoolean("grantedDays", "settings"),
        )
    }

    private fun decodeEntitlements(array: JsonArray): List<YearEntitlement> {
        val entitlements =
            array.mapIndexed { index, element ->
                val path = "entitlements[$index]"
                val entry = element.obj(path)
                YearEntitlement(
                    year = entry.year("year", path),
                    days = entry.halfDays("halfDays", path),
                    carryOver = entry.halfDays("carryOverHalfDays", path),
                    carryOverExpiry = entry.monthDay("carryOverExpiry", path),
                    carryOverExpiryYears = entry.expiryYears(path),
                )
            }
        val years = entitlements.map { it.year }
        if (years.size != years.toSet().size) damaged("entitlements lists a year twice")
        return entitlements
    }

    private fun decodeMarks(
        array: JsonArray,
        version: Int,
    ): Pair<Map<LocalDate, DayMark>, Set<LocalDate>> {
        val marks = mutableMapOf<LocalDate, DayMark>()
        val granted = mutableSetOf<LocalDate>()
        array.forEachIndexed { index, element ->
            val path = "markedDays[$index]"
            val entry = element.obj(path)
            val date = entry.date("date", path)
            val name = entry.string("mark", path)
            val mark =
                DayMark.entries.find { it.backupName() == name }
                    ?: damaged("$path.mark: unknown mark \"$name\"")
            if (marks.put(date, mark) != null) damaged("markedDays lists $date twice")
            // Version 1 has no such key; from version 2 on a missing one is damage, not "planned".
            if (version >= 2 && entry.requiredBoolean("granted", path)) granted += date
        }
        return marks to granted
    }

    // The names below are part of the file format; the fixture in BackupFileTest pins them.

    private fun DayMark.backupName() =
        when (this) {
            DayMark.FULL -> "full"
            DayMark.HALF -> "half"
        }

    private fun RegionalExtra.backupName() =
        when (this) {
            RegionalExtra.AUGSBURGER_FRIEDENSFEST -> "augsburger-friedensfest"
            RegionalExtra.FRONLEICHNAM_IN_TEILEN -> "fronleichnam-in-teilen"
        }

    private fun RegionalOptOut.backupName() =
        when (this) {
            RegionalOptOut.MARIAE_HIMMELFAHRT_BAYERN -> "mariae-himmelfahrt-bayern"
        }

    private fun VacationDays.halfDays() = (days * 2).toInt()

    // Reading helpers: every failure names the place in the file.

    private fun damaged(detail: String): Nothing = throw BackupException.Damaged(detail)

    private fun JsonElement.obj(path: String) =
        this as? JsonObject ?: damaged("$path must be an object")

    private fun JsonElement.string(path: String): String {
        val primitive = this as? JsonPrimitive
        if (primitive == null || primitive is JsonNull ||
            !primitive.isString
        ) {
            damaged("$path must be text")
        }
        return primitive.content
    }

    private fun JsonObject.field(
        key: String,
        path: String,
    ) = this[key] ?: damaged("$path.$key is missing")

    private fun JsonObject.string(
        key: String,
        path: String,
    ) = field(key, path).string("$path.$key")

    private fun JsonObject.int(
        key: String,
        path: String,
    ): Int {
        val primitive = field(key, path) as? JsonPrimitive
        if (primitive == null || primitive is JsonNull ||
            primitive.isString
        ) {
            damaged("$path.$key must be a number")
        }
        return primitive.content.toIntOrNull() ?: damaged("$path.$key must be a whole number")
    }

    /** False when the key is missing; a value that is not true or false is damaged. */
    private fun JsonObject.optionalBoolean(
        key: String,
        path: String,
    ): Boolean {
        val element = this[key] ?: return false
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive is JsonNull || primitive.isString) {
            damaged("$path.$key must be true or false")
        }
        return primitive.content.toBooleanStrictOrNull()
            ?: damaged("$path.$key must be true or false")
    }

    private fun JsonObject.requiredBoolean(
        key: String,
        path: String,
    ): Boolean {
        field(key, path)
        return optionalBoolean(key, path)
    }

    private fun JsonObject.obj(
        key: String,
        path: String,
    ) = field(key, path).obj("$path.$key")

    private fun JsonObject.array(
        key: String,
        path: String,
    ) = field(key, path) as? JsonArray ?: damaged("$path.$key must be a list")

    private fun JsonObject.halfDays(
        key: String,
        path: String,
    ): VacationDays {
        val halves = int(key, path)
        if (halves !in 0..MAX_HALF_DAYS) damaged("$path.$key must be between 0 and $MAX_HALF_DAYS")
        return VacationDays.ofHalves(halves)
    }

    private fun JsonObject.year(
        key: String,
        path: String,
    ): Int {
        val year = int(key, path)
        if (year !in
            MIN_YEAR..MAX_YEAR
        ) {
            damaged("$path.$key must be between $MIN_YEAR and $MAX_YEAR")
        }
        return year
    }

    /** 0 when the key is missing (files from before it existed); out of range is damaged. */
    private fun JsonObject.expiryYears(path: String): Int {
        if (this["carryOverExpiryYears"] == null) return 0
        val years = int("carryOverExpiryYears", path)
        if (years !in 0..YearEntitlement.MAX_EXPIRY_YEARS) {
            damaged(
                "$path.carryOverExpiryYears must be between 0 and ${YearEntitlement.MAX_EXPIRY_YEARS}",
            )
        }
        return years
    }

    private fun JsonObject.date(
        key: String,
        path: String,
    ): LocalDate {
        val date =
            try {
                LocalDate.parse(string(key, path))
            } catch (_: DateTimeParseException) {
                damaged("$path.$key is not a date like 2026-07-01")
            }
        if (date.year !in MIN_YEAR..MAX_YEAR) {
            damaged("$path.$key must be in the years $MIN_YEAR to $MAX_YEAR")
        }
        return date
    }

    private fun JsonObject.monthDay(
        key: String,
        path: String,
    ): MonthDay =
        try {
            MonthDay.parse(string(key, path))
        } catch (_: DateTimeParseException) {
            damaged("$path.$key is not a day like --12-24")
        }

    private fun <T> JsonObject.names(
        key: String,
        path: String,
        lookup: (String) -> T?,
    ): Set<T> =
        array(key, path)
            .mapIndexed { index, element ->
                val name = element.string("$path.$key[$index]")
                lookup(name) ?: damaged("$path.$key[$index]: unknown value \"$name\"")
            }.toSet()
}
