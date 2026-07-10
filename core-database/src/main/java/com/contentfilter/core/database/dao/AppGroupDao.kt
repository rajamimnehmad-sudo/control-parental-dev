package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.AppGroupAppEntity
import com.contentfilter.core.database.entity.AppGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_groups WHERE enabled = 1 AND deviceId = :deviceId ORDER BY name COLLATE NOCASE")
    fun observeEnabledGroupsForDevice(deviceId: String): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM app_group_apps WHERE enabled = 1 AND groupId IN (:groupIds)")
    fun observeEnabledAppsForGroups(groupIds: List<String>): Flow<List<AppGroupAppEntity>>

    @Query("SELECT * FROM app_group_apps WHERE enabled = 1 AND groupId IN (:groupIds)")
    suspend fun enabledAppsForGroups(groupIds: List<String>): List<AppGroupAppEntity>

    @Query("SELECT * FROM app_groups WHERE enabled = 1 AND deviceId = :deviceId ORDER BY name COLLATE NOCASE")
    suspend fun enabledGroupsForDevice(deviceId: String): List<AppGroupEntity>

    @Query("SELECT * FROM app_groups WHERE id = :id LIMIT 1")
    suspend fun groupById(id: String): AppGroupEntity?

    @Query("SELECT * FROM app_group_apps WHERE id = :id LIMIT 1")
    suspend fun appById(id: String): AppGroupAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: AppGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: AppGroupAppEntity)

    @Query("DELETE FROM app_groups WHERE id = :id")
    suspend fun deleteGroupById(id: String)

    @Query("DELETE FROM app_group_apps WHERE id = :id")
    suspend fun deleteAppById(id: String)

    @Query("SELECT * FROM app_group_apps WHERE groupId = :groupId")
    suspend fun appsForGroup(groupId: String): List<AppGroupAppEntity>

    @Query("DELETE FROM app_groups")
    suspend fun deleteAllGroups()

    @Query("DELETE FROM app_group_apps")
    suspend fun deleteAllApps()
}
