package com.contentfilter.user.chromedataplane

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal data class ChromeStreamResult(
    val bytesWritten: Long,
    val chunked: Boolean,
)

internal class ChromeHttpResponseIntegrityException(
    val declaredLength: Long,
    val bytesWritten: Long,
    val additionalBodyByteObserved: Boolean,
) : IOException(
        if (additionalBodyByteObserved) {
            "Response body exceeded declared length $declaredLength"
        } else {
            "Response body ended after $bytesWritten of $declaredLength bytes"
        },
    )

internal class ChromeHttp1ResponseWriter(
    private val streamBufferBytes: Int = DefaultStreamBufferBytes,
) {
    init {
        require(streamBufferBytes > 0)
    }

    fun writeBuffered(
        output: OutputStream,
        request: ChromePhotosProxyRequest,
        response: ChromePhotosSanitizedResponse,
        forceChunked: Boolean = false,
    ): ChromeStreamResult =
        write(
            output = output,
            request = request,
            statusCode = response.statusCode,
            statusText = response.statusText,
            headers = response.headers,
            body = response.bytes.inputStream(),
            bodyLength = if (forceChunked) -1L else response.bytes.size.toLong(),
        )

    fun writeStreaming(
        output: OutputStream,
        request: ChromePhotosProxyRequest,
        response: ChromePhotosUpstreamResponse,
    ): ChromeStreamResult =
        write(
            output = output,
            request = request,
            statusCode = response.statusCode,
            statusText = response.statusText,
            headers = response.headers,
            body = response.body,
            bodyLength = response.bodyLength,
        )

    private fun write(
        output: OutputStream,
        request: ChromePhotosProxyRequest,
        statusCode: Int,
        statusText: String,
        headers: List<ChromeHttpHeader>,
        body: InputStream,
        bodyLength: Long,
    ): ChromeStreamResult {
        val bodyAllowed = responseMayHaveBody(request.method, statusCode)
        val originalContentLength = headers.firstValue("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
        val safeHeaders = ChromeHttpHeaderPolicy.downstreamResponseHeaders(headers)
        val effectiveLength =
            when {
                statusCode in 100..199 || statusCode == 204 -> null
                statusCode == 205 -> 0L
                request.method == ChromePhotosProxyRequest.Head -> originalContentLength
                statusCode == 304 -> originalContentLength
                !bodyAllowed -> null
                bodyLength >= 0 -> bodyLength
                else -> null
            }
        val chunked = bodyAllowed && effectiveLength == null
        ChromeHttp1Wire.writeAscii(output, "HTTP/1.1 $statusCode ${statusText.sanitizeReason()}\r\n")
        safeHeaders
            .filterNot { it.name.equals("Content-Length", ignoreCase = true) }
            .forEach { header -> ChromeHttp1Wire.writeAscii(output, "${header.name}: ${header.value}\r\n") }
        if (effectiveLength != null) ChromeHttp1Wire.writeAscii(output, "Content-Length: $effectiveLength\r\n")
        if (chunked) ChromeHttp1Wire.writeAscii(output, "Transfer-Encoding: chunked\r\n")
        ChromeHttp1Wire.writeAscii(
            output,
            "Connection: ${if (request.closeAfterResponse) "close" else "keep-alive"}\r\n\r\n",
        )
        output.flush()
        if (!bodyAllowed) return ChromeStreamResult(0, chunked = false)

        val total =
            if (chunked) {
                writeChunkedBody(output, body)
            } else {
                writeFixedLengthBody(output, body, effectiveLength ?: error("Missing response length"))
            }
        output.flush()
        return ChromeStreamResult(total, chunked)
    }

    private fun writeFixedLengthBody(
        output: OutputStream,
        body: InputStream,
        declaredLength: Long,
    ): Long {
        val buffer = ByteArray(streamBufferBytes)
        var total = 0L
        while (total < declaredLength) {
            val maximumRead = minOf(buffer.size.toLong(), declaredLength - total).toInt()
            val read = body.read(buffer, 0, maximumRead)
            if (read < 0) {
                throw ChromeHttpResponseIntegrityException(
                    declaredLength = declaredLength,
                    bytesWritten = total,
                    additionalBodyByteObserved = false,
                )
            }
            if (read == 0) continue
            output.write(buffer, 0, read)
            total += read
        }
        if (body.read() >= 0) {
            throw ChromeHttpResponseIntegrityException(
                declaredLength = declaredLength,
                bytesWritten = total,
                additionalBodyByteObserved = true,
            )
        }
        return total
    }

    private fun writeChunkedBody(
        output: OutputStream,
        body: InputStream,
    ): Long {
        val buffer = ByteArray(streamBufferBytes)
        var total = 0L
        while (true) {
            val read = body.read(buffer)
            if (read < 0) {
                ChromeHttp1Wire.writeAscii(output, "0\r\n\r\n")
                return total
            }
            if (read == 0) continue
            ChromeHttp1Wire.writeAscii(output, read.toString(16) + "\r\n")
            output.write(buffer, 0, read)
            ChromeHttp1Wire.writeAscii(output, "\r\n")
            total += read
        }
    }

    private fun String.sanitizeReason(): String =
        filter { it.code in 32..126 }
            .take(MaximumReasonLength)
            .ifBlank { "Status" }

    private companion object {
        const val DefaultStreamBufferBytes = 32 * 1024
        const val MaximumReasonLength = 64
    }
}
