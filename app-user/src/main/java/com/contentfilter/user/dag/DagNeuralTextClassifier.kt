package com.contentfilter.user.dag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

fun interface DagNeuralTextStage {
    suspend fun classifyBatch(texts: List<String>): List<DagSemanticPrediction?>
}

@Singleton
class DagNeuralTextClassifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
    ) : DagNeuralTextStage {
        private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }
        private val verifiedModel = AtomicReference<File?>()
        private val session = AtomicReference<OrtSession?>()
        private val preparationMutex = Mutex()
        private val inferenceMutex = Mutex()

        suspend fun prepare() {
            if (
                session.get() != null ||
                BuildConfig.DAG_NEURAL_MODEL_URL.isBlank() ||
                Build.SUPPORTED_64_BIT_ABIS.isEmpty()
            ) {
                return
            }
            preparationMutex.withLock {
                if (session.get() != null) return
                val model = verifiedModel() ?: return
                createSession(model)?.let { loaded ->
                    if (!session.compareAndSet(null, loaded)) loaded.close()
                }
            }
        }

        suspend fun release() {
            preparationMutex.withLock {
                inferenceMutex.withLock {
                    session.getAndSet(null)?.close()
                }
            }
        }

        override suspend fun classifyBatch(texts: List<String>): List<DagSemanticPrediction?> {
            if (texts.isEmpty()) return emptyList()
            val coroutineContext = currentCoroutineContext()
            return inferenceMutex.withLock {
                val activeSession = session.get() ?: return@withLock List(texts.size) { null }
                dagMapNeuralBatch(
                    values = texts,
                    ensureActive = { coroutineContext.ensureActive() },
                ) { text ->
                    classify(activeSession, text)
                }
            }
        }

        private fun classify(
            session: OrtSession,
            text: String,
        ): DagSemanticPrediction? {
            return runCatching {
                OnnxTensor.createTensor(environment, arrayOf(text.take(MaxInputCharacters))).use { input ->
                    session.run(mapOf(InputName to input)).use { result ->
                        val probabilities =
                            (result[0].value as? Array<*>)?.firstOrNull() as? FloatArray
                                ?: return@use null
                        val ranking = probabilities.indices.sortedByDescending { probabilities[it] }
                        val best = ranking.firstOrNull() ?: return@use null
                        DagSemanticPrediction(
                            category = Categories[best],
                            confidence = probabilities[best],
                            margin = probabilities[best] - probabilities.getOrElse(ranking.getOrElse(1) { best }) { 0f },
                            modelVersion = ModelVersion,
                        )
                    }
                }
            }.getOrNull()
        }

        private fun verifiedModel(): File? =
            runCatching {
                verifiedModel.get()?.takeIf(File::isFile)?.let { return@runCatching it }
                val model = modelFile()
                val marker = verificationMarkerFile(model)
                val verified =
                    if (model.hasCurrentVerificationMarker(marker)) {
                        true
                    } else {
                        marker.delete()
                        if (model.hasExpectedDigest()) {
                            model.persistVerificationMarker(marker)
                            true
                        } else {
                            downloadVerifiedModel(model, marker)
                        }
                    }
                if (!verified) {
                    return@runCatching null
                }
                verifiedModel.compareAndSet(null, model)
                verifiedModel.get()
            }.getOrNull()

        private fun createSession(model: File): OrtSession? =
            runCatching {
                val options = OrtSession.SessionOptions()
                try {
                    options.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                    options.setInterOpNumThreads(1)
                    options.setIntraOpNumThreads(2)
                    options.setMemoryPatternOptimization(false)
                    options.setCPUArenaAllocator(false)
                    environment.createSession(model.absolutePath, options)
                } finally {
                    options.close()
                }
            }.getOrNull()

        private fun downloadVerifiedModel(
            destination: File,
            marker: File,
        ): Boolean {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.download")
            temporary.delete()
            return runCatching {
                FileOutputStream(temporary).use { output ->
                    ModelParts.forEach { part ->
                        val request =
                            Request.Builder()
                                .url(BuildConfig.DAG_NEURAL_MODEL_URL + part.filename)
                                .get()
                                .build()
                        client.newCall(request).execute().use { response ->
                            require(response.isSuccessful && response.request.url.isHttps)
                            val body = requireNotNull(response.body)
                            require(body.contentLength() == part.bytes)
                            val digest = MessageDigest.getInstance("SHA-256")
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DownloadBufferBytes)
                                var written = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    written += count
                                    require(written <= part.bytes)
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                                require(written == part.bytes)
                            }
                            require(digest.hex() == part.sha256)
                        }
                    }
                }
                require(temporary.hasExpectedDigest())
                destination.delete()
                require(temporary.renameTo(destination))
                destination.persistVerificationMarker(marker)
                true
            }.onFailure {
                temporary.delete()
            }.getOrDefault(false)
        }

        private fun modelFile(): File = File(context.noBackupFilesDir, "dag/models/$ModelFilename")

        private fun verificationMarkerFile(model: File): File = File(model.parentFile, ".${model.name}.verified")

        private fun File.hasCurrentVerificationMarker(marker: File): Boolean {
            if (!marker.isFile || marker.length() !in 1..MaximumVerificationMarkerBytes) return false
            val before = verificationMetadata() ?: return false
            val contents = runCatching { marker.readText(Charsets.UTF_8) }.getOrNull() ?: return false
            val after = verificationMetadata() ?: return false
            return before == after &&
                dagNeuralModelVerificationMarkerMatches(
                    marker = contents,
                    modelVersion = ModelVersion,
                    expectedSha256 = ModelSha256,
                    expectedBytes = ModelBytes,
                    metadata = after,
                )
        }

        private fun File.persistVerificationMarker(marker: File) {
            val metadata = verificationMetadata() ?: return
            val contents =
                dagNeuralModelVerificationMarker(
                    modelVersion = ModelVersion,
                    expectedSha256 = ModelSha256,
                    expectedBytes = ModelBytes,
                    metadata = metadata,
                ) ?: return
            val temporary = File(marker.parentFile, "${marker.name}.tmp")
            temporary.delete()
            runCatching {
                FileOutputStream(temporary).use { output ->
                    output.write(contents.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                marker.delete()
                require(temporary.renameTo(marker))
            }.onFailure {
                temporary.delete()
            }
        }

        private fun File.verificationMetadata(): DagNeuralModelFileMetadata? =
            runCatching {
                val stat = Os.stat(absolutePath)
                if (!OsConstants.S_ISREG(stat.st_mode)) return@runCatching null
                DagNeuralModelFileMetadata(
                    fileName = name,
                    bytes = stat.st_size,
                    device = stat.st_dev,
                    inode = stat.st_ino,
                    modifiedSeconds = stat.st_mtim.tv_sec,
                    modifiedNanoseconds = stat.st_mtim.tv_nsec,
                    changedSeconds = stat.st_ctim.tv_sec,
                    changedNanoseconds = stat.st_ctim.tv_nsec,
                )
            }.getOrNull()

        private fun File.hasExpectedDigest(): Boolean {
            val before = verificationMetadata() ?: return false
            if (before.bytes != ModelBytes || before.bytes !in 1..MaximumDownloadBytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(this).use { input ->
                val buffer = ByteArray(DownloadBufferBytes)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val after = verificationMetadata() ?: return false
            return before == after && digest.hex() == ModelSha256
        }

        private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

        companion object {
            const val ModelVersion = "dag-neural-minilm-2"
            private const val InputName = "text"
            private const val ModelFilename = "dag-multilingual-minilm-v1.onnx"
            private const val ModelSha256 = "641b05b3775dbb94ba7291c7fe607d0bfa304b844cdf6551271018fb287c3627"
            private const val ModelBytes = 123_379_433L

            // The classifier was trained with a 128-token window. Bounding the
            // raw input keeps the fused tokenizer close to that window and
            // prevents page-sized quadratic transformer work.
            private const val MaxInputCharacters = 384
            private const val MaximumDownloadBytes = 130L * 1024L * 1024L
            private const val DownloadBufferBytes = 128 * 1024
            private const val MaximumVerificationMarkerBytes = 1_024L
            private val ModelParts =
                listOf(
                    ModelPart(
                        "dag-multilingual-minilm-v1-81f961904468-part-00",
                        41_943_040L,
                        "24f1dd60a6145ec63891fa9cf89348a41f1d12058495020c68dfa5f0bc8c97ad",
                    ),
                    ModelPart(
                        "dag-multilingual-minilm-v1-81f961904468-part-01",
                        41_943_040L,
                        "0e8bbc09bf5b0ca08d82a66fbf97e0f21602d921f090124cf5eb4de304361d93",
                    ),
                    ModelPart(
                        "dag-multilingual-minilm-v1-641b05b3775d-part-02",
                        39_493_353L,
                        "e666a96a84c239289c523b48426eeaaba9d5a1eb81d28573881b36d504ed81e3",
                    ),
                )
            private val Categories =
                listOf("general", "sexual", "dating", "gambling", "drugs", "violence", "sensitive_context")

            private data class ModelPart(
                val filename: String,
                val bytes: Long,
                val sha256: String,
            )
        }
    }

internal data class DagNeuralModelFileMetadata(
    val fileName: String,
    val bytes: Long,
    val device: Long,
    val inode: Long,
    val modifiedSeconds: Long,
    val modifiedNanoseconds: Long,
    val changedSeconds: Long,
    val changedNanoseconds: Long,
)

internal fun dagNeuralModelVerificationMarker(
    modelVersion: String,
    expectedSha256: String,
    expectedBytes: Long,
    metadata: DagNeuralModelFileMetadata,
): String? {
    if (
        modelVersion.isBlank() ||
        modelVersion.any { it == '\n' || it == '\r' } ||
        expectedSha256.length != 64 ||
        expectedSha256.any { it !in '0'..'9' && it !in 'a'..'f' } ||
        expectedBytes <= 0L ||
        metadata.fileName.isBlank() ||
        metadata.fileName.any { it == '\n' || it == '\r' } ||
        metadata.bytes != expectedBytes ||
        metadata.device < 0L ||
        metadata.inode <= 0L ||
        metadata.modifiedSeconds <= 0L ||
        metadata.modifiedNanoseconds !in 0L..999_999_999L ||
        metadata.changedSeconds <= 0L ||
        metadata.changedNanoseconds !in 0L..999_999_999L
    ) {
        return null
    }
    return listOf(
        DagNeuralModelVerificationMarkerSchema,
        modelVersion,
        expectedSha256,
        expectedBytes,
        metadata.fileName,
        metadata.bytes,
        metadata.device,
        metadata.inode,
        metadata.modifiedSeconds,
        metadata.modifiedNanoseconds,
        metadata.changedSeconds,
        metadata.changedNanoseconds,
    ).joinToString(separator = "\n")
}

internal fun dagNeuralModelVerificationMarkerMatches(
    marker: String?,
    modelVersion: String,
    expectedSha256: String,
    expectedBytes: Long,
    metadata: DagNeuralModelFileMetadata,
): Boolean =
    marker != null &&
        marker ==
        dagNeuralModelVerificationMarker(
            modelVersion = modelVersion,
            expectedSha256 = expectedSha256,
            expectedBytes = expectedBytes,
            metadata = metadata,
        )

internal inline fun <T, R> dagMapNeuralBatch(
    values: List<T>,
    ensureActive: () -> Unit,
    transform: (T) -> R,
): List<R> =
    values.map { value ->
        ensureActive()
        transform(value)
    }

private const val DagNeuralModelVerificationMarkerSchema = "dag-neural-model-verification-v1"
