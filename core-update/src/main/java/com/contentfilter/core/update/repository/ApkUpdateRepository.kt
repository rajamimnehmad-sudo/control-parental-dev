package com.contentfilter.core.update.repository

import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.model.UpdateManifest

interface ApkUpdateRepository {
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult

    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (Int) -> Unit = {},
    ): UpdateDownloadResult
}
