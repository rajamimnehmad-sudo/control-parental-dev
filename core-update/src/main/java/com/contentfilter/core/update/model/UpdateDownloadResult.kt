package com.contentfilter.core.update.model

import java.io.File

sealed interface UpdateDownloadResult {
    data class Success(val apk: File) : UpdateDownloadResult
    data object DownloadError : UpdateDownloadResult
    data object InvalidChecksum : UpdateDownloadResult
}
