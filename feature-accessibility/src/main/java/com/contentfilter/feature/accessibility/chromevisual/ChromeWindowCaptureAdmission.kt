package com.contentfilter.feature.accessibility.chromevisual

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Serializes the platform screenshot interval independently for each accessibility window. */
internal class ChromeWindowCaptureAdmission(
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val awaitMillis: suspend (Long) -> Unit = { delay(it) },
    private val onPlatformRequest: (ChromeWindowCaptureAdmissionEvent) -> Unit = ::logPlatformRequest,
) {
    private val lock = Any()
    private val latestPendingSequenceByWindow = mutableMapOf<Int, Long>()
    private val lastPlatformRequestAtByWindow = mutableMapOf<Int, Long>()
    private var nextPendingSequence = 0L

    suspend fun runWhenAdmitted(
        windowId: Int,
        platformRequest: () -> Unit,
    ): Boolean {
        val pendingSequence =
            synchronized(lock) {
                (++nextPendingSequence).also { latestPendingSequenceByWindow[windowId] = it }
            }
        var terminal = false
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                when (val decision = decision(windowId, pendingSequence)) {
                    is AdmissionDecision.Invoke -> {
                        terminal = true
                        onPlatformRequest(
                            ChromeWindowCaptureAdmissionEvent(
                                windowId = windowId,
                                requestedAtMillis = decision.requestedAtMillis,
                                previousRequestAtMillis = decision.previousRequestAtMillis,
                            ),
                        )
                        platformRequest()
                        return true
                    }
                    AdmissionDecision.Superseded -> {
                        terminal = true
                        return false
                    }
                    is AdmissionDecision.Wait -> awaitMillis(decision.millis)
                }
            }
        } finally {
            if (!terminal) {
                synchronized(lock) {
                    if (latestPendingSequenceByWindow[windowId] == pendingSequence) {
                        latestPendingSequenceByWindow.remove(windowId)
                    }
                }
            }
        }
    }

    private fun decision(
        windowId: Int,
        pendingSequence: Long,
    ): AdmissionDecision =
        synchronized(lock) {
            if (latestPendingSequenceByWindow[windowId] != pendingSequence) {
                return@synchronized AdmissionDecision.Superseded
            }
            val now = nowMillis()
            val previousRequestAtMillis = lastPlatformRequestAtByWindow[windowId]
            val eligibleAt = previousRequestAtMillis?.plus(MinimumRequestIntervalMillis) ?: now
            if (now < eligibleAt) {
                AdmissionDecision.Wait(eligibleAt - now)
            } else {
                lastPlatformRequestAtByWindow[windowId] = now
                latestPendingSequenceByWindow.remove(windowId)
                AdmissionDecision.Invoke(now, previousRequestAtMillis)
            }
        }

    private sealed interface AdmissionDecision {
        data class Invoke(
            val requestedAtMillis: Long,
            val previousRequestAtMillis: Long?,
        ) : AdmissionDecision

        data object Superseded : AdmissionDecision

        data class Wait(
            val millis: Long,
        ) : AdmissionDecision
    }

    companion object {
        const val MinimumRequestIntervalMillis = 334L
        val Shared = ChromeWindowCaptureAdmission()

        private const val LogTag = "ChromeWindowCapture"

        private fun logPlatformRequest(event: ChromeWindowCaptureAdmissionEvent) {
            val interval = event.previousRequestAtMillis?.let { event.requestedAtMillis - it }
            Log.i(
                LogTag,
                "phase=platform_request windowId=${event.windowId} " +
                    "requestedAtElapsedMs=${event.requestedAtMillis} intervalMs=${interval ?: "first"}",
            )
        }
    }
}

internal data class ChromeWindowCaptureAdmissionEvent(
    val windowId: Int,
    val requestedAtMillis: Long,
    val previousRequestAtMillis: Long?,
)
