package com.contentfilter.core.security

interface AuthSessionStore {
    fun current(): AuthSession?

    fun save(session: AuthSession)

    fun clear()
}
