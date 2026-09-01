package com.contentfilter.user.chromedataplane

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromePhotoDecisionSessionTest {
    @Test
    fun `benchmark config accepts only the bounded DEV matrix`() {
        assertEquals(64, ChromePhotoDecisionBenchmarkConfig().maximumCacheEntries)
        assertEquals(1, ChromePhotoDecisionBenchmarkConfig().maximumConcurrentInferences)
        assertEquals(2, ChromePhotoDecisionBenchmarkConfig().maximumQueueEntries)
        assertEquals(5_000L, ChromePhotoDecisionBenchmarkConfig().timeoutMillis)
        assertEquals(256, ChromePhotoDecisionBenchmarkConfig(256, 2).maximumCacheEntries)

        assertFailsWith<IllegalArgumentException> { ChromePhotoDecisionBenchmarkConfig(65, 1) }
        assertFailsWith<IllegalArgumentException> { ChromePhotoDecisionBenchmarkConfig(64, 3) }
    }

    @Test
    fun `benchmark variants preserve decisions for the same corpus`() {
        val expected =
            listOf(
                ChromePhotoDecision.Safe,
                ChromePhotoDecision.Block,
                ChromePhotoDecision.Unknown,
                ChromePhotoDecision.Safe,
            )
        val variants =
            listOf(
                ChromePhotoDecisionBenchmarkConfig(64, 1),
                ChromePhotoDecisionBenchmarkConfig(256, 1),
                ChromePhotoDecisionBenchmarkConfig(256, 2),
            )

        variants.forEach { config ->
            val decisions = expected.iterator()
            val engine = FakeEngine { ChromePhotoDecisionResult(decisions.next(), "fixed_corpus") }
            val session =
                ChromePhotosBoundedDecisionSession(
                    engine = engine,
                    maximumCacheEntries = config.maximumCacheEntries,
                    maximumQueueEntries = config.maximumQueueEntries,
                    maximumConcurrentInferences = config.maximumConcurrentInferences,
                    timeoutMillis = config.timeoutMillis,
                )

            val actual =
                expected.indices.map { index ->
                    val bytes = "corpus-$index".toByteArray()
                    session.decide(sha256(bytes), bytes, "image/png").decision
                }

            assertEquals(expected, actual)
            session.close()
        }
    }

    @Test
    fun `cache hit avoids a second inference and session identity scopes entries`() {
        val engine = FakeEngine { safeResult() }
        val session = session(engine)
        val bytes = "same-image".toByteArray()

        val first = session.decide(sha256(bytes), bytes, "image/png")
        val second = session.decide(sha256(bytes), bytes.copyOf(), "image/png")
        engine.identityVersion = "R3.1-other-policy"
        val afterIdentityChange = session.decide(sha256(bytes), bytes, "image/png")

        assertEquals(ChromePhotoDecisionSource.Engine, first.source)
        assertEquals(ChromePhotoDecisionSource.Cache, second.source)
        assertEquals(ChromePhotoDecisionSource.Engine, afterIdentityChange.source)
        assertEquals(2, engine.calls.get())
        assertEquals(2, session.cacheSize())
        assertEquals(1, session.metrics().cacheHits)
    }

    @Test
    fun `same content hash with different canonical MIME never shares cache`() {
        val engine = FakeEngine { safeResult() }
        val session = session(engine)
        val bytes = "same-image".toByteArray()

        val png = session.decide(sha256(bytes), bytes, "image/png")
        val jpeg = session.decide(sha256(bytes), bytes, "image/jpeg")
        val pngAgain = session.decide(sha256(bytes), bytes, "image/png; charset=binary")

        assertEquals(ChromePhotoDecisionSource.Engine, png.source)
        assertEquals(ChromePhotoDecisionSource.Engine, jpeg.source)
        assertEquals(ChromePhotoDecisionSource.Cache, pngAgain.source)
        assertEquals(2, engine.calls.get())
        assertEquals(2, session.cacheSize())
        session.close()
    }

    @Test
    fun `simultaneous same hash requests deduplicate in flight inference`() {
        val inferenceEntered = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val engine =
            FakeEngine {
                inferenceEntered.countDown()
                releaseInference.await()
                safeResult()
            }
        val session = session(engine, timeoutMillis = 2_000)
        val callers = Executors.newFixedThreadPool(2)
        val bytes = "dedupe".toByteArray()

        val first = callers.submit(Callable { session.decide(sha256(bytes), bytes, "image/png") })
        assertTrue(inferenceEntered.await(1, TimeUnit.SECONDS))
        val second = callers.submit(Callable { session.decide(sha256(bytes), bytes, "image/png") })
        assertTrue(awaitCondition { session.metrics().dedupeHits == 1L })
        releaseInference.countDown()

        assertEquals(ChromePhotoDecision.Safe, first.get(1, TimeUnit.SECONDS).decision)
        assertEquals(ChromePhotoDecisionSource.InFlight, second.get(1, TimeUnit.SECONDS).source)
        assertEquals(1, engine.calls.get())
        assertEquals(1, session.metrics().engineCalls)
        callers.shutdownNow()
        session.close()
    }

    @Test
    fun `bounded queue rejects excess work fail closed`() {
        val inferenceEntered = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val engine =
            FakeEngine {
                inferenceEntered.countDown()
                releaseInference.await()
                safeResult()
            }
        val session = session(engine, maximumQueueEntries = 1, timeoutMillis = 2_000)
        val callers = Executors.newFixedThreadPool(2)
        val firstBytes = "first".toByteArray()
        val secondBytes = "second".toByteArray()
        val rejectedBytes = "rejected".toByteArray()

        val first = callers.submit(Callable { session.decide(sha256(firstBytes), firstBytes, "image/png") })
        assertTrue(inferenceEntered.await(1, TimeUnit.SECONDS))
        val second = callers.submit(Callable { session.decide(sha256(secondBytes), secondBytes, "image/png") })
        assertTrue(awaitCondition { session.metrics().queuePeak == 1 })
        val rejected = session.decide(sha256(rejectedBytes), rejectedBytes, "image/png")
        releaseInference.countDown()

        assertEquals(ChromePhotoDecision.Unknown, rejected.decision)
        assertEquals(ChromePhotoDecisionSource.QueueFull, rejected.source)
        assertEquals(1, session.metrics().queueRejects)
        assertEquals(ChromePhotoDecision.Safe, first.get(1, TimeUnit.SECONDS).decision)
        assertEquals(ChromePhotoDecision.Safe, second.get(1, TimeUnit.SECONDS).decision)
        assertEquals(1, session.metrics().inferencePeak)
        callers.shutdownNow()
        session.close()
    }

    @Test
    fun `timeout cancels delivery and never caches late SAFE`() {
        val inferenceEntered = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val engine =
            FakeEngine {
                inferenceEntered.countDown()
                runCatching { releaseInference.await() }
                safeResult()
            }
        val session = session(engine, timeoutMillis = 30)
        val bytes = "slow".toByteArray()

        val result = session.decide(sha256(bytes), bytes, "image/png")
        releaseInference.countDown()
        assertTrue(inferenceEntered.await(1, TimeUnit.SECONDS))

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals(ChromePhotoDecisionSource.Timeout, result.source)
        assertEquals(1, session.metrics().timeouts)
        assertEquals(0, session.cacheSize())
        session.close()
    }

    @Test
    fun `engine exception and closed session remain fail closed`() {
        val engine = FakeEngine { error("boom") }
        val session = session(engine)
        val bytes = "error".toByteArray()

        val failed = session.decide(sha256(bytes), bytes, "image/png")
        session.close()
        session.close()
        val afterClose = session.decide(sha256(bytes), bytes, "image/png")

        assertEquals(ChromePhotoDecision.Unknown, failed.decision)
        assertEquals(ChromePhotoDecisionSource.Error, failed.source)
        assertEquals(ChromePhotoDecision.Unknown, afterClose.decision)
        assertEquals(1, engine.closeCalls.get())
        assertEquals(0, session.cacheSize())
    }

    @Test
    fun `systemic engine loss is reported once while every image stays UNKNOWN`() {
        val failures = mutableListOf<String>()
        val engine =
            FakeEngine {
                ChromePhotoDecisionResult(
                    ChromePhotoDecision.Unknown,
                    "analyzer_unavailable",
                    ChromePhotoDecisionSource.Unavailable,
                )
            }.apply { healthy = false }
        val session =
            ChromePhotosBoundedDecisionSession(
                engine = engine,
                onSystemicFailure = failures::add,
            )

        repeat(2) { index ->
            val bytes = "systemic-$index".toByteArray()
            assertEquals(
                ChromePhotoDecision.Unknown,
                session.decide(sha256(bytes), bytes, "image/png").decision,
            )
        }

        assertEquals(listOf("analyzer_unavailable"), failures)
        session.close()
    }

    @Test
    fun `bounded LRU cache and clear do not retain image bytes`() {
        val engine = FakeEngine { safeResult() }
        val session = session(engine, maximumCacheEntries = 2)
        listOf("one", "two", "three").forEach { value ->
            val bytes = value.toByteArray()
            session.decide(sha256(bytes), bytes, "image/png")
        }

        assertEquals(2, session.cacheSize())
        assertEquals(1, session.metrics().cacheEvictions)
        session.clear()
        assertEquals(0, session.cacheSize())
        assertEquals(0, session.metrics().inFlightEntries)
        session.close()
    }

    @Test
    fun `engine metrics report preprocessing prepared image bins and decision basis`() {
        val engine =
            FakeEngine {
                safeResult().copy(
                    basis = "RegionalConsensus",
                    preparedImageCount = 5,
                    timings =
                        ChromePhotoDecisionTimings(
                            decodeAndPreprocessMs = 7.0,
                            inferenceMs = 12.0,
                            totalLocalMs = 20.0,
                        ),
                )
            }
        val session = session(engine)
        val bytes = "metrics".toByteArray()

        session.decide(sha256(bytes), bytes, "image/png")
        session.decide(sha256(bytes), bytes, "image/png")
        val metrics = session.metrics()

        assertEquals(7.0, metrics.preprocessP50Ms)
        assertEquals(7.0, metrics.preprocessP95Ms)
        assertEquals(1, metrics.preparedImageCount5)
        assertEquals(0, metrics.preparedImageCountOther)
        assertEquals(mapOf("RegionalConsensus" to 1L), metrics.decisionBasisCounts)
        assertEquals(0.5, metrics.engineCallsPerRequest)
        session.close()
    }

    @Test
    fun `concurrency two runs two distinct decisions without changing verdicts`() {
        val bothEntered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val engine =
            FakeEngine {
                bothEntered.countDown()
                release.await()
                safeResult()
            }
        val session = session(engine, maximumConcurrentInferences = 2, timeoutMillis = 2_000)
        val callers = Executors.newFixedThreadPool(2)
        val firstBytes = "parallel-one".toByteArray()
        val secondBytes = "parallel-two".toByteArray()

        val first = callers.submit(Callable { session.decide(sha256(firstBytes), firstBytes, "image/png") })
        val second = callers.submit(Callable { session.decide(sha256(secondBytes), secondBytes, "image/png") })
        assertTrue(bothEntered.await(1, TimeUnit.SECONDS))
        release.countDown()

        assertEquals(ChromePhotoDecision.Safe, first.get(1, TimeUnit.SECONDS).decision)
        assertEquals(ChromePhotoDecision.Safe, second.get(1, TimeUnit.SECONDS).decision)
        assertEquals(2, session.metrics().inferencePeak)
        callers.shutdownNow()
        session.close()
    }

    @Test
    fun `clear invalidates in flight generation and late SAFE cannot escape or repopulate cache`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine =
            FakeEngine {
                entered.countDown()
                runCatching { release.await() }
                safeResult()
            }
        val session = session(engine, timeoutMillis = 2_000)
        val caller = Executors.newSingleThreadExecutor()
        val bytes = "generation".toByteArray()
        val pending = caller.submit(Callable { session.decide(sha256(bytes), bytes, "image/png") })
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        session.clear()
        release.countDown()
        val result = pending.get(1, TimeUnit.SECONDS)

        assertEquals(ChromePhotoDecision.Unknown, result.decision)
        assertEquals(0, session.cacheSize())
        assertEquals(0, session.metrics().inFlightEntries)
        caller.shutdownNow()
        session.close()
    }

    @Test
    fun `transformer only returns original bytes for SAFE engine decision`() {
        val original = "original".toByteArray()
        val placeholder = "placeholder".toByteArray()
        val safe = transformerWith(ChromePhotoDecision.Safe, placeholder).transform("image/png", original)
        val block = transformerWith(ChromePhotoDecision.Block, placeholder).transform("image/jpeg", original)
        val unknown = transformerWith(ChromePhotoDecision.Unknown, placeholder).transform("image/webp", original)

        assertContentEquals(original, safe.bytes)
        assertContentEquals(placeholder, block.bytes)
        assertContentEquals(placeholder, unknown.bytes)
        assertFalse(block.bytes.contentEquals(original))
        assertFalse(unknown.bytes.contentEquals(original))
    }

    private fun transformerWith(
        decision: ChromePhotoDecision,
        placeholder: ByteArray,
    ): ChromePhotosResourceTransformer {
        val engine = FakeEngine { ChromePhotoDecisionResult(decision, "test") }
        return ChromePhotosResourceTransformer.forDecisionSession(session(engine), placeholder)
    }

    private fun session(
        engine: ChromePhotoDecisionEngine,
        maximumCacheEntries: Int = 8,
        maximumQueueEntries: Int = 2,
        maximumConcurrentInferences: Int = 1,
        timeoutMillis: Long = 500,
    ) = ChromePhotosBoundedDecisionSession(
        engine = engine,
        maximumCacheEntries = maximumCacheEntries,
        maximumQueueEntries = maximumQueueEntries,
        maximumConcurrentInferences = maximumConcurrentInferences,
        timeoutMillis = timeoutMillis,
    )

    private fun safeResult() =
        ChromePhotoDecisionResult(
            decision = ChromePhotoDecision.Safe,
            reason = "model_allow",
            timings = ChromePhotoDecisionTimings(inferenceMs = 12.0, totalLocalMs = 15.0),
        )

    private fun awaitCondition(condition: () -> Boolean): Boolean {
        repeat(10_000) {
            if (condition()) return true
            Thread.yield()
        }
        return false
    }

    private class FakeEngine(
        private val decision: () -> ChromePhotoDecisionResult,
    ) : ChromePhotoDecisionEngine {
        var identityVersion = "R3.1"
        var healthy = true
        override val identity: ChromePhotoDecisionIdentity
            get() = ChromePhotoDecisionIdentity("fake", identityVersion, "a".repeat(64), "policy")
        val calls = AtomicInteger()
        val closeCalls = AtomicInteger()

        override fun decide(
            imageBytes: ByteArray,
            mimeType: String,
        ): ChromePhotoDecisionResult {
            calls.incrementAndGet()
            return decision()
        }

        override fun close() {
            closeCalls.incrementAndGet()
        }

        override fun isHealthy(): Boolean = healthy
    }
}
