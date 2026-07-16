package com.contentfilter.user.apps

import android.content.Context
import com.contentfilter.core.domain.repository.InstallApprovalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesInstallApprovalStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : InstallApprovalStore {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        @Synchronized
        override fun initializeBaseline(packageNames: Set<String>) {
            if (preferences.contains(KnownPackagesKey)) return
            preferences.edit().putStringSet(KnownPackagesKey, packageNames.normalized()).commit()
        }

        override fun isKnown(packageName: String): Boolean = packageName in knownPackages()

        override fun isPending(packageName: String): Boolean = packageName in pendingPackages()

        @Synchronized
        override fun markPending(packageName: String) {
            val normalized = packageName.normalized() ?: return
            preferences
                .edit()
                .putStringSet(KnownPackagesKey, knownPackages() + normalized)
                .putStringSet(PendingPackagesKey, pendingPackages() + normalized)
                .commit()
        }

        @Synchronized
        override fun markApproved(packageName: String) {
            val normalized = packageName.normalized() ?: return
            preferences
                .edit()
                .putStringSet(KnownPackagesKey, knownPackages() + normalized)
                .putStringSet(PendingPackagesKey, pendingPackages() - normalized)
                .commit()
        }

        @Synchronized
        override fun remove(packageName: String) {
            val normalized = packageName.normalized() ?: return
            preferences
                .edit()
                .putStringSet(KnownPackagesKey, knownPackages() - normalized)
                .putStringSet(PendingPackagesKey, pendingPackages() - normalized)
                .commit()
        }

        private fun knownPackages(): Set<String> =
            preferences.getStringSet(KnownPackagesKey, emptySet()).orEmpty().toSet()

        private fun pendingPackages(): Set<String> =
            preferences.getStringSet(PendingPackagesKey, emptySet()).orEmpty().toSet()

        private fun Set<String>.normalized(): Set<String> = mapNotNull { it.normalized() }.toSet()

        private fun String.normalized(): String? = trim().takeIf { it.isNotEmpty() }

        private companion object {
            const val PreferencesName = "install-approval"
            const val KnownPackagesKey = "known-packages"
            const val PendingPackagesKey = "pending-packages"
        }
    }
