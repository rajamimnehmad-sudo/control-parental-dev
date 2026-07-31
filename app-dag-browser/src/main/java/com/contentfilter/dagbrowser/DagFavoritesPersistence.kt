package com.contentfilter.dagbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class DagFavorite(
    val url: String,
    val title: String,
)

internal class DagFavoritesPersistence(context: Context) {
    private val preferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(isAllowedUrl: (String) -> Boolean): List<DagFavorite> =
        DagFavoritesCodec.decode(preferences.getString(StateKey, null), isAllowedUrl)

    fun toggle(
        favorite: DagFavorite,
        isAllowedUrl: (String) -> Boolean,
    ): Boolean {
        if (!isAllowedUrl(favorite.url)) return false
        val current = load(isAllowedUrl)
        val updated =
            if (current.any { it.url == favorite.url }) {
                current.filterNot { it.url == favorite.url }
            } else {
                listOf(favorite) + current
            }
        preferences.edit().putString(StateKey, DagFavoritesCodec.encode(updated)).apply()
        return updated.any { it.url == favorite.url }
    }

    private companion object {
        const val PreferencesName = "dag_browser_favorites"
        const val StateKey = "favorites_v1"
    }
}

internal object DagFavoritesCodec {
    const val MaxEntries = 100
    private const val SchemaVersion = 1
    private const val MaxUrlLength = 4_096
    private const val MaxTitleLength = 160

    fun encode(favorites: List<DagFavorite>): String {
        val rows = JSONArray()
        favorites.take(MaxEntries).forEach { favorite ->
            rows.put(
                JSONObject()
                    .put("url", favorite.url.take(MaxUrlLength))
                    .put("title", favorite.title.take(MaxTitleLength)),
            )
        }
        return JSONObject().put("version", SchemaVersion).put("favorites", rows).toString()
    }

    fun decode(
        raw: String?,
        isAllowedUrl: (String) -> Boolean,
    ): List<DagFavorite> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            require(root.optInt("version", -1) == SchemaVersion)
            val rows = root.optJSONArray("favorites") ?: return emptyList()
            buildList {
                for (index in 0 until minOf(rows.length(), MaxEntries)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val url = row.optString("url").take(MaxUrlLength)
                    if (!isAllowedUrl(url)) continue
                    add(DagFavorite(url, row.optString("title").take(MaxTitleLength)))
                }
            }.distinctBy(DagFavorite::url)
        }.getOrDefault(emptyList())
    }
}
