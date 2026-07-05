package com.contentfilter.user.updates

import com.contentfilter.core.update.model.UpdateManifest

data class UpdatesUiState(
    val status: UpdatesStatus = UpdatesStatus.Idle,
    val manifest: UpdateManifest? = null,
    val devMessage: String = "",
)

enum class UpdatesStatus {
    Idle,
    Checking,
    Available,
    UpToDate,
    NotConfigured,
    SearchFailed,
    Downloading,
    ReadyToInstall,
    NeedsInstallPermission,
    ChecksumFailed,
    DownloadFailed,
}
