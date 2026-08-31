package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import java.util.LinkedHashMap

internal enum class ChromeMediaShieldSelfShieldLivenessPhase {
    ReleaseCompleted,
    ParserContinued,
    OriginalScriptStarted,
}

internal data class ChromeMediaShieldSelfShieldLivenessMetrics(
    val releaseCompleted: Long = 0L,
    val parserContinued: Long = 0L,
    val originalScriptStarted: Long = 0L,
    val rejected: Long = 0L,
    val outstanding: Int = 0,
)

/** Bounded DEV-only trace. It observes H20 parser liveness and grants no presentation authority. */
internal class ChromeMediaShieldSelfShieldLiveness(
    private val maximumDocuments: Int = MaximumDocuments,
) {
    private data class Entry(
        val identity: ChromeMediaShieldSelfReadyIdentity,
        val next: ChromeMediaShieldSelfShieldLivenessPhase,
    )

    private val entries = LinkedHashMap<String, Entry>(maximumDocuments, 0.75f, true)
    private var releaseCompleted = 0L
    private var parserContinued = 0L
    private var originalScriptStarted = 0L
    private var rejected = 0L

    init {
        require(maximumDocuments > 0)
    }

    @Synchronized
    fun arm(
        token: String,
        identity: ChromeMediaShieldSelfReadyIdentity,
    ) {
        val digest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(token)
        entries[digest] = Entry(identity, ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted)
        while (entries.size > maximumDocuments) entries.remove(entries.entries.first().key)
    }

    @Synchronized
    fun claim(
        token: String,
        identity: ChromeMediaShieldSelfReadyIdentity,
        phase: ChromeMediaShieldSelfShieldLivenessPhase,
        expectOriginalScript: Boolean = true,
    ): Boolean {
        val digest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(token)
        val entry = entries[digest]
        if (entry == null || entry.identity != identity || entry.next != phase) {
            rejected += 1L
            return false
        }
        when (phase) {
            ChromeMediaShieldSelfShieldLivenessPhase.ReleaseCompleted -> {
                releaseCompleted += 1L
                entries[digest] = entry.copy(next = ChromeMediaShieldSelfShieldLivenessPhase.ParserContinued)
            }
            ChromeMediaShieldSelfShieldLivenessPhase.ParserContinued -> {
                parserContinued += 1L
                if (expectOriginalScript) {
                    entries[digest] = entry.copy(next = ChromeMediaShieldSelfShieldLivenessPhase.OriginalScriptStarted)
                } else {
                    entries.remove(digest)
                }
            }
            ChromeMediaShieldSelfShieldLivenessPhase.OriginalScriptStarted -> {
                originalScriptStarted += 1L
                entries.remove(digest)
            }
        }
        return true
    }

    @Synchronized
    fun metrics() =
        ChromeMediaShieldSelfShieldLivenessMetrics(
            releaseCompleted = releaseCompleted,
            parserContinued = parserContinued,
            originalScriptStarted = originalScriptStarted,
            rejected = rejected,
            outstanding = entries.size,
        )

    private companion object {
        const val MaximumDocuments = 64
    }
}
