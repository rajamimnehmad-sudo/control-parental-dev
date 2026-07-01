package com.contentfilter.core.network.remote

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

private const val LogTag = "SupabaseRemote"
internal const val OfflineUserMessage = "Sin conexión. Mostrando datos guardados."

internal fun httpFailure(
    source: String,
    code: Int,
    responseBody: String,
): RemoteResult.Failure {
    val reason = if (code >= 500) {
        OfflineUserMessage
    } else {
        "No se pudo completar la operación."
    }
    Log.w(LogTag, "$source failed. HTTP $code. Body: ${responseBody.ifBlank { "<empty>" }}")
    return RemoteResult.Failure(reason = reason, retryable = code >= 500)
}

internal fun exceptionFailure(
    source: String,
    exception: Exception,
): RemoteResult.Failure {
    val message = exception.message?.takeIf { it.isNotBlank() } ?: exception.toString()
    Log.e(LogTag, "$source failed with ${exception.javaClass.name}: $message", exception)
    return RemoteResult.Failure(
        reason = if (exception.isConnectivityFailure()) {
            OfflineUserMessage
        } else {
            "$source failed: ${exception.javaClass.simpleName}: $message"
        },
        retryable = true,
    )
}

private fun Exception.isConnectivityFailure(): Boolean =
    this is UnknownHostException ||
        this is SocketTimeoutException ||
        this is ConnectException ||
        this is IOException

private fun String.toReadableErrorDetail(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val json = JSONObject(trimmed)
        listOf(
            json.optString("message"),
            json.optString("msg"),
            json.optString("error_description"),
            json.optString("error"),
            json.optString("hint"),
            json.optString("details"),
            json.optString("code"),
        ).filter { it.isNotBlank() }
            .distinct()
            .joinToString(separator = " | ")
            .ifBlank { trimmed }
    }.recoverCatching {
        val array = JSONArray(trimmed)
        if (array.length() == 0) trimmed else array.toString()
    }.getOrElse {
        trimmed
    }
}
