package dev.ishubhamsingh.shelfly.appfunctions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dev.ishubhamsingh.shelfly.appfunctions.dto.ItemDto
import javax.inject.Inject

/**
 * AppFunctions entry point for external agents (e.g. Gemini).
 *
 * The appfunctions compiler generates the concrete [ShelflyAppFunctionServiceImpl] (declared in
 * the manifest) and `assets/shelfly_app_functions.xml`. Each declaration here only delegates to
 * [ShelflyAppFunctions], which the in-app agent also calls directly. The KDoc on each function
 * is the description agents see.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "ShelflyAppFunctionServiceImpl",
    appFunctionXmlFileName = "shelfly_app_functions",
)
abstract class ShelflyAppFunctionService : AppFunctionService() {

    @Inject lateinit var functions: ShelflyAppFunctions

    /**
     * Echo function used during toolchain verification.
     * Returns the [message] prefixed with "pong: ".
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun ping(message: String): String = functions.ping(message)

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
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addItem(
        name: String,
        expiryDate: String,
        category: String,
        qty: Double?,
        unit: String?,
        notes: String?,
    ): ItemDto = functions.addItem(name, expiryDate, category, qty, unit, notes)

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
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addItemFromMfg(
        name: String,
        mfgDate: String,
        bestBeforeMonths: Int,
        category: String,
        qty: Double?,
        unit: String?,
        notes: String?,
    ): ItemDto = functions.addItemFromMfg(name, mfgDate, bestBeforeMonths, category, qty, unit, notes)

    /**
     * List items expiring within the next [withinDays] days (default 7).
     *
     * @param withinDays How many days ahead to look.
     * @return Items expiring in that window, sorted by expiry date ascending.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listExpiringSoon(withinDays: Int): List<ItemDto> =
        functions.listExpiringSoon(withinDays)

    /**
     * List all items that have already passed their expiry date.
     *
     * @return Expired items sorted by expiry date descending.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listExpired(): List<ItemDto> = functions.listExpired()

    /**
     * List all items in a specific category.
     *
     * @param category One of FOOD, MEDICINE, COSMETIC, OTHER.
     * @return All items in that category sorted by expiry date.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listByCategory(category: String): List<ItemDto> =
        functions.listByCategory(category)

    /**
     * Get a single item by its ID.
     *
     * @param id The UUID of the item.
     * @return The item DTO, or null if not found.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getItem(id: String): ItemDto? = functions.getItem(id)

    /**
     * Mark an item as consumed, removing it from the active shelf view.
     *
     * @param id The UUID of the item to mark consumed.
     * @return true if the item was found and updated, false otherwise.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun markConsumed(id: String): Boolean = functions.markConsumed(id)

    /**
     * Update the expiry date of an existing item.
     *
     * @param id The UUID of the item.
     * @param newExpiry New ISO-8601 expiry date, e.g. "2026-09-30".
     * @return The updated item DTO, or null if the item was not found.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun updateExpiry(id: String, newExpiry: String): ItemDto? =
        functions.updateExpiry(id, newExpiry)

    /**
     * Permanently delete an item from the shelf.
     *
     * @param id The UUID of the item to delete.
     * @return true if the item existed and was deleted, false otherwise.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun deleteItem(id: String): Boolean = functions.deleteItem(id)
}
