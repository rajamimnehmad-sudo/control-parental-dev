package com.contentfilter.feature.accessibility.time

/**
 * Tracks foreground app time with monotonic elapsed time.
 */
class AppUsageTracker {
    private val accumulatedMillisByPackage = mutableMapOf<String, Long>()
    private var currentPackageName: String? = null
    private var currentStartedAtElapsedMillis: Long = 0L
    private var currentStartedAtEpochMillis: Long = 0L

    fun onForegroundApp(
        packageName: String,
        elapsedRealtimeMillis: Long,
        epochMillis: Long,
    ): UsageTransition? {
        if (packageName == currentPackageName) return null
        val transition = finishCurrent(elapsedRealtimeMillis, epochMillis)
        currentPackageName = packageName
        currentStartedAtElapsedMillis = elapsedRealtimeMillis
        currentStartedAtEpochMillis = epochMillis
        return transition
    }

    fun usedMinutes(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ): Int {
        val accumulated = accumulatedMillisByPackage[packageName] ?: 0L
        return ((accumulated + activeMillis(packageName, elapsedRealtimeMillis)) / MillisPerMinute).toInt()
    }

    fun activeMinutes(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ): Int = (activeMillis(packageName, elapsedRealtimeMillis) / MillisPerMinute).toInt()

    private fun activeMillis(
        packageName: String,
        elapsedRealtimeMillis: Long,
    ): Long {
        val active = if (packageName == currentPackageName) {
            (elapsedRealtimeMillis - currentStartedAtElapsedMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        return active
    }

    fun finishCurrent(
        elapsedRealtimeMillis: Long,
        epochMillis: Long,
    ): UsageTransition? {
        val packageName = currentPackageName ?: return null
        val duration = (elapsedRealtimeMillis - currentStartedAtElapsedMillis).coerceAtLeast(0L)
        accumulatedMillisByPackage[packageName] = (accumulatedMillisByPackage[packageName] ?: 0L) + duration
        currentPackageName = null
        return UsageTransition(
            packageName = packageName,
            startedAtEpochMillis = currentStartedAtEpochMillis,
            endedAtEpochMillis = epochMillis,
        )
    }

    fun checkpointCurrent(
        elapsedRealtimeMillis: Long,
        epochMillis: Long,
        minimumDurationMillis: Long,
    ): UsageTransition? {
        val packageName = currentPackageName ?: return null
        val duration = (elapsedRealtimeMillis - currentStartedAtElapsedMillis).coerceAtLeast(0L)
        if (duration < minimumDurationMillis) return null
        val transition = UsageTransition(
            packageName = packageName,
            startedAtEpochMillis = currentStartedAtEpochMillis,
            endedAtEpochMillis = epochMillis,
        )
        currentStartedAtElapsedMillis = elapsedRealtimeMillis
        currentStartedAtEpochMillis = epochMillis
        return transition
    }

    fun currentPackageName(): String? = currentPackageName

    companion object {
        private const val MillisPerMinute = 60_000L
    }
}
