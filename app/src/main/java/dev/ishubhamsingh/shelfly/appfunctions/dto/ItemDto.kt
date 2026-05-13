package dev.ishubhamsingh.shelfly.appfunctions.dto

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class ItemDto(
    val id: String,
    val name: String,
    val category: String,
    val expiryDate: String,      // ISO-8601 date: "2026-05-17"
    val qty: Double?,
    val unit: String?,
    val notes: String?,
    val createdAt: String,       // ISO-8601 instant
    val consumed: Boolean,
    val consumedAt: String?,     // ISO-8601 instant, null if not consumed
    val daysUntilExpiry: Long,
    val status: String,          // "GOOD" | "EXPIRING_SOON" | "EXPIRED" | "CONSUMED"
)
