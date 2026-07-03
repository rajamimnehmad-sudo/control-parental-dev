package com.contentfilter.core.security

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseAuthClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class StoredAuthTokenProvider
    @Inject
    constructor(
        private val store: AuthSessionStore,
        private val authClient: SupabaseAuthClient,
    ) : AuthTokenProvider {
        private val refreshMutex = Mutex()

        override suspend fun currentToken(): String? {
            val session = store.current() ?: return null
            if (session.isUsable()) return session.accessToken

            return refreshMutex.withLock {
                val latest = store.current() ?: return@withLock null
                if (latest.isUsable()) return@withLock latest.accessToken
                val refreshToken = latest.refreshToken?.takeIf { it.isNotBlank() } ?: return@withLock null
                when (val refreshed = authClient.refreshSession(refreshToken)) {
                    is RemoteResult.Success -> {
                        val refreshedSession =
                            AuthSession(
                                accessToken = refreshed.value.accessToken,
                                refreshToken = refreshed.value.refreshToken ?: refreshToken,
                                expiresAtEpochMillis = System.currentTimeMillis() + refreshed.value.expiresInSeconds * 1000,
                            )
                        store.save(refreshedSession)
                        refreshedSession.accessToken
                    }
                    is RemoteResult.Failure -> null
                }
            }
        }

        private fun AuthSession.isUsable(): Boolean =
            expiresAtEpochMillis > System.currentTimeMillis() + RefreshSkewMillis

        private companion object {
            const val RefreshSkewMillis = 60_000L
        }
    }
