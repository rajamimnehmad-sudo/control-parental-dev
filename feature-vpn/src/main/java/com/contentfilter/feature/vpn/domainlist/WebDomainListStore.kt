package com.contentfilter.feature.vpn.domainlist

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDomainListStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DynamicDomainBlocklist {
        private val directory = File(context.filesDir, DirectoryName).apply { mkdirs() }
        private val currentFile = File(directory, CurrentFileName)
        private val previousFile = File(directory, PreviousFileName)
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val initial = loadValid(currentFile) ?: loadValid(previousFile)
        private val active = AtomicReference(initial)
        private val status = MutableStateFlow(initialStatus(initial))

        val version: Long
            get() = active.get()?.version ?: 0L

        override fun categoryFor(domain: String): String? = active.get()?.categoryFor(domain)

        fun observeStatus(): StateFlow<WebDomainListStatus> = status

        @Synchronized
        fun install(
            bytes: ByteArray,
            expectedVersion: Long,
        ): Boolean {
            val parsed = WebDomainList.parse(bytes)
            require(parsed.version == expectedVersion) { "Manifest and list versions differ." }
            if (parsed.version <= version) return false
            val temporary = File(directory, TemporaryFileName)
            temporary.outputStream().use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (currentFile.exists()) {
                previousFile.delete()
                check(currentFile.renameTo(previousFile)) { "Could not retain previous domain list." }
            }
            if (!temporary.renameTo(currentFile)) {
                if (previousFile.exists()) previousFile.renameTo(currentFile)
                error("Could not activate downloaded domain list.")
            }
            active.set(parsed)
            val installedAt = System.currentTimeMillis()
            preferences.edit()
                .putLong(InstalledVersionKey, parsed.version)
                .putLong(InstalledAtKey, installedAt)
                .apply()
            status.value =
                WebDomainListStatus(
                    version = parsed.version,
                    installedAtEpochMillis = installedAt,
                    state = WebDomainListState.Active,
                    lastCheckAtEpochMillis = status.value.lastCheckAtEpochMillis,
                    lastCheckResult = status.value.lastCheckResult,
                    canaryIncluded = parsed.canaryIncluded,
                    lastError = null,
                )
            return true
        }

        fun recordCheck(result: DomainListUpdateResult) {
            val now = System.currentTimeMillis()
            val current = active.get()
            val failed = result as? DomainListUpdateResult.Failed
            preferences.edit()
                .putLong(LastCheckAtKey, now)
                .putString(LastCheckResultKey, result.label)
                .putString(LastErrorKey, failed?.reason)
                .apply()
            status.value =
                WebDomainListStatus(
                    version = current?.version ?: 0L,
                    installedAtEpochMillis = installedAt(current),
                    state =
                        when {
                            failed != null -> WebDomainListState.Error
                            current != null -> WebDomainListState.Active
                            else -> WebDomainListState.NoBase
                        },
                    lastCheckAtEpochMillis = now,
                    lastCheckResult = result.label,
                    canaryIncluded = current?.canaryIncluded == true,
                    lastError = failed?.reason,
                )
        }

        private fun initialStatus(list: WebDomainList?): WebDomainListStatus =
            WebDomainListStatus(
                version = list?.version ?: 0L,
                installedAtEpochMillis = installedAt(list),
                state =
                    when {
                        preferences.getString(LastErrorKey, null) != null -> WebDomainListState.Error
                        list == null -> WebDomainListState.NoBase
                        else -> WebDomainListState.Active
                    },
                lastCheckAtEpochMillis = preferences.getLong(LastCheckAtKey, 0L),
                lastCheckResult = preferences.getString(LastCheckResultKey, null) ?: "Todavia no comprobada",
                canaryIncluded = list?.canaryIncluded == true,
                lastError = preferences.getString(LastErrorKey, null),
            )

        private fun installedAt(list: WebDomainList?): Long =
            if (list == null) {
                0L
            } else if (preferences.getLong(InstalledVersionKey, 0L) == list.version) {
                preferences.getLong(InstalledAtKey, 0L)
            } else {
                currentFile.takeIf(File::exists)?.lastModified() ?: previousFile.lastModified()
            }

        private fun loadValid(file: File): WebDomainList? =
            if (!file.exists()) {
                null
            } else {
                runCatching { WebDomainList.parse(file.readBytes()) }
                    .onFailure { Log.w(LogTag, "Domain list cache rejected file=${file.name}") }
                    .getOrNull()
            }

        private companion object {
            const val LogTag = "WebDomainListStore"
            const val DirectoryName = "web-domain-list"
            const val CurrentFileName = "current.bin"
            const val PreviousFileName = "previous.bin"
            const val TemporaryFileName = "download.tmp"
            const val PreferencesName = "web-domain-list-local-status"
            const val InstalledVersionKey = "installed-version"
            const val InstalledAtKey = "installed-at"
            const val LastCheckAtKey = "last-check-at"
            const val LastCheckResultKey = "last-check-result"
            const val LastErrorKey = "last-error"
        }
    }

enum class WebDomainListState {
    Active,
    Error,
    NoBase,
}

data class WebDomainListStatus(
    val version: Long,
    val installedAtEpochMillis: Long,
    val state: WebDomainListState,
    val lastCheckAtEpochMillis: Long,
    val lastCheckResult: String,
    val canaryIncluded: Boolean,
    val lastError: String?,
)
