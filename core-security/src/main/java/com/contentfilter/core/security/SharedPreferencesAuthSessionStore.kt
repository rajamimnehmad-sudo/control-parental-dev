package com.contentfilter.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesAuthSessionStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AuthSessionStore {
        private val preferences = context.getSharedPreferences(StoreName, Context.MODE_PRIVATE)

        override fun current(): AuthSession? {
            val accessToken = preferences.getString(AccessTokenKey, null) ?: return null
            return AuthSession(
                accessToken = accessToken,
                refreshToken = preferences.getString(RefreshTokenKey, null),
                expiresAtEpochMillis = preferences.getLong(ExpiresAtKey, 0L),
            )
        }

        override fun save(session: AuthSession) {
            preferences.edit()
                .putString(AccessTokenKey, session.accessToken)
                .putString(RefreshTokenKey, session.refreshToken)
                .putLong(ExpiresAtKey, session.expiresAtEpochMillis)
                .apply()
        }

        override fun clear() {
            preferences.edit().clear().apply()
        }

        private companion object {
            const val StoreName = "auth-session"
            const val AccessTokenKey = "access-token"
            const val RefreshTokenKey = "refresh-token"
            const val ExpiresAtKey = "expires-at"
        }
    }
