package com.contentfilter.core.domain.chrome

import java.security.MessageDigest
import java.util.LinkedHashMap

data class ChromeMediaShieldDocumentIdentity(
    val protectionSessionId: String,
    val policyEpoch: Long,
    val navigationSequence: Long,
    val documentSequence: Long,
    val tokenDigest: String,
    val topLevel: Boolean,
)

data class ChromeMediaShieldDocumentAuthoritySnapshot(
    val protectionSessionId: String = "",
    val policyEpoch: Long = 0L,
    val issuedDocuments: Int = 0,
    val readyClaims: Int = 0,
    val currentTopLevel: ChromeMediaShieldDocumentIdentity? = null,
)

data class ChromeMediaShieldReadyClaim(
    val identity: ChromeMediaShieldDocumentIdentity,
    val lifecycleSequence: Long,
)

sealed interface ChromeMediaShieldReadyClaimResult {
    data class Claimed(
        val claim: ChromeMediaShieldReadyClaim,
    ) : ChromeMediaShieldReadyClaimResult

    data class Invalid(
        val reason: String,
    ) : ChromeMediaShieldReadyClaimResult
}

data class ChromeMediaShieldAccessibilityContext(
    val windowId: Int,
    val rootIdentityDigest: String,
    val markerIdentityDigest: String,
)

/**
 * Process-local, bounded authority issued by the DEV document transformer.
 *
 * Raw ready tokens never enter this registry. A process restart or explicit clear removes every
 * authority and therefore fails closed.
 */
object ChromeMediaShieldDocumentAuthorityRegistry {
    private var protectionSessionId = ""
    private var policyEpoch = 0L
    private var navigationSequence = 0L
    private var documentSequence = 0L
    private val issuedByDigest =
        LinkedHashMap<String, ChromeMediaShieldDocumentIdentity>(MaximumIssuedDocuments, 0.75f, true)
    private val readyLifecycleByDigest = mutableMapOf<String, Long>()

    @Synchronized
    fun beginSession(
        sessionId: String,
        epoch: Long,
    ) {
        require(sessionId.isNotBlank())
        require(epoch > 0L)
        protectionSessionId = sessionId
        policyEpoch = epoch
        navigationSequence = 0L
        documentSequence = 0L
        issuedByDigest.clear()
        readyLifecycleByDigest.clear()
    }

    @Synchronized
    fun issue(
        sessionId: String,
        epoch: Long,
        readyToken: String,
        topLevel: Boolean,
    ): ChromeMediaShieldDocumentIdentity? {
        if (!matchesSession(sessionId, epoch) || !readyToken.isStrictReadyToken()) return null
        if (topLevel) {
            navigationSequence += 1L
        }
        documentSequence += 1L
        while (issuedByDigest.size >= MaximumIssuedDocuments) {
            val removable = issuedByDigest.entries.firstOrNull { !it.value.topLevel } ?: issuedByDigest.entries.firstOrNull()
            if (removable == null) break
            issuedByDigest.remove(removable.key)
            readyLifecycleByDigest.remove(removable.key)
        }
        val identity =
            ChromeMediaShieldDocumentIdentity(
                protectionSessionId = sessionId,
                policyEpoch = epoch,
                navigationSequence = navigationSequence,
                documentSequence = documentSequence,
                tokenDigest = digestReadyToken(readyToken),
                topLevel = topLevel,
            )
        issuedByDigest[identity.tokenDigest] = identity
        return identity
    }

    /**
     * Consumes one strictly newer document-owned visibility lifecycle. Network cancellation may
     * prevent an earlier lifecycle from reaching the proxy, so gaps are allowed; equality and
     * regression are always rejected as replay.
     */
    @Synchronized
    fun claimReady(
        readyToken: String,
        lifecycleSequence: Long,
    ): ChromeMediaShieldReadyClaimResult = claimReadyLocked(readyToken, lifecycleSequence, requireTopLevel = false)

    /** Claims only a top-level document without consuming a subdocument lifecycle on rejection. */
    @Synchronized
    fun claimTopLevelReady(
        readyToken: String,
        lifecycleSequence: Long,
    ): ChromeMediaShieldReadyClaimResult = claimReadyLocked(readyToken, lifecycleSequence, requireTopLevel = true)

