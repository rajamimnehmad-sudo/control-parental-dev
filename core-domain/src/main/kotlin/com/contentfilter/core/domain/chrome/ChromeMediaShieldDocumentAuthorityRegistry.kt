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

/** Exact document-owned capability presented by the H20 parser-first bootstrap. */
data class ChromeMediaShieldSelfReadyIdentity(
    val protectionSessionId: String,
    val policyEpoch: Long,
    val navigationSequence: Long,
    val documentSequence: Long,
    val lifecycleSequence: Long,
    val topLevel: Boolean,
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

data class ChromeMediaShieldForegroundActivation(
    val claim: ChromeMediaShieldReadyClaim,
    val accessibilityContext: ChromeMediaShieldAccessibilityContext,
    val activationSequence: Long,
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
    private var foregroundActivationSequence = 0L
    private var foregroundActivation: ChromeMediaShieldForegroundActivation? = null

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
        foregroundActivationSequence = 0L
        foregroundActivation = null
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
            if (foregroundActivation?.claim?.identity?.tokenDigest == removable.key) {
                foregroundActivation = null
            }
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

    /** Removes only an exact undelivered transformer capability. */
    @Synchronized
    fun revokeIssued(identity: ChromeMediaShieldDocumentIdentity): Boolean {
        if (issuedByDigest[identity.tokenDigest] != identity) return false
        issuedByDigest.remove(identity.tokenDigest)
        readyLifecycleByDigest.remove(identity.tokenDigest)
        if (foregroundActivation?.claim?.identity == identity) foregroundActivation = null
        return true
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

    /**
     * Claims only the exact document that owns [readyToken]. Unlike the H19 foreground path this
     * grants no Android presentation capability: the accepted response is useful solely to the
     * requesting document closure, which may then remove its own parser-first curtain.
     */
    @Synchronized
    fun claimSelfReady(
        readyToken: String,
        expected: ChromeMediaShieldSelfReadyIdentity,
    ): ChromeMediaShieldReadyClaimResult {
        if (!readyToken.isStrictReadyToken() || expected.lifecycleSequence <= 0L) {
            return ChromeMediaShieldReadyClaimResult.Invalid("self_ready_malformed")
        }
        val tokenDigest = digestReadyToken(readyToken)
        val identity =
            issuedByDigest[tokenDigest]
                ?: return ChromeMediaShieldReadyClaimResult.Invalid("self_ready_not_issued")
        if (
            identity.protectionSessionId != expected.protectionSessionId ||
            identity.policyEpoch != expected.policyEpoch ||
            identity.navigationSequence != expected.navigationSequence ||
            identity.documentSequence != expected.documentSequence ||
            identity.topLevel != expected.topLevel
        ) {
            return ChromeMediaShieldReadyClaimResult.Invalid("self_ready_identity_mismatch")
        }
        if (!matchesSession(identity.protectionSessionId, identity.policyEpoch)) {
            return ChromeMediaShieldReadyClaimResult.Invalid("self_ready_session_stale")
        }
        val previousLifecycle = readyLifecycleByDigest[tokenDigest] ?: 0L
        if (expected.lifecycleSequence <= previousLifecycle) {
            return ChromeMediaShieldReadyClaimResult.Invalid("self_ready_replay")
        }
        readyLifecycleByDigest[tokenDigest] = expected.lifecycleSequence
        return ChromeMediaShieldReadyClaimResult.Claimed(
            ChromeMediaShieldReadyClaim(identity, expected.lifecycleSequence),
        )
    }

    /** Read-only validation for a DEV failure diagnostic before SELF_READY is consumed. */
    @Synchronized
    fun validatesUnclaimedSelfReady(
        readyToken: String,
        expected: ChromeMediaShieldSelfReadyIdentity,
    ): Boolean {
        if (!readyToken.isStrictReadyToken() || expected.lifecycleSequence <= 0L) return false
        val tokenDigest = digestReadyToken(readyToken)
        val identity = issuedByDigest[tokenDigest] ?: return false
        return identity.protectionSessionId == expected.protectionSessionId &&
            identity.policyEpoch == expected.policyEpoch &&
            identity.navigationSequence == expected.navigationSequence &&
            identity.documentSequence == expected.documentSequence &&
            identity.topLevel == expected.topLevel &&
            matchesSession(identity.protectionSessionId, identity.policyEpoch) &&
            tokenDigest !in readyLifecycleByDigest
    }

    /**
     * Resolves an already claimed top-level lifecycle without consuming a second lifecycle.
     *
     * The active-document handshake uses this for PROVE/PRESENT. It authenticates the document
     * capability only; the Accessibility boundary must still prove the current Chrome
     * window/root/surface independently before presentation.
     */
    @Synchronized
    fun resolveTopLevelReady(
        readyToken: String,
        lifecycleSequence: Long,
    ): ChromeMediaShieldReadyClaim? {
        if (!readyToken.isStrictReadyToken() || lifecycleSequence <= 0L) return null
        val tokenDigest = digestReadyToken(readyToken)
        val identity = issuedByDigest[tokenDigest] ?: return null
        return ChromeMediaShieldReadyClaim(identity, lifecycleSequence)
            .takeIf(::isCurrentTopLevelClaimLocked)
    }

    /** Executes [commit] under the registry monitor only while [claim] remains current. */
    @Synchronized
    fun commitIfTopLevelReadyCurrent(
        claim: ChromeMediaShieldReadyClaim,
        commit: () -> Boolean,
    ): Boolean = isCurrentTopLevelClaimLocked(claim) && commit()

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

    private fun isCurrentTopLevelClaimLocked(claim: ChromeMediaShieldReadyClaim): Boolean {
        val identity = issuedByDigest[claim.identity.tokenDigest] ?: return false
        return identity == claim.identity &&
            identity.topLevel &&
            matchesSession(identity.protectionSessionId, identity.policyEpoch) &&
            readyLifecycleByDigest[identity.tokenDigest] == claim.lifecycleSequence
    }

    /**
     * Activates foreground authority only after an exact browser-side Accessibility focus source
     * has been verified. A network READY claim alone can never choose the foreground tab/window.
     */
    @Synchronized
    fun activateClaimedForeground(
        claim: ChromeMediaShieldReadyClaim,
        accessibilityContext: ChromeMediaShieldAccessibilityContext,
    ): ChromeMediaShieldForegroundActivation? {
        if (!accessibilityContext.isValid()) return null
        val identity = issuedByDigest[claim.identity.tokenDigest] ?: return null
        if (
            identity != claim.identity ||
            !identity.topLevel ||
            !matchesSession(identity.protectionSessionId, identity.policyEpoch) ||
            readyLifecycleByDigest[identity.tokenDigest] != claim.lifecycleSequence
        ) {
            return null
        }
        foregroundActivationSequence += 1L
        return ChromeMediaShieldForegroundActivation(
            claim = claim,
            accessibilityContext = accessibilityContext,
            activationSequence = foregroundActivationSequence,
        ).also { foregroundActivation = it }
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
        return foregroundActivation
            ?.takeIf { activation ->
                activation.claim.identity.tokenDigest == tokenDigest &&
                    activation.claim.identity.protectionSessionId == sessionId &&
                    activation.claim.identity.policyEpoch == epoch &&
                    activation.claim.lifecycleSequence == lifecycleSequence &&
                    activation.accessibilityContext == accessibilityContext &&
                    issuedByDigest[tokenDigest] == activation.claim.identity &&
                    readyLifecycleByDigest[tokenDigest] == lifecycleSequence
            }?.claim?.identity
    }

    /**
     * Performs the final presentation commit under the same registry monitor as the exact
     * foreground validation. Activation, invalidation, session replacement, and this commit
     * therefore have one in-process total order. Chrome's external window/root continuity is
     * checked separately at the Accessibility boundary; this monitor does not claim to serialize
     * browser-process presentation changes.
     */
    @Synchronized
    fun commitIfClaimedForegroundCurrent(
        claim: ChromeMediaShieldReadyClaim,
        accessibilityContext: ChromeMediaShieldAccessibilityContext,
        commit: () -> Boolean,
    ): Boolean {
        val identity =
            resolveClaimedForeground(
                sessionId = claim.identity.protectionSessionId,
                epoch = claim.identity.policyEpoch,
                tokenDigest = claim.identity.tokenDigest,
                lifecycleSequence = claim.lifecycleSequence,
                accessibilityContext = accessibilityContext,
            ) ?: return false
        if (identity != claim.identity) return false
        return commit()
    }

    @Synchronized
    fun deactivateClaimedForeground(claim: ChromeMediaShieldReadyClaim? = null): Boolean {
        val current = foregroundActivation ?: return false
        if (claim != null && current.claim != claim) return false
        foregroundActivation = null
        return true
    }

    @Synchronized
    fun invalidateTopLevel(sessionId: String) {
        if (sessionId != protectionSessionId) return
        val digests = issuedByDigest.filterValues { it.topLevel }.keys
        issuedByDigest.keys.removeAll(digests)
        readyLifecycleByDigest.keys.removeAll(digests)
        foregroundActivation = null
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
        foregroundActivationSequence = 0L
        foregroundActivation = null
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
