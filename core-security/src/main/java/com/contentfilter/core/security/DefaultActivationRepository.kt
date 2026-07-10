package com.contentfilter.core.security

import android.util.Log
import com.contentfilter.core.domain.model.ActivationCredentials
import com.contentfilter.core.domain.model.ActivationResult
import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.repository.ActivationRepository
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.DeviceRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.network.config.DeviceTokenProvider
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseActivationClient
import com.contentfilter.core.network.remote.SupabaseAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultActivationRepository
    @Inject
    constructor(
        private val authClient: SupabaseAuthClient,
        private val activationClient: SupabaseActivationClient,
        private val sessionStore: AuthSessionStore,
        private val deviceTokenProvider: DeviceTokenProvider,
        private val activationRepository: DeviceActivationRepository,
        private val deviceRepository: DeviceRepository,
        private val systemStatusRepository: SystemStatusRepository,
    ) : ActivationRepository {
        override suspend fun activate(credentials: ActivationCredentials): ActivationResult =
            withContext(Dispatchers.IO) {
                activationRepository.currentActivation()?.let { activation ->
                    val isTokenRelink =
                        credentials.email.isBlank() &&
                            credentials.password.isBlank() &&
                            credentials.activationCode.isNotBlank()
                    if (isTokenRelink) {
                        Log.i(
                            LogTag,
                            "Existing local activation will be replaced by token relink. oldDeviceId=${activation.deviceId}",
                        )
                    } else {
                    if (credentials.email.isNotBlank() && credentials.password.isNotBlank()) {
                        val session =
                            when (val result = authClient.signInWithPassword(credentials.email, credentials.password)) {
                                is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                                is RemoteResult.Success -> result.value
                            }
                        sessionStore.save(
                            AuthSession(
                                accessToken = session.accessToken,
                                refreshToken = session.refreshToken,
                                expiresAtEpochMillis = System.currentTimeMillis() + session.expiresInSeconds * 1000,
                            ),
                        )
                    }
                    systemStatusRepository.updateLicenseState(LicenseState.Active)
                    Log.i(
                        LogTag,
                        "Activation skipped; local device is already activated deviceId=${activation.deviceId}",
                    )
                    return@withContext ActivationResult.Activated(activation)
                    }
                }
                val activation =
                    if (credentials.appRole == AdminRole && credentials.email.isNotBlank() && credentials.password.isNotBlank()) {
                        val existingSession =
                            when (val result = authClient.signInWithPassword(credentials.email, credentials.password)) {
                                is RemoteResult.Success -> result.value
                                is RemoteResult.Failure -> null
                            }
                        val paired =
                            when (
                                val result =
                                    activationClient.pairAdminDeviceWithPassword(
                                        pairingCode = credentials.activationCode,
                                        email = credentials.email,
                                        password = credentials.password,
                                        displayName = credentials.deviceDisplayName,
                                        appVersionCode = credentials.appVersionCode,
                                        accessToken = existingSession?.accessToken,
                                    )
                            ) {
                                is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                                is RemoteResult.Success ->
                                    result.value.also { dto ->
                                        dto.deviceToken?.let(deviceTokenProvider::saveDeviceToken)
                                    }
                            }
                        val session = existingSession ?: when (val result = authClient.signInWithPassword(credentials.email, credentials.password)) {
                            is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                            is RemoteResult.Success -> result.value
                        }
                        sessionStore.save(
                            AuthSession(
                                accessToken = session.accessToken,
                                refreshToken = session.refreshToken,
                                expiresAtEpochMillis = System.currentTimeMillis() + session.expiresInSeconds * 1000,
                            ),
                        )
                        paired
                    } else if (credentials.email.isBlank() && credentials.password.isBlank()) {
                        when (
                            val result =
                                activationClient.pairDeviceWithCode(
                                    pairingCode = credentials.activationCode,
                                    displayName = credentials.deviceDisplayName,
                                    appVersionCode = credentials.appVersionCode,
                                    appRole = credentials.appRole,
                                )
                        ) {
                            is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                            is RemoteResult.Success ->
                                result.value.also { dto ->
                                    dto.deviceToken?.let(deviceTokenProvider::saveDeviceToken)
                                }
                        }
                    } else {
                        val session =
                            when (val result = authClient.signInWithPassword(credentials.email, credentials.password)) {
                                is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                                is RemoteResult.Success -> result.value
                            }
                        sessionStore.save(
                            AuthSession(
                                accessToken = session.accessToken,
                                refreshToken = session.refreshToken,
                                expiresAtEpochMillis = System.currentTimeMillis() + session.expiresInSeconds * 1000,
                            ),
                        )
                        when (
                            val result =
                                activationClient.activateDevice(
                                    activationCode = credentials.activationCode,
                                    displayName = credentials.deviceDisplayName,
                                    appVersionCode = credentials.appVersionCode,
                                    appRole = credentials.appRole,
                                )
                        ) {
                            is RemoteResult.Failure -> return@withContext ActivationResult.Failed(result.reason)
                            is RemoteResult.Success -> result.value
                        }
                    }
                val domain =
                    DeviceActivation(
                        id = activation.activationId,
                        accountId = activation.accountId,
                        deviceId = activation.deviceId,
                        activatedAtEpochMillis = System.currentTimeMillis(),
                    )
                activationRepository.saveActivation(domain)
                Log.i(
                    LogTag,
                    "Activation saved. accountId=${domain.accountId} newDeviceId=${domain.deviceId}",
                )
                deviceRepository.saveDevice(
                    Device(
                        id = domain.deviceId,
                        accountId = domain.accountId,
                        displayName = credentials.deviceDisplayName,
                        appRole = credentials.appRole,
                    ),
                )
                systemStatusRepository.updateLicenseState(LicenseState.Active)
                return@withContext ActivationResult.Activated(domain)
            }

        private companion object {
            const val LogTag = "ActivationRepository"
            const val AdminRole = "admin"
        }
    }
