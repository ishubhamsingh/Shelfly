package dev.ishubhamsingh.shelfly.appfunctions.mapping

import dev.ishubhamsingh.shelfly.appfunctions.dto.ItemDto
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.domain.util.daysUntilExpiry
import dev.ishubhamsingh.shelfly.domain.util.statusFor

fun Item.toDto(settings: Settings = Settings()): ItemDto = ItemDto(
    id             = id,
    name           = name,
    category       = category.name,
    expiryDate     = expiryDate.toString(),
    qty            = qty,
    unit           = unit,
    notes          = notes,
    createdAt      = createdAt.toString(),
    consumed       = consumed,
    consumedAt     = consumedAt?.toString(),
    daysUntilExpiry = daysUntilExpiry,
    status         = statusFor(settings.defaultLeadTimeDays).name,
)
