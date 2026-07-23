package com.contentfilter.user.dag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagNeuralTextClassifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
    ) {
        private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OrtEnvironment.getEnvironment() }
        private val session = AtomicReference<OrtSession?>()

        suspend fun prepare() {
            if (session.get() != null || BuildConfig.DAG_NEURAL_MODEL_URL.isBlank()) return
            val model = modelFile()
            if (!model.hasExpectedDigest()) {
                downloadVerifiedModel(model)
            }
            if (!model.hasExpectedDigest()) return
            val options = OrtSession.SessionOptions()
            runCatching {
                options.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                environment.createSession(model.absolutePath, options)
            }.onSuccess { loaded ->
                if (!session.compareAndSet(null, loaded)) loaded.close()
            }.onFailure {
                model.delete()
            }
            options.close()
        }

        internal fun classify(text: String): DagSemanticPrediction? {
            val activeSession = session.get() ?: return null
            return runCatching {
                OnnxTensor.createTensor(environment, arrayOf(text.take(MaxInputCharacters))).use { input ->
                    activeSession.run(mapOf(InputName to input)).use { result ->
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

        private fun downloadVerifiedModel(destination: File) {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.download")
            temporary.delete()
            runCatching {
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
            }.onFailure {
                temporary.delete()
            }
        }

        private fun modelFile(): File = File(context.noBackupFilesDir, "dag/models/$ModelFilename")

        private fun File.hasExpectedDigest(): Boolean {
            if (!isFile || length() !in 1..MaximumDownloadBytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(this).use { input ->
                val buffer = ByteArray(DownloadBufferBytes)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == ModelSha256
        }

        private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

        companion object {
            const val ModelVersion = "dag-neural-minilm-1"
            private const val InputName = "text"
            private const val ModelFilename = "dag-multilingual-minilm-v1.onnx"
            private const val ModelSha256 = "641b05b3775dbb94ba7291c7fe607d0bfa304b844cdf6551271018fb287c3627"
            private const val MaxInputCharacters = 2_000
            private const val MaximumDownloadBytes = 130L * 1024L * 1024L
            private const val DownloadBufferBytes = 128 * 1024
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
