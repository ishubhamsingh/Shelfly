package dev.ishubhamsingh.shelfly.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val name: String,
    val category: Category,
    val expiryDate: LocalDate,
    val qty: Double?,
    val unit: String?,
    val notes: String?,
    val createdAt: Instant,
    val consumed: Boolean = false,
    val consumedAt: Instant? = null,
)
