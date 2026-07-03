package com.contentfilter.user.internet

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.AccessRequest
import com.contentfilter.core.domain.model.AccessRequestType
import com.contentfilter.core.domain.model.PolicyTargetType
import com.contentfilter.core.domain.model.RequestStatus
import com.contentfilter.core.domain.repository.AccessRequestRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.dto.RemoteAccessRequestDto
import com.contentfilter.core.network.remote.RemoteRequestRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.sync.SyncScheduler
import com.contentfilter.core.sync.engine.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InternetViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val accessRequestRepository: AccessRequestRepository,
        private val remoteRequestRepository: RemoteRequestRepository,
        private val activationRepository: DeviceActivationRepository,
        private val syncScheduler: SyncScheduler,
        private val syncEngine: SyncEngine,
    ) : ViewModel() {
        private val domainInput = MutableStateFlow("")
        private val recentDomains = MutableStateFlow(loadRecentDomains())
        private val message = MutableStateFlow("")
        private val isSending = MutableStateFlow(false)

        val uiState =
            combine(
                domainInput,
                recentDomains,
                accessRequestRepository.observePendingRequests(),
                message,
                isSending,
            ) { input, recent, requests, currentMessage, sending ->
                val pendingDomains =
                    requests
                        .filter { it.targetType == PolicyTargetType.Domain }
                        .mapTo(mutableSetOf()) { it.targetDomain ?: it.target }
                InternetUiState(
                    domainInput = input,
                    recentBlockedDomains =
                        recent.map { entry ->
                            BlockedDomainUiState(domain = entry.domain, pending = entry.domain in pendingDomains)
                        },
                    pendingDomains = pendingDomains,
                    message = currentMessage,
                    isSending = sending,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = InternetUiState(),
            )

        fun onDomainChanged(value: String) {
            domainInput.value = value
            message.value = ""
        }

        fun selectDomain(value: String) {
            domainInput.value = normalizeDomain(value).orEmpty()
            message.value = ""
        }

        fun refreshRecentDomains() {
            recentDomains.value = loadRecentDomains()
        }

        fun requestOneHour(domain: String = domainInput.value) {
            saveRequest(
                rawDomain = domain,
                requestType = AccessRequestType.EXTRA_TIME,
                requestedMinutes = OneHourMinutes,
                successMessage = "Solicitud de una hora enviada.",
            )
        }

        fun requestAccess(domain: String = domainInput.value) {
            saveRequest(
                rawDomain = domain,
                requestType = AccessRequestType.DOMAIN_ACCESS,
                requestedMinutes = null,
                successMessage = "Solicitud de acceso enviada.",
            )
        }

        private fun saveRequest(
            rawDomain: String,
            requestType: AccessRequestType,
            requestedMinutes: Int?,
            successMessage: String,
        ) {
            val domain = normalizeDomain(rawDomain)
            if (domain == null) {
                message.value = "Pegá un link o dominio válido."
                return
            }
            isSending.value = true
            message.value = "Enviando solicitud..."
            viewModelScope.launch {
                val request =
                    AccessRequest(
                        id = UUID.randomUUID().toString(),
                        requestType = requestType,
                        targetType = PolicyTargetType.Domain,
                        target = domain,
                        targetPackageName = null,
                        targetDomain = domain,
                        reason = "Solicitud desde Internet",
                        requestedMinutes = requestedMinutes,
                        status = RequestStatus.PendingLocal,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        expiresAtEpochMillis = null,
                    )
                val activation = withContext(Dispatchers.IO) { activationRepository.currentActivation() }
                val pushedDirectly =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            activation?.let {
                                remoteRequestRepository.upsertAccessRequest(
                                    request.toRemoteDto(
                                        accountId = it.accountId,
                                        deviceId = it.deviceId,
                                    ),
                                )
                            }
                        }.getOrNull() is RemoteResult.Success
                    }
                accessRequestRepository.saveRequest(
                    request.copy(
                        status = if (pushedDirectly) RequestStatus.PendingRemote else RequestStatus.PendingLocal,
                    ),
                )
                syncScheduler.requestSync()
                val synced =
                    withContext(Dispatchers.IO) {
                        pushedDirectly ||
                            runCatching {
                                val syncOk =
                                    syncEngine.syncOnce().success &&
                                        syncEngine.syncAccessRequestsFull().success &&
                                        syncEngine.syncRequestResultsFull().success
                                syncOk
                            }.getOrDefault(false)
                    }
                isSending.value = false
                message.value =
                    when {
                        activation == null -> "Solicitud guardada, pero este celular no está enlazado."
                        synced -> successMessage
                        else -> "Solicitud guardada. Se enviará cuando haya conexión."
                    }
            }
        }

        private fun loadRecentDomains(): List<BlockedDomainEntry> {
            val cutoff = System.currentTimeMillis() - RecentBlockedWindowMillis
            return context.getSharedPreferences(BlockedPrefsName, Context.MODE_PRIVATE)
                .getString(BlockedDomainsKey, "")
                .orEmpty()
                .split("|")
                .mapNotNull { it.toBlockedDomainEntryOrNull() }
                .filter { it.blockedAtEpochMillis >= cutoff }
                .filterNot { it.domain.isNoisyDomain() }
                .sortedByDescending { it.blockedAtEpochMillis }
                .distinctBy { it.domain }
                .take(MaxRecentDomains)
        }

        private fun String.toBlockedDomainEntryOrNull(): BlockedDomainEntry? {
            if (isBlank()) return null
            val domain = normalizeDomain(substringBefore(",")) ?: return null
            val timestamp =
                substringAfter(",", missingDelimiterValue = "")
                    .toLongOrNull()
                    ?: System.currentTimeMillis()
            return BlockedDomainEntry(domain, timestamp)
        }

        private fun String.isNoisyDomain(): Boolean {
            val labels = split(".")
            val baseDomain = labels.takeLast(2).joinToString(".")
            return labels.any { it in NoisyDomainLabels } || baseDomain in NoisyBaseDomains
        }

        private data class BlockedDomainEntry(
            val domain: String,
            val blockedAtEpochMillis: Long,
        )

        private fun normalizeDomain(value: String): String? {
            val trimmed = value.trim().lowercase()
            if (trimmed.isBlank()) return null
            val host =
                runCatching {
                    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
                    URI(withScheme).host
                }.getOrNull() ?: trimmed.substringBefore("/").substringBefore("?")
            return host
                .removePrefix("www.")
                .takeIf { DomainRegex.matches(it) }
        }

        private fun AccessRequest.toRemoteDto(
            accountId: String,
            deviceId: String,
        ): RemoteAccessRequestDto {
            val createdAt = Instant.ofEpochMilli(createdAtEpochMillis).toString()
            return RemoteAccessRequestDto(
                id = id,
                accountId = accountId,
                deviceId = deviceId,
                requestType = requestType.name,
                targetType = targetType.name,
                target = target,
                targetPackageName = targetPackageName,
                targetDomain = targetDomain,
                reason = reason,
                requestedMinutes = requestedMinutes,
                status = RequestStatus.PendingRemote.name,
                createdAt = createdAt,
                updatedAt = Instant.now().toString(),
                expiresAt = expiresAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() },
                deletedAt = null,
            )
        }

        private companion object {
            const val OneHourMinutes = 60
            const val BlockedPrefsName = "blocked_domains"
            const val BlockedDomainsKey = "domains"
            const val MaxRecentDomains = 20
            const val RecentBlockedWindowMillis = 60 * 60 * 1000L
            val DomainRegex = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
            val NoisyDomainLabels =
                setOf(
                    "api",
                    "apis",
                    "auth",
                    "collect",
                    "crash",
                    "discover",
                    "events",
                    "firebase",
                    "gms",
                    "graph",
                    "logs",
                    "metrics",
                    "optimizationguide-pa",
                    "telemetry",
                    "us-auth2",
                    "vas",
                )
            val NoisyBaseDomains =
                setOf(
                    "googleapis.com",
                    "gstatic.com",
                    "samsungapps.com",
                    "samsungosp.com",
                    "ureca-lab.com",
                )
        }
    }
