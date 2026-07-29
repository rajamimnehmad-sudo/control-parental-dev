package com.contentfilter.user.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.update.config.UpdateConfigProvider
import com.contentfilter.core.update.install.ApkInstaller
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.repository.ApkUpdateRepository
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val updateRepository: ApkUpdateRepository,
        private val updateConfigProvider: UpdateConfigProvider,
        private val apkInstaller: ApkInstaller,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
        private var downloadedApk: File? = null
        private var downloadedAdminApk: File? = null
        private var downloadedDagApk: File? = null
        private var autoCheckStarted = false

        fun autoCheckAndDownload() {
            if (autoCheckStarted) return
            autoCheckStarted = true
            viewModelScope.launch {
                runCatching {
                    when (val result = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE)) {
                        is UpdateCheckResult.Available -> {
                            _uiState.value =
                                UpdatesUiState(
                                    status = UpdatesStatus.Available,
                                    manifest = result.manifest,
                                )
                        }
                        UpdateCheckResult.NetworkError,
                        UpdateCheckResult.NotConfigured,
                        -> Unit
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.value =
                                UpdatesUiState(
                                    status = UpdatesStatus.UpToDate,
                                    manifest = result.manifest,
                                )
                        }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Auto update failed: ${exception.message}", exception)
                }
            }
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                _uiState.value = UpdatesUiState(status = UpdatesStatus.Checking)
                runCatching {
                    when (val result = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE)) {
                        is UpdateCheckResult.Available -> {
                            _uiState.value =
                                UpdatesUiState(
                                    status = UpdatesStatus.Available,
                                    manifest = result.manifest,
                                )
                        }
                        UpdateCheckResult.NetworkError -> {
                            _uiState.value = UpdatesUiState(status = UpdatesStatus.SearchFailed)
                        }
                        UpdateCheckResult.NotConfigured -> {
                            _uiState.value = UpdatesUiState(status = UpdatesStatus.NotConfigured)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.value =
                                UpdatesUiState(
                                    status = UpdatesStatus.UpToDate,
                                    manifest = result.manifest,
                                )
                        }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Update check failed: ${exception.message}", exception)
                    _uiState.value = UpdatesUiState(status = UpdatesStatus.SearchFailed)
                }
            }
        }

        fun downloadUpdate() {
            val manifest = _uiState.value.manifest ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(status = UpdatesStatus.Downloading, downloadProgressPercent = 0) }
                runCatching {
                    downloadAndMaybeInstall(manifest)
                }.onFailure { exception ->
                    Log.e(LogTag, "Update download failed: ${exception.message}", exception)
                    _uiState.update { it.copy(status = UpdatesStatus.DownloadFailed) }
                }
            }
        }

        fun installDownloadedUpdate() {
            val apk = downloadedApk ?: return
            if (apkInstaller.canRequestPackageInstalls()) {
                apkInstaller.openPackageInstaller(apk)
            } else {
                _uiState.update { it.copy(status = UpdatesStatus.NeedsInstallPermission) }
            }
        }

        fun openInstallPermissionSettings() {
            runCatching {
                apkInstaller.openInstallPermissionSettings()
            }.onFailure { exception ->
                Log.e(LogTag, "Open install permission settings failed: ${exception.message}", exception)
            }
        }

        fun resumePendingInstallAfterPermission() {
            if (!apkInstaller.canRequestPackageInstalls()) return
            when {
                _uiState.value.status == UpdatesStatus.NeedsInstallPermission && downloadedApk != null -> {
                    _uiState.update { it.copy(status = UpdatesStatus.ReadyToInstall) }
                    installDownloadedUpdate()
                }
                _uiState.value.adminInstallStatus == CompanionInstallStatus.NeedsInstallPermission &&
                    downloadedAdminApk != null -> {
                    _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.ReadyToInstall) }
                    installDownloadedAdmin()
                }
                _uiState.value.dagInstallStatus == CompanionInstallStatus.NeedsInstallPermission &&
                    downloadedDagApk != null -> {
                    _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.ReadyToInstall) }
                    installDownloadedDag()
                }
            }
        }

        fun prepareAdminInstall() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        adminInstallStatus = CompanionInstallStatus.Checking,
                        adminDownloadProgressPercent = null,
                    )
                }
                runCatching {
                    when (
                        val result =
                            updateRepository.checkForUpdate(
                                updateConfigProvider.adminManifestUrl(),
                                installedAdminVersionCode(),
                            )
                    ) {
                        is UpdateCheckResult.Available -> downloadAdmin(result.manifest)
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.AlreadyInstalled) }
                        }
                        UpdateCheckResult.NetworkError,
                        UpdateCheckResult.NotConfigured,
                        -> _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.Failed) }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Admin bootstrap failed: ${exception.message}", exception)
                    _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.Failed) }
                }
            }
        }

        fun installDownloadedAdmin() {
            val apk = downloadedAdminApk ?: return
            if (!apkInstaller.canRequestPackageInstalls()) {
                _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.NeedsInstallPermission) }
                return
            }
            val opened = apkInstaller.openVerifiedCompanionInstaller(apk, adminPackageName())
            if (!opened) {
                _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.VerificationFailed) }
            }
        }

        fun prepareDagInstall() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        dagInstallStatus = CompanionInstallStatus.Checking,
                        dagDownloadProgressPercent = null,
                    )
                }
                runCatching {
                    when (
                        val result =
                            updateRepository.checkForUpdate(
                                dagManifestUrl(updateConfigProvider.manifestUrl()),
                                installedVersionCode(dagPackageName()),
                            )
                    ) {
                        is UpdateCheckResult.Available -> downloadDag(result.manifest)
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.AlreadyInstalled) }
                        }
                        UpdateCheckResult.NetworkError,
                        UpdateCheckResult.NotConfigured,
                        -> _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.Failed) }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "DAG bootstrap failed: ${exception.message}", exception)
                    _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.Failed) }
                }
            }
        }

        fun installDownloadedDag() {
            val apk = downloadedDagApk ?: return
            if (!apkInstaller.canRequestPackageInstalls()) {
                _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.NeedsInstallPermission) }
                return
            }
            val opened = apkInstaller.openVerifiedCompanionInstaller(apk, dagPackageName())
            if (!opened) {
                _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.VerificationFailed) }
            }
        }

        private suspend fun downloadAdmin(manifest: com.contentfilter.core.update.model.UpdateManifest) {
            _uiState.update {
                it.copy(
                    adminInstallStatus = CompanionInstallStatus.Downloading,
                    adminDownloadProgressPercent = 0,
                )
            }
            when (
                val result =
                    updateRepository.download(manifest) { progress ->
                        _uiState.update { it.copy(adminDownloadProgressPercent = progress) }
                    }
            ) {
                is UpdateDownloadResult.Success -> {
                    downloadedAdminApk = result.apk
                    _uiState.update {
                        it.copy(
                            adminInstallStatus =
                                if (apkInstaller.canRequestPackageInstalls()) {
                                    CompanionInstallStatus.ReadyToInstall
                                } else {
                                    CompanionInstallStatus.NeedsInstallPermission
                                },
                            adminDownloadProgressPercent = 100,
                        )
                    }
                }
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.Failed) }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update { it.copy(adminInstallStatus = CompanionInstallStatus.VerificationFailed) }
                }
            }
        }

        private suspend fun downloadDag(manifest: com.contentfilter.core.update.model.UpdateManifest) {
            _uiState.update {
                it.copy(
                    dagInstallStatus = CompanionInstallStatus.Downloading,
                    dagDownloadProgressPercent = 0,
                )
            }
            when (
                val result =
                    updateRepository.download(manifest) { progress ->
                        _uiState.update { it.copy(dagDownloadProgressPercent = progress) }
                    }
            ) {
                is UpdateDownloadResult.Success -> {
                    downloadedDagApk = result.apk
                    _uiState.update {
                        it.copy(
                            dagInstallStatus =
                                if (apkInstaller.canRequestPackageInstalls()) {
                                    CompanionInstallStatus.ReadyToInstall
                                } else {
                                    CompanionInstallStatus.NeedsInstallPermission
                                },
                            dagDownloadProgressPercent = 100,
                        )
                    }
                }
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.Failed) }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update { it.copy(dagInstallStatus = CompanionInstallStatus.VerificationFailed) }
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun installedAdminVersionCode(): Int = installedVersionCode(adminPackageName())

        @Suppress("DEPRECATION")
        private fun installedVersionCode(packageName: String): Int =
            runCatching {
                val packageInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(0),
                        )
                    } else {
                        context.packageManager.getPackageInfo(packageName, 0)
                    }
                packageInfo.longVersionCode.toInt()
            }.getOrDefault(0)

        private fun adminPackageName(): String =
            when {
                context.packageName.endsWith(".dev") -> "com.contentfilter.admin.dev"
                context.packageName.endsWith(".beta") -> "com.contentfilter.admin.beta"
                else -> "com.contentfilter.admin"
            }

        private fun dagPackageName(): String =
            when {
                context.packageName.endsWith(".dev") -> "com.contentfilter.dagbrowser.dev"
                context.packageName.endsWith(".beta") -> "com.contentfilter.dagbrowser.beta"
                else -> "com.contentfilter.dagbrowser"
            }

        private suspend fun downloadAndMaybeInstall(manifest: com.contentfilter.core.update.model.UpdateManifest) {
            when (
                val result =
                    updateRepository.download(manifest) { progress ->
                        _uiState.update { it.copy(downloadProgressPercent = progress) }
                    }
            ) {
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update { it.copy(status = UpdatesStatus.DownloadFailed, downloadProgressPercent = null) }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update { it.copy(status = UpdatesStatus.ChecksumFailed, downloadProgressPercent = null) }
                }
                is UpdateDownloadResult.Success -> {
                    downloadedApk = result.apk
                    if (apkInstaller.canRequestPackageInstalls()) {
                        _uiState.update {
                            it.copy(
                                status = UpdatesStatus.ReadyToInstall,
                                downloadProgressPercent = 100,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                status = UpdatesStatus.NeedsInstallPermission,
                                downloadProgressPercent = 100,
                            )
                        }
                    }
                }
            }
        }

        private companion object {
            const val LogTag = "Updates"
        }
    }

internal fun dagManifestUrl(baseUrl: String): String {
    if (baseUrl.isBlank()) return ""
    val suffixIndex = baseUrl.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) baseUrl.length else it }
    val directory = baseUrl.substring(0, suffixIndex).substringBeforeLast('/', missingDelimiterValue = "")
    return if (directory.isBlank()) "" else "$directory/app-dag-browser-dev-manifest.json"
}
