package com.contentfilter.user.updates

import com.contentfilter.core.update.model.UpdateManifest

data class UpdatesUiState(
    val status: UpdatesStatus = UpdatesStatus.Idle,
    val manifest: UpdateManifest? = null,
    val downloadProgressPercent: Int? = null,
    val adminInstallStatus: AdminInstallStatus = AdminInstallStatus.Idle,
    val adminDownloadProgressPercent: Int? = null,
)

enum class AdminInstallStatus {
    Idle,
    Checking,
    Downloading,
    ReadyToInstall,
    NeedsInstallPermission,
    AlreadyInstalled,
    VerificationFailed,
    Failed,
}

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
