package com.contentfilter.feature.accessibility.chromevisual

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldCurrentWebRootPolicyTest {
    @Test
    fun `exact browser issued web root can continue an already event bound document`() {
        assertTrue(
            ChromeMediaShieldCurrentWebRootPolicy.verifies(
                evidence(),
                WebRootUniqueId,
                RootIdentityDigest,
            ),
        )
    }

    @Test
    fun `tab document window and root replacements fail closed`() {
        listOf(
            evidence(candidateUniqueId = "background-tab-web-root"),
            evidence(candidateWindowId = WindowId + 1),
            evidence(expectedWindowId = WindowId + 1),
            evidence(windowRootPackageName = "example.hostile"),
            evidence(candidatePackageName = "example.hostile"),
            evidence(candidateClassName = "android.widget.FrameLayout"),
            evidence(candidateVisibleToUser = false),
            evidence(candidateAttachedToWindowRoot = false),
        ).forEach { candidate ->
            assertFalse(
                ChromeMediaShieldCurrentWebRootPolicy.verifies(
                    candidate,
                    WebRootUniqueId,
                    RootIdentityDigest,
                ),
            )
        }
        assertFalse(ChromeMediaShieldCurrentWebRootPolicy.verifies(evidence(), "", RootIdentityDigest))
    }

    @Test
    fun `same web root under a replacement native root fails closed`() {
        assertFalse(
            ChromeMediaShieldCurrentWebRootPolicy.verifies(
                evidence(nativeWindowRootUniqueId = "replacement-native-root"),
                WebRootUniqueId,
                RootIdentityDigest,
            ),
        )
    }

    private fun evidence(
        expectedWindowId: Int = WindowId,
        windowRootPackageName: String = ChromePackageName,
        nativeWindowRootUniqueId: String = NativeRootUniqueId,
        candidatePackageName: String = ChromePackageName,
        candidateClassName: String = ChromeMediaShieldWebRootContract.ClassName,
        candidateWindowId: Int = WindowId,
        candidateUniqueId: String? = WebRootUniqueId,
        candidateVisibleToUser: Boolean = true,
        candidateAttachedToWindowRoot: Boolean = true,
    ) = ChromeMediaShieldCurrentWebRootEvidence(
        expectedWindowId = expectedWindowId,
        windowRootPackageName = windowRootPackageName,
        nativeWindowRootUniqueId = nativeWindowRootUniqueId,
        candidatePackageName = candidatePackageName,
        candidateClassName = candidateClassName,
        candidateWindowId = candidateWindowId,
        candidateUniqueId = candidateUniqueId,
        candidateVisibleToUser = candidateVisibleToUser,
        candidateAttachedToWindowRoot = candidateAttachedToWindowRoot,
    )

    private companion object {
        const val ChromePackageName = "com.android.chrome"
        const val WindowId = 17
        const val NativeRootUniqueId = "chrome-native-root:17"
        const val WebRootUniqueId = "chrome-web-root:17"
        val RootIdentityDigest =
            checkNotNull(
                ChromeMediaShieldForegroundContextPolicy.bindingDigest(
                    nativeRootUniqueId = NativeRootUniqueId,
                    webRootUniqueId = WebRootUniqueId,
                ),
            )
    }
}
