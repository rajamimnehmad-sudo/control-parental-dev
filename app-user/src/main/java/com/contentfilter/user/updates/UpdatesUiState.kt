package com.contentfilter.user.updates

import com.contentfilter.core.update.model.UpdateManifest

data class UpdatesUiState(
    val status: UpdatesStatus = UpdatesStatus.Idle,
    val manifest: UpdateManifest? = null,
    val downloadProgressPercent: Int? = null,
    val adminInstallStatus: CompanionInstallStatus = CompanionInstallStatus.Idle,
    val adminDownloadProgressPercent: Int? = null,
    val dagInstallStatus: CompanionInstallStatus = CompanionInstallStatus.Idle,
    val dagDownloadProgressPercent: Int? = null,
)

enum class CompanionInstallStatus {
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
