package com.contentfilter.core.network.remote

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal fun <T> RemoteResult<JSONArray>.mapArray(mapper: (JSONObject) -> T): RemoteResult<List<T>> =
    when (this) {
        is RemoteResult.Failure -> this
        is RemoteResult.Success -> {
            runCatching {
                val values = buildList {
                    for (index in 0 until value.length()) {
                        add(mapper(value.getJSONObject(index)))
                    }
                }
                RemoteResult.Success(values)
            }.getOrElse { exception ->
                Log.e(LogTag, "Remote array mapping failed: ${exception.message}", exception)
                RemoteResult.Failure("Remote response could not be parsed.", retryable = true)
            }
        }
    }

private const val LogTag = "RemoteResultMappers"
