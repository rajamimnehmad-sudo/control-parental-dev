package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentTransportCancellationRegistration
import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierCompletion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldParserBarrierAdmissionTest {
    @Test
    fun `structural publication retained before request is consumed immediately`() {
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val completion = RecordingCompletion()
        val admission =
            admission(
                readContext = { ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding) },
                observed = observed,
            )

        admission.accept(completion)

        assertEquals(1, completion.ready)
        assertEquals(listOf(Binding), observed)
        assertFalse(admission.hasPending())
    }

    @Test
    fun `absent foreground window waits for one structural event and becomes ready exactly once`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        val waiting = mutableListOf<String>()
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val completion = RecordingCompletion()
        val admission = admission({ context }, waiting, observed)

        admission.accept(completion)
        assertTrue(admission.hasPending())
        assertEquals(listOf("foreground_window_unavailable"), waiting)
        assertEquals(0, completion.ready)

        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.onChromeStructuralEvent()
        admission.onChromeStructuralEvent()

        assertFalse(admission.hasPending())
        assertEquals(1, completion.ready)
        assertEquals(listOf(Binding), observed)
    }

    @Test
    fun `concurrent pending requests share the same structural publication`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val admission = admission(readContext = { context }, observed = observed)
        val first = RecordingCompletion()
        val second = RecordingCompletion()

        admission.accept(first)
        admission.accept(second)

        assertTrue(admission.hasPending())
        assertEquals(0, first.rejected)
        assertEquals(0, second.rejected)

        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.onChromeStructuralEvent()

        assertEquals(1, first.ready)
        assertEquals(1, second.ready)
        assertEquals(listOf(Binding, Binding), observed)
        assertFalse(admission.hasPending())
    }

    @Test
    fun `bounded admission supersedes only the oldest request`() {
        val admission = admission()
        val completions = List(5) { RecordingCompletion() }

        completions.forEach(admission::accept)

        assertTrue(admission.hasPending())
        assertEquals(listOf(1, 0, 0, 0, 0), completions.map { it.superseded })
        assertEquals(listOf(false, true, true, true, true), completions.map { it.isPending() })
    }

    @Test
    fun `one hundred arrivals retain only the latest four until one structural publication`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val admission = admission(readContext = { context }, observed = observed)
        val completions = List(100) { RecordingCompletion() }

        completions.forEach(admission::accept)

        assertEquals(96, completions.count { it.superseded == 1 })
        assertEquals(4, completions.count { it.isPending() })
        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.onChromeStructuralEvent()

        assertEquals(4, completions.count { it.ready == 1 })
        assertEquals(4, observed.size)
        assertFalse(admission.hasPending())
    }

    @Test
    fun `individual cancellation frees capacity without superseding another request`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        var cancellations = 0
        val completions = List(5) { RecordingCompletion() }
        val admission =
            ChromeMediaShieldParserBarrierAdmission(
                readContext = { context },
                onWaiting = {},
                onReady = {},
                onCancelled = { cancellations += 1 },
            )

        completions.take(4).forEach(admission::accept)
        completions[1].transportCancel()
        assertTrue(admission.onTransportCancelled(completions[1]))
        admission.accept(completions[4])
        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.onChromeStructuralEvent()

        assertEquals(1, cancellations)
        assertEquals(0, completions.sumOf { it.superseded })
        assertEquals(listOf(1, 0, 1, 1, 1), completions.map { it.ready })
        assertFalse(admission.hasPending())
    }

    @Test
    fun `current found publication purges a terminal oldest and admits the new request`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val admission = admission(readContext = { context }, observed = observed)
        val completions = List(5) { RecordingCompletion() }

        completions.take(4).forEach(admission::accept)
        assertTrue(completions.first().supersede())
        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.accept(completions.last())

        assertEquals(0, completions.first().ready)
        assertEquals(listOf(1, 1, 1, 1), completions.drop(1).map { it.ready })
        assertEquals(4, observed.size)
        assertFalse(admission.hasPending())
    }

    @Test
    fun `transport cancellation and close make later structural events inert`() {
        var context: ChromeMediaShieldActiveDocumentContextReadResult =
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        var cancellations = 0
        val completion = RecordingCompletion()
        val admission =
            ChromeMediaShieldParserBarrierAdmission(
                readContext = { context },
                onWaiting = {},
                onReady = { error("must not become ready") },
                onCancelled = { cancellations += 1 },
            )

        admission.accept(completion)
        completion.transportCancel()
        assertTrue(admission.onTransportCancelled(completion))
        context = ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding)
        admission.onChromeStructuralEvent()
        admission.close()

        assertEquals(1, cancellations)
        assertEquals(0, completion.ready)
        assertEquals(0, completion.rejected)
    }

    @Test
    fun `close rejects the pending request fail closed`() {
        val completion = RecordingCompletion()
        val admission = admission()

        admission.accept(completion)
        admission.close()

        assertFalse(admission.hasPending())
        assertEquals(1, completion.rejected)
    }

    @Test
    fun `late completion refusal never records a ready native context`() {
        val observed = mutableListOf<ChromeMediaShieldActiveDocumentNativeBinding>()
        val immediate = RecordingCompletion()
        val admission =
            admission(
                readContext = { ChromeMediaShieldActiveDocumentContextReadResult.Found(Binding) },
                observed = observed,
            )

        assertTrue(immediate.reject())
        admission.accept(immediate)

        assertEquals(0, immediate.ready)
        assertTrue(observed.isEmpty())
        assertFalse(admission.hasPending())
    }

    private fun admission(
        readContext: () -> ChromeMediaShieldActiveDocumentContextReadResult = {
            ChromeMediaShieldActiveDocumentContextReadResult.Unavailable("foreground_window_unavailable")
        },
        waiting: MutableList<String> = mutableListOf(),
        observed: MutableList<ChromeMediaShieldActiveDocumentNativeBinding> = mutableListOf(),
    ): ChromeMediaShieldParserBarrierAdmission =
        ChromeMediaShieldParserBarrierAdmission(
            readContext = readContext,
            onWaiting = waiting::add,
            onReady = observed::add,
            onCancelled = {},
        )

    private class RecordingCompletion : ChromeMediaShieldParserBarrierCompletion {
        private var pending = true
        var ready = 0
        var rejected = 0
        var superseded = 0

        override fun onTransportCancelled(callback: () -> Unit) =
            ChromeMediaShieldActiveDocumentTransportCancellationRegistration.Registered

        override fun ready(): Boolean {
            if (!pending) return false
            pending = false
            ready += 1
            return true
        }

        override fun reject(): Boolean {
            if (!pending) return false
            pending = false
            rejected += 1
            return true
        }

        override fun supersede(): Boolean {
            if (!pending) return false
            pending = false
            superseded += 1
            return true
        }

        override fun isPending(): Boolean = pending

        fun transportCancel() {
            pending = false
        }
    }

    private companion object {
        val Binding =
            ChromeMediaShieldActiveDocumentNativeBinding(
                windowId = 17,
                viewport = ChromeVisualViewport(0, 0, 720, 1_500),
                nativeRootDigest = "a".repeat(64),
                nativeRootBindingKind = ChromeMediaShieldNativeRootBindingKind.RetainedNode,
            )
    }
}
