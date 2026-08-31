package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierBridge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierCompletion

/**
 * Lets a parser-blocking external script proceed only while exactly one Chrome application window
 * and native root are foreground. Waiting for an AX WebView would deadlock renderer progress at
 * this boundary. This gate carries no document capability and grants no presentation authority.
 */
internal class ChromeMediaShieldParserBarrierCoordinator(
    private val service: AccessibilityService,
    readContext: () -> ChromeMediaShieldActiveDocumentContextReadResult,
) : AutoCloseable {
    private val admission =
        ChromeMediaShieldParserBarrierAdmission(
            readContext = readContext,
            onWaiting = { reason -> Log.i(LogTag, "phase=parser_barrier_waiting reason=$reason") },
            onReady = { binding -> Log.i(LogTag, "phase=parser_barrier_ready windowId=${binding.windowId}") },
            onCancelled = { Log.i(LogTag, "phase=parser_barrier_cancelled") },
        )
    private val registration = ChromeMediaShieldParserBarrierBridge.register(::onRequest)
    private var closed = false

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        checkMainThread()
        if (
            !ChromeMediaShieldParserBarrierEventPolicy.shouldReevaluate(
                eventType = event.eventType,
                packageName = event.packageName?.toString(),
            )
        ) {
            return
        }
        admission.onChromeStructuralEvent()
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        admission.close()
        registration.close()
    }

    private fun onRequest(completion: ChromeMediaShieldParserBarrierCompletion) {
        val dispatch = ChromeMediaShieldActiveDocumentDispatchGuard()
        val transportRegistration =
            completion.onTransportCancelled {
                dispatch.cancel()
                service.mainExecutor.execute { cancelPendingOnMain(completion) }
            }
        if (transportRegistration != ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered) return
        service.mainExecutor.execute {
            if (!dispatch.mayDispatch(transportRegistration)) return@execute
            acceptOnMain(completion)
        }
    }

    private fun acceptOnMain(completion: ChromeMediaShieldParserBarrierCompletion) {
        checkMainThread()
        if (closed) {
            completion.reject()
            return
        }
        admission.accept(completion)
    }

    private fun cancelPendingOnMain(completion: ChromeMediaShieldParserBarrierCompletion) {
        checkMainThread()
        admission.onTransportCancelled(completion)
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    private companion object {
        const val LogTag = "GloshH19Ready"
    }
}

/** Window-list changes may carry no package; the exact context reader remains the authority. */
internal object ChromeMediaShieldParserBarrierEventPolicy {
    fun shouldReevaluate(
        eventType: Int,
        packageName: String?,
    ): Boolean =
        eventType in StructuralEventTypes &&
            (packageName == ChromePackageName || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)

    private const val ChromePackageName = "com.android.chrome"
    private val StructuralEventTypes =
        setOf(
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
}
