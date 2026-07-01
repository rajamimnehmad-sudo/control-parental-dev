package com.contentfilter.core.security

import com.contentfilter.core.network.config.AuthTokenProvider
import javax.inject.Inject

class StoredAuthTokenProvider
    @Inject
    constructor(
        private val store: AuthSessionStore,
    ) : AuthTokenProvider {
        override fun currentToken(): String? {
            val session = store.current() ?: return null
            return if (session.expiresAtEpochMillis > System.currentTimeMillis()) {
                session.accessToken
            } else {
                null
            }
        }
    }
