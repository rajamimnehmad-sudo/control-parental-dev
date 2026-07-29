package com.contentfilter.dagbrowser

import android.content.Context

internal class DagTabPersistence(context: Context) {
    private val preferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(isAllowedUrl: (String) -> Boolean): DagPersistedTabs? =
        DagTabStateCodec.decode(
            raw = preferences.getString(StateKey, null),
            isAllowedUrl = isAllowedUrl,
        )

    fun save(state: DagPersistedTabs) {
        preferences.edit()
            .putString(StateKey, DagTabStateCodec.encode(state))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(StateKey).apply()
    }

    private companion object {
        const val PreferencesName = "dag_browser_tabs"
        const val StateKey = "tabs_v1"
    }
}
