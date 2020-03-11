package com.nononsenseapps.feeder.db.room

import androidx.room.TypeConverter
import com.nononsenseapps.feeder.util.sloppyLinkToStrictURLNoThrows
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZonedDateTime
import java.net.URL

class Converters {

    @TypeConverter
    fun dateTimeFromString(value: String?): ZonedDateTime? {
        var dt: ZonedDateTime? = null
        if (value != null) {
            try {
                dt = ZonedDateTime.parse(value)
            } catch (t: Throwable) {
            }
        }
        return dt
    }

    @TypeConverter
    fun stringFromDateTime(value: ZonedDateTime?): String? =
            value?.toString()

    @TypeConverter
    fun stringFromURL(value: URL?): String? =
            value?.toString()

    @TypeConverter
    fun urlFromString(value: String?): URL? =
            value?.let { sloppyLinkToStrictURLNoThrows(it) }

    @TypeConverter
    fun instantFromLong(value: Long?): Instant? =
            try {
                value?.let { Instant.ofEpochMilli(it) }
            } catch (t: Throwable) {
                null
            }

    @TypeConverter
    fun longFromInstant(value: Instant?): Long? =
            value?.toEpochMilli()

    @TypeConverter
    fun longFromDuration(value: Duration?): Long? =
            value?.seconds

    @TypeConverter
    fun durationFromLong(value: Long?): Duration? =
            value?.let { Duration.ofSeconds(it) }

    @TypeConverter
    fun stringFromRingBuffer(value: DurationRingBuffer?): String? =
            value?.toString()

    @TypeConverter
    fun ringBufferFromString(value: String?): DurationRingBuffer? =
            value?.let {
                val vals = it.split(",")
                DurationRingBuffer(
                        index = vals.first().toInt(),
                        buffer = vals.drop(1).map { Duration.ofSeconds(it.toLong()) }.toTypedArray()
                )
            }

    @TypeConverter
    fun localDateTimeFromString(value: String?): LocalDateTime? =
            value?.let { LocalDateTime.parse(it) }

    @TypeConverter
    fun stringFromLocalDateTime(value: LocalDateTime?): String? =
            value?.let { it.toString() }
}
