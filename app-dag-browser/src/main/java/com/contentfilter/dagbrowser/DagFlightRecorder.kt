package com.contentfilter.dagbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal enum class DagFlightEventType(val wireValue: String) {
    AppStarted("app_started"),
    NavigationStarted("navigation_started"),
    BarrierReady("barrier_ready"),
    DocumentSanitized("document_sanitized"),
    ViewportReady("viewport_ready"),
    PageVisible("page_visible"),
    BarrierTimeout("barrier_timeout"),
    MediaDecision("media_decision"),
    MediaDrop("media_drop"),
    MediaResource("media_resource"),
    MediaElement("media_element"),
}

internal data class DagFlightEvent(
    val type: DagFlightEventType,
    val tabId: Long? = null,
    val candidateId: String? = null,
    val carrier: String? = null,
    val priority: String? = null,
    val action: String? = null,
    val reason: String? = null,
    val basis: String? = null,
    val byteCount: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val score: Float? = null,
    val fullScore: Float? = null,
    val bridgeMillis: Long? = null,
    val queueMillis: Long? = null,
    val nativeMillis: Long? = null,
    val inferenceMillis: Double? = null,
    val inferenceCount: Int? = null,
    val count: Int? = null,
    val pageUrl: String? = null,
    val resourceUrl: String? = null,
    val requestId: String? = null,
    val resourceType: String? = null,
    val sourceKind: String? = null,
    val sourceInstance: String? = null,
    val mimeType: String? = null,
    val frameId: Int? = null,
    val statusCode: Int? = null,
    val fromCache: Boolean? = null,
    val decisionCacheHit: Boolean? = null,
    val activeStreams: Int? = null,
    val queuedAnalyses: Int? = null,
    val capturedBytes: Int? = null,
    val naturalWidth: Int? = null,
    val naturalHeight: Int? = null,
    val renderedWidth: Int? = null,
    val renderedHeight: Int? = null,
    val visualState: String? = null,
)

private data class DagPendingFlightEvent(
    val sequence: Long,
    val wallMillis: Long,
    val elapsedMillis: Long,
    val event: DagFlightEvent,
)

internal data class DagFlightSnapshot(
    val reportId: String,
    val sessionId: String,
    val createdAtMillis: Long,
    val events: JSONArray,
    val droppedInMemory: Long,
) {
    val eventCount: Int get() = events.length()
}

/**
 * Bounded, local-first diagnostic journal. Page/resource locations are reduced to origin, path and
 * query-key names; query values, user info, fragments, page text, media bytes, pixels, headers and
 * credentials are never persisted. Disk work is serialized away from UI and media-analysis
 * threads.
 */
