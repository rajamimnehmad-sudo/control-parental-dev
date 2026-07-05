package com.contentfilter.user.updates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.TelemetryRepository
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        private val updateRepository: ApkUpdateRepository,
        private val apkInstaller: ApkInstaller,
        private val localDataRepair: UserLocalDataRepair,
        private val telemetryRepository: TelemetryRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
        private var downloadedApk: File? = null
        private var autoCheckStarted = false

        init {
            if (BuildConfig.FLAVOR == "dev") {
                viewModelScope.launch {
                    telemetryRepository.observeDiagnostics().collect { diagnostics ->
                        _uiState.update {
                            it.copy(
                                diagnosticsText = diagnostics.formatDiagnostics(),
                                diagnosticsSummaryText = diagnostics.formatInternetSummary(),
                            )
                        }
                    }
                }
            }
        }

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

        fun clearDiagnostics() {
            if (BuildConfig.FLAVOR != "dev") return
            viewModelScope.launch {
                runCatching {
                    telemetryRepository.clearDiagnostics()
                    _uiState.update { it.copy(devMessage = "Diagnóstico limpiado.") }
                }.onFailure { exception ->
                    Log.e(LogTag, "Clear diagnostics failed: ${exception.message}", exception)
                    _uiState.update { it.copy(devMessage = "No se pudo limpiar diagnóstico.") }
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

private fun List<TechnicalDiagnostic>.formatDiagnostics(): String {
    if (isEmpty()) return "Sin eventos."
    return joinToString(separator = "\n") { diagnostic ->
        "${diagnostic.occurredAtEpochMillis.toDisplayDate()} ${diagnostic.type} ${diagnostic.message}"
    }
}

private fun List<TechnicalDiagnostic>.formatInternetSummary(): String {
    val messages = map { it.message }
    val latestSnapshot = messages.lastOrNull { it.contains("action=snapshot-received") }
    val latestDnsDecision = messages.lastOrNull { it.contains("layer=vpn-dns") && it.contains("action=dns-decision") }
    val latestBlockedHost = messages.lastOrNull { it.contains("layer=vpn-dns") && it.contains("result=Block") }?.field("host")
    val latestAllowedHost = messages.lastOrNull { it.contains("layer=vpn-dns") && it.contains("result=Allow") }?.field("host")
    val latestBypass =
        messages.lastOrNull {
            it.contains("action=encrypted-dns") ||
                it.contains("action=quic") ||
                it.contains("reason=policy-not-loaded")
        }
    val vpnActive = messages.any { it.contains("VPN started") || it.contains("VPN active") }
    val reconnectApplied = messages.any { it.contains("layer=reconnect") && it.contains("result=applied") }
    val ruleCount = latestSnapshot?.field("ruleCount") ?: "desconocido"
    val searchBlockRules = latestSnapshot?.field("searchBlockRules") ?: "desconocido"
    val strict = latestSnapshot?.field("strict") ?: "desconocido"
    val searchState =
        when {
            latestSnapshot == null -> "desconocidos"
            searchBlockRules.toIntOrNull()?.let { it > 0 } == true -> "bloqueados"
            else -> "permitidos"
        }
    val policyLoaded = latestSnapshot?.field("policyLoaded") ?: "no"
    return listOf(
        "Estado Internet",
        "Politica cargada: $policyLoaded",
        "WebMode strict: $strict",
        "Buscadores: $searchState",
        "Rule count: $ruleCount",
        "Ultimo host bloqueado: ${latestBlockedHost ?: "ninguno"}",
        "Ultimo host permitido: ${latestAllowedHost ?: "ninguno"}",
        "Ultima decision DNS: ${latestDnsDecision?.field("result") ?: "ninguna"}",
        "Ultimo bypass sospechoso: ${latestBypass?.summaryLine() ?: "ninguno"}",
        "VPN activo: ${if (vpnActive) "si" else "no"}",
        "Reconnect aplicado: ${if (reconnectApplied) "si" else "no"}",
    ).joinToString("\n")
}

private fun String.field(name: String): String? =
    split(" ")
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }

private fun String.summaryLine(): String =
    listOfNotNull(field("action"), field("result"), field("protocol"), field("dstPort"), field("reason"))
        .joinToString(" ")
        .ifBlank { take(80) }

private fun Long.toDisplayDate(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
