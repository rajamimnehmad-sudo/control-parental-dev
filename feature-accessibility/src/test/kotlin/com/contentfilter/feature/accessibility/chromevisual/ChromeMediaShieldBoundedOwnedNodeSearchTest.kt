package com.contentfilter.feature.accessibility.chromevisual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeMediaShieldBoundedOwnedNodeSearchTest {
    @Test
    fun `exact anchor early in a large tree returns without scanning the remaining tree`() {
        val tracker = HandleTracker()
        val anchor = Node("anchor", exact = true)
        val largeBranch = Node("large", children = (1..700).map { Node("leaf-$it") })
        val result =
            search(maximumNodeReads = 4).findDescendant(
                borrowedRoot = tracker.borrow(Node("root", children = listOf(anchor, largeBranch))),
                childCount = tracker::childCount,
                copyChild = tracker::copyChild,
                isExactMatch = { it.node.exact },
                close = tracker::close,
            )

        assertTrue(result is ChromeMediaShieldOwnedNodeSearchResult.Found)
        val ownedAnchor = (result as ChromeMediaShieldOwnedNodeSearchResult.Found).node
        assertEquals("anchor", ownedAnchor.node.id)
        assertEquals(0, tracker.closeCount(ownedAnchor))
        assertEquals(1, tracker.createdCount("anchor"))
        assertEquals(0, tracker.createdCount("large"))
        assertEquals(0, tracker.createdCount("leaf-1"))
        assertEquals(0, tracker.rootCloseCount())

        tracker.close(ownedAnchor)
        tracker.assertEveryHandleClosedOnce()
    }

    @Test
    fun `exact anchor after queued siblings transfers only the anchor`() {
        val tracker = HandleTracker()
        val result =
            search(maximumNodeReads = 4).findDescendant(
                borrowedRoot =
                    tracker.borrow(
                        Node(
                            "root",
                            children = listOf(Node("queued"), Node("anchor", exact = true)),
                        ),
                    ),
                childCount = tracker::childCount,
                copyChild = tracker::copyChild,
                isExactMatch = { it.node.exact },
                close = tracker::close,
            )

        assertTrue(result is ChromeMediaShieldOwnedNodeSearchResult.Found)
        val ownedAnchor = (result as ChromeMediaShieldOwnedNodeSearchResult.Found).node
        assertEquals(1, tracker.closeCountForNode("queued"))
        assertEquals(0, tracker.closeCount(ownedAnchor))
        tracker.close(ownedAnchor)
        tracker.assertEveryHandleClosedOnce()
    }

    @Test
    fun `overflow closes visited and queued ownership exactly once`() {
        val tracker = HandleTracker()
        val chain = Node("a", children = listOf(Node("b", children = listOf(Node("c")))))
        val result =
            search(maximumNodeReads = 2).findDescendant(
                borrowedRoot = tracker.borrow(Node("root", children = listOf(chain))),
                childCount = tracker::childCount,
                copyChild = tracker::copyChild,
                isExactMatch = { false },
                close = tracker::close,
            )

        assertEquals(ChromeMediaShieldOwnedNodeSearchResult.Overflow, result)
        tracker.assertEveryHandleClosedOnce()
    }

    @Test
    fun `absent anchor closes every traversed handle exactly once`() {
        val tracker = HandleTracker()
        val tree = Node("a", children = listOf(Node("b"), Node("c")))
        val result =
            search(maximumNodeReads = 8).findDescendant(
                borrowedRoot = tracker.borrow(Node("root", children = listOf(tree))),
                childCount = tracker::childCount,
                copyChild = tracker::copyChild,
                isExactMatch = { false },
                close = tracker::close,
            )

        assertEquals(ChromeMediaShieldOwnedNodeSearchResult.Absent, result)
        tracker.assertEveryHandleClosedOnce()
    }

    @Test
    fun `exact budget exhaustion distinguishes complete absence from overflow`() {
        val complete = HandleTracker()
        val completeResult =
            search(maximumNodeReads = 512).findDescendant(
                borrowedRoot =
                    complete.borrow(
                        Node("root", children = (1..512).map { Node("complete-$it") }),
                    ),
                childCount = complete::childCount,
                copyChild = complete::copyChild,
                isExactMatch = { false },
                close = complete::close,
            )
        assertEquals(ChromeMediaShieldOwnedNodeSearchResult.Absent, completeResult)
        assertEquals(512, complete.totalCreated())
        complete.assertEveryHandleClosedOnce()

        val overflowing = HandleTracker()
        val overflowResult =
            search(maximumNodeReads = 512).findDescendant(
                borrowedRoot =
                    overflowing.borrow(
                        Node("root", children = (1..513).map { Node("overflow-$it") }),
                    ),
                childCount = overflowing::childCount,
                copyChild = overflowing::copyChild,
                isExactMatch = { false },
                close = overflowing::close,
            )
        assertEquals(ChromeMediaShieldOwnedNodeSearchResult.Overflow, overflowResult)
        assertEquals(512, overflowing.totalCreated())
        overflowing.assertEveryHandleClosedOnce()
    }

    @Test
    fun `exception closes current and queued handles exactly once`() {
        val tracker = HandleTracker()
        val first = Node("first")
        val queued = Node("queued")

        val result =
            runCatching {
                search(maximumNodeReads = 8).findDescendant(
                    borrowedRoot = tracker.borrow(Node("root", children = listOf(first, queued))),
                    childCount = { handle ->
                        if (handle.node.id == "first") error("stale node")
                        tracker.childCount(handle)
                    },
                    copyChild = tracker::copyChild,
                    isExactMatch = { false },
                    close = tracker::close,
                )
            }

        assertTrue(result.isFailure)
        tracker.assertEveryHandleClosedOnce()
    }

    @Test
    fun `child acquisition and predicate exceptions close acquired ownership`() {
        val acquisition = HandleTracker()
        val acquisitionResult =
            runCatching {
                search(maximumNodeReads = 8).findDescendant(
                    borrowedRoot =
                        acquisition.borrow(
                            Node("root", children = listOf(Node("queued"), Node("boom"))),
                        ),
                    childCount = acquisition::childCount,
                    copyChild = { handle, index ->
                        if (index == 1) error("stale child")
                        acquisition.copyChild(handle, index)
                    },
                    isExactMatch = { false },
                    close = acquisition::close,
                )
            }
        assertTrue(acquisitionResult.isFailure)
        acquisition.assertEveryHandleClosedOnce()

        val predicate = HandleTracker()
        val predicateResult =
            runCatching {
                search(maximumNodeReads = 8).findDescendant(
                    borrowedRoot = predicate.borrow(Node("root", children = listOf(Node("candidate")))),
                    childCount = predicate::childCount,
                    copyChild = predicate::copyChild,
                    isExactMatch = { error("stale predicate") },
                    close = predicate::close,
                )
            }
        assertTrue(predicateResult.isFailure)
        predicate.assertEveryHandleClosedOnce()
    }

    private fun search(maximumNodeReads: Int) = ChromeMediaShieldBoundedOwnedNodeSearch<Handle>(maximumNodeReads)

    private data class Node(
        val id: String,
        val exact: Boolean = false,
        val children: List<Node> = emptyList(),
    )

    private class Handle(
        val node: Node,
    )

    private class HandleTracker {
        private val handles = mutableListOf<Handle>()
        private val closeCounts = mutableMapOf<Handle, Int>()
        private var borrowedRoot: Handle? = null

        fun borrow(node: Node): Handle = Handle(node).also { borrowedRoot = it }

        fun childCount(handle: Handle): Int = handle.node.children.size

        fun copyChild(
            handle: Handle,
            index: Int,
        ): Handle = copy(handle.node.children[index])

        fun close(handle: Handle) {
            closeCounts[handle] = closeCount(handle) + 1
        }

        fun closeCount(handle: Handle): Int = closeCounts[handle] ?: 0

        fun createdCount(nodeId: String): Int = handles.count { it.node.id == nodeId }

        fun closeCountForNode(nodeId: String): Int =
            handles
                .filter { it.node.id == nodeId }
                .sumOf(::closeCount)

        fun totalCreated(): Int = handles.size

        fun rootCloseCount(): Int = borrowedRoot?.let(::closeCount) ?: 0

        fun assertEveryHandleClosedOnce() {
            assertTrue(handles.isNotEmpty())
            handles.forEach { handle -> assertEquals("node=${handle.node.id}", 1, closeCount(handle)) }
        }

        private fun copy(node: Node): Handle = Handle(node).also(handles::add)
    }
}
