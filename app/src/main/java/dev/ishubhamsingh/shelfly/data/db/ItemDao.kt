package dev.ishubhamsingh.shelfly.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY expiryDate ASC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE consumed = 0 ORDER BY expiryDate ASC")
    fun observeNonConsumed(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE consumed = 0 AND expiryDate >= :todayEpochDay ORDER BY expiryDate ASC")
    fun observeActive(todayEpochDay: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE consumed = 0 AND expiryDate < :todayEpochDay ORDER BY expiryDate DESC")
    fun observeExpired(todayEpochDay: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE consumed = 0 AND expiryDate BETWEEN :todayEpochDay AND :windowEndEpochDay ORDER BY expiryDate ASC")
    fun observeExpiringSoon(todayEpochDay: Long, windowEndEpochDay: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE consumed = 1 ORDER BY consumedAt DESC")
    fun observeConsumed(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE category = :category ORDER BY expiryDate ASC")
    fun observeByCategory(category: Category): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: String): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): Item?

    @Query("SELECT * FROM items WHERE consumed = 0 AND expiryDate BETWEEN :todayEpochDay AND :windowEndEpochDay")
    suspend fun getExpiringSoon(todayEpochDay: Long, windowEndEpochDay: Long): List<Item>

    @Query("SELECT * FROM items WHERE consumed = 0 AND expiryDate < :todayEpochDay")
    suspend fun getExpired(todayEpochDay: Long): List<Item>

    @Query("SELECT * FROM items WHERE category = :category")
    suspend fun getByCategory(category: Category): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item)

    @Update
    suspend fun update(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)
}
