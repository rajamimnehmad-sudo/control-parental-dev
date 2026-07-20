package com.contentfilter.admin.updates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.admin.BuildConfig
import com.contentfilter.admin.auth.AdminLocalDataResetter
import com.contentfilter.core.sync.realtime.RealtimeSyncCoordinator
import com.contentfilter.core.update.install.ApkInstaller
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.repository.ApkUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AdminUpdatesViewModel
    @Inject
    constructor(
        private val updateRepository: ApkUpdateRepository,
        private val apkInstaller: ApkInstaller,
        private val localDataResetter: AdminLocalDataResetter,
        private val realtimeSyncCoordinator: RealtimeSyncCoordinator,
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
                            _uiState.value =
                                AdminUpdatesUiState(
                                    status = AdminUpdatesStatus.Available,
                                    manifest = result.manifest,
                                )
                        }
                        UpdateCheckResult.NetworkError,
                        UpdateCheckResult.NotConfigured,
                        -> Unit
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.value =
                                AdminUpdatesUiState(
                                    status = AdminUpdatesStatus.UpToDate,
                                    manifest = result.manifest,
                                )
                        }
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
                            _uiState.value =
                                AdminUpdatesUiState(
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
                        is UpdateCheckResult.UpToDate -> {
                            _uiState.value =
                                AdminUpdatesUiState(
                                    status = AdminUpdatesStatus.UpToDate,
                                    manifest = result.manifest,
                                )
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
                _uiState.update { it.copy(status = AdminUpdatesStatus.Downloading, downloadProgressPercent = 0) }
                runCatching {
                    downloadAndMaybeInstall(manifest)
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

        fun resumePendingInstallAfterPermission() {
            if (
                _uiState.value.status != AdminUpdatesStatus.NeedsInstallPermission ||
                downloadedApk == null ||
                !apkInstaller.canRequestPackageInstalls()
            ) {
                return
            }
            _uiState.update { it.copy(status = AdminUpdatesStatus.ReadyToInstall) }
            installDownloadedUpdate()
        }

        fun requestResetLocalAdmin() {
            _uiState.update { it.copy(showResetConfirmation = true, resetMessage = "") }
        }

        fun dismissResetLocalAdmin() {
            _uiState.update { it.copy(showResetConfirmation = false) }
        }

        fun resetLocalAdmin() {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        showResetConfirmation = false,
                        resetMessage = "Reseteando administrador local...",
                    )
                }
                runCatching {
                    realtimeSyncCoordinator.stop()
                    localDataResetter.resetForNewAdminToken()
                }.onSuccess {
                    _uiState.update {
                        it.copy(resetMessage = "Listo. La app volverá al Login para ingresar el nuevo token.")
                    }
                }.onFailure { exception ->
                    Log.e(LogTag, "Admin local reset failed: ${exception.message}", exception)
                    _uiState.update {
                        it.copy(resetMessage = "No se pudo resetear. Cerrá la app y volvé a intentar.")
                    }
                }
            }
        }

        private suspend fun downloadAndMaybeInstall(manifest: com.contentfilter.core.update.model.UpdateManifest) {
            when (
                val result =
                    updateRepository.download(manifest) { progress ->
                        _uiState.update { it.copy(downloadProgressPercent = progress) }
                    }
            ) {
                UpdateDownloadResult.DownloadError -> {
                    _uiState.update {
                        it.copy(
                            status = AdminUpdatesStatus.DownloadFailed,
                            downloadProgressPercent = null,
                        )
                    }
                }
                UpdateDownloadResult.InvalidChecksum -> {
                    _uiState.update {
                        it.copy(
                            status = AdminUpdatesStatus.ChecksumFailed,
                            downloadProgressPercent = null,
                        )
                    }
                }
                is UpdateDownloadResult.Success -> {
                    downloadedApk = result.apk
                    if (apkInstaller.canRequestPackageInstalls()) {
                        _uiState.update {
                            it.copy(
                                status = AdminUpdatesStatus.ReadyToInstall,
                                downloadProgressPercent = 100,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                status = AdminUpdatesStatus.NeedsInstallPermission,
                                downloadProgressPercent = 100,
                            )
                        }
                    }
                }
            }
        }

        private companion object {
            const val LogTag = "AdminUpdates"
        }
    }
