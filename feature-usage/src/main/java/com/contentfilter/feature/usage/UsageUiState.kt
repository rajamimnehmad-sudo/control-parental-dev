package com.contentfilter.feature.usage

data class UsageUiState(
    val localDate: String = "",
    val items: List<UsageItemUiState> = emptyList(),
    val message: String = "Uso diario local.",
)

data class UsageItemUiState(
    val packageName: String,
    val usedMinutes: Int,
)
