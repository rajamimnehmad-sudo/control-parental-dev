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
    val limitText: String =
        if (dailyLimitMinutes == null) {
            "Sin límite"
        } else {
            "$usedMinutes/$dailyLimitMinutes min hoy"
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
