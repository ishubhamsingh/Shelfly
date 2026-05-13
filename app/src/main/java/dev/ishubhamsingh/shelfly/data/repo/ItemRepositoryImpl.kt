package dev.ishubhamsingh.shelfly.data.repo

import dev.ishubhamsingh.shelfly.data.db.ItemDao
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class ItemRepositoryImpl @Inject constructor(
    private val dao: ItemDao,
) : ItemRepository {

    override fun observeAll(): Flow<List<Item>> = dao.observeAll()

    override fun observeActive(): Flow<List<Item>> =
        dao.observeActive(LocalDate.now().toEpochDay())

    override fun observeExpired(): Flow<List<Item>> =
        dao.observeExpired(LocalDate.now().toEpochDay())

    override fun observeExpiringSoon(withinDays: Int): Flow<List<Item>> {
        val today = LocalDate.now()
        return dao.observeExpiringSoon(
            todayEpochDay = today.toEpochDay(),
            windowEndEpochDay = today.plusDays(withinDays.toLong()).toEpochDay(),
        )
    }

    override fun observeConsumed(): Flow<List<Item>> = dao.observeConsumed()

    override fun observeByCategory(category: Category): Flow<List<Item>> =
        dao.observeByCategory(category)

    override suspend fun getById(id: String): Item? = dao.getById(id)

    override suspend fun getExpiringSoon(withinDays: Int): List<Item> {
        val today = LocalDate.now()
        return dao.getExpiringSoon(
            todayEpochDay = today.toEpochDay(),
            windowEndEpochDay = today.plusDays(withinDays.toLong()).toEpochDay(),
        )
    }

    override suspend fun getExpired(): List<Item> =
        dao.getExpired(LocalDate.now().toEpochDay())

    override suspend fun getByCategory(category: Category): List<Item> =
        dao.getByCategory(category)

    override suspend fun insert(item: Item) = dao.insert(item)

    override suspend fun update(item: Item) = dao.update(item)

    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
