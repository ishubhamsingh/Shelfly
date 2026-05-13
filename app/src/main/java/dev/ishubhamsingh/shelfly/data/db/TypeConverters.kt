package dev.ishubhamsingh.shelfly.data.db

import androidx.room.TypeConverter
import dev.ishubhamsingh.shelfly.domain.model.Category
import java.time.Instant
import java.time.LocalDate

class TypeConverters {

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? =
        value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? =
        value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun categoryToString(value: Category?): String? = value?.name

    @TypeConverter
    fun stringToCategory(value: String?): Category? =
        value?.let { Category.valueOf(it) }
}
