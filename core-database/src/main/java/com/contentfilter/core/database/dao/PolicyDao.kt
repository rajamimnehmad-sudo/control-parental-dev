package com.contentfilter.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies WHERE active = 1 LIMIT 1")
    fun observeActivePolicy(): Flow<PolicyEntity?>

    @Query("SELECT * FROM policies WHERE active = 1 LIMIT 1")
    suspend fun activePolicy(): PolicyEntity?

    @Query("SELECT * FROM policy_rules WHERE policyId = :policyId")
    suspend fun rulesForPolicy(policyId: String): List<PolicyRuleEntity>

    @Query("SELECT * FROM policy_rules WHERE policyId = :policyId")
    fun observeRulesForPolicy(policyId: String): Flow<List<PolicyRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(policy: PolicyEntity)

    @Query("UPDATE policies SET active = 0 WHERE id = :policyId")
    suspend fun deactivatePolicy(policyId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: PolicyRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<PolicyRuleEntity>)

    @Query("DELETE FROM policy_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: String)

    @Query("DELETE FROM policy_rules")
    suspend fun deleteAllRules()
}