    private fun claimReadyLocked(
        readyToken: String,
        lifecycleSequence: Long,
        requireTopLevel: Boolean,
    ): ChromeMediaShieldReadyClaimResult {
        if (!readyToken.isStrictReadyToken() || lifecycleSequence <= 0L) {
            return ChromeMediaShieldReadyClaimResult.Invalid("ready_claim_malformed")
        }
        val tokenDigest = digestReadyToken(readyToken)
        val identity =
            issuedByDigest[tokenDigest]
                ?: return ChromeMediaShieldReadyClaimResult.Invalid("ready_claim_not_issued")
        if (requireTopLevel && !identity.topLevel) {
            return ChromeMediaShieldReadyClaimResult.Invalid("ready_top_level_required")
        }
        if (!matchesSession(identity.protectionSessionId, identity.policyEpoch)) {
            return ChromeMediaShieldReadyClaimResult.Invalid("ready_claim_session_stale")
        }
        val previousLifecycle = readyLifecycleByDigest[tokenDigest] ?: 0L
        if (lifecycleSequence <= previousLifecycle) {
            return ChromeMediaShieldReadyClaimResult.Invalid("ready_claim_lifecycle_stale")
        }
        readyLifecycleByDigest[tokenDigest] = lifecycleSequence
        return ChromeMediaShieldReadyClaimResult.Claimed(
            ChromeMediaShieldReadyClaim(identity, lifecycleSequence),
        )
    }

    /** Read-only AX resolution after an accepted ready handshake; it performs no TOFU claim. */
    @Synchronized
    fun resolveClaimedForeground(
        sessionId: String,
        epoch: Long,
        tokenDigest: String,
        lifecycleSequence: Long,
        accessibilityContext: ChromeMediaShieldAccessibilityContext,
    ): ChromeMediaShieldDocumentIdentity? {
        if (
            !matchesSession(sessionId, epoch) ||
            !tokenDigest.matches(Sha256Pattern) ||
            lifecycleSequence <= 0L ||
            !accessibilityContext.isValid()
        ) {
            return null
        }
        val identity = issuedByDigest[tokenDigest] ?: return null
        return identity.takeIf {
            it.topLevel &&
                it.protectionSessionId == sessionId &&
                it.policyEpoch == epoch &&
                readyLifecycleByDigest[tokenDigest] == lifecycleSequence
        }
    }

    @Synchronized
    fun invalidateTopLevel(sessionId: String) {
        if (sessionId != protectionSessionId) return
        val digests = issuedByDigest.filterValues { it.topLevel }.keys
        issuedByDigest.keys.removeAll(digests)
        readyLifecycleByDigest.keys.removeAll(digests)
    }

    @Synchronized
    fun snapshot(): ChromeMediaShieldDocumentAuthoritySnapshot =
        ChromeMediaShieldDocumentAuthoritySnapshot(
            protectionSessionId = protectionSessionId,
            policyEpoch = policyEpoch,
            issuedDocuments = issuedByDigest.size,
            readyClaims = readyLifecycleByDigest.size,
            currentTopLevel = issuedByDigest.values.lastOrNull { it.topLevel },
        )

    @Synchronized
    fun clear() {
        protectionSessionId = ""
        policyEpoch = 0L
        navigationSequence = 0L
        documentSequence = 0L
        issuedByDigest.clear()
        readyLifecycleByDigest.clear()
    }

    fun digestReadyToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.US_ASCII))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun matchesSession(
        sessionId: String,
        epoch: Long,
    ): Boolean =
        sessionId.isNotBlank() &&
            sessionId == protectionSessionId &&
            epoch == policyEpoch

    private fun String.isStrictReadyToken(): Boolean =
        length in MinimumTokenCharacters..MaximumTokenCharacters &&
            all { character -> character.isLetterOrDigit() || character == '-' || character == '_' }

    private fun ChromeMediaShieldAccessibilityContext.isValid(): Boolean =
        windowId >= 0 &&
            rootIdentityDigest.matches(Sha256Pattern) &&
            markerIdentityDigest.matches(Sha256Pattern)

    private const val MaximumIssuedDocuments = 128
    private const val MinimumTokenCharacters = 22
    private const val MaximumTokenCharacters = 64
    private val Sha256Pattern = Regex("[0-9a-f]{64}")
}
