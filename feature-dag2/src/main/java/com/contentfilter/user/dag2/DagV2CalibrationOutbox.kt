package com.contentfilter.user.dag2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
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

internal data class DagV2CalibrationOutboxLimits(
    val maxItems: Int = 50,
    val maxTotalBytes: Long = 20L * 1024L * 1024L,
    val maxEncryptedItemBytes: Int = 720 * 1024,
    val maxAgeMillis: Long = 30L * 24L * 60L * 60L * 1_000L,
    val maxRejectionReceipts: Int = 100,
    val maxEncryptedReceiptBytes: Int = 16 * 1024,
    val rejectionRetentionMillis: Long = 30L * 24L * 60L * 60L * 1_000L,
)

internal data class DagV2CalibrationRejectionReceipt(
    val submissionId: String,
    val reason: String,
    val rejectedAt: String,
)

@Singleton
class DagV2CalibrationOutboxStore private constructor(
    private val directory: File,
    private val rejectionDirectory: File,
    private val cipher: DagV2CalibrationCipher,
    private val limits: DagV2CalibrationOutboxLimits,
    private val nowMillis: () -> Long,
) {
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        cipher: DagV2AndroidKeystoreCipher,
    ) : this(
        directory = File(context.noBackupFilesDir, DagV2CalibrationOutboxNamespace),
        rejectionDirectory = File(context.noBackupFilesDir, DagV2CalibrationRejectionNamespace),
        cipher = cipher,
        limits = DagV2CalibrationOutboxLimits(),
        nowMillis = System::currentTimeMillis,
    )

    internal constructor(
        rootDirectory: File,
        cipher: DagV2CalibrationCipher,
        limits: DagV2CalibrationOutboxLimits = DagV2CalibrationOutboxLimits(),
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        directory = File(rootDirectory, DagV2CalibrationOutboxNamespace),
        rejectionDirectory = File(rootDirectory, DagV2CalibrationRejectionNamespace),
        cipher = cipher,
        limits = limits,
        nowMillis = nowMillis,
    )

    @Synchronized
    fun enqueue(submission: DagV2CalibrationSubmission): DagV2CalibrationEnqueueResult {
        if (!ensureDirectory(directory)) return DagV2CalibrationEnqueueResult.PersistenceFailure
        val existing = pending()
        val duplicate =
            existing.any {
                it.contentSha256 == submission.contentSha256 &&
                    it.decision == submission.decision
            }
        existing.forEach { it.jpegBytes?.fill(0) }
        if (duplicate) return DagV2CalibrationEnqueueResult.Duplicate

        val plain = submission.toJson().toString().encodeToByteArray()
        val encrypted =
            try {
                cipher.encrypt(plain)
            } catch (_: Exception) {
                return DagV2CalibrationEnqueueResult.PersistenceFailure
            } finally {
                plain.fill(0)
            }
        return try {
            when {
                encrypted.size > limits.maxEncryptedItemBytes ->
                    DagV2CalibrationEnqueueResult.TooLarge
                !hasCapacityFor(encrypted.size) ->
                    DagV2CalibrationEnqueueResult.Full
                writeEncrypted(
                    parent = directory,
                    temporaryName = "${submission.submissionId}.pending",
                    targetName = "${submission.submissionId}.enc",
                    encrypted = encrypted,
                ) -> DagV2CalibrationEnqueueResult.Queued
                else -> DagV2CalibrationEnqueueResult.PersistenceFailure
            }
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
            .mapNotNull(::readSubmission)
            .sortedBy { submission ->
                runCatching { Instant.parse(submission.createdAt) }.getOrDefault(Instant.EPOCH)
            }
    }

    @Synchronized
    fun removeAccepted(submissionId: String): Boolean = safePendingFile(submissionId)?.delete() == true

    @Synchronized
    fun quarantinePermanent(
        submissionId: String,
        reason: String,
    ): Boolean {
        val sanitizedReason = reason.sanitizedDagV2RejectionReason()
        if (!ensureDirectory(rejectionDirectory)) return false
        expireRejectionReceipts()
        val receipts =
            rejectionDirectory
                .listFiles { file -> file.isFile && file.name.endsWith(".rejected.enc") }
                .orEmpty()
                .sortedBy(File::lastModified)
        val excessReceiptCount = receipts.size - limits.maxRejectionReceipts + 1
        if (excessReceiptCount > 0 && !receipts.take(excessReceiptCount).all(File::delete)) return false
        val plain =
            JSONObject()
                .put("reason", sanitizedReason)
                .toString()
                .encodeToByteArray()
        val encrypted =
            try {
                cipher.encrypt(plain)
            } catch (_: Exception) {
                return false
            } finally {
                plain.fill(0)
            }
        return try {
            encrypted.size <= limits.maxEncryptedReceiptBytes &&
                writeEncrypted(
                    parent = rejectionDirectory,
                    temporaryName = "$submissionId.rejected.pending",
                    targetName = "$submissionId.rejected.enc",
                    encrypted = encrypted,
                )
        } finally {
            encrypted.fill(0)
        }
    }

    @Synchronized
    fun removePermanentlyRejected(submissionId: String): Boolean = safePendingFile(submissionId)?.delete() == true

    @Synchronized
    fun expirePending(): Int {
        val cutoff = nowMillis() - limits.maxAgeMillis
        return directory
            .listFiles { file -> file.isFile && file.extension == "enc" && file.lastModified() < cutoff }
            .orEmpty()
            .count(File::delete)
    }

    @Synchronized
    fun clearTemporaryFiles() {
        sequenceOf(directory, rejectionDirectory)
            .flatMap { parent ->
                parent
                    .listFiles { file -> file.isFile && file.extension == "pending" }
                    .orEmpty()
                    .asSequence()
            }.forEach(File::delete)
    }

    internal fun rejectionReceipts(): List<DagV2CalibrationRejectionReceipt> =
        rejectionDirectory
            .listFiles { file -> file.isFile && file.name.endsWith(".rejected.enc") }
            .orEmpty()
            .sortedBy(File::lastModified)
            .mapNotNull(::readReceipt)

    private fun hasCapacityFor(nextBytes: Int): Boolean {
        val files =
            directory
                .listFiles { file -> file.isFile && file.extension == "enc" }
                .orEmpty()
        return files.size < limits.maxItems &&
            files.sumOf(File::length) + nextBytes <= limits.maxTotalBytes
    }

    private fun expireRejectionReceipts(): Int {
        val cutoff = nowMillis() - limits.rejectionRetentionMillis
        return rejectionDirectory
            .listFiles {
                    file ->
                file.isFile &&
                    file.name.endsWith(".rejected.enc") &&
                    file.lastModified() < cutoff
            }.orEmpty()
            .count(File::delete)
    }

    private fun readSubmission(file: File): DagV2CalibrationSubmission? = readEncryptedJson(file)?.toSubmissionOrNull()

    private fun readReceipt(file: File): DagV2CalibrationRejectionReceipt? =
        readEncryptedJson(file)?.let {
            runCatching {
                DagV2CalibrationRejectionReceipt(
                    submissionId = file.name.removeSuffix(".rejected.enc"),
                    reason = it.getString("reason"),
                    rejectedAt = Instant.ofEpochMilli(file.lastModified()).toString(),
                )
            }.getOrNull()
        }

    private fun readEncryptedJson(file: File): JSONObject? {
        val encrypted = runCatching { file.readBytes() }.getOrNull() ?: return null
        val plain =
            try {
                cipher.decrypt(encrypted)
            } catch (_: Exception) {
                return null
            } finally {
                encrypted.fill(0)
            }
        return try {
            JSONObject(plain.decodeToString())
        } catch (_: Exception) {
            null
        } finally {
            plain.fill(0)
        }
    }

    private fun safePendingFile(submissionId: String): File? {
        if (!submissionId.matches(IdentifierPattern)) return null
        val file = File(directory, "$submissionId.enc")
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }

    private fun writeEncrypted(
        parent: File,
        temporaryName: String,
        targetName: String,
        encrypted: ByteArray,
    ): Boolean {
        val temporary = File(parent, temporaryName)
        val target = File(parent, targetName)
        return runCatching {
            check(!target.exists()) { "encrypted_target_exists" }
            temporary.outputStream().use { it.write(encrypted) }
            check(temporary.renameTo(target)) { "encrypted_commit_failed" }
            true
        }.getOrElse {
            temporary.delete()
            false
        }
    }

    private fun ensureDirectory(target: File): Boolean = target.exists() || target.mkdirs()

    private fun DagV2CalibrationSubmission.toJson(): JSONObject =
        JSONObject()
            .put("submission_id", submissionId)
            .put("content_sha256", contentSha256)
            .put("perceptual_hash", perceptualHash)
            .put("jpeg", jpegBytes?.let { Base64.getEncoder().encodeToString(it) })
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

    private fun JSONObject.toSubmissionOrNull(): DagV2CalibrationSubmission? =
        runCatching {
            DagV2CalibrationSubmission(
                submissionId = getString("submission_id"),
                contentSha256 = getString("content_sha256"),
                perceptualHash = getString("perceptual_hash"),
                jpegBytes =
                    optString("jpeg")
                        .takeIf(String::isNotBlank)
                        ?.let { Base64.getDecoder().decode(it) },
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
        }.getOrNull()

    private companion object {
        val IdentifierPattern = Regex("[0-9a-fA-F-]{36}")
    }
}

private fun String.sanitizedDagV2RejectionReason(): String =
    lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(80)
        .ifBlank { "submission_rejected" }
