package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DagVideoDocumentPortRegistryTest {
    @Test
    fun `latest top-level document owns reconfiguration for its tab`() {
        val registry = DagVideoDocumentPortRegistry<Any>()
        val oldPort = Any()
        val currentPort = Any()

        registry.connect(document(tabId = 7L, port = oldPort))
        registry.connect(document(tabId = 7L, port = currentPort))
        registry.disconnect(tabId = 7L, port = oldPort)

        assertSame(currentPort, registry.current(7L)?.port)
    }

    @Test
    fun `documents remain isolated across tab switches`() {
        val registry = DagVideoDocumentPortRegistry<Any>()
        val firstPort = Any()
        val secondPort = Any()

        registry.connect(document(tabId = 8L, port = firstPort))
        registry.connect(
            document(
                tabId = 9L,
                port = secondPort,
                eligible = false,
                fixture = true,
            ),
        )

        assertSame(firstPort, registry.current(8L)?.port)
        assertSame(secondPort, registry.current(9L)?.port)
        assertEquals(false, registry.current(9L)?.eligibleTopLevelDocument)
        assertEquals(true, registry.current(9L)?.fixture)
    }

    @Test
    fun `disconnect and tab removal clear only the exact current document`() {
        val registry = DagVideoDocumentPortRegistry<Any>()
        val firstPort = Any()
        val secondPort = Any()
        registry.connect(document(tabId = 10L, port = firstPort))
        registry.connect(document(tabId = 11L, port = secondPort))

        registry.disconnect(tabId = 10L, port = firstPort)
        registry.remove(11L)

        assertNull(registry.current(10L))
        assertNull(registry.current(11L))
    }

    private fun document(
        tabId: Long,
        port: Any,
        eligible: Boolean = true,
        fixture: Boolean = false,
    ) = DagVideoDocumentPort(
        tabId = tabId,
        port = port,
        eligibleTopLevelDocument = eligible,
        fixture = fixture,
    )
}
