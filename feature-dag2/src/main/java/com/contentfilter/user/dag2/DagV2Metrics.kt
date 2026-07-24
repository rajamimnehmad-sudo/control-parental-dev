package com.contentfilter.user.dag2

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class DagV2MetricSnapshot(
    val nonImageBypassCount: Int = 0,
    val imageRequestCount: Int = 0,
    val imagePlaceholderCount: Int = 0,
    val imageCancelledCount: Int = 0,
    val serviceWorkerRequestCount: Int = 0,
    val visualPendingCount: Int = 0,
)

@Singleton
class DagV2Metrics
    @Inject
    constructor() {
        private val nonImageBypass = AtomicInteger()
        private val imageRequests = AtomicInteger()
        private val imagePlaceholders = AtomicInteger()
        private val imageCancelled = AtomicInteger()
        private val serviceWorkerRequests = AtomicInteger()
        private val visualPending = AtomicInteger()
        private val activeNavigationToken = AtomicReference<String?>(null)
        private val mutableSnapshot = MutableStateFlow(DagV2MetricSnapshot())

        val snapshot: StateFlow<DagV2MetricSnapshot> = mutableSnapshot.asStateFlow()

        fun event(
            name: String,
            session: DagV2DocumentSessionState? = null,
            value: Int? = null,
        ) {
            val fields =
                buildString {
                    append("event=")
                    append(name)
                    session?.let {
                        append(" session=")
                        append(it.sessionId.take(8))
                        append(" token=")
                        append(it.navigationToken.take(8))
                    }
                    value?.let {
                        append(" value=")
                        append(it)
                    }
                }
            Log.i(LogTag, fields)
        }

        fun nonImageBypass() {
            nonImageBypass.incrementAndGet()
            publish()
        }

        fun beginDocument(session: DagV2DocumentSessionState) {
            activeNavigationToken.set(session.navigationToken)
            visualPending.set(0)
            publish()
        }

        fun imageRequest(
            source: DagV2ResourceSource,
            session: DagV2DocumentSessionState?,
        ) {
            imageRequests.incrementAndGet()
            if (session?.navigationToken == activeNavigationToken.get()) visualPending.incrementAndGet()
            @Suppress("UNUSED_VARIABLE")
            val recordedSource = source
            publish()
        }

        fun serviceWorkerRequest() {
            serviceWorkerRequests.incrementAndGet()
            publish()
        }

        fun imagePlaceholder(session: DagV2DocumentSessionState?) {
            imagePlaceholders.incrementAndGet()
            if (session?.navigationToken == activeNavigationToken.get()) {
                visualPending.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
            publish()
        }

        fun imageCancelled() {
            imageCancelled.incrementAndGet()
            publish()
        }

        fun imageCompleted(session: DagV2DocumentSessionState) {
            if (session.navigationToken == activeNavigationToken.get()) {
                visualPending.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
            }
            publish()
        }

        private fun publish() {
            mutableSnapshot.value =
                DagV2MetricSnapshot(
                    nonImageBypassCount = nonImageBypass.get(),
                    imageRequestCount = imageRequests.get(),
                    imagePlaceholderCount = imagePlaceholders.get(),
                    imageCancelledCount = imageCancelled.get(),
                    serviceWorkerRequestCount = serviceWorkerRequests.get(),
                    visualPendingCount = visualPending.get(),
                )
        }

        private companion object {
            const val LogTag = "DagV2Metrics"
        }
    }
