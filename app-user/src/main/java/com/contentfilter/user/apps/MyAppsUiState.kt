package com.contentfilter.user.apps

data class MyAppsUiState(
    val apps: List<MyAppItemUiState> = emptyList(),
    val searchQuery: String = "",
    val message: String = "",
)

data class MyAppItemUiState(
    val name: String,
    val packageName: String,
    val iconBase64: String?,
    val status: AppAccessStatus,
    val dailyLimitMinutes: Int?,
    val usedMinutes: Int,
    val isRequesting: Boolean = false,
) {
    val remainingMinutes: Int? = dailyLimitMinutes?.let { (it - usedMinutes).coerceAtLeast(0) }
    val limitText: String =
        when {
            dailyLimitMinutes == null -> "Sin límite"
            remainingMinutes == 0 -> "Límite agotado: $usedMinutes/$dailyLimitMinutes min hoy"
            else -> "Restan $remainingMinutes min de $dailyLimitMinutes min"
        }
}

enum class AppAccessStatus {
    Allowed,
    Limited,
    LimitReached,
    ExtraTime,
    Blocked,
    RequiresAuthorization,
    WaitingAuthorization,
    WaitingExtraTime,
}
