package com.contentfilter.core.network.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseRelinkCompletionTest {
    @Test
    fun `parses boolean rpc responses`() {
        assertEquals(true, parseRpcBoolean(" true\n"))
        assertEquals(false, parseRpcBoolean("false"))
        assertNull(parseRpcBoolean("{}"))
    }

    @Test
    fun `accepts only a confirmed relink completion`() {
        assertIs<RemoteResult.Success<Unit>>(
            RemoteResult.Success(true).toRelinkCompletionResult(),
        )

        val rejected =
            assertIs<RemoteResult.Failure>(
                RemoteResult.Success(false).toRelinkCompletionResult(),
            )
        assertFalse(rejected.retryable)
    }

    @Test
    fun `preserves remote failures`() {
        val original = RemoteResult.Failure("Sin conexión", retryable = true)
        val result = assertIs<RemoteResult.Failure>(original.toRelinkCompletionResult())

        assertEquals(original.reason, result.reason)
        assertTrue(result.retryable)
    }
}
