package com.contentfilter.core.update.repository

import android.content.Context
import com.contentfilter.core.update.config.UpdateConfigProvider
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.model.UpdateManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DevApkUpdateRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val configProvider: UpdateConfigProvider,
        private val httpClient: OkHttpClient,
    ) : ApkUpdateRepository {
        private val updateHttpClient =
            httpClient.newBuilder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .retryOnConnectionFailure(true)
                .connectTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(DownloadTimeoutSeconds, TimeUnit.SECONDS)
                .build()

        override suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult =
            checkForUpdate(configProvider.manifestUrl(), currentVersionCode)

        override suspend fun checkForUpdate(
            manifestUrl: String,
            currentVersionCode: Int,
        ): UpdateCheckResult =
            withContext(Dispatchers.IO) {
                if (manifestUrl.isBlank()) return@withContext UpdateCheckResult.NotConfigured

                val manifest = fetchManifest(manifestUrl) ?: return@withContext UpdateCheckResult.NetworkError
                if (manifest.versionCode > currentVersionCode) {
                    UpdateCheckResult.Available(manifest)
                } else {
                    UpdateCheckResult.UpToDate(manifest)
                }
            }

        override suspend fun download(
            manifest: UpdateManifest,
            onProgress: (Int) -> Unit,
        ): UpdateDownloadResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val updateDir = File(context.cacheDir, UpdateCacheDir).apply { mkdirs() }
                    val apk = File(updateDir, manifest.apkFileName())
                    val partial = File(updateDir, "${apk.name}.partial")
                    apk.delete()
                    onProgress(0)
                    repeat(MaxDownloadAttempts) { attempt ->
                        val existingBytes = partial.length().takeIf { partial.exists() } ?: 0L
                        val requestBuilder =
                            Request.Builder()
                                .url(manifest.apkUrl)
                                .header("Accept-Encoding", "identity")
                                .header("Cache-Control", "no-cache")
                                .get()
                        if (existingBytes > 0L) {
                            requestBuilder.header("Range", "bytes=$existingBytes-")
                        }
                        updateHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                            if (!response.isSuccessful) {
                                if (existingBytes > 0L) partial.delete()
                                if (attempt == MaxDownloadAttempts - 1) return@withContext UpdateDownloadResult.DownloadError
                                return@repeat
                            }
                            val body = response.body ?: return@withContext UpdateDownloadResult.DownloadError
                            val append = existingBytes > 0L && response.code == HttpPartialContent
                            if (!append) partial.delete()
                            val initialBytes = if (append) existingBytes else 0L
                            val expectedBytes = body.contentLength().takeIf { it > 0L }?.plus(initialBytes)
                            body.byteStream().use { input ->
                                FileOutputStream(partial, append).use { output ->
                                    val buffer = ByteArray(BufferSizeBytes)
                                    var downloaded = initialBytes
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        output.write(buffer, 0, read)
                                        downloaded += read
                                        expectedBytes?.let { total ->
                                            onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 99))
                                        }
                                    }
                                }
                            }
                            if (expectedBytes == null || partial.length() >= expectedBytes) {
                                if (!partial.renameTo(apk)) {
                                    partial.delete()
                                    return@withContext UpdateDownloadResult.DownloadError
                                }
                                if (!apk.sha256Matches(manifest.apkSha256)) {
                                    apk.delete()
                                    return@withContext UpdateDownloadResult.InvalidChecksum
                                }
                                onProgress(100)
                                return@withContext UpdateDownloadResult.Success(apk)
                            }
                        }
                    }
                    UpdateDownloadResult.DownloadError
                }.getOrElse { UpdateDownloadResult.DownloadError }
            }

        private fun fetchManifest(url: String): UpdateManifest? {
            val request = Request.Builder().url(url).get().build()
            return runCatching {
                updateHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.string()?.toUpdateManifest()
                }
            }.getOrNull()
        }

        private fun String.toUpdateManifest(): UpdateManifest {
            val json = JSONObject(this)
            return UpdateManifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                apkSha256 = json.getString("apkSha256"),
                releaseNotes = json.optString("releaseNotes"),
            )
        }

        private fun File.sha256Matches(expected: String): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().use { input ->
                val buffer = ByteArray(BufferSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString(separator = "") { "%02x".format(it) }
            return actual.equals(expected.trim(), ignoreCase = true)
        }

        private companion object {
            fun UpdateManifest.apkFileName(): String = apkUrl.substringAfterLast('/').ifBlank { DefaultUpdateApkName }

            const val UpdateCacheDir = "updates"
            const val DefaultUpdateApkName = "dev-update.apk"
            const val BufferSizeBytes = 64 * 1024
            const val NetworkTimeoutSeconds = 60L
            const val DownloadTimeoutSeconds = 600L
            const val MaxDownloadAttempts = 3
            const val HttpPartialContent = 206
        }
    }
