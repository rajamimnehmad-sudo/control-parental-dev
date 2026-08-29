package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract

internal fun ChromePhotosProxyRequest.successDisposition(): ChromeHttpConnectionDisposition =
    if (closeAfterResponse) ChromeHttpConnectionDisposition.Close else ChromeHttpConnectionDisposition.Continue

internal fun ChromePhotosFixtureResponse.asUpstreamResponse(): ChromePhotosUpstreamResponse =
    ChromePhotosUpstreamResponse(
        host = ChromePhotosDataPlaneLabContract.FixtureHost,
        statusCode = statusCode,
        statusText = statusText,
        headers = headers + ChromeHttpHeader("Content-Type", contentType),
        body = originalBytes.inputStream(),
        bodyLength = originalBytes.size.toLong(),
        protocol = "fixture",
    )

internal fun ChromeMediaShieldDocumentResult.asSanitizedResponse(): ChromePhotosSanitizedResponse =
    when (this) {
        is ChromeMediaShieldDocumentResult.Transformed ->
            ChromePhotosSanitizedResponse(
                statusCode = 200,
                statusText = "OK",
                headers = document.headers,
                bytes = document.bytes,
                decision = ChromePhotosResourceDecision.Passthrough,
                cacheHit = false,
                contentHash = null,
                inputBytes = 0,
            )
        is ChromeMediaShieldDocumentResult.FailClosed ->
            ChromePhotosSanitizedResponse(
                statusCode = 200,
                statusText = "OK",
                headers = headers,
                bytes = bytes,
                decision = ChromePhotosResourceDecision.Passthrough,
                cacheHit = false,
                contentHash = null,
                inputBytes = 0,
            )
    }

internal fun ChromeMediaShieldDocumentResult.logValue(): String =
    when (this) {
        is ChromeMediaShieldDocumentResult.Transformed ->
            "transformed documentSequence=${document.identity.documentSequence} " +
                "navigationSequence=${document.identity.navigationSequence} token=${document.identity.tokenDigest.take(12)}"
        is ChromeMediaShieldDocumentResult.FailClosed -> "fail_closed reason=$reason"
    }

internal fun ChromePhotosFixtureResponse.asPassthroughSanitizedResponse(
    inspectedResponse: ChromePhotosUpstreamResponse = asUpstreamResponse(),
): ChromePhotosSanitizedResponse =
    ChromePhotosSanitizedResponse(
        statusCode = statusCode,
        statusText = statusText,
        headers = ChromeHttpHeaderPolicy.downstreamResponseHeaders(inspectedResponse.headers),
        bytes = originalBytes,
        decision = ChromePhotosResourceDecision.Passthrough,
        cacheHit = false,
        contentHash = null,
        inputBytes = originalBytes.size,
    )
