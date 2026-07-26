package com.contentfilter.user.dag2

import javax.inject.Inject
import javax.inject.Singleton

enum class DagV2CalibrationQueueResult {
    Queued,
    Deduplicated,
    Stale,
    Disabled,
}

@Singleton
class DagV2CalibrationCandidateQueue
    @Inject
    constructor() {
        private val candidates = LinkedHashMap<String, DagV2CalibrationCandidate>()
        private var enabled = false
        private var sessionId: String? = null
        private var navigationToken: String? = null

        @Synchronized
        fun resetLabSession() {
            enabled = false
            clearSensitiveState()
        }

        @Synchronized
        fun setEnabled(value: Boolean) {
            enabled = value
            if (!value) clearSensitiveState()
        }

        @Synchronized
        fun onDocument(session: DagV2DocumentSessionState) {
            candidates.clear()
            sessionId = session.sessionId
            navigationToken = session.navigationToken
        }

        @Synchronized
        fun offer(candidate: DagV2CalibrationCandidate): DagV2CalibrationQueueResult {
            if (!enabled) return DagV2CalibrationQueueResult.Disabled
            if (
                !candidate.reviewable ||
                candidate.attribution != DagV2RequestAttribution.Current ||
                candidate.sessionId != sessionId ||
                candidate.navigationToken != navigationToken
            ) {
                return DagV2CalibrationQueueResult.Stale
            }
            val identity = candidate.normalizedResourceIdentity
            if (candidates.containsKey(identity)) return DagV2CalibrationQueueResult.Deduplicated
            candidates[identity] = candidate
            while (candidates.size > MaxCandidates) {
                candidates.remove(candidates.keys.first())
            }
            return DagV2CalibrationQueueResult.Queued
        }

        @Synchronized
        fun snapshot(): List<DagV2CalibrationCandidate> =
            candidates.values.sortedByDescending(DagV2CalibrationCandidate::observedAt)

        @Synchronized
        fun candidate(candidateId: String): DagV2CalibrationCandidate? =
            candidates.values.firstOrNull { it.candidateId == candidateId }

        @Synchronized
        fun remove(candidateId: String) {
            val key = candidates.entries.firstOrNull { it.value.candidateId == candidateId }?.key
            if (key != null) candidates.remove(key)
        }

        @Synchronized
        fun clearSensitiveState() {
            candidates.clear()
            sessionId = null
            navigationToken = null
        }

        private companion object {
            const val MaxCandidates = 100
        }
    }
