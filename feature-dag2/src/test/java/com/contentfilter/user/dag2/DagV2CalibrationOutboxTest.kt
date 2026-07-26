package com.contentfilter.user.dag2

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DagV2CalibrationOutboxTest {
    @Test
    fun `full outbox rejects new item without deleting oldest pending`() =
        withStore(DagV2CalibrationOutboxLimits(maxItems = 1)) { store, _ ->
            val oldest = submission(1)
            val next = submission(2)

            assertEquals(DagV2CalibrationEnqueueResult.Queued, store.enqueue(oldest))
            assertEquals(DagV2CalibrationEnqueueResult.Full, store.enqueue(next))

            assertEquals(listOf(oldest.submissionId), store.pending().map { it.submissionId })
        }

    @Test
    fun `duplicate creates no second encrypted file`() =
        withStore { store, root ->
            val first = submission(1)
            val duplicate = first.copy(submissionId = UUID.randomUUID().toString())

            assertEquals(DagV2CalibrationEnqueueResult.Queued, store.enqueue(first))
            assertEquals(DagV2CalibrationEnqueueResult.Duplicate, store.enqueue(duplicate))

            assertEquals(1, encryptedPendingFiles(root).size)
        }

    @Test
    fun `expiry is explicit and never used implicitly to make space`() {
        var now = System.currentTimeMillis()
        withStore(
            limits = DagV2CalibrationOutboxLimits(maxItems = 1, maxAgeMillis = 1_000),
            nowMillis = { now },
        ) { store, _ ->
            val oldest = submission(1)
            assertEquals(DagV2CalibrationEnqueueResult.Queued, store.enqueue(oldest))
            now = System.currentTimeMillis() + 1_001

            assertEquals(DagV2CalibrationEnqueueResult.Full, store.enqueue(submission(2)))
            assertEquals(listOf(oldest.submissionId), store.pending().map { it.submissionId })
            assertEquals(1, store.expirePending())
            assertEquals(emptyList(), store.pending())
        }
    }

    @Test
    fun `oversized and persistence failures are explicit`() {
        withStore(DagV2CalibrationOutboxLimits(maxEncryptedItemBytes = 8)) { store, _ ->
            assertEquals(DagV2CalibrationEnqueueResult.TooLarge, store.enqueue(submission(1)))
        }
        withStore(cipher = ThrowingCipher) { store, _ ->
            assertEquals(DagV2CalibrationEnqueueResult.PersistenceFailure, store.enqueue(submission(1)))
        }
    }

    @Test
    fun `permanent failure is quarantined and does not block later accepted item`() =
        withStore { store, root ->
            val rejected = submission(1)
            val accepted = submission(2)
            store.enqueue(rejected)
            store.enqueue(accepted)
            val gateway =
                FakeGateway {
                    if (it.submissionId == rejected.submissionId) {
                        DagV2CalibrationDeliveryResult.PermanentFailure("submission_rejected_400")
                    } else {
                        DagV2CalibrationDeliveryResult.Accepted("sample-b", false, true)
                    }
                }

            val result =
                runBlocking {
                    DagV2CalibrationOutboxFlusher(
                        store = store,
                        gateway = gateway,
                        metrics = DagV2Metrics(),
                        maxAttempts = 1,
                        retryDelayMillis = 0,
                    ).flush()
                }

            assertIs<DagV2CalibrationDeliveryResult.PermanentFailure>(result)
            assertEquals(listOf(rejected.submissionId, accepted.submissionId), gateway.deliveredIds)
            assertEquals(emptyList(), store.pending())
            assertEquals("submission_rejected_400", store.rejectionReceipts().single().reason)
            assertTrue(
                File(root, DagV2CalibrationRejectionNamespace)
                    .listFiles()
                    .orEmpty()
                    .single()
                    .readBytes()
                    .none { it == '{'.code.toByte() },
            )
        }

    @Test
    fun `encrypted rejection quarantine is bounded without retaining rejected image bytes`() =
        withStore(DagV2CalibrationOutboxLimits(maxRejectionReceipts = 1)) { store, root ->
            val first = submission(1)
            val second = submission(2)

            assertTrue(store.quarantinePermanent(first.submissionId, "first_rejection"))
            assertTrue(store.quarantinePermanent(second.submissionId, "second_rejection"))

            val receipt = store.rejectionReceipts().single()
            assertEquals(second.submissionId, receipt.submissionId)
            assertEquals("second_rejection", receipt.reason)
            assertEquals(1, File(root, DagV2CalibrationRejectionNamespace).listFiles().orEmpty().size)
            assertTrue(
                root
                    .walkTopDown()
                    .filter(File::isFile)
                    .none { it.readBytes().containsSubsequence(first.jpegBytes ?: byteArrayOf()) },
            )
        }

    @Test
    fun `quarantine persistence failure preserves rejected pending and still delivers later items`() =
        withStore { store, root ->
            val rejected = submission(1)
            val accepted = submission(2)
            store.enqueue(rejected)
            store.enqueue(accepted)
            File(root, DagV2CalibrationRejectionNamespace).writeText("blocked")
            val gateway =
                FakeGateway {
                    if (it.submissionId == rejected.submissionId) {
                        DagV2CalibrationDeliveryResult.PermanentFailure("submission_rejected_400")
                    } else {
                        DagV2CalibrationDeliveryResult.Accepted("sample-b", false, true)
                    }
                }

            runBlocking {
                DagV2CalibrationOutboxFlusher(
                    store = store,
                    gateway = gateway,
                    metrics = DagV2Metrics(),
                    maxAttempts = 1,
                    retryDelayMillis = 0,
                ).flush()
            }

            assertEquals(listOf(rejected.submissionId, accepted.submissionId), gateway.deliveredIds)
            assertEquals(listOf(rejected.submissionId), store.pending().map { it.submissionId })
        }

    @Test
    fun `temporary failure keeps ordered pending items`() =
        withStore { store, _ ->
            val first = submission(1)
            val second = submission(2)
            store.enqueue(first)
            store.enqueue(second)
            val gateway = FakeGateway { DagV2CalibrationDeliveryResult.TemporaryFailure("network_timeout") }

            runBlocking {
                DagV2CalibrationOutboxFlusher(
                    store = store,
                    gateway = gateway,
                    metrics = DagV2Metrics(),
                    maxAttempts = 1,
                    retryDelayMillis = 0,
                ).flush()
            }

            assertEquals(listOf(first.submissionId), gateway.deliveredIds)
            assertEquals(
                listOf(first.submissionId, second.submissionId),
                store.pending().map { it.submissionId },
            )
        }

    @Test
    fun `acceptance removes only its own encrypted pending file`() =
        withStore { store, _ ->
            val accepted = submission(1)
            val temporary = submission(2)
            store.enqueue(accepted)
            store.enqueue(temporary)
            val gateway =
                FakeGateway {
                    if (it.submissionId == accepted.submissionId) {
                        DagV2CalibrationDeliveryResult.Accepted("sample-a", false, true)
                    } else {
                        DagV2CalibrationDeliveryResult.TemporaryFailure("network_timeout")
                    }
                }

            runBlocking {
                DagV2CalibrationOutboxFlusher(
                    store = store,
                    gateway = gateway,
                    metrics = DagV2Metrics(),
                    maxAttempts = 1,
                    retryDelayMillis = 0,
                ).flush()
            }

            assertEquals(listOf(temporary.submissionId), store.pending().map { it.submissionId })
        }

    @Test
    fun `pending survives store close and reopen without plaintext jpeg`() =
        withTemporaryRoot { root ->
            val jpeg = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
            val firstStore = DagV2CalibrationOutboxStore(root, XorCipher)
            val pending = submission(1).copy(jpegBytes = jpeg.copyOf())
            assertEquals(DagV2CalibrationEnqueueResult.Queued, firstStore.enqueue(pending))

            val reopened = DagV2CalibrationOutboxStore(root, XorCipher)
            assertEquals(listOf(pending.submissionId), reopened.pending().map { it.submissionId })
            assertContentEquals(jpeg, reopened.pending().single().jpegBytes)
            root.walkTopDown()
                .filter(File::isFile)
                .forEach { file -> assertFalse(file.readBytes().containsSubsequence(jpeg), file.path) }
        }

    @Test
    fun `outbox failure keeps candidate and preview available for retry`() {
        val candidate =
            DagV2CalibrationCandidate(
                candidateId = UUID.randomUUID().toString(),
                sessionId = "session",
                navigationToken = "token",
                resourceUrl = "https://images.example/photo.jpg",
                documentOrigin = "https://shop.example",
                resourceOrigin = "https://images.example",
                resourceKind = DagV2ResourceKind.RasterImage,
                observedWidth = null,
                observedHeight = null,
                observedAt = 1,
                attribution = DagV2RequestAttribution.Current,
                reviewable = true,
            )
        val preview = DagV2CalibrationNormalizedImage(byteArrayOf(1, 2, 3), 1, 1)
        val state =
            DagV2CalibrationReviewState(
                enabled = true,
                candidates = listOf(candidate),
                reviewOpen = true,
                previewCandidate = candidate,
                preview = preview,
                previewFingerprint =
                    DagV2CalibrationFingerprintResult(
                        contentSha256 = "a".repeat(64),
                        perceptualHash = "0".repeat(16),
                    ),
            )

        val updated = state.withEnqueueFailure(DagV2CalibrationEnqueueResult.Full)

        assertSame(candidate, updated.previewCandidate)
        assertSame(preview, updated.preview)
        assertEquals(listOf(candidate), updated.candidates)
        assertTrue(updated.statusMessage.orEmpty().contains("no fue almacenada"))
    }

    private fun withStore(
        limits: DagV2CalibrationOutboxLimits = DagV2CalibrationOutboxLimits(),
        cipher: DagV2CalibrationCipher = XorCipher,
        nowMillis: () -> Long = System::currentTimeMillis,
        block: (DagV2CalibrationOutboxStore, File) -> Unit,
    ) = withTemporaryRoot { root ->
        block(DagV2CalibrationOutboxStore(root, cipher, limits, nowMillis), root)
    }

    private fun withTemporaryRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("dag-v2-outbox-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun submission(index: Int): DagV2CalibrationSubmission =
        DagV2CalibrationSubmission(
            submissionId = UUID.nameUUIDFromBytes("submission-$index".encodeToByteArray()).toString(),
            contentSha256 = index.toString(16).padStart(64, '0'),
            perceptualHash = index.toString(16).padStart(16, '0'),
            jpegBytes = byteArrayOf(index.toByte(), 2, 3, 4),
            width = 2,
            height = 2,
            sourceKind = "rasterimage",
            sourceHost = "images.example",
            documentHost = "shop.example",
            sourceUrlHash = (index + 100).toString(16).padStart(64, '0'),
            decision = DagV2CalibrationDecision.Unsure,
            createdAt = "2026-07-26T00:00:${index.toString().padStart(2, '0')}Z",
        )

    private fun encryptedPendingFiles(root: File): List<File> =
        File(root, DagV2CalibrationOutboxNamespace)
            .listFiles { file -> file.extension == "enc" }
            .orEmpty()
            .toList()

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean =
        indices.any { start ->
            start + needle.size <= size &&
                needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }

    private object XorCipher : DagV2CalibrationCipher {
        override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor Mask).toByte() }.toByteArray()

        override fun decrypt(encrypted: ByteArray): ByteArray = encrypt(encrypted)

        private const val Mask = 0x5A
    }

    private object ThrowingCipher : DagV2CalibrationCipher {
        override fun encrypt(plain: ByteArray): ByteArray = error("cipher_failure")

        override fun decrypt(encrypted: ByteArray): ByteArray = error("cipher_failure")
    }

    private class FakeGateway(
        private val result: (DagV2CalibrationSubmission) -> DagV2CalibrationDeliveryResult,
    ) : DagV2CalibrationGateway {
        val deliveredIds = mutableListOf<String>()

        override suspend fun deliver(submission: DagV2CalibrationSubmission): DagV2CalibrationDeliveryResult {
            deliveredIds += submission.submissionId
            return result(submission)
        }
    }
}
