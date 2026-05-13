package dev.ishubhamsingh.shelfly.domain.model

data class Settings(
    val defaultLeadTimeDays: Int = 3,
    val dynamicColor: Boolean = false,
    val quietHours: Boolean = true,
)
