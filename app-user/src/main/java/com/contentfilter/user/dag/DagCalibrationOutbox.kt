package com.contentfilter.user.dag

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal data class DagCalibrationOutboxItem(
    val id: String,
    val createdAtEpochMillis: Long,
    val payload: JSONObject,
)

@Singleton
class DagCalibrationOutboxStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        @Synchronized
        internal fun enqueue(payload: JSONObject): String {
            val items = load()
            val key = payload.dagCalibrationDedupeKey()
            items.firstOrNull { it.payload.dagCalibrationDedupeKey() == key }?.let { return it.id }
            check(items.size < MaximumPendingItems) { "DAG calibration outbox is full" }
            val item =
                DagCalibrationOutboxItem(
                    id = UUID.randomUUID().toString(),
                    createdAtEpochMillis = System.currentTimeMillis(),
                    payload = JSONObject(payload.toString()),
                )
            persist(items + item)
            return item.id
        }

        @Synchronized
        internal fun pending(): List<DagCalibrationOutboxItem> = load()

        @Synchronized
        internal fun remove(id: String) {
            persist(load().filterNot { it.id == id })
        }

        private fun load(): List<DagCalibrationOutboxItem> {
            val encoded = preferences.getString(EncryptedItemsKey, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(decrypt(encoded))
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.getJSONObject(index)
                        add(
                            DagCalibrationOutboxItem(
                                id = value.getString("id"),
                                createdAtEpochMillis = value.optLong("created_at", 0L),
                                payload = value.getJSONObject("payload"),
                            ),
                        )
                    }
                }.filter { item ->
                    item.createdAtEpochMillis > 0L &&
                        System.currentTimeMillis() - item.createdAtEpochMillis <= MaximumAgeMillis
                }
            }.getOrElse {
                preferences.edit().remove(EncryptedItemsKey).commit()
                emptyList()
            }
        }

        private fun persist(items: List<DagCalibrationOutboxItem>) {
            if (items.isEmpty()) {
                check(preferences.edit().remove(EncryptedItemsKey).commit())
                return
            }
            val array =
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject()
                                .put("id", item.id)
                                .put("created_at", item.createdAtEpochMillis)
                                .put("payload", item.payload),
                        )
                    }
                }
            check(preferences.edit().putString(EncryptedItemsKey, encrypt(array.toString())).commit())
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }

        private fun decrypt(value: String): String {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            require(payload.size > IvLength)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TagLengthBits, payload.copyOfRange(0, IvLength)),
            )
            return cipher.doFinal(payload.copyOfRange(IvLength, payload.size)).toString(Charsets.UTF_8)
        }

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
            (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        private companion object {
            const val PreferencesName = "dag-calibration-outbox"
            const val EncryptedItemsKey = "items"
            const val MaximumPendingItems = 24
            const val MaximumAgeMillis = 7 * 24 * 60 * 60 * 1_000L
            const val Transformation = "AES/GCM/NoPadding"
            const val AndroidKeyStore = "AndroidKeyStore"
            const val KeyAlias = "dag-calibration-outbox-v1"
            const val IvLength = 12
            const val TagLengthBits = 128
        }
    }

internal fun JSONObject.dagCalibrationDedupeKey(): String =
    listOf(
        optString("device_id"),
        optString("action"),
        optString("image_hash"),
        optString("model_version"),
    ).joinToString("|")

internal class DagCalibrationRetryScheduler(
    private val context: Context,
) {
    fun requestRetry() {
        val request =
            OneTimeWorkRequestBuilder<DagCalibrationRetryWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkName,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WorkName = "dag-calibration-delivery"
    }
}

@HiltWorker
internal class DagCalibrationRetryWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted workerParameters: WorkerParameters,
        private val activationRepository: DeviceActivationRepository,
        private val calibrationRepository: DagCalibrationRepository,
    ) : CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result {
            val deviceId = activationRepository.currentActivation()?.deviceId ?: return Result.success()
            return if (calibrationRepository.flushPending(deviceId)) Result.success() else Result.retry()
        }
    }
