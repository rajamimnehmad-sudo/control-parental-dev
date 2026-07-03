package com.contentfilter.core.network.dto

import org.json.JSONObject

internal fun JSONObject.optNullableString(name: String): String? {
    if (isNull(name)) return null
    return optString(name)
        .trim()
        .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}
