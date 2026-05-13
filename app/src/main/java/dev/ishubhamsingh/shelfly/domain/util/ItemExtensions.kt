package dev.ishubhamsingh.shelfly.domain.util

import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val Item.daysUntilExpiry: Long
    get() = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)

fun Item.statusFor(leadTimeDays: Int): ItemStatus = when {
    consumed -> ItemStatus.CONSUMED
    daysUntilExpiry < 0 -> ItemStatus.EXPIRED
    daysUntilExpiry <= leadTimeDays -> ItemStatus.EXPIRING_SOON
    else -> ItemStatus.GOOD
}

fun Item.formattedQty(): String? {
    if (qty == null) return null
    val qtyStr = if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()
    return if (unit != null) "$qtyStr $unit" else qtyStr
}
