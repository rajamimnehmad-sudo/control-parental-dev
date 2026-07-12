package com.contentfilter.feature.accessibility.service

class AccessibilityWebActionDebouncer(
    private val debounceMillis: Long = DefaultDebounceMillis,
) {
    private val lastActions = mutableMapOf<ActionKey, Long>()

    fun shouldPerform(
        packageName: String,
        host: String?,
        policyRevision: Long,
        action: SearchNavigationAction,
        elapsedRealtimeMillis: Long,
    ): Boolean {
        val key = ActionKey(packageName, host.orEmpty(), policyRevision, action)
        val previousAtMillis = lastActions[key]
        if (previousAtMillis != null && elapsedRealtimeMillis - previousAtMillis < debounceMillis) return false
        lastActions[key] = elapsedRealtimeMillis
        if (lastActions.size > MaxRememberedActions) {
            lastActions.entries.removeAll { elapsedRealtimeMillis - it.value >= debounceMillis }
        }
        return true
    }

    fun clear() {
        lastActions.clear()
    }

    private data class ActionKey(
        val packageName: String,
        val host: String,
        val policyRevision: Long,
        val action: SearchNavigationAction,
    )

    private companion object {
        const val DefaultDebounceMillis = 1_500L
        const val MaxRememberedActions = 128
    }
}
