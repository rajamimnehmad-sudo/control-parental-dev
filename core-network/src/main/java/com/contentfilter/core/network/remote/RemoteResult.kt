package com.contentfilter.core.network.remote

sealed interface RemoteResult<out T> {
    data class Success<T>(val value: T) : RemoteResult<T>
    data class Failure(val reason: String, val retryable: Boolean) : RemoteResult<Nothing>
}
