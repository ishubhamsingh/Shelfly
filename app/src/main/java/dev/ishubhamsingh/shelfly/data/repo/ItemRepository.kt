package dev.ishubhamsingh.shelfly.data.repo

import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ItemRepository {
    fun observeAll(): Flow<List<Item>>
    fun observeNonConsumed(): Flow<List<Item>>
    fun observeActive(): Flow<List<Item>>
    fun observeExpired(): Flow<List<Item>>
    fun observeExpiringSoon(withinDays: Int): Flow<List<Item>>
    fun observeConsumed(): Flow<List<Item>>
    fun observeByCategory(category: Category): Flow<List<Item>>

    fun observeById(id: String): Flow<Item?>
    suspend fun getById(id: String): Item?
    suspend fun getExpiringSoon(withinDays: Int): List<Item>
    suspend fun getExpired(): List<Item>
    suspend fun getByCategory(category: Category): List<Item>

    suspend fun insert(item: Item)
    suspend fun update(item: Item)
    suspend fun deleteById(id: String)
}
