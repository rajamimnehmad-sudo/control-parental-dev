package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "policy_rules",
    foreignKeys = [
        ForeignKey(
            entity = PolicyEntity::class,
            parentColumns = ["id"],
            childColumns = ["policyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["policyId"]),
        Index(value = ["policyId", "enabled", "priority"]),
        Index(value = ["scope", "target"]),
    ],
)
data class PolicyRuleEntity(
    @PrimaryKey val id: String,
    val policyId: String,
    val scope: String,
    val target: String,
    val action: String,
    val priority: Int,
    val enabled: Boolean,
)
