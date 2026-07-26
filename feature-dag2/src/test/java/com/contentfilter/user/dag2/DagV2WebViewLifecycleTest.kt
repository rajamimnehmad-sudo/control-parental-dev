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
}
