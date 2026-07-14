package com.contentfilter.user.dag

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagHistoryStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val mutableEntries = MutableStateFlow(loadSafely())

        fun observe(): StateFlow<List<DagHistoryEntry>> = mutableEntries

        @Synchronized
        fun addSearch(
            query: String,
            nowEpochMillis: Long = System.currentTimeMillis(),
        ) {
            add(
                DagHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    type = DagHistoryType.Search,
                    value = query.take(MaxValueCharacters),
                    url = null,
                    title = query.take(MaxTitleCharacters),
                    visitedAtEpochMillis = nowEpochMillis,
                ),
            )
        }

        @Synchronized
        fun addPage(
            url: String,
            title: String,
            nowEpochMillis: Long = System.currentTimeMillis(),
        ) {
            add(
                DagHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    type = DagHistoryType.Page,
                    value = url.take(MaxValueCharacters),
                    url = url.take(MaxValueCharacters),
                    title = title.take(MaxTitleCharacters).ifBlank { DagContentClassifier.domainFrom(url) },
                    visitedAtEpochMillis = nowEpochMillis,
                ),
            )
        }

        @Synchronized
        fun delete(id: String) {
            persist(mutableEntries.value.filterNot { it.id == id })
        }

        @Synchronized
        fun clear() {
            preferences.edit().remove(EncryptedHistoryKey).commit()
            mutableEntries.value = emptyList()
        }

        private fun add(entry: DagHistoryEntry) {
            val deduplicated =
                mutableEntries.value.filterNot {
                    it.type == entry.type && it.value.equals(entry.value, ignoreCase = true)
                }
            persist((listOf(entry) + deduplicated).take(MaxEntries))
        }

        private fun loadSafely(): List<DagHistoryEntry> {
            val encoded = preferences.getString(EncryptedHistoryKey, null) ?: return emptyList()
            return runCatching { decodeEntries(decrypt(encoded)) }
                .getOrElse {
                    preferences.edit().remove(EncryptedHistoryKey).commit()
                    emptyList()
                }
        }

        private fun persist(entries: List<DagHistoryEntry>) {
            runCatching {
                preferences.edit().putString(EncryptedHistoryKey, encrypt(encodeEntries(entries))).commit()
            }.onSuccess {
                mutableEntries.value = entries
            }.onFailure {
                preferences.edit().remove(EncryptedHistoryKey).commit()
                mutableEntries.value = emptyList()
            }
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(payload, Base64.NO_WRAP)
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

        companion object {
            internal fun encodeEntries(entries: List<DagHistoryEntry>): String =
                JSONArray().apply {
                    entries.forEach { entry ->
                        put(
                            JSONObject()
                                .put("id", entry.id)
                                .put("type", entry.type.name)
                                .put("value", entry.value)
                                .put("url", entry.url ?: JSONObject.NULL)
                                .put("title", entry.title ?: JSONObject.NULL)
                                .put("visited_at", entry.visitedAtEpochMillis),
                        )
                    }
                }.toString()

            internal fun decodeEntries(value: String): List<DagHistoryEntry> {
                val array = JSONArray(value)
                return (0 until array.length()).map { index ->
                    val json = array.getJSONObject(index)
                    DagHistoryEntry(
                        id = json.getString("id"),
                        type = DagHistoryType.valueOf(json.getString("type")),
                        value = json.getString("value"),
                        url = json.optString("url").takeIf { it.isNotBlank() && it != "null" },
                        title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
                        visitedAtEpochMillis = json.getLong("visited_at"),
                    )
                }.take(MaxEntries)
            }

            private const val PreferencesName = "dag-history"
            private const val EncryptedHistoryKey = "entries"
            private const val AndroidKeyStore = "AndroidKeyStore"
            private const val KeyAlias = "content-filter-dag-history-v1"
            private const val Transformation = "AES/GCM/NoPadding"
            private const val IvLength = 12
            private const val TagLengthBits = 128
            private const val MaxEntries = 200
            private const val MaxValueCharacters = 2_048
            private const val MaxTitleCharacters = 180
        }
    }
