package com.contentfilter.dagbrowser

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

internal data class DagDiagnosticDeviceInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)

internal data class DagDiagnosticUploadReceipt(
    val reportCode: String,
    val expiresAt: String,
)

internal class DagDiagnosticReportUploader(
    private val endpoint: String,
    private val uploadToken: String,
    private val io: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    val configured: Boolean
        get() = endpoint.startsWith("https://") && uploadToken.length >= MinimumTokenLength

    fun upload(
        snapshot: DagFlightSnapshot,
        device: DagDiagnosticDeviceInfo,
        callback: (Result<DagDiagnosticUploadReceipt>) -> Unit,
    ) {
        if (!configured) {
            callback(Result.failure(IllegalStateException("Diagnostic upload unavailable")))
            return
        }
        io.execute {
            callback(runCatching { uploadBlocking(snapshot, device) })
        }
    }

    private fun uploadBlocking(
        snapshot: DagFlightSnapshot,
        device: DagDiagnosticDeviceInfo,
    ): DagDiagnosticUploadReceipt {
        val compressed = compressedReport(snapshot, device)
        require(compressed.size <= MaxCompressedReportBytes) { "Diagnostic report is too large" }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Content-Encoding", "gzip")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-DAG-Diagnostic-Token", uploadToken)
            connection.setFixedLengthStreamingMode(compressed.size)
            connection.outputStream.use { it.write(compressed) }
            val status = connection.responseCode
            val body =
                (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(MaxResponseChars) }
                    .orEmpty()
            if (status !in 200..299) error("Diagnostic upload failed ($status)")
            val json = JSONObject(body)
            val reportCode = json.optString("report_code")
            val expiresAt = json.optString("expires_at")
            require(ReportCodePattern.matches(reportCode) && expiresAt.isNotBlank()) {
                "Invalid diagnostic receipt"
            }
            return DagDiagnosticUploadReceipt(reportCode, expiresAt)
        } finally {
            connection.disconnect()
            compressed.fill(0)
        }
    }

    internal fun compressedReport(
        snapshot: DagFlightSnapshot,
        device: DagDiagnosticDeviceInfo,
    ): ByteArray {
        val report =
            JSONObject()
                .put("schema_version", SchemaVersion)
                .put("report_id", snapshot.reportId)
                .put("session_id", snapshot.sessionId)
                .put("created_at_ms", snapshot.createdAtMillis)
                .put("event_count", snapshot.eventCount)
                .put("dropped_in_memory", snapshot.droppedInMemory)
                .put(
                    "app",
                    JSONObject()
                        .put("package", device.packageName.take(MaxMetadataLength))
                        .put("version_code", device.versionCode)
                        .put("version_name", device.versionName.take(MaxMetadataLength)),
                )
                .put(
                    "device",
                    JSONObject()
                        .put("sdk", device.sdkInt)
                        .put("manufacturer", device.manufacturer.take(MaxMetadataLength))
                        .put("model", device.model.take(MaxMetadataLength)),
                )
                .put("events", snapshot.events)
        return gzip(report.toString().toByteArray(Charsets.UTF_8))
    }

    override fun close() {
        io.shutdownNow()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        bytes.fill(0)
        return output.toByteArray()
    }

    private companion object {
        const val SchemaVersion = 1
        const val MinimumTokenLength = 32
        const val MaxMetadataLength = 80
        const val MaxCompressedReportBytes = 256 * 1024
        const val MaxResponseChars = 4_096
        const val ConnectTimeoutMillis = 10_000
        const val ReadTimeoutMillis = 20_000
        val ReportCodePattern = Regex("^DAG-[A-Z0-9]{8}$")
    }
}
