package com.contentfilter.dagbrowser

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DagFlightRecorderTest {
    @Test
    fun `snapshot contains bounded typed metadata without source material`() {
        val directory = Files.createTempDirectory("dag-flight-test").toFile()
        var wall = 1_000L
        var elapsed = 10L
        val recorder = DagFlightRecorder(directory, { wall++ }, { elapsed++ })
        try {
            recorder.record(
                DagFlightEvent(
                    type = DagFlightEventType.MediaDecision,
                    candidateId = "image_1_secret",
                    carrier = "network",
                    priority = "visible",
                    action = "block",
                    reason = "model_filter",
                    byteCount = 2_048,
                    width = 137,
                    height = 137,
                    score = 0.9453f,
                    queueMillis = 4,
                    nativeMillis = 38,
                ),
            )
            val snapshot = recorder.awaitSnapshot()
            assertEquals(1, snapshot.eventCount)
            val candidate = snapshot.events.getJSONObject(0).getString("candidate")
            assertEquals(16, candidate.length)
            val encoded = snapshot.events.toString()
            assertTrue(encoded.contains("model_filter"))
            assertTrue(encoded.contains(candidate))
            assertFalse(encoded.contains("image_1_secret"))
            assertFalse(encoded.contains("http"))
            assertFalse(encoded.contains("source"))
            assertFalse(encoded.contains("pixels"))
            recorder.record(
                DagFlightEvent(
                    type = DagFlightEventType.MediaDecision,
                    candidateId = "image_2_secret",
                ),
            )
            assertNotEquals(candidate, recorder.awaitSnapshot().events.getJSONObject(1).getString("candidate"))
        } finally {
            recorder.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `unsafe free form values are omitted and clearing preserves no events`() {
        val directory = Files.createTempDirectory("dag-flight-clear-test").toFile()
        val recorder = DagFlightRecorder(directory, { 1L }, { 1L })
        try {
            recorder.record(
                DagFlightEvent(
                    type = DagFlightEventType.MediaDrop,
                    carrier = "https://private.example/image.jpg",
                    reason = "query=user@example.com",
                    count = 1,
                ),
            )
            val before = recorder.awaitSnapshot().events.getJSONObject(0)
            assertFalse(before.has("carrier"))
            assertFalse(before.has("reason"))

            val cleared = CountDownLatch(1)
            var clearResult = false
            recorder.clear {
                clearResult = it
                cleared.countDown()
            }
            assertTrue(cleared.await(2, TimeUnit.SECONDS))
            assertTrue(clearResult)
            assertEquals(0, recorder.awaitSnapshot().eventCount)
        } finally {
            recorder.close()
            directory.deleteRecursively()
        }
    }

    private fun DagFlightRecorder.awaitSnapshot(): DagFlightSnapshot {
        val latch = CountDownLatch(1)
        var result: Result<DagFlightSnapshot>? = null
        snapshot {
            result = it
            latch.countDown()
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        return requireNotNull(result).getOrThrow()
    }
}
