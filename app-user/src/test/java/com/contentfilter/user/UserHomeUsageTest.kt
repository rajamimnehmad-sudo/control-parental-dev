package com.contentfilter.user

import com.contentfilter.user.apps.AppAccessStatus
import com.contentfilter.user.apps.MyAppGroupUiState
import com.contentfilter.user.apps.MyAppItemUiState
import com.contentfilter.user.apps.MyAppsUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class UserHomeUsageTest {
    @Test
    fun `shows apps and enabled groups at or above seventy percent`() {
        val state =
            MyAppsUiState(
                apps =
                    listOf(
                        app("video", "Video", used = 45, limit = 60),
                        app("chat", "Chat", used = 20, limit = 60),
                    ),
                appGroups =
                    listOf(
                        group("social", "Social", used = 80, limit = 90, packages = listOf("chat")),
                    ),
            )

        val items = nearLimitItems(state)

        assertEquals(listOf("Social", "Video"), items.map(UserHomeLimitItem::title))
        assertEquals(UserHomeLimitKind.Group, items.first().kind)
        assertEquals(listOf("Chat"), items.first().icons.map(UserHomeAppIcon::name))
    }

    @Test
    fun `ignores disabled groups and apps with active extra time`() {
        val state =
            MyAppsUiState(
                apps = listOf(app("video", "Video", used = 60, limit = 60, extra = 15)),
                appGroups = listOf(group("games", "Juegos", used = 90, limit = 90, enabled = false)),
            )

        assertEquals(emptyList(), nearLimitItems(state))
    }

    private fun app(
        packageName: String,
        name: String,
        used: Int,
        limit: Int,
        extra: Int? = null,
    ) = MyAppItemUiState(
        name = name,
        packageName = packageName,
        iconBase64 = null,
        status = AppAccessStatus.Limited,
        dailyLimitMinutes = limit,
        extraTimeRemainingMinutes = extra,
        usedMinutes = used,
    )

    private fun group(
        id: String,
        name: String,
        used: Int,
        limit: Int,
        packages: List<String> = emptyList(),
        enabled: Boolean = true,
    ) = MyAppGroupUiState(
        id = id,
        name = name,
        enabled = enabled,
        limitMinutes = limit,
        usedMinutes = used,
        appCount = packages.size,
        color = "#000000",
        packageNames = packages,
    )
}
