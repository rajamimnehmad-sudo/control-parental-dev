package com.contentfilter.user.dag2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal interface DagV2CalibrationCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(encrypted: ByteArray): ByteArray
}

@Singleton
internal class DagV2AndroidKeystoreCipher
    @Inject
    constructor() : DagV2CalibrationCipher {
        override fun encrypt(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            return cipher.iv + cipher.doFinal(plain)
        }

        override fun decrypt(encrypted: ByteArray): ByteArray {
            require(encrypted.size > IvBytes)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TagBits, encrypted.copyOfRange(0, IvBytes)),
            )
            return cipher.doFinal(encrypted, IvBytes, encrypted.size - IvBytes)
        }

        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KeyStoreName).apply { load(null) }
            (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreName)
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(
                                KeyAlias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                }.generateKey()
        }

        private companion object {
            const val KeyStoreName = "AndroidKeyStore"
            const val KeyAlias = DagV2CalibrationOutboxNamespace
            const val Transformation = "AES/GCM/NoPadding"
            const val IvBytes = 12
            const val TagBits = 128
        }
    }

@Singleton
class DagV2CalibrationOutboxStore
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        private val cipher: DagV2AndroidKeystoreCipher,
    ) {
        private val directory = File(context.noBackupFilesDir, DagV2CalibrationOutboxNamespace)

        @Synchronized
        fun enqueue(submission: DagV2CalibrationSubmission): Boolean {
            directory.mkdirs()
            purgeExpired()
            val existing = pending()
            val duplicate =
                existing.any {
                    it.contentSha256 == submission.contentSha256 &&
                        it.decision == submission.decision
                }
            existing.forEach { it.jpegBytes?.fill(0) }
            if (duplicate) {
                return false
            }
            val plain = submission.toJson().toString().encodeToByteArray()
            val encrypted =
                try {
                    cipher.encrypt(plain)
                } finally {
                    plain.fill(0)
                }
            try {
                if (encrypted.size > MaxEncryptedItemBytes) return false
                pruneFor(encrypted.size)
                val temporary = File(directory, "${submission.submissionId}.pending")
                val target = File(directory, "${submission.submissionId}.enc")
                temporary.outputStream().use { it.write(encrypted) }
                check(temporary.renameTo(target)) { "outbox_commit_failed" }
                return true
            } finally {
                encrypted.fill(0)
            }
        }

        @Synchronized
        fun pending(): List<DagV2CalibrationSubmission> {
            if (!directory.exists()) return emptyList()
            return directory
                .listFiles { file -> file.isFile && file.extension == "enc" }
                .orEmpty()
                .sortedBy(File::lastModified)
                .mapNotNull(::read)
        }

        @Synchronized
        fun removeAccepted(submissionId: String) {
            safeFile(submissionId)?.delete()
        }

        @Synchronized
        fun clearTemporaryFiles() {
            directory
                .listFiles { file -> file.isFile && file.extension == "pending" }
                .orEmpty()
                .forEach(File::delete)
        }

        private fun read(file: File): DagV2CalibrationSubmission? {
            val encrypted = runCatching { file.readBytes() }.getOrNull() ?: return null
            val plain =
                try {
                    cipher.decrypt(encrypted)
                } catch (_: Exception) {
                    file.delete()
                    return null
                } finally {
                    encrypted.fill(0)
                }
            return try {
                JSONObject(plain.decodeToString()).toSubmission()
            } catch (_: Exception) {
                file.delete()
                null
            } finally {
                plain.fill(0)
            }
        }

        private fun safeFile(submissionId: String): File? {
            if (!submissionId.matches(IdentifierPattern)) return null
            val file = File(directory, "$submissionId.enc")
            return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
        }

        private fun purgeExpired() {
            val cutoff = System.currentTimeMillis() - MaxAgeMillis
            directory
                .listFiles()
                .orEmpty()
                .filter { it.lastModified() < cutoff || it.extension == "pending" }
                .forEach(File::delete)
        }

        private fun pruneFor(nextBytes: Int) {
            val files =
                directory
                    .listFiles { file -> file.extension == "enc" }
                    .orEmpty()
                    .sortedBy(File::lastModified)
                    .toMutableList()
            var total = files.sumOf(File::length)
            while (files.size >= MaxItems || total + nextBytes > MaxTotalBytes) {
                val oldest = files.removeFirstOrNull() ?: break
                total -= oldest.length()
                oldest.delete()
            }
        }

        private fun DagV2CalibrationSubmission.toJson(): JSONObject =
            JSONObject()
                .put("submission_id", submissionId)
                .put("content_sha256", contentSha256)
                .put("perceptual_hash", perceptualHash)
                .put("jpeg", jpegBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) })
                .put("existing_content_sha256", existingContentSha256)
                .put("width", width)
                .put("height", height)
                .put("source_kind", sourceKind)
                .put("source_host", sourceHost)
                .put("document_host", documentHost)
                .put("source_url_hash", sourceUrlHash)
                .put("decision", decision.wireValue)
                .put("policy_version", policyVersion)
                .put("collector_version", collectorVersion)
                .put("created_at", createdAt)

        private fun JSONObject.toSubmission(): DagV2CalibrationSubmission =
            DagV2CalibrationSubmission(
                submissionId = getString("submission_id"),
                contentSha256 = getString("content_sha256"),
                perceptualHash = getString("perceptual_hash"),
                jpegBytes =
                    optString("jpeg")
                        .takeIf(String::isNotBlank)
                        ?.let { Base64.decode(it, Base64.NO_WRAP) },
                existingContentSha256 = optString("existing_content_sha256").takeIf(String::isNotBlank),
                width = getInt("width"),
                height = getInt("height"),
                sourceKind = getString("source_kind"),
                sourceHost = getString("source_host"),
                documentHost = getString("document_host"),
                sourceUrlHash = getString("source_url_hash"),
                decision = DagV2CalibrationDecision.entries.single { it.wireValue == getString("decision") },
                policyVersion = getString("policy_version"),
                collectorVersion = getString("collector_version"),
                createdAt = optString("created_at", Instant.EPOCH.toString()),
            )

        private companion object {
            const val MaxItems = 50
            const val MaxTotalBytes = 20L * 1024L * 1024L

            // JSON + Base64 expands a valid 512 KiB JPEG by roughly one third.
            const val MaxEncryptedItemBytes = 720 * 1024
            const val MaxAgeMillis = 30L * 24L * 60L * 60L * 1_000L
            val IdentifierPattern = Regex("[0-9a-fA-F-]{36}")
        }
    }
