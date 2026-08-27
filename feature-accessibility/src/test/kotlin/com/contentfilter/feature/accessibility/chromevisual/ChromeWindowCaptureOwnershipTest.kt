package com.contentfilter.feature.accessibility.chromevisual

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeWindowCaptureOwnershipTest {
    @Test
    fun `resume transferred frame cancelled before consumption closes exactly once`() {
        withBlockedDispatcher { scope, releaseDispatcher ->
            val callback = Callback<Resource>()
            val resource = Resource()
            val consumed = AtomicBoolean(false)
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val owned = callback.await()
                    consumed.set(true)
                    owned.close()
                }

            callback.resume(resource, resource)
            assertEquals(0, resource.closeCount.get())
            job.cancel()
            releaseDispatcher()
            runBlocking { job.join() }
            resource.close()

            assertFalse(consumed.get())
            assertEquals(1, resource.closeCount.get())
        }
    }

    @Test
    fun `cancellation before callback closes callback resource exactly once`() {
        withBlockedDispatcher { scope, releaseDispatcher ->
            val callback = Callback<Resource>()
            val resource = Resource()
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    callback.await().close()
                }

            job.cancel()
            callback.resume(resource, resource)
            releaseDispatcher()
            runBlocking { job.join() }
            resource.close()

            assertEquals(1, resource.closeCount.get())
        }
    }

    @Test
    fun `normal consumption transfers ownership to caller`() {
        withBlockedDispatcher { scope, releaseDispatcher ->
            val callback = Callback<Resource>()
            val resource = Resource()
            val consumed = AtomicBoolean(false)
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    callback.await().close()
                    consumed.set(true)
                }

            callback.resume(resource, resource)
            assertEquals(0, resource.closeCount.get())
            releaseDispatcher()
            runBlocking { job.join() }

            assertTrue(consumed.get())
            assertEquals(1, resource.closeCount.get())
        }
    }

    @Test
    fun `failure without frame transfers no resource`() {
        withBlockedDispatcher { scope, releaseDispatcher ->
            val callback = Callback<String>()
            val consumed = AtomicBoolean(false)
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    consumed.set(callback.await() == "failed")
                }

            callback.resume("failed", resource = null)
            releaseDispatcher()
            runBlocking { job.join() }

            assertTrue(consumed.get())
        }
    }

    private fun withBlockedDispatcher(test: (CoroutineScope, releaseDispatcher: () -> Unit) -> Unit) {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val blockerStarted = CountDownLatch(1)
            val unblockDispatcher = CountDownLatch(1)
            dispatcher.dispatch(EmptyCoroutineContext) {
                blockerStarted.countDown()
                unblockDispatcher.await()
            }
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS))
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                test(scope, unblockDispatcher::countDown)
            } finally {
                unblockDispatcher.countDown()
                scope.cancel()
            }
        }
    }

    private class Callback<T> {
        private var continuation: CancellableContinuation<T>? = null

        suspend fun await(): T = suspendCancellableCoroutine { continuation = it }

        fun resume(
            value: T,
            resource: AutoCloseable?,
        ) {
            checkNotNull(continuation).resumeWithOwnedResource(value, resource)
        }
    }

    private class Resource : AutoCloseable {
        val closeCount = AtomicInteger()
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) closeCount.incrementAndGet()
        }
    }
}
