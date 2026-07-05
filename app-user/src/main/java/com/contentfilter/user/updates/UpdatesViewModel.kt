package com.contentfilter.user.updates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.update.install.ApkInstaller
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.repository.ApkUpdateRepository
import com.contentfilter.user.BuildConfig
import com.contentfilter.user.repair.UserLocalDataRepair
import dagger.hilt.android.lifecycle.HiltViewModel
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
        private val updateRepository: ApkUpdateRepository,
        private val apkInstaller: ApkInstaller,
        private val localDataRepair: UserLocalDataRepair,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
        private var downloadedApk: File? = null
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
                                    status = UpdatesStatus.Downloading,
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
                        UpdateCheckResult.UpToDate -> {
                            _uiState.value = UpdatesUiState(status = UpdatesStatus.UpToDate)
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
                _uiState.update { it.copy(status = UpdatesStatus.Downloading) }
                runCatching {
                    downloadAndMaybeInstall(manifest, openInstaller = true)
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

        fun resetLocalDataForRelink() {
            viewModelScope.launch {
                runCatching {
                    localDataRepair.resetLocalDataForRelink()
                    _uiState.update {
                        it.copy(devMessage = "Datos locales limpiados. Volvé a enlazar con un código nuevo.")
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Local relink reset failed: ${exception.message}", exception)
                    _uiState.update {
                        it.copy(devMessage = "No se pudo limpiar datos locales.")
                    }
                }
            }
        }

        private suspend fun downloadAndMaybeInstall(
            manifest: com.contentfilter.core.update.model.UpdateManifest,
            openInstaller: Boolean,
        ) {
            when (val result = updateRepository.download(manifest)) {
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update { it.copy(status = UpdatesStatus.DownloadFailed) }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update { it.copy(status = UpdatesStatus.ChecksumFailed) }
                }
                is UpdateDownloadResult.Success -> {
                    downloadedApk = result.apk
                    if (apkInstaller.canRequestPackageInstalls()) {
                        _uiState.update { it.copy(status = UpdatesStatus.ReadyToInstall) }
                        if (openInstaller) apkInstaller.openPackageInstaller(result.apk)
                    } else {
                        _uiState.update { it.copy(status = UpdatesStatus.NeedsInstallPermission) }
                    }
                }
            }
        }

        private companion object {
            const val LogTag = "Updates"
        }
    }
