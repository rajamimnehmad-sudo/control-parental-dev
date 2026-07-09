package com.contentfilter.user.apps

data class MyAppsUiState(
    val apps: List<MyAppItemUiState> = emptyList(),
    val appGroups: List<MyAppGroupUiState> = emptyList(),
    val searchQuery: String = "",
    val message: String = "",
    val isRefreshing: Boolean = false,
)

data class MyAppGroupUiState(
    val id: String,
    val name: String,
    val limitMinutes: Int,
    val usedMinutes: Int,
    val appCount: Int,
    val color: String,
    val packageNames: List<String>,
) {
    val remainingMinutes: Int = (limitMinutes - usedMinutes).coerceAtLeast(0)
    val progress: Float =
        if (limitMinutes <= 0) {
            0f
        } else {
            (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        }

    val label: String =
        if (remainingMinutes == 0) {
            "Grupo agotado: $usedMinutes/$limitMinutes min"
        } else {
            "Restan $remainingMinutes min de $limitMinutes min"
        }
}

data class MyAppItemUiState(
    val name: String,
    val packageName: String,
    val iconBase64: String?,
    val status: AppAccessStatus,
    val dailyLimitMinutes: Int?,
    val extraTimeRemainingMinutes: Int? = null,
    val usedMinutes: Int,
    val isRequesting: Boolean = false,
) {
    val remainingMinutes: Int? = dailyLimitMinutes?.let { (it - usedMinutes).coerceAtLeast(0) }
    val timeProgress: Float? =
        dailyLimitMinutes?.let { limit ->
            if (limit <= 0) 0f else (usedMinutes.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        }
    val limitText: String =
        when {
            extraTimeRemainingMinutes != null -> "Tiempo extra: restan $extraTimeRemainingMinutes min"
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

enum class MyAppsQuickFilter {
    All,
    WithTime,
    InGroup,
    Blocked,
}
