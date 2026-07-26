package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2WebViewLifecycleTest {
    @Test
    fun `release neutralizes execution before destroying the webview`() {
        val calls = mutableListOf<String>()
        val port =
            object : DagV2WebViewReleasePort {
                override fun stopLoading() = calls.add("stop").let {}

                override fun loadNeutralDocument() = calls.add("blank").let {}

                override fun removeBridge() = calls.add("bridge").let {}

                override fun neutralizeClients() = calls.add("clients").let {}

                override fun clearCallbacks() = calls.add("callbacks").let {}

                override fun destroy() = calls.add("destroy").let {}
            }

        DagV2WebViewReleaseSequence.release(port)

        assertEquals(listOf("stop", "blank", "bridge", "clients", "callbacks", "destroy"), calls)
    }

    @Test
    fun `host close releases the active webview exactly once`() {
        val host = DagV2WebViewHost<Any>()
        val view = Any()
        var releases = 0

        host.attach(view) { releases += 1 }
        host.close()
        host.close()
        host.detach(view)

        assertEquals(1, releases)
    }

    @Test
    fun `host replacement releases the previous webview`() {
        val host = DagV2WebViewHost<Any>()
        val first = Any()
        val second = Any()
        val released = mutableListOf<String>()

        host.attach(first) { released += "first" }
        host.attach(second) { released += "second" }
        host.close()

        assertEquals(listOf("first", "second"), released)
    }

    @Test
    fun `old activity release cannot close a newer lab generation`() {
        val gate = DagV2LabLifecycleGate()
        val old = gate.acquire()
        val current = gate.acquire()

        assertEquals(false, gate.release(old))
        assertEquals(true, gate.release(current))
    }
}
