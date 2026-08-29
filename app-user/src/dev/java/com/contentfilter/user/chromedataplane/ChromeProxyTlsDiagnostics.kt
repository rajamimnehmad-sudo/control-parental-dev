package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

internal enum class ChromeProxyTlsSide(
    val logValue: String,
) {
    Client("client"),
    Upstream("upstream"),
}

internal data class ChromeProxyTlsContext(
    val side: ChromeProxyTlsSide,
    val stage: String,
    val correlationId: String,
    val host: String,
    val authority: String,
    val sni: String?,
)

internal data class ChromeProxyTlsFailure(
    val errorClass: String,
    val rootCauseClass: String,
    val causeChain: String,
    val isHandshake: Boolean,
) {
    fun logLine(context: ChromeProxyTlsContext): String =
        buildString {
            append("phase=tls_failed")
            append(" side=${context.side.logValue}")
            append(" stage=${context.stage}")
            append(" correlationId=${context.correlationId}")
            append(" hostClass=${ChromeProxyLogPrivacy.hostClass(context.host)}")
            append(" hostHash=${ChromeProxyLogPrivacy.digest(context.host)}")
            append(" authorityHash=${ChromeProxyLogPrivacy.digest(context.authority)}")
            append(
                " sniHash=" +
                    (context.sni?.let(ChromeProxyLogPrivacy::digest) ?: SniNotObserved),
            )
            append(" error=$errorClass")
            append(" rootCause=$rootCauseClass")
            append(" causeChain=$causeChain")
        }

    private companion object {
        const val SniNotObserved = "not_observed"
    }
}

/** Keeps request destinations out of DEV logs while retaining stable correlation evidence. */
internal object ChromeProxyLogPrivacy {
    fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.lowercase(Locale.US).toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(DigestLogLength)

    fun hostClass(host: String): String =
        if (host == ChromePhotosDataPlaneLabContract.FixtureHost) "fixture" else "network"

    private const val DigestLogLength = 16
}

/**
 * Classifies TLS failures using exception classes only. Exception messages and stack traces are
 * deliberately excluded because they may contain URLs, certificate details, or other user data.
 */
internal object ChromeProxyTlsDiagnostics {
    fun classify(error: Throwable): ChromeProxyTlsFailure? {
        val chain = safeCauseChain(error)
        val tlsError =
            chain.firstOrNull { it is SSLHandshakeException }
                ?: chain.firstOrNull { it is SSLException }
                ?: return null
        return ChromeProxyTlsFailure(
            errorClass = tlsError.safeClassName(),
            rootCauseClass = chain.last().safeClassName(),
            causeChain = chain.joinToString(separator = ">") { it.safeClassName() },
            isHandshake = tlsError is SSLHandshakeException,
        )
    }

    fun logLine(
        context: ChromeProxyTlsContext,
        error: Throwable,
    ): String? = classify(error)?.logLine(context)

    private fun safeCauseChain(error: Throwable): List<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val chain = ArrayList<Throwable>(MaximumCauseDepth)
        var current: Throwable? = error
        while (current != null && chain.size < MaximumCauseDepth && seen.add(current)) {
            chain += current
            current = current.cause
        }
        return chain
    }

    private fun Throwable.safeClassName(): String =
        javaClass.simpleName
            .ifBlank { javaClass.name.substringAfterLast('.') }
            .filter { character -> character.isLetterOrDigit() || character in SafeClassPunctuation }
            .take(MaximumClassNameLength)
            .ifBlank { "Throwable" }

    private const val MaximumCauseDepth = 8
    private const val MaximumClassNameLength = 96
    private const val SafeClassPunctuation = "_.$"
}
