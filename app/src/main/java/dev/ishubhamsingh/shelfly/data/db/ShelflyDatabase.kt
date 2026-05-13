package dev.ishubhamsingh.shelfly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.ishubhamsingh.shelfly.domain.model.Item

@Database(
    entities = [Item::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(dev.ishubhamsingh.shelfly.data.db.TypeConverters::class)
abstract class ShelflyDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
