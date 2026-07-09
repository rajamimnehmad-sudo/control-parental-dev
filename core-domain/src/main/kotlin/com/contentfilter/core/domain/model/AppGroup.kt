package com.contentfilter.core.domain.model

data class AppGroup(
    val id: String,
    val deviceId: String,
    val name: String,
    val color: String,
    val limitMinutes: Int,
    val resetMinuteOfDay: Int,
    val enabled: Boolean,
    val apps: List<AppGroupApp> = emptyList(),
)

data class AppGroupApp(
    val id: String,
    val groupId: String,
    val packageName: String,
    val enabled: Boolean,
)
