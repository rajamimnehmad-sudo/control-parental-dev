package com.contentfilter.feature.vpn.domainlist

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
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
        private val active = AtomicReference(loadValid(currentFile) ?: loadValid(previousFile))

        val version: Long
            get() = active.get()?.version ?: 0L

        override fun categoryFor(domain: String): String? = active.get()?.categoryFor(domain)

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
            return true
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
        }
    }