internal class DagFlightRecorder private constructor(
    private val directory: File,
    private val enabled: Boolean,
    private val wallClockMillis: () -> Long,
    private val elapsedRealtimeMillis: () -> Long,
    private val io: ScheduledExecutorService,
) : AutoCloseable {
    constructor(
        context: Context,
        enabled: Boolean = true,
    ) : this(
        directory = File(context.filesDir, DirectoryName),
        enabled = enabled,
        wallClockMillis = System::currentTimeMillis,
        elapsedRealtimeMillis = android.os.SystemClock::elapsedRealtime,
        io =
            Executors.newSingleThreadScheduledExecutor { work ->
                Thread(work, "dag-flight-recorder").apply {
                    priority = Thread.MIN_PRIORITY
                    isDaemon = true
                }
            },
    )

    internal constructor(
        directory: File,
        wallClockMillis: () -> Long,
        elapsedRealtimeMillis: () -> Long,
    ) : this(
        directory = directory,
        enabled = true,
        wallClockMillis = wallClockMillis,
        elapsedRealtimeMillis = elapsedRealtimeMillis,
        io = Executors.newSingleThreadScheduledExecutor(),
    )

    private val sessionId = UUID.randomUUID().toString()
    private val sessionSalt = UUID.randomUUID().toString()
    private val sequence = AtomicLong(0L)
    private val droppedInMemory = AtomicLong(0L)
    private val closed = AtomicBoolean(false)
    private val pending = ArrayDeque<DagPendingFlightEvent>()
    private val pendingLock = Any()
    private val currentFile get() = File(directory, CurrentFileName)
    private val previousFile get() = File(directory, PreviousFileName)

    init {
        if (enabled) {
            directory.mkdirs()
            io.scheduleWithFixedDelay(::flushSafely, FlushIntervalSeconds, FlushIntervalSeconds, TimeUnit.SECONDS)
        }
    }

    fun record(event: DagFlightEvent) {
        if (!enabled || closed.get()) return
        val pendingEvent =
            DagPendingFlightEvent(
                sequence = sequence.incrementAndGet(),
                wallMillis = wallClockMillis(),
                elapsedMillis = elapsedRealtimeMillis(),
                event = event,
            )
        var flushNow = false
        synchronized(pendingLock) {
            if (pending.size >= MaxPendingEvents) {
                pending.removeFirst()
                droppedInMemory.incrementAndGet()
            }
            pending.addLast(pendingEvent)
            flushNow = pending.size >= FlushBatchSize
        }
        if (flushNow) io.execute(::flushSafely)
    }

    fun snapshot(callback: (Result<DagFlightSnapshot>) -> Unit) {
        if (!enabled || closed.get()) {
            callback(Result.failure(IllegalStateException("Diagnostic recorder unavailable")))
            return
        }
        io.execute {
            val result =
                runCatching {
                    flushPending()
                    val lines =
                        buildList {
                            if (previousFile.isFile) addAll(previousFile.readLines())
                            if (currentFile.isFile) addAll(currentFile.readLines())
                        }.takeLast(MaxReportEvents)
                    val events = JSONArray()
                    lines.forEach { line -> runCatching { events.put(JSONObject(line)) } }
                    DagFlightSnapshot(
                        reportId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        createdAtMillis = wallClockMillis(),
                        events = events,
                        droppedInMemory = droppedInMemory.get(),
                    )
                }
            callback(result)
        }
    }

    fun clear(callback: (Boolean) -> Unit = {}) {
        if (!enabled || closed.get()) {
            callback(false)
            return
        }
        io.execute {
            synchronized(pendingLock) { pending.clear() }
            droppedInMemory.set(0L)
            callback(
                runCatching { currentFile.delete() || !currentFile.exists() }.getOrDefault(false) &&
                    runCatching { previousFile.delete() || !previousFile.exists() }.getOrDefault(false),
            )
        }
    }

    fun flush() {
        if (enabled && !closed.get()) io.execute(::flushSafely)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        io.execute(::flushSafely)
        io.shutdown()
    }

    private fun encode(pendingEvent: DagPendingFlightEvent): JSONObject {
        val event = pendingEvent.event
        return JSONObject()
            .put("sequence", pendingEvent.sequence)
            .put("wall_ms", pendingEvent.wallMillis)
            .put("elapsed_ms", pendingEvent.elapsedMillis)
            .put("type", event.type.wireValue)
            .put("event_schema", EventSchemaVersion)
            .put("app_version_code", BuildConfig.VERSION_CODE)
            .apply {
                event.tabId?.let { put("tab", it.coerceAtLeast(0L)) }
                event.candidateId?.takeIf(CandidateIdPattern::matches)?.let { put("candidate", candidateToken(it)) }
                event.carrier?.takeIf(SafeValuePattern::matches)?.let { put("carrier", it) }
                event.priority?.takeIf(SafeValuePattern::matches)?.let { put("priority", it) }
                event.action?.takeIf(SafeValuePattern::matches)?.let { put("action", it) }
                event.reason?.takeIf(SafeValuePattern::matches)?.let { put("reason", it) }
                event.basis?.takeIf(SafeValuePattern::matches)?.let { put("basis", it) }
                event.byteCount?.let { put("bytes", it.coerceIn(0, MaxRecordedBytes)) }
                event.width?.let { put("width", it.coerceIn(0, MaxRecordedDimension)) }
                event.height?.let { put("height", it.coerceIn(0, MaxRecordedDimension)) }
                event.score?.takeIf(Float::isFinite)?.let { put("score", it.coerceIn(0f, 1f).toDouble()) }
                event.fullScore?.takeIf(Float::isFinite)?.let { put("full_score", it.coerceIn(0f, 1f).toDouble()) }
                event.bridgeMillis?.let { put("bridge_ms", it.coerceIn(-1L, MaxRecordedMillis)) }
                event.queueMillis?.let { put("queue_ms", it.coerceIn(0L, MaxRecordedMillis)) }
                event.nativeMillis?.let { put("native_ms", it.coerceIn(0L, MaxRecordedMillis)) }
                event.inferenceMillis?.takeIf(Double::isFinite)?.let {
                    put("inference_ms", it.coerceIn(0.0, MaxRecordedMillis.toDouble()))
                }
                event.inferenceCount?.let { put("inferences", it.coerceIn(0, MaxRecordedInferences)) }
                event.count?.let { put("count", it.coerceIn(1, MaxRecordedCount)) }
                event.pageUrl?.let { raw ->
                    sanitizedUrl(raw)?.let { put("page_url", it) }
                    put("page", opaqueToken(raw.take(MaxRawUrlChars)))
                }
                event.resourceUrl?.let { raw ->
                    sanitizedUrl(raw)?.let { put("resource_url", it) }
                    put("resource", opaqueToken(raw.take(MaxRawUrlChars)))
                }
                event.requestId?.takeIf(RequestIdPattern::matches)?.let { put("request", opaqueToken(it)) }
                event.resourceType?.takeIf(SafeValuePattern::matches)?.let { put("resource_type", it) }
                event.sourceKind?.takeIf(SafeValuePattern::matches)?.let { put("source_kind", it) }
                event.sourceInstance?.takeIf(SafeValuePattern::matches)?.let { put("source_instance", it) }
                event.mimeType?.let(::sanitizedMimeType)?.let { put("mime", it) }
                event.frameId?.let { put("frame", it.coerceIn(0, MaxRecordedFrame)) }
                event.statusCode?.let { put("status", it.coerceIn(0, 999)) }
                event.fromCache?.let { put("from_cache", it) }
                event.decisionCacheHit?.let { put("decision_cache", it) }
                event.activeStreams?.let { put("active_streams", it.coerceIn(0, MaxRecordedCount)) }
                event.queuedAnalyses?.let { put("queued_analyses", it.coerceIn(0, MaxRecordedCount)) }
                event.capturedBytes?.let { put("captured_bytes", it.coerceIn(0, MaxRecordedBytes)) }
                event.naturalWidth?.let { put("natural_width", it.coerceIn(0, MaxRecordedDimension)) }
                event.naturalHeight?.let { put("natural_height", it.coerceIn(0, MaxRecordedDimension)) }
                event.renderedWidth?.let { put("rendered_width", it.coerceIn(0, MaxRecordedDimension)) }
                event.renderedHeight?.let { put("rendered_height", it.coerceIn(0, MaxRecordedDimension)) }
                event.visualState?.takeIf(SafeValuePattern::matches)?.let { put("visual_state", it) }
            }
    }

    private fun candidateToken(candidateId: String): String {
        return opaqueToken(candidateId)
    }

    private fun opaqueToken(value: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest("$sessionSalt:$value".toByteArray(Charsets.UTF_8))
        return digest.take(CandidateTokenBytes).joinToString("") { "%02x".format(it) }
    }

    private fun sanitizedUrl(value: String): String? {
        val trimmed = value.trim().take(MaxRawUrlChars)
        if (trimmed.startsWith("data:", ignoreCase = true)) return "data:image"
        if (trimmed.startsWith("blob:", ignoreCase = true)) return "blob"
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val host = uri.host?.lowercase()?.takeIf(HostPattern::matches) ?: return null
        val port = uri.port.takeIf { it in 1..65535 }?.let { ":$it" }.orEmpty()
        val path =
            uri.rawPath.orEmpty()
                .take(MaxRecordedUrlChars / 2)
                .replace(UnsafeUrlCharacterPattern, "")
                .ifBlank { "/" }
        val queryKeys =
            uri.rawQuery.orEmpty()
                .split('&')
                .asSequence()
                .map { it.substringBefore('=').take(MaxQueryKeyChars) }
                .filter(QueryKeyPattern::matches)
                .distinct()
                .take(MaxQueryKeys)
                .toList()
        val query = queryKeys.takeIf(List<String>::isNotEmpty)?.joinToString("&", prefix = "?").orEmpty()
        return "$scheme://$host$port$path$query".take(MaxRecordedUrlChars)
    }

    private fun sanitizedMimeType(value: String): String? =
        value.substringBefore(';').trim().lowercase().takeIf(MimeTypePattern::matches)

    private fun flushSafely() {
        runCatching { flushPending() }
    }

    private fun flushPending() {
        val batch =
            synchronized(pendingLock) {
                if (pending.isEmpty()) return
                buildList(pending.size) {
                    while (pending.isNotEmpty()) add(pending.removeFirst())
                }
            }
        directory.mkdirs()
        val encoded = batch.map { encode(it).toString() }
        rotateIfNeeded(encoded.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 })
        currentFile.appendText(encoded.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun rotateIfNeeded(incomingBytes: Int) {
        if (!currentFile.exists() || currentFile.length() + incomingBytes <= MaxFileBytes) return
        if (previousFile.exists()) previousFile.delete()
        if (!currentFile.renameTo(previousFile)) currentFile.delete()
    }

    private companion object {
        const val DirectoryName = "dag-diagnostics"
        const val CurrentFileName = "flight-current.jsonl"
        const val PreviousFileName = "flight-previous.jsonl"
        const val FlushIntervalSeconds = 2L
        const val FlushBatchSize = 32
        const val MaxPendingEvents = 512
        const val MaxReportEvents = 4_096
        const val MaxFileBytes = 512L * 1024L
        const val CandidateTokenBytes = 8
        const val EventSchemaVersion = 2
        const val MaxRecordedBytes = 8 * 1024 * 1024
        const val MaxRecordedDimension = 16_384
        const val MaxRecordedMillis = 60_000L
        const val MaxRecordedInferences = 8
        const val MaxRecordedCount = 100_000
        const val MaxRecordedFrame = 10_000
        const val MaxRawUrlChars = 4_096
        const val MaxRecordedUrlChars = 768
        const val MaxQueryKeyChars = 48
        const val MaxQueryKeys = 16
        val CandidateIdPattern = Regex("^[A-Za-z0-9_-]{1,80}$")
        val RequestIdPattern = Regex("^[A-Za-z0-9_.:-]{1,160}$")
        val SafeValuePattern = Regex("^[a-z0-9_]{1,48}$")
        val HostPattern = Regex("^[A-Za-z0-9.-]{1,253}$")
        val QueryKeyPattern = Regex("^[A-Za-z0-9_.%~-]{1,48}$")
        val MimeTypePattern = Regex("^[a-z0-9.+-]{1,64}/[a-z0-9.+-]{1,64}$")
        val UnsafeUrlCharacterPattern = Regex("[\\p{Cc}\\p{Cf}\\s]")
    }
}
