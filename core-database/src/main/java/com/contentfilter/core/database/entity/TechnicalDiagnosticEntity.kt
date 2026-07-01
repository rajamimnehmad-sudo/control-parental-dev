package com.contentfilter.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "technical_diagnostics")
data class TechnicalDiagnosticEntity(
    @PrimaryKey val id: String,
    val type: String,
    val message: String,
    val occurredAtEpochMillis: Long,
)
