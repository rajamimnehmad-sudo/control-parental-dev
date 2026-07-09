package com.contentfilter.core.data

import com.contentfilter.core.database.entity.AppGroupAppEntity
import com.contentfilter.core.database.entity.AppGroupEntity
import com.contentfilter.core.domain.model.AppGroup
import com.contentfilter.core.domain.model.AppGroupApp

internal fun AppGroupEntity.toDomain(apps: List<AppGroupAppEntity>): AppGroup =
    AppGroup(
        id = id,
        deviceId = deviceId,
        name = name,
        color = color,
        limitMinutes = limitMinutes,
        resetMinuteOfDay = resetMinuteOfDay,
        enabled = enabled,
        apps = apps.filter { it.groupId == id }.map { it.toDomain() },
    )

internal fun AppGroup.toEntity(updatedAtEpochMillis: Long = System.currentTimeMillis()): AppGroupEntity =
    AppGroupEntity(
        id = id,
        deviceId = deviceId,
        name = name,
        color = color,
        limitMinutes = limitMinutes,
        resetMinuteOfDay = resetMinuteOfDay,
        enabled = enabled,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

internal fun AppGroupAppEntity.toDomain(): AppGroupApp =
    AppGroupApp(
        id = id,
        groupId = groupId,
        packageName = packageName,
        enabled = enabled,
    )

internal fun AppGroupApp.toEntity(updatedAtEpochMillis: Long = System.currentTimeMillis()): AppGroupAppEntity =
    AppGroupAppEntity(
        id = id,
        groupId = groupId,
        packageName = packageName,
        enabled = enabled,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
