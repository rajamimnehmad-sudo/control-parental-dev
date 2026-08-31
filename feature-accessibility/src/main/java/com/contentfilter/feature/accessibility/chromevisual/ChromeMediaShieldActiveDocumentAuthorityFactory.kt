package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import java.security.SecureRandom
import java.util.Base64

internal object ChromeMediaShieldActiveDocumentChallengeFactory {
    fun random(secureRandom: SecureRandom): ChromeMediaShieldActiveDocumentChallenge {
        val bytes = ByteArray(ChallengeBytes)
        secureRandom.nextBytes(bytes)
        return try {
            ChromeMediaShieldActiveDocumentChallenge.fromEncoded(
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            )
        } finally {
            bytes.fill(0)
        }
    }
}

internal fun ChromeMediaShieldActiveDocumentAttempt.leaseContext(
    authority: ChromeMediaShieldActiveDocumentAuthority =
        ChromeMediaShieldActiveDocumentAuthority(
            claim,
            binding.windowId,
            binding.nativeRootDigest,
        ),
): ChromePhotosDataPlaneLeaseContext {
    val snapshot = checkNotNull(surface)
    return ChromePhotosDataPlaneLeaseContext(
        packageName = ActiveDocumentChromePackageName,
        windowId = binding.windowId,
        epoch = snapshot.epoch,
        viewport = binding.viewport,
        activeDocument = authority,
    )
}
