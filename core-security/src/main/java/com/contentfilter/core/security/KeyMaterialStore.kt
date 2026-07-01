package com.contentfilter.core.security

/**
 * Contract for secure key material backed by Android Keystore in later phases.
 */
interface KeyMaterialStore {
    suspend fun hasKey(alias: String): Boolean
}
