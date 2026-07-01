package com.contentfilter.admin.updates

import com.contentfilter.core.update.model.UpdateManifest

data class AdminUpdatesUiState(
    val status: AdminUpdatesStatus = AdminUpdatesStatus.Idle,
    val manifest: UpdateManifest? = null,
)

enum class AdminUpdatesStatus {
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
