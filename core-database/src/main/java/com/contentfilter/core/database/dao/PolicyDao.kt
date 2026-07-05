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
    @Query("SELECT * FROM policies WHERE active = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    fun observeActivePolicy(): Flow<PolicyEntity?>

    @Query("SELECT * FROM policies WHERE active = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun activePolicy(): PolicyEntity?

    @Query(
        """
        SELECT * FROM policies
        WHERE active = 1 AND deviceId = :deviceId
        ORDER BY updatedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    fun observeActivePolicyForDevice(deviceId: String): Flow<PolicyEntity?>

    @Query(
        """
        SELECT * FROM policies
        WHERE active = 1 AND deviceId = :deviceId
        ORDER BY updatedAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun activePolicyForDevice(deviceId: String): PolicyEntity?

    @Query("SELECT * FROM policies WHERE active = 1 ORDER BY updatedAtEpochMillis DESC")
    fun observeActivePolicies(): Flow<List<PolicyEntity>>

    @Query("SELECT * FROM policies WHERE active = 1 ORDER BY updatedAtEpochMillis DESC")
    suspend fun activePolicies(): List<PolicyEntity>

    @Query("SELECT policyId FROM policy_rules WHERE id = :ruleId LIMIT 1")
    suspend fun policyIdForRule(ruleId: String): String?

    @Query("SELECT * FROM policies WHERE id = :policyId LIMIT 1")
    suspend fun policyById(policyId: String): PolicyEntity?

    @Query("SELECT * FROM policy_rules WHERE policyId = :policyId")
    suspend fun rulesForPolicy(policyId: String): List<PolicyRuleEntity>

    @Query("SELECT * FROM policy_rules WHERE policyId = :policyId")
    fun observeRulesForPolicy(policyId: String): Flow<List<PolicyRuleEntity>>

    @Query(
        """
        SELECT policy_rules.* FROM policy_rules
        INNER JOIN policies ON policies.id = policy_rules.policyId
        WHERE policies.active = 1
        """,
    )
    fun observeRulesForActivePolicies(): Flow<List<PolicyRuleEntity>>

    @Query(
        """
        SELECT policy_rules.* FROM policy_rules
        INNER JOIN policies ON policies.id = policy_rules.policyId
        WHERE policies.active = 1
        """,
    )
    suspend fun rulesForActivePolicies(): List<PolicyRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(policy: PolicyEntity)

    @Query("UPDATE policies SET active = 0 WHERE id = :policyId")
    suspend fun deactivatePolicy(policyId: String)

    @Query("UPDATE policies SET active = 0 WHERE id != :policyId")
    suspend fun deactivateOtherPolicies(policyId: String)

    @Query("UPDATE policies SET active = 0 WHERE id != :policyId AND deviceId = :deviceId")
    suspend fun deactivateOtherPoliciesForDevice(
        policyId: String,
        deviceId: String,
    )

    @Query("UPDATE policies SET active = 0 WHERE id != :policyId AND deviceId IS NULL")
    suspend fun deactivateOtherPoliciesWithoutDevice(policyId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: PolicyRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<PolicyRuleEntity>)

    @Query("DELETE FROM policy_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: String)

    @Query("DELETE FROM policy_rules")
    suspend fun deleteAllRules()

    @Query("DELETE FROM policies")
    suspend fun deleteAllPolicies()
}
