package com.contentfilter.dagbrowser

import org.json.JSONArray
import org.json.JSONObject

internal data class DagPersistedTab(
    val url: String,
    val title: String,
)

internal data class DagPersistedTabs(
    val tabs: List<DagPersistedTab>,
    val activeIndex: Int,
)

internal object DagTabStateCodec {
    private const val SchemaVersion = 1
    private const val MaxTabs = 8
    private const val MaxUrlLength = 4_096
    private const val MaxTitleLength = 160

    fun encode(state: DagPersistedTabs): String {
        val rows = JSONArray()
        state.tabs.take(MaxTabs).forEach { tab ->
            rows.put(
                JSONObject()
                    .put("url", tab.url.take(MaxUrlLength))
                    .put("title", tab.title.take(MaxTitleLength)),
            )
        }
        return JSONObject()
            .put("version", SchemaVersion)
            .put("activeIndex", state.activeIndex.coerceIn(0, (rows.length() - 1).coerceAtLeast(0)))
            .put("tabs", rows)
            .toString()
    }

    fun decode(
        raw: String?,
        isAllowedUrl: (String) -> Boolean,
    ): DagPersistedTabs? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            require(root.optInt("version", -1) == SchemaVersion)
            val rows = root.optJSONArray("tabs") ?: return null
            val requestedActiveIndex = root.optInt("activeIndex", 0)
            val indexedTabs =
                buildList {
                    for (index in 0 until minOf(rows.length(), MaxTabs)) {
                        val row = rows.optJSONObject(index) ?: continue
                        val url = row.optString("url").take(MaxUrlLength)
                        if (!isAllowedUrl(url)) continue
                        add(
                            index to
                                DagPersistedTab(
                                    url = url,
                                    title = row.optString("title").take(MaxTitleLength),
                                ),
                        )
                    }
                }
            if (indexedTabs.isEmpty()) return null
            val tabs = indexedTabs.map { it.second }
            val activeIndex =
                indexedTabs.indexOfFirst { it.first == requestedActiveIndex }
                    .takeIf { it >= 0 }
                    ?: indexedTabs.indexOfLast { it.first < requestedActiveIndex }.coerceAtLeast(0)
            DagPersistedTabs(
                tabs = tabs,
                activeIndex = activeIndex.coerceIn(tabs.indices),
            )
        }.getOrNull()
    }
}
