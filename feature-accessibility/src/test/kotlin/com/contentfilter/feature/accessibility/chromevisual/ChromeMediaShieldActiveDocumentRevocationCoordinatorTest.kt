package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentHandshakeCompletion
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldActiveDocumentRevocationCoordinatorTest {
    @Test
    fun `protocol ack occurs only after exact opaque commit`() {
        val surface = RecordingSurface()
        val completion = RecordingCompletion()
        val terminals = mutableListOf<ChromeMediaShieldActiveDocumentRevocationTerminal>()
        val coordinator = coordinator(surface)

        coordinator.begin(1L, completion, terminals::add)

        assertEquals(1, surface.submissions)
        assertEquals(0, completion.revocations)
        assertTrue(coordinator.hasPending())

        surface.commit(true)

        assertEquals(1, completion.revocations)
        assertEquals(0, completion.rejections)
        assertFalse(coordinator.hasPending())
        assertEquals(1, terminals.size)
        assertTrue(terminals.single().transportCompleted)
    }

    @Test
    fun `transport cancellation rejects and late opaque callback cannot ack`() {
        val surface = RecordingSurface()
        val completion = RecordingCompletion()
        val coordinator = coordinator(surface)

        coordinator.begin(2L, completion) {}
        assertTrue(coordinator.onTransportCancelled(completion))

        assertEquals(0, completion.revocations)
        assertEquals(1, completion.rejections)
        assertFalse(coordinator.hasPending())

        surface.commit(true)

        assertEquals(0, completion.revocations)
        assertEquals(1, completion.rejections)
    }

    @Test
    fun `submission failure rejects once and late callback is inert`() {
        val surface = RecordingSurface(result = ChromePhotosProtectedSurfaceRevokeResult.Failed)
        val completion = RecordingCompletion()
        val coordinator = coordinator(surface)

        coordinator.begin(3L, completion) {}

        assertEquals(0, completion.revocations)
        assertEquals(1, completion.rejections)
        assertFalse(coordinator.hasPending())
        surface.commit(true)
        assertEquals(0, completion.revocations)
        assertEquals(1, completion.rejections)
    }

    @Test
    fun `already opaque acks without a platform submission`() {
        val surface = RecordingSurface(alreadyOpaque = true)
        val completion = RecordingCompletion()
        val coordinator = coordinator(surface)

        coordinator.begin(4L, completion) {}

        assertEquals(0, surface.submissions)
        assertEquals(1, completion.revocations)
        assertEquals(0, completion.rejections)
    }

    @Test
    fun `cancel current is one shot and close cannot produce delayed ack`() {
        val surface = RecordingSurface()
        val completion = RecordingCompletion()
        val coordinator = coordinator(surface)

        coordinator.begin(5L, completion) {}
        assertTrue(coordinator.cancelCurrent())
        assertFalse(coordinator.cancelCurrent())
        coordinator.close()
        surface.commit(true)

        assertEquals(0, completion.revocations)
        assertEquals(1, completion.rejections)
    }

    private fun coordinator(surface: RecordingSurface) =
        ChromeMediaShieldActiveDocumentRevocationCoordinator(
            surfaceAlreadyOpaque = { surface.alreadyOpaque },
            submitOpaque = surface::submit,
        )

    private class RecordingSurface(
        val alreadyOpaque: Boolean = false,
        private val result: ChromePhotosProtectedSurfaceRevokeResult =
            ChromePhotosProtectedSurfaceRevokeResult.Submitted,
    ) {
        var submissions = 0
        private var callback: ((Boolean) -> Unit)? = null

        fun submit(callback: (Boolean) -> Unit): ChromePhotosProtectedSurfaceRevokeResult {
            submissions += 1
            this.callback = callback
            return result
        }

        fun commit(committed: Boolean) {
            callback?.invoke(committed)
        }
    }

    private class RecordingCompletion : ChromeMediaShieldActiveDocumentHandshakeCompletion {
        var revocations = 0
        var rejections = 0

        override fun onTransportCancelled(
            callback: () -> Unit,
        ): ChromeMediaShieldActiveDocumentTransportCancellationRegistration =
            ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered

        override fun issueChallenge(challenge: ChromeMediaShieldActiveDocumentChallenge) = false

        override fun acceptProof() = false

        override fun acceptPresentation() = false

        override fun acceptRevocation(): Boolean {
            revocations += 1
            return true
        }

        override fun reject(): Boolean {
            rejections += 1
            return true
        }
    }
}
