package com.contentfilter.user.chromedataplane

import android.util.Log
import java.io.InputStream
import java.io.OutputStream

internal data class ChromePhotosProxyMetrics(
    val connections: Long = 0,
    val requests: Long = 0,
    val safeDecisions: Long = 0,
    val blockedDecisions: Long = 0,
    val unknownDecisions: Long = 0,
    val passthroughResponses: Long = 0,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val failures: Long = 0,
    val originalBytes: Long = 0,
    val deliveredBytes: Long = 0,
    val streamedResponses: Long = 0,
    val queueRejected: Long = 0,
    val serviceWorkerScriptBlocked: Long = 0,
    val activeConnectionsPeak: Int = 0,
    val latencyP50Millis: Double = 0.0,
    val latencyP95Millis: Double = 0.0,
    val latencyP99Millis: Double = 0.0,
    val upstream: ChromePhotosUpstreamMetrics = ChromePhotosUpstreamMetrics(0, 0, 0),
    val webSemanticsReport: String = "not_run",
    val imageAuthorityReport: String = "not_run",
    val preRenderShieldReport: String = "not_run",
    val mediaShieldReport: String = "not_run",
    val decisionSession: ChromePhotoDecisionSessionMetrics = ChromePhotoDecisionSessionMetrics(),
    val imageAuthority: ChromeImageAuthorityMetrics = ChromeImageAuthorityMetrics(),
    val networkVisualDelivery: ChromeNetworkVisualDeliverySnapshot = ChromeNetworkVisualDeliverySnapshot(),
    val mediaShieldDocuments: ChromeMediaShieldDocumentMetrics = ChromeMediaShieldDocumentMetrics(),
    val mediaShieldReady: ChromeMediaShieldReadyEndpointMetrics = ChromeMediaShieldReadyEndpointMetrics(),
)

internal enum class ChromeHttpConnectionDisposition {
    Continue,
    Close,
}

internal fun consumeChromeConnectHeaders(
    input: InputStream,
    maximumHeaderCount: Int,
    maximumHeaderBytes: Int,
    maximumLineBytes: Int,
) {
    var total = 0
    repeat(maximumHeaderCount) {
        val line = input.readChromeConnectLine(maximumLineBytes) ?: error("Truncated CONNECT headers")
        total += line.length + 2
        check(total <= maximumHeaderBytes) { "CONNECT headers too large" }
        if (line.isEmpty()) return
        check(':' in line && line.firstOrNull()?.isWhitespace() != true) { "Malformed CONNECT header" }
    }
    error("Too many CONNECT headers")
}

internal fun InputStream.readChromeConnectLine(maximumLineBytes: Int): String? {
    val bytes = ArrayList<Byte>()
    var carriageReturn = false
    while (bytes.size <= maximumLineBytes) {
        val value = read()
        if (value < 0 && bytes.isEmpty() && !carriageReturn) return null
        if (value < 0) error("Truncated CONNECT line")
        if (carriageReturn) {
            check(value == '\n'.code) { "CONNECT requires CRLF" }
            return bytes.toByteArray().toString(Charsets.US_ASCII)
        }
        when (value) {
            '\r'.code -> carriageReturn = true
            '\n'.code -> error("Bare LF")
            else -> bytes += value.toByte()
        }
    }
    error("CONNECT line too long")
}

internal fun writeChromeProxyPlainError(
    output: OutputStream,
    code: Int,
    message: String,
) {
    val body = message.toByteArray(Charsets.UTF_8)
    ChromeHttp1Wire.writeAscii(
        output,
        "HTTP/1.1 $code Error\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n",
    )
    output.write(body)
    output.flush()
}

internal fun logProxyLifecycle(
    message: String,
    error: Throwable?,
) {
    if (error == null) Log.i("ChromePhotosDataPlane", message) else Log.e("ChromePhotosDataPlane", message, error)
}

internal data class ChromeProxyLatencySnapshot(
    val p50: Double,
    val p95: Double,
    val p99: Double,
)

internal class ChromeProxyLatencyWindow(
    private val capacity: Int,
) {
    private val samples = ArrayDeque<Long>(capacity)

    @Synchronized
    fun add(nanos: Long) {
        if (samples.size == capacity) samples.removeFirst()
        samples.addLast(nanos)
    }

    @Synchronized
    fun snapshot(): ChromeProxyLatencySnapshot {
        if (samples.isEmpty()) return ChromeProxyLatencySnapshot(0.0, 0.0, 0.0)
        val sorted = samples.sorted()

        fun percentile(value: Double): Double {
            val index = ((sorted.size - 1) * value).toInt().coerceIn(sorted.indices)
            return sorted[index] / 1_000_000.0
        }
        return ChromeProxyLatencySnapshot(percentile(0.50), percentile(0.95), percentile(0.99))
    }
}
