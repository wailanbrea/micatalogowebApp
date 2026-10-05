package com.example.bspos.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

/** Explicit TEXT UUIDs and UTC epoch milliseconds; money is already stored as Long cents. */
class DatabaseConverters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
