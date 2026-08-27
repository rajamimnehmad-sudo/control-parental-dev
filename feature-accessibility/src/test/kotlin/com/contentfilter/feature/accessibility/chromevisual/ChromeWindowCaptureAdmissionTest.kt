package com.contentfilter.feature.accessibility.chromevisual

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeWindowCaptureAdmissionTest {
    @Test
    fun `first capture invokes platform immediately`() =
        runBlocking {
            val fixture = Fixture()

            assertTrue(fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform))

            assertEquals(1, fixture.platformCalls.get())
            assertEquals(0, fixture.delays.pendingCount())
            assertEquals(listOf(0L), fixture.events.map { it.requestedAtMillis })
        }

    @Test
    fun `same window at plus 100 waits until plus 334`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 100L

            val capture = fixture.captureAsync(this, WindowA)
            val delay = fixture.delays.next()
            assertEquals(234L, delay.millis)
            assertEquals(1, fixture.platformCalls.get())

            fixture.clock.now = 334L
            delay.release.complete(Unit)
            assertTrue(capture.await())
            assertEquals(2, fixture.platformCalls.get())
            assertEquals(listOf(0L, 334L), fixture.events.map { it.requestedAtMillis })
            assertEquals(334L, fixture.events.last().requestedAtMillis - fixture.events.first().requestedAtMillis)
        }

    @Test
    fun `same window at plus 333 does not invoke platform`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 333L

            val capture = fixture.captureAsync(this, WindowA)
            val delay = fixture.delays.next()

            assertEquals(1L, delay.millis)
            assertEquals(1, fixture.platformCalls.get())
            capture.cancelAndJoin()
        }

    @Test
    fun `same window at plus 334 invokes exactly once`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 334L

            assertTrue(fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform))

            assertEquals(2, fixture.platformCalls.get())
        }

    @Test
    fun `cancellation during admission wait makes no platform call`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 100L
            val capture = fixture.captureAsync(this, WindowA)
            fixture.delays.next()

            capture.cancelAndJoin()

            assertEquals(1, fixture.platformCalls.get())
        }

    @Test
    fun `cancellation after platform call preserves timestamp`() =
        runBlocking {
            val fixture = Fixture()
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                fixture.admission.runWhenAdmitted(WindowA) {
                    fixture.callPlatform()
                    throw kotlinx.coroutines.CancellationException("cancel after call")
                }
            }
            fixture.clock.now = 100L

            val capture = fixture.captureAsync(this, WindowA)
            val delay = fixture.delays.next()

            assertEquals(234L, delay.millis)
            assertEquals(1, fixture.platformCalls.get())
            capture.cancelAndJoin()
        }

    @Test
    fun `different windows have independent admission`() =
        runBlocking {
            val fixture = Fixture()

            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.admission.runWhenAdmitted(WindowB, fixture::callPlatform)

            assertEquals(2, fixture.platformCalls.get())
            assertEquals(0, fixture.delays.pendingCount())
        }

    @Test
    fun `only latest of multiple pending requests reaches platform`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 100L
            val older = fixture.captureAsync(this, WindowA)
            val olderDelay = fixture.delays.next()
            val latest = fixture.captureAsync(this, WindowA)
            val latestDelay = fixture.delays.next()

            fixture.clock.now = 334L
            olderDelay.release.complete(Unit)
            assertFalse(older.await())
            latestDelay.release.complete(Unit)
            assertTrue(latest.await())

            assertEquals(2, fixture.platformCalls.get())
        }

    @Test
    fun `completion does not move eligible instant`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 333L

            val capture = fixture.captureAsync(this, WindowA)
            val delay = fixture.delays.next()

            assertEquals(1L, delay.millis)
            capture.cancelAndJoin()
        }

    @Test
    fun `unexpected platform error is one invocation with no retry`() =
        runBlocking {
            val fixture = Fixture()
            var errorCode = 0

            assertTrue(
                fixture.admission.runWhenAdmitted(WindowA) {
                    fixture.callPlatform()
                    errorCode = ErrorTakeScreenshotIntervalTimeShort
                },
            )

            assertEquals(ErrorTakeScreenshotIntervalTimeShort, errorCode)
            assertEquals(1, fixture.platformCalls.get())
            assertEquals(0, fixture.delays.pendingCount())
        }

    @Test
    fun `stop cancellation during wait prevents later platform call`() =
        runBlocking {
            val fixture = Fixture()
            fixture.admission.runWhenAdmitted(WindowA, fixture::callPlatform)
            fixture.clock.now = 100L
            val capture = fixture.captureAsync(this, WindowA)
            val delay = fixture.delays.next()

            capture.cancelAndJoin()
            fixture.clock.now = 334L
            delay.release.complete(Unit)

            assertEquals(1, fixture.platformCalls.get())
        }

    private class Fixture {
        val clock = FakeClock()
        val delays = ManualDelays()
        val platformCalls = AtomicInteger()
        val events = mutableListOf<ChromeWindowCaptureAdmissionEvent>()
        val admission =
            ChromeWindowCaptureAdmission(
                nowMillis = clock::currentMillis,
                awaitMillis = delays::await,
                onPlatformRequest = events::add,
            )

        fun callPlatform() {
            platformCalls.incrementAndGet()
        }

        fun captureAsync(
            scope: CoroutineScope,
            windowId: Int,
        ) = scope.async(start = CoroutineStart.UNDISPATCHED) {
            admission.runWhenAdmitted(windowId, ::callPlatform)
        }
    }

    private class FakeClock {
        var now: Long = 0L

        fun currentMillis(): Long = now
    }

    private class ManualDelays {
        private val requests = Channel<Request>(Channel.UNLIMITED)

        suspend fun await(millis: Long) {
            val request = Request(millis)
            requests.send(request)
            request.release.await()
        }

        suspend fun next(): Request = requests.receive()

        fun pendingCount(): Int = requests.tryReceive().getOrNull()?.let { 1 } ?: 0
    }

    private data class Request(
        val millis: Long,
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private companion object {
        const val WindowA = 101
        const val WindowB = 202
        const val ErrorTakeScreenshotIntervalTimeShort = 3
    }
}
