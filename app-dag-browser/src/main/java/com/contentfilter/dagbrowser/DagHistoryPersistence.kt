package com.contentfilter.dagbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class DagHistoryEntry(
    val url: String,
    val title: String,
    val visitedAtMillis: Long,
)

internal class DagHistoryPersistence(context: Context) {
    private val preferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(isAllowedUrl: (String) -> Boolean): List<DagHistoryEntry> =
        DagHistoryCodec.decode(
            raw = preferences.getString(StateKey, null),
            isAllowedUrl = isAllowedUrl,
        )

    fun record(
        entry: DagHistoryEntry,
        isAllowedUrl: (String) -> Boolean,
    ) {
        if (!isAllowedUrl(entry.url)) return
        val updated =
            buildList {
                add(entry)
                addAll(load(isAllowedUrl).filterNot { it.url == entry.url })
            }.take(DagHistoryCodec.MaxEntries)
        preferences.edit().putString(StateKey, DagHistoryCodec.encode(updated)).apply()
    }

    fun clear() {
        preferences.edit().remove(StateKey).apply()
    }

    private companion object {
        const val PreferencesName = "dag_browser_history"
        const val StateKey = "history_v1"
    }
}

internal object DagHistoryCodec {
    const val MaxEntries = 100
    private const val SchemaVersion = 1
    private const val MaxUrlLength = 4_096
    private const val MaxTitleLength = 160

    fun encode(entries: List<DagHistoryEntry>): String {
        val rows = JSONArray()
        entries.take(MaxEntries).forEach { entry ->
            rows.put(
                JSONObject()
                    .put("url", entry.url.take(MaxUrlLength))
                    .put("title", entry.title.take(MaxTitleLength))
                    .put("visitedAtMillis", entry.visitedAtMillis.coerceAtLeast(0L)),
            )
        }
        return JSONObject()
            .put("version", SchemaVersion)
            .put("entries", rows)
            .toString()
    }

    fun decode(
        raw: String?,
        isAllowedUrl: (String) -> Boolean,
    ): List<DagHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            require(root.optInt("version", -1) == SchemaVersion)
            val rows = root.optJSONArray("entries") ?: return emptyList()
            buildList {
                for (index in 0 until minOf(rows.length(), MaxEntries)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val url = row.optString("url").take(MaxUrlLength)
                    if (!isAllowedUrl(url)) continue
                    add(
                        DagHistoryEntry(
                            url = url,
                            title = row.optString("title").take(MaxTitleLength),
                            visitedAtMillis = row.optLong("visitedAtMillis").coerceAtLeast(0L),
                        ),
                    )
                }
            }.distinctBy(DagHistoryEntry::url)
        }.getOrDefault(emptyList())
    }
}
