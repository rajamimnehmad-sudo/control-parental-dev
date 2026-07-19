package com.contentfilter.user

import com.contentfilter.user.apps.MyAppItemUiState
import com.contentfilter.user.apps.MyAppsUiState

internal data class UserHomeLimitItem(
    val id: String,
    val title: String,
    val kind: UserHomeLimitKind,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val icons: List<UserHomeAppIcon>,
) {
    val progress: Float =
        if (limitMinutes <= 0) {
            0f
        } else {
            (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
        }
    val remainingMinutes: Int = (limitMinutes - usedMinutes).coerceAtLeast(0)
}

internal data class UserHomeAppIcon(
    val name: String,
    val iconBase64: String?,
)

internal enum class UserHomeLimitKind {
    App,
    Group,
}

internal fun nearLimitItems(
    state: MyAppsUiState,
    threshold: Float = NearLimitThreshold,
): List<UserHomeLimitItem> {
    val appsByPackage = state.apps.associateBy(MyAppItemUiState::packageName)
    val appItems =
        state.apps.mapNotNull { app ->
            val limit = app.dailyLimitMinutes ?: return@mapNotNull null
            val progress = app.timeProgress ?: return@mapNotNull null
            if (app.extraTimeRemainingMinutes != null || progress < threshold) return@mapNotNull null
            UserHomeLimitItem(
                id = "app:${app.packageName}",
                title = app.name,
                kind = UserHomeLimitKind.App,
                usedMinutes = app.usedMinutes,
                limitMinutes = limit,
                icons = listOf(UserHomeAppIcon(app.name, app.iconBase64)),
            )
        }
    val groupItems =
        state.appGroups.mapNotNull { group ->
            if (!group.enabled || group.limitMinutes <= 0 || group.progress < threshold) return@mapNotNull null
            UserHomeLimitItem(
                id = "group:${group.id}",
                title = group.name,
                kind = UserHomeLimitKind.Group,
                usedMinutes = group.usedMinutes,
                limitMinutes = group.limitMinutes,
                icons =
                    group.packageNames.mapNotNull { packageName ->
                        appsByPackage[packageName]?.let { app -> UserHomeAppIcon(app.name, app.iconBase64) }
                    },
            )
        }
    return (appItems + groupItems)
        .sortedWith(
            compareByDescending<UserHomeLimitItem> { it.progress }
                .thenBy { it.remainingMinutes }
                .thenBy { it.title.lowercase() },
        )
}

private const val NearLimitThreshold = 0.70f
