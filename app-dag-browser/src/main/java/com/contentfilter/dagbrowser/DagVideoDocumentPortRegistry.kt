package com.contentfilter.dagbrowser

internal data class DagVideoDocumentPort<Port : Any>(
    val tabId: Long,
    val port: Port,
    val eligibleTopLevelDocument: Boolean,
    val fixture: Boolean,
)

internal class DagVideoDocumentPortRegistry<Port : Any> {
    private val currentByTab = mutableMapOf<Long, DagVideoDocumentPort<Port>>()

    fun connect(document: DagVideoDocumentPort<Port>) {
        currentByTab[document.tabId] = document
    }

    fun disconnect(
        tabId: Long,
        port: Port,
    ) {
        val current = currentByTab[tabId] ?: return
        if (current.port === port) currentByTab.remove(tabId)
    }

    fun current(tabId: Long): DagVideoDocumentPort<Port>? = currentByTab[tabId]

    fun remove(tabId: Long) {
        currentByTab.remove(tabId)
    }
}
