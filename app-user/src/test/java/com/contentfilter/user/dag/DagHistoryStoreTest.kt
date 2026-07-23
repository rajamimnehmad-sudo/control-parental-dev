package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals

class DagHistoryStoreTest {
    @Test
    fun `history codec preserves searches pages and unicode`() {
        val expected =
            listOf(
                DagHistoryEntry(
                    id = "search-1",
                    type = DagHistoryType.Search,
                    value = "זמני תפילה",
                    url = null,
                    title = "זמני תפילה",
                    visitedAtEpochMillis = 10L,
                ),
                DagHistoryEntry(
                    id = "page-1",
                    type = DagHistoryType.Page,
                    value = "https://example.com/guía",
                    url = "https://example.com/guía",
                    title = "Guía segura",
                    visitedAtEpochMillis = 20L,
                ),
            )

        val decoded = DagHistoryStore.decodeEntries(DagHistoryStore.encodeEntries(expected))

        assertEquals(expected, decoded)
    }

    @Test
    fun `page approval codec preserves exact scope and invalidation metadata`() {
        val expected =
            listOf(
                DagPageApproval(
                    url = "https://example.com/product?id=1",
                    fingerprint = "abc123",
                    policyVersion = 42L,
                    modelVersion = "model-v1",
                    approvedAtEpochMillis = 100L,
                    expiresAtEpochMillis = 200L,
                ),
            )

        val decoded = DagHistoryStore.decodePageApprovals(DagHistoryStore.encodePageApprovals(expected))

        assertEquals(expected, decoded)
    }

    @Test
    fun `tab session codec preserves tabs and forces browser revalidation`() {
        val result =
            DagSearchResult(
                title = "Resultado",
                url = "https://example.com",
                domain = "example.com",
                description = "Descripción",
                classification =
                    DagClassificationResult(
                        decision = DagClassification.Allowed,
                        category = "safe",
                        confidence = 0.98f,
                        modelVersion = "model-v1",
                    ),
            )
        val session =
            DagSavedTabSession(
                activeTabId = "browser",
                tabs =
                    listOf(
                        DagSavedTab(
                            id = "results",
                            lastUsedAtEpochMillis = 100L,
                            snapshot =
                                DagTabSnapshot(
                                    address = "consulta",
                                    view = DagView.Results,
                                    results = listOf(result),
                                ),
                        ),
                        DagSavedTab(
                            id = "browser",
                            lastUsedAtEpochMillis = 200L,
                            snapshot =
                                DagTabSnapshot(
                                    address = "https://example.com",
                                    view = DagView.Browser,
                                    pageStatus = DagPageStatus.Visible,
                                    results = listOf(result),
                                    requestedUrl = "https://example.com",
                                ),
                        ),
                    ),
            )

        val decoded = DagHistoryStore.decodeTabSession(DagHistoryStore.encodeTabSession(session))

        assertEquals("browser", decoded.activeTabId)
        assertEquals(listOf(result), decoded.tabs.first().snapshot.results)
        assertEquals(DagPageStatus.Loading, decoded.tabs.last().snapshot.pageStatus)
        assertEquals("https://example.com", decoded.tabs.last().snapshot.requestedUrl)
        assertEquals(listOf(result), decoded.tabs.last().snapshot.results)
        assertEquals(listOf(100L, 200L), decoded.tabs.map { it.lastUsedAtEpochMillis })
    }

    @Test
    fun `empty tab requires a clean start snapshot`() {
        assertEquals(true, DagTabSnapshot().isEmptyTab())
        assertEquals(false, DagTabSnapshot(address = "consulta").isEmptyTab())
        assertEquals(false, DagTabSnapshot(view = DagView.History).isEmptyTab())
        assertEquals(false, DagTabSnapshot(requestedUrl = "https://example.com").isEmptyTab())
    }

    @Test
    fun `tab session persists at most fifty suspended tabs`() {
        val tabs =
            (1..55).map { index ->
                DagSavedTab(
                    id = "tab-$index",
                    snapshot = DagTabSnapshot(address = "consulta $index", view = DagView.Results),
                    lastUsedAtEpochMillis = index.toLong(),
                )
            }
        val decoded =
            DagHistoryStore.decodeTabSession(
                DagHistoryStore.encodeTabSession(DagSavedTabSession(activeTabId = "tab-1", tabs = tabs)),
            )

        assertEquals(50, decoded.tabs.size)
    }
}
