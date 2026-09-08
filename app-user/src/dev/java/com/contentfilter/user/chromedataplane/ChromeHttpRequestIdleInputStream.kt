package com.contentfilter.user.chromedataplane

import java.io.FilterInputStream
import java.io.InputStream
import java.net.SocketTimeoutException

/** Reclaims a worker only while no request byte has arrived; active messages keep their full timeout. */
internal class ChromeHttpRequestIdleInputStream(
    input: InputStream,
    private val setReadTimeoutMillis: (Int) -> Unit,
    private val idleTimeoutMillis: Int,
    private val activeTimeoutMillis: Int,
) : FilterInputStream(input) {
    private var awaitingFirstByte = false

    fun beginRequest() {
        setReadTimeoutMillis(idleTimeoutMillis)
        awaitingFirstByte = true
    }

    override fun read(): Int = readWithIdleDeadline { `in`.read() }.also { if (it >= 0) requestStarted() }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int = readWithIdleDeadline { `in`.read(bytes, offset, length) }.also { if (it > 0) requestStarted() }

    private inline fun readWithIdleDeadline(read: () -> Int): Int =
        try {
            read()
        } catch (error: SocketTimeoutException) {
            if (awaitingFirstByte) throw ChromeHttpIdleTimeoutException()
            throw error
        }

    private fun requestStarted() {
        if (!awaitingFirstByte) return
        awaitingFirstByte = false
        setReadTimeoutMillis(activeTimeoutMillis)
    }
}
