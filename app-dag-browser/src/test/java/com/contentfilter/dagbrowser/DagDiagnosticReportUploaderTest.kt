package com.contentfilter.dagbrowser

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagDiagnosticReportUploaderTest {
    @Test
    fun `report is compressed and contains only sanitized flight data`() {
        val events =
            JSONArray().put(
                JSONObject()
                    .put("sequence", 1)
                    .put("wall_ms", 1_786_477_356_000)
                    .put("elapsed_ms", 25)
                    .put("type", "media_decision")
                    .put("candidate", "0123456789abcdef")
                    .put("action", "block")
                    .put("reason", "model_filter"),
            )
        val uploader = DagDiagnosticReportUploader("https://example.test/report", "a".repeat(64))
        try {
            assertTrue(uploader.configured)
            val compressed =
                uploader.compressedReport(
                    DagFlightSnapshot(
                        reportId = "7b037e86-cd41-4b2d-a9c8-874dadc6fdc1",
                        sessionId = "6246dacf-7b43-4d73-a520-64873cc627f6",
                        createdAtMillis = 1_786_477_356_000,
                        events = events,
                        droppedInMemory = 0,
                    ),
                    DagDiagnosticDeviceInfo(
                        packageName = "com.contentfilter.dagbrowser.dev",
                        versionCode = 204,
                        versionName = "0.70.08-dev",
                        sdkInt = 36,
                        manufacturer = "Samsung",
                        model = "SM-S908E",
                    ),
                )
            val decoded =
                GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
            val report = JSONObject(decoded)
            assertEquals(1, report.getInt("schema_version"))
            assertEquals(1, report.getInt("event_count"))
            assertEquals("model_filter", report.getJSONArray("events").getJSONObject(0).getString("reason"))
            assertFalse(decoded.contains("url", ignoreCase = true))
            assertFalse(decoded.contains("cookie", ignoreCase = true))
            assertFalse(decoded.contains("pixels", ignoreCase = true))
        } finally {
            uploader.close()
        }
    }

    @Test
    fun `upload configuration rejects insecure endpoints and short tokens`() {
        val insecure = DagDiagnosticReportUploader("http://example.test", "a".repeat(64))
        val shortToken = DagDiagnosticReportUploader("https://example.test", "short")
        try {
            assertFalse(insecure.configured)
            assertFalse(shortToken.configured)
        } finally {
            insecure.close()
            shortToken.close()
        }
    }
}
