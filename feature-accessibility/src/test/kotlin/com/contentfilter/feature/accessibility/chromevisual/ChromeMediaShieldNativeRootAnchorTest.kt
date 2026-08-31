package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ChromeMediaShieldNativeRootAnchorTest {
    @Test
    fun `platform unique id is exact and does not retain a copy`() {
        val fixture = Fixture()

        val first = fixture.anchor.identify(Node("a"), 17, "root-a")
        val second = fixture.anchor.identify(Node("a"), 17, "root-a")

        assertEquals(first, second)
        assertEquals(ChromeMediaShieldNativeRootBindingKind.PlatformUniqueId, first?.kind)
        assertEquals(0, fixture.copies)
        assertEquals(0, fixture.closes)
    }

    @Test
    fun `missing unique id reuses only a refreshed equal owned root`() {
        val fixture = Fixture()

        val first = fixture.anchor.identify(Node("a"), 17, null)
        val second = fixture.anchor.identify(Node("a"), 17, null)

        assertEquals(first, second)
        assertEquals(ChromeMediaShieldNativeRootBindingKind.RetainedNode, first?.kind)
        assertEquals(1, fixture.copies)
        assertEquals(1, fixture.refreshes)
        assertEquals(0, fixture.closes)
    }

    @Test
    fun `root window replacement and refresh failure rotate and close exactly once`() {
        val fixture = Fixture()
        val first = fixture.anchor.identify(Node("a"), 17, null)
        val replacement = fixture.anchor.identify(Node("b"), 17, null)
        fixture.refreshAllowed = false
        val detached = fixture.anchor.identify(Node("b"), 17, null)
        val otherWindow = fixture.anchor.identify(Node("b"), 18, null)

        assertNotEquals(first, replacement)
        assertNotEquals(replacement, detached)
        assertNotEquals(detached, otherWindow)
        assertEquals(4, fixture.copies)
        assertEquals(3, fixture.closes)

        fixture.anchor.close()
        fixture.anchor.close()
        assertEquals(4, fixture.closes)
    }

    @Test
    fun `invalid window never acquires or retains a root`() {
        val fixture = Fixture()

        assertNull(fixture.anchor.identify(Node("a"), -1, null))
        assertEquals(0, fixture.copies)
        assertEquals(0, fixture.closes)
    }

    private class Fixture {
        var copies = 0
        var refreshes = 0
        var closes = 0
        var refreshAllowed = true
        val anchor =
            ChromeMediaShieldNativeRootAnchor<Node>(
                copy = { node ->
                    copies += 1
                    node.copy()
                },
                refresh = {
                    refreshes += 1
                    refreshAllowed
                },
                sameNode = { first, second -> first.id == second.id },
                closeResource = { closes += 1 },
            )
    }

    private data class Node(
        val id: String,
    )
}
