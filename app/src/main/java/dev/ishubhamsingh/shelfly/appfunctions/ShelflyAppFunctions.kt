package dev.ishubhamsingh.shelfly.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import dev.ishubhamsingh.shelfly.appfunctions.dto.ItemDto
import dev.ishubhamsingh.shelfly.appfunctions.mapping.toDto
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.domain.model.Item
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class ShelflyAppFunctions @Inject constructor(
    private val itemRepo: ItemRepository,
    private val settingsRepo: SettingsRepository,
) {

    // ── Phase 2a spike function (kept for regression) ─────────────────────────

    /**
     * Echo function used during toolchain verification.
     * Returns the [message] prefixed with "pong: ".
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun ping(
        appFunctionContext: AppFunctionContext,
        message: String,
    ): String = "pong: $message"

    // ── Phase 2b — full surface ────────────────────────────────────────────────

    /**
     * Add a new item to the shelf.
     *
     * @param name Human-readable name, e.g. "Whole milk".
     * @param expiryDate ISO-8601 date string, e.g. "2026-05-17".
     * @param category One of FOOD, MEDICINE, COSMETIC, OTHER.
     * @param qty Optional numeric quantity.
     * @param unit Optional unit string, e.g. "g", "ml", "tablets".
     * @param notes Optional freeform notes.
     * @return The created item as a DTO.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addItem(
        appFunctionContext: AppFunctionContext,
        name: String,
        expiryDate: String,
        category: String,
        qty: Double?,
        unit: String?,
        notes: String?,
    ): ItemDto {
        val settings = settingsRepo.settings.first()
        val item = Item(
            id         = UUID.randomUUID().toString(),
            name       = name.trim(),
            category   = Category.valueOf(category.uppercase()),
            expiryDate = LocalDate.parse(expiryDate),
            qty        = qty,
            unit       = unit?.trim(),
            notes      = notes?.trim(),
            createdAt  = Instant.now(),
        )
        itemRepo.insert(item)
        return item.toDto(settings)
    }

    /**
     * Add an item whose packaging only shows a manufacture date and a "best before N months" window.
     *
     * @param name Human-readable name.
     * @param mfgDate ISO-8601 manufacture date, e.g. "2025-11-01".
     * @param bestBeforeMonths Number of months the item is good for after manufacture.
     * @param category One of FOOD, MEDICINE, COSMETIC, OTHER.
     * @param qty Optional numeric quantity.
     * @param unit Optional unit string.
     * @param notes Optional notes.
     * @return The created item with the computed expiry date.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addItemFromMfg(
        appFunctionContext: AppFunctionContext,
        name: String,
        mfgDate: String,
        bestBeforeMonths: Int,
        category: String,
        qty: Double?,
        unit: String?,
        notes: String?,
    ): ItemDto {
        val settings = settingsRepo.settings.first()
        val expiry = LocalDate.parse(mfgDate).plusMonths(bestBeforeMonths.toLong())
        val item = Item(
            id         = UUID.randomUUID().toString(),
            name       = name.trim(),
            category   = Category.valueOf(category.uppercase()),
            expiryDate = expiry,
            qty        = qty,
            unit       = unit?.trim(),
            notes      = notes?.trim(),
            createdAt  = Instant.now(),
        )
        itemRepo.insert(item)
        return item.toDto(settings)
    }

    /**
     * List items expiring within the next [withinDays] days (default 7).
     *
     * @param withinDays How many days ahead to look.
     * @return Items expiring in that window, sorted by expiry date ascending.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listExpiringSoon(
        appFunctionContext: AppFunctionContext,
        withinDays: Int,
    ): List<ItemDto> {
        val settings = settingsRepo.settings.first()
        return itemRepo.getExpiringSoon(withinDays).map { it.toDto(settings) }
    }

    /**
     * List all items that have already passed their expiry date.
     *
     * @return Expired items sorted by expiry date descending.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listExpired(
        appFunctionContext: AppFunctionContext,
    ): List<ItemDto> {
        val settings = settingsRepo.settings.first()
        return itemRepo.getExpired().map { it.toDto(settings) }
    }

    /**
     * List all items in a specific category.
     *
     * @param category One of FOOD, MEDICINE, COSMETIC, OTHER.
     * @return All items in that category sorted by expiry date.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listByCategory(
        appFunctionContext: AppFunctionContext,
        category: String,
    ): List<ItemDto> {
        val settings = settingsRepo.settings.first()
        val cat = Category.valueOf(category.uppercase())
        return itemRepo.getByCategory(cat).map { it.toDto(settings) }
    }

    /**
     * Get a single item by its ID.
     *
     * @param id The UUID of the item.
     * @return The item DTO, or null if not found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getItem(
        appFunctionContext: AppFunctionContext,
        id: String,
    ): ItemDto? {
        val settings = settingsRepo.settings.first()
        return itemRepo.getById(id)?.toDto(settings)
    }

    /**
     * Mark an item as consumed, removing it from the active shelf view.
     *
     * @param id The UUID of the item to mark consumed.
     * @return true if the item was found and updated, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun markConsumed(
        appFunctionContext: AppFunctionContext,
        id: String,
    ): Boolean {
        val item = itemRepo.getById(id) ?: return false
        itemRepo.update(item.copy(consumed = true, consumedAt = Instant.now()))
        return true
    }

    /**
     * Update the expiry date of an existing item.
     *
     * @param id The UUID of the item.
     * @param newExpiry New ISO-8601 expiry date, e.g. "2026-09-30".
     * @return The updated item DTO, or null if the item was not found.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpiry(
        appFunctionContext: AppFunctionContext,
        id: String,
        newExpiry: String,
    ): ItemDto? {
        val settings = settingsRepo.settings.first()
        val item = itemRepo.getById(id) ?: return null
        val updated = item.copy(expiryDate = LocalDate.parse(newExpiry))
        itemRepo.update(updated)
        return updated.toDto(settings)
    }

    /**
     * Permanently delete an item from the shelf.
     *
     * @param id The UUID of the item to delete.
     * @return true if the item existed and was deleted, false otherwise.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteItem(
        appFunctionContext: AppFunctionContext,
        id: String,
    ): Boolean {
        val exists = itemRepo.getById(id) != null
        if (!exists) return false
        itemRepo.deleteById(id)
        return true
    }
}
