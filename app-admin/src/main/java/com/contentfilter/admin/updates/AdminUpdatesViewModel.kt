package com.contentfilter.admin.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.contentfilter.admin.BuildConfig
import com.contentfilter.core.update.install.ApkInstaller
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.repository.ApkUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AdminUpdatesViewModel
    @Inject
    constructor(
        private val updateRepository: ApkUpdateRepository,
        private val apkInstaller: ApkInstaller,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AdminUpdatesUiState())
        val uiState: StateFlow<AdminUpdatesUiState> = _uiState.asStateFlow()
        private var downloadedApk: File? = null
        private var autoCheckStarted = false

        fun autoCheckAndDownload() {
            if (autoCheckStarted) return
            autoCheckStarted = true
            viewModelScope.launch {
                runCatching {
                    when (val result = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE)) {
                        is UpdateCheckResult.Available -> {
                            _uiState.value = AdminUpdatesUiState(
                                status = AdminUpdatesStatus.Downloading,
                                manifest = result.manifest,
                            )
                            downloadAndMaybeInstall(result.manifest, openInstaller = true)
                        }
                        UpdateCheckResult.NetworkError,
                        UpdateCheckResult.NotConfigured,
                        UpdateCheckResult.UpToDate,
                        -> Unit
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Admin auto update failed: ${exception.message}", exception)
                }
            }
        }

        fun checkForUpdates() {
            viewModelScope.launch {
                _uiState.value = AdminUpdatesUiState(status = AdminUpdatesStatus.Checking)
                runCatching {
                    when (val result = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE)) {
                        is UpdateCheckResult.Available -> {
                            _uiState.value = AdminUpdatesUiState(
                                status = AdminUpdatesStatus.Available,
                                manifest = result.manifest,
                            )
                        }
                        UpdateCheckResult.NetworkError -> {
                            _uiState.value = AdminUpdatesUiState(status = AdminUpdatesStatus.SearchFailed)
                        }
                        UpdateCheckResult.NotConfigured -> {
                            _uiState.value = AdminUpdatesUiState(status = AdminUpdatesStatus.NotConfigured)
                        }
                        UpdateCheckResult.UpToDate -> {
                            _uiState.value = AdminUpdatesUiState(status = AdminUpdatesStatus.UpToDate)
                        }
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Admin update check failed: ${exception.message}", exception)
                    _uiState.value = AdminUpdatesUiState(status = AdminUpdatesStatus.SearchFailed)
                }
            }
        }

        fun downloadUpdate() {
            val manifest = _uiState.value.manifest ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(status = AdminUpdatesStatus.Downloading) }
                runCatching {
                    downloadAndMaybeInstall(manifest, openInstaller = true)
                }.onFailure { exception ->
                    Log.e(LogTag, "Admin update download failed: ${exception.message}", exception)
                    _uiState.update { it.copy(status = AdminUpdatesStatus.DownloadFailed) }
                }
            }
        }

        fun installDownloadedUpdate() {
            val apk = downloadedApk ?: return
            if (apkInstaller.canRequestPackageInstalls()) {
                apkInstaller.openPackageInstaller(apk)
            } else {
                _uiState.update { it.copy(status = AdminUpdatesStatus.NeedsInstallPermission) }
            }
        }

        fun openInstallPermissionSettings() {
            runCatching {
                apkInstaller.openInstallPermissionSettings()
            }.onFailure { exception ->
                Log.e(LogTag, "Open install permission settings failed: ${exception.message}", exception)
            }
        }

        private suspend fun downloadAndMaybeInstall(
            manifest: com.contentfilter.core.update.model.UpdateManifest,
            openInstaller: Boolean,
        ) {
            when (val result = updateRepository.download(manifest)) {
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update { it.copy(status = AdminUpdatesStatus.DownloadFailed) }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update { it.copy(status = AdminUpdatesStatus.ChecksumFailed) }
                }
                is UpdateDownloadResult.Success -> {
                    downloadedApk = result.apk
                    if (apkInstaller.canRequestPackageInstalls()) {
                        _uiState.update { it.copy(status = AdminUpdatesStatus.ReadyToInstall) }
                        if (openInstaller) apkInstaller.openPackageInstaller(result.apk)
                    } else {
                        _uiState.update { it.copy(status = AdminUpdatesStatus.NeedsInstallPermission) }
                    }
                }
            }
        }

        private companion object {
            const val LogTag = "AdminUpdates"
        }
    }
