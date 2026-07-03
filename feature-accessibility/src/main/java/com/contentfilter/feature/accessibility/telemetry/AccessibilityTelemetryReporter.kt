package com.contentfilter.feature.accessibility.telemetry

import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.TelemetryRepository
import java.util.UUID
import javax.inject.Inject

class AccessibilityTelemetryReporter
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
    ) {
        suspend fun recordServiceState(state: String) {
            record(type = "accessibility-state", message = state)
        }

        suspend fun recordDecision(
            packageName: String,
            decision: PolicyDecision,
        ) {
            record(
                type = "accessibility-decision",
                message = "App decision: ${decision.label()} package=$packageName",
            )
        }

        suspend fun recordSettingsProtection() {
            record(type = "accessibility-settings-protection", message = "Settings protection action applied.")
        }

        suspend fun recordError(message: String) {
            record(type = "accessibility-error", message = message.take(MaxMessageLength))
        }

        private suspend fun record(
            type: String,
            message: String,
        ) {
            telemetryRepository.record(
                TechnicalDiagnostic(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    message = message,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }

        private fun PolicyDecision.label(): String =
            when (this) {
                is PolicyDecision.Allow -> if (safeSearchRequired) "AllowSafeSearch" else "Allow"
                is PolicyDecision.Block -> "Block"
                is PolicyDecision.GrantExtraTime -> "GrantExtraTime"
                is PolicyDecision.HealthWarning -> "HealthWarning"
                is PolicyDecision.RequestAuthorization -> "RequestAuthorization"
                is PolicyDecision.RequireActivation -> "RequireActivation"
                is PolicyDecision.RequireUpdate -> "RequireUpdate"
                is PolicyDecision.Warn -> "Warn"
            }

        private companion object {
            const val MaxMessageLength = 120
        }
    }
