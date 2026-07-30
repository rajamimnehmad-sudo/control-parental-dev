package com.contentfilter.user.help

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.contentfilter.core.domain.help.HelpContext
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class GloshiaModelPhase {
    Missing,
    Downloading,
    Verifying,
    Ready,
    Loading,
    Unsupported,
    Error,
}

data class GloshiaModelState(
    val phase: GloshiaModelPhase,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = ModelSizeBytes,
    val detail: String? = null,
) {
    val progress: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    val canGenerate: Boolean
        get() = phase == GloshiaModelPhase.Ready

    companion object {
        const val ModelSizeBytes = 647_377_840L
    }
}

@Singleton
class GloshiaLocalModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        httpClient: OkHttpClient,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val preparationMutex = Mutex()
        private val generationMutex = Mutex()
        private val client =
            httpClient
                .newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        private val modelDirectory =
            requireNotNull(context.getExternalFilesDir(ModelDirectoryName)) {
                "No hay almacenamiento disponible para GloshIA"
            }
        private val modelFile = File(modelDirectory, ModelFileName)
        private val partialFile = File(modelDirectory, "$ModelFileName.partial")
        private val cacheDirectory = File(context.cacheDir, "gloshia-litert")
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val supported = Build.SUPPORTED_ABIS.any { it == SupportedAbi }
        private val enoughMemory = deviceMemoryClassMb() >= MinimumMemoryClassMb

        private val _state =
            MutableStateFlow(
                when {
                    !supported ->
                        GloshiaModelState(
                            phase = GloshiaModelPhase.Unsupported,
                            detail = "Este teléfono no tiene arquitectura ARM64.",
                        )
                    !enoughMemory ->
                        GloshiaModelState(
                            phase = GloshiaModelPhase.Unsupported,
                            detail = "Este teléfono no tiene memoria suficiente para el modelo conversacional.",
                        )
                    modelIsTrusted() -> GloshiaModelState(GloshiaModelPhase.Ready, ModelSizeBytes, ModelSizeBytes)
                    modelFile.length() == ModelSizeBytes -> GloshiaModelState(GloshiaModelPhase.Verifying)
                    else -> GloshiaModelState(GloshiaModelPhase.Missing)
                },
            )
        val state: StateFlow<GloshiaModelState> = _state.asStateFlow()

        private var engine: Engine? = null
        private var conversation: Conversation? = null
        private var conversationTurns = 0

        init {
            modelDirectory.mkdirs()
            cacheDirectory.mkdirs()
            if (_state.value.phase == GloshiaModelPhase.Verifying) {
                scope.launch { verifyAndMarkReady(modelFile) }
            }
        }

        fun prepare() {
            if (!supported || !enoughMemory) return
            scope.launch {
                preparationMutex.withLock {
                    when {
                        modelIsTrusted() -> _state.value = GloshiaModelState(GloshiaModelPhase.Ready, ModelSizeBytes)
                        modelFile.length() == ModelSizeBytes -> verifyAndMarkReady(modelFile)
                        else -> downloadModel()
                    }
                }
            }
        }

        suspend fun generate(
            prompt: String,
            context: HelpContext,
            reliableAnswer: String,
        ): String? =
            generationMutex.withLock {
                if (!modelIsTrusted()) return@withLock null
                try {
                    val activeConversation = ensureConversation()
                    if (conversationTurns >= MaxConversationTurns) {
                        activeConversation.close()
                        conversation = createConversation(requireNotNull(engine))
                        conversationTurns = 0
                    }
                    val request =
                        GloshiaPromptPolicy.userMessage(
                            prompt = prompt,
                            context = context,
                            reliableAnswer = reliableAnswer,
                        )
                    val response =
                        withTimeout(GenerationTimeoutMs) {
                            withContext(Dispatchers.Default) {
                                val output = StringBuilder()
                                requireNotNull(conversation).sendMessageAsync(request).collect { message ->
                                    val chunk =
                                        message.contents.contents
                                            .filterIsInstance<Content.Text>()
                                            .joinToString(separator = "") { it.text }
                                    if (chunk.startsWith(output.toString())) {
                                        output.clear()
                                    }
                                    output.append(chunk)
                                }
                                output.toString()
                            }
                        }
                    conversationTurns += 1
                    GloshiaPromptPolicy.sanitizeResponse(response, prompt).takeIf(String::isNotBlank)
                } catch (error: Throwable) {
                    conversation?.close()
                    conversation = null
                    engine?.close()
                    engine = null
                    fail("No se pudo iniciar el chat local. La ayuda básica sigue disponible.")
                    throw error
                }
            }

        fun close() {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            scope.cancel()
        }

        private suspend fun ensureConversation(): Conversation {
            conversation?.let { return it }
            _state.value = GloshiaModelState(GloshiaModelPhase.Loading, ModelSizeBytes)
            val loadedEngine =
                withContext(Dispatchers.Default) {
                    runCatching { createEngine(Backend.GPU()) }
                        .getOrElse { createEngine(Backend.CPU(threadCount = cpuThreadCount())) }
                }
            engine = loadedEngine
            return createConversation(loadedEngine).also {
                conversation = it
                conversationTurns = 0
                _state.value = GloshiaModelState(GloshiaModelPhase.Ready, ModelSizeBytes)
            }
        }

        private fun createEngine(backend: Backend): Engine {
            val candidate =
                Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        maxNumTokens = MaxModelTokens,
                        cacheDir = cacheDirectory.absolutePath,
                    ),
                )
            return try {
                candidate.initialize()
                candidate
            } catch (error: Throwable) {
                candidate.close()
                throw error
            }
        }

        private fun createConversation(engine: Engine): Conversation =
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(GloshiaPromptPolicy.systemInstruction),
                    samplerConfig =
                        SamplerConfig(
                            topK = 20,
                            topP = 0.85,
                            temperature = 0.3,
                            seed = 42,
                        ),
                ),
            )

        private suspend fun downloadModel() {
            modelDirectory.mkdirs()
            val existingBytes = partialFile.length().coerceAtMost(ModelSizeBytes)
            _state.value =
                GloshiaModelState(
                    phase = GloshiaModelPhase.Downloading,
                    downloadedBytes = existingBytes,
                )
            val requestBuilder = Request.Builder().url(ModelUrl)
            if (existingBytes > 0) requestBuilder.header("Range", "bytes=$existingBytes-")
            val response = client.newCall(requestBuilder.build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    fail("No se pudo descargar el modelo (${it.code}).")
                    return
                }
                val append = existingBytes > 0 && it.code == PartialContentCode
                val startingBytes = if (append) existingBytes else 0L
                val body =
                    it.body ?: run {
                        fail("La descarga del modelo llegó vacía.")
                        return
                    }
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DownloadBufferSize)
                        var downloaded = startingBytes
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            _state.value =
                                GloshiaModelState(
                                    phase = GloshiaModelPhase.Downloading,
                                    downloadedBytes = downloaded,
                                )
                        }
                    }
                }
            }
            if (partialFile.length() != ModelSizeBytes) {
                fail("La descarga quedó incompleta. Podés reanudarla.")
                return
            }
            verifyAndMarkReady(partialFile)
        }

        private suspend fun verifyAndMarkReady(candidate: File) {
            _state.value =
                GloshiaModelState(
                    phase = GloshiaModelPhase.Verifying,
                    downloadedBytes = candidate.length(),
                )
            val digest = withContext(Dispatchers.IO) { candidate.sha256() }
            if (!digest.equals(ModelSha256, ignoreCase = true)) {
                candidate.delete()
                clearTrustedModel()
                fail("El modelo no superó la verificación de seguridad.")
                return
            }
            if (candidate != modelFile) {
                if (modelFile.exists()) modelFile.delete()
                if (!candidate.renameTo(modelFile)) {
                    fail("No se pudo guardar el modelo verificado.")
                    return
                }
            }
            preferences
                .edit()
                .putString(TrustedShaKey, ModelSha256)
                .putLong(TrustedLengthKey, modelFile.length())
                .putLong(TrustedModifiedKey, modelFile.lastModified())
                .apply()
            _state.value = GloshiaModelState(GloshiaModelPhase.Ready, ModelSizeBytes)
        }

        private fun modelIsTrusted(): Boolean =
            modelFile.length() == ModelSizeBytes &&
                preferences.getString(TrustedShaKey, null) == ModelSha256 &&
                preferences.getLong(TrustedLengthKey, -1) == modelFile.length() &&
                preferences.getLong(TrustedModifiedKey, -1) == modelFile.lastModified()

        private fun clearTrustedModel() {
            preferences.edit().clear().apply()
        }

        private fun fail(message: String) {
            _state.value =
                GloshiaModelState(
                    phase = GloshiaModelPhase.Error,
                    downloadedBytes = partialFile.length(),
                    detail = message,
                )
        }

        private fun deviceMemoryClassMb(): Int =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass

        private fun cpuThreadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

        private fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().buffered().use { input ->
                val buffer = ByteArray(HashBufferSize)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
        }

        private companion object {
            const val ModelDirectoryName = "gloshia"
            const val ModelFileName = "Qwen2_0.5B_Instruct.litertlm"
            const val ModelUrl =
                "https://huggingface.co/litert-community/Qwen2-0.5B-Instruct/resolve/" +
                    "f2949f79a8154234747a794348d77554ae0e1fb0/Qwen2_0.5B_Instruct.litertlm"
            const val ModelSha256 = "0f01cc004b8eb62b92ba6be85ed05a248ba0d2f78af94c4949b313eccfb4c157"
            const val PreferencesName = "gloshia_local_model"
            const val TrustedShaKey = "trusted_sha"
            const val TrustedLengthKey = "trusted_length"
            const val TrustedModifiedKey = "trusted_modified"
            const val SupportedAbi = "arm64-v8a"
            const val MinimumMemoryClassMb = 256
            const val PartialContentCode = 206
            const val DownloadBufferSize = 128 * 1024
            const val HashBufferSize = 1024 * 1024
            const val MaxModelTokens = 1024
            const val MaxConversationTurns = 8
            const val GenerationTimeoutMs = 45_000L
            const val ModelSizeBytes = GloshiaModelState.ModelSizeBytes
        }
    }
