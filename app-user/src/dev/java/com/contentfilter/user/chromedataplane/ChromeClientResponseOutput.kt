package com.contentfilter.user.chromedataplane

import java.io.IOException
import java.io.OutputStream
import java.net.SocketException

internal class ChromeClientResponseDisconnected(cause: SocketException) : IOException(cause)

/** Tags only exceptions emitted by the client's output, never errors reading the upstream body. */
internal class ChromeClientResponseOutput(private val delegate: OutputStream) : OutputStream() {
    override fun write(value: Int) = downstream { delegate.write(value) }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) = downstream {
        delegate.write(
            bytes,
            offset,
            length,
        )
    }

    override fun flush() = downstream { delegate.flush() }

    private inline fun downstream(action: () -> Unit) {
        try {
            action()
        } catch (error: SocketException) {
            throw ChromeClientResponseDisconnected(error)
        }
    }
}
