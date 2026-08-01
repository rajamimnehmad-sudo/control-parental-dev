package com.contentfilter.dagbrowser

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DagLifecycleImageAnalyzerTest {
    @Test
    fun `close waits for two active inferences and closes exactly once`() {
        val delegate = BlockingCloseableAnalyzer(expectedAnalyses = 2)
        val analyzer = DagLifecycleImageAnalyzer(delegate)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results =
                List(2) {
                    executor.submit<DagImageAnalysisResult> {
                        analyzer.analyze(preparedImage())
                    }
                }
            assertTrue(delegate.started.await(TestTimeoutSeconds, TimeUnit.SECONDS))
            analyzer.close()
            assertEquals(0, delegate.closeCount.get())

            val rejected = assertIs<DagImageAnalysisResult.Unavailable>(analyzer.analyze(preparedImage()))
            assertEquals(DagLifecycleImageAnalyzer.AnalyzerClosedReason, rejected.reason)

            delegate.release.countDown()
            results.forEach { result ->
                assertEquals(
                    DagImageAnalysisResult.Classified(0.2f),
                    result.get(TestTimeoutSeconds, TimeUnit.SECONDS),
                )
            }
            assertEquals(1, delegate.closeCount.get())

            analyzer.close()
            assertEquals(1, delegate.closeCount.get())
        } finally {
            delegate.release.countDown()
            analyzer.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TestTimeoutSeconds, TimeUnit.SECONDS))
        }
    }

    private fun preparedImage() =
        DagPreparedImage(
            width = DagImageDecodeContract.TargetSize,
            height = DagImageDecodeContract.TargetSize,
            rgb888 = ByteArray(DagImageDecodeContract.PreparedByteCount),
        )

    private class BlockingCloseableAnalyzer(
        expectedAnalyses: Int,
    ) : DagImageAnalyzer,
        Closeable {
        val started = CountDownLatch(expectedAnalyses)
        val release = CountDownLatch(1)
        val closeCount = AtomicInteger(0)

        override fun analyze(image: DagPreparedImage): DagImageAnalysisResult {
            started.countDown()
            release.await()
            return DagImageAnalysisResult.Classified(0.2f)
        }

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private companion object {
        const val TestTimeoutSeconds = 5L
    }
}
