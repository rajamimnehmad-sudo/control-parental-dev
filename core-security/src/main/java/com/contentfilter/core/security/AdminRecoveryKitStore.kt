package com.contentfilter.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class RevealedRecoveryCode(
    val code: String,
    val remaining: Int,
)

@Singleton
class AdminRecoveryKitStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        @Synchronized
        fun save(
            deviceId: String,
            revision: Long,
            codes: List<String>,
        ) {
            require(codes.size == KitSize)
            val kits = loadAll().toMutableMap()
            kits[deviceId] = StoredKit(revision = revision, codes = codes, revealedSlots = emptySet())
            persist(kits)
        }

        @Synchronized
        fun remaining(
            deviceId: String,
            expectedRevision: Long? = null,
        ): Int =
            loadAll()[deviceId]
                ?.takeIf { expectedRevision == null || it.revision == expectedRevision }
                ?.let { kit -> kit.codes.indices.count { it !in kit.revealedSlots } } ?: 0

        @Synchronized
        fun revealNext(
            deviceId: String,
            expectedRevision: Long? = null,
        ): RevealedRecoveryCode? {
            val kits = loadAll().toMutableMap()
            val kit = kits[deviceId] ?: return null
            if (expectedRevision != null && kit.revision != expectedRevision) return null
            val slot = kit.codes.indices.firstOrNull { it !in kit.revealedSlots } ?: return null
            val updated = kit.copy(revealedSlots = kit.revealedSlots + slot)
            kits[deviceId] = updated
            persist(kits)
            return RevealedRecoveryCode(
                code = kit.codes[slot],
                remaining = kit.codes.size - updated.revealedSlots.size,
            )
        }

        private fun loadAll(): Map<String, StoredKit> {
            val encoded = preferences.getString(EncryptedStateKey, null) ?: return emptyMap()
            return runCatching { decrypt(encoded).toKits() }.getOrElse { emptyMap() }
        }

        private fun persist(kits: Map<String, StoredKit>) {
            val json =
                JSONObject().also { root ->
                    kits.forEach { (deviceId, kit) ->
                        root.put(
                            deviceId,
                            JSONObject()
                                .put("revision", kit.revision)
                                .put("codes", JSONArray(kit.codes))
                                .put("revealed", JSONArray(kit.revealedSlots.sorted())),
                        )
                    }
                }
            check(preferences.edit().putString(EncryptedStateKey, encrypt(json.toString())).commit())
        }

        private fun String.toKits(): Map<String, StoredKit> {
            val root = JSONObject(this)
            return buildMap {
                root.keys().forEach { deviceId ->
                    val json = root.optJSONObject(deviceId) ?: return@forEach
                    val codesJson = json.optJSONArray("codes") ?: return@forEach
                    val codes = buildList { repeat(codesJson.length()) { add(codesJson.optString(it)) } }
                    if (codes.size != KitSize || codes.any(String::isBlank)) return@forEach
                    val revealedJson = json.optJSONArray("revealed") ?: JSONArray()
                    val revealed = buildSet { repeat(revealedJson.length()) { add(revealedJson.optInt(it, -1)) } }
                    put(
                        deviceId,
                        StoredKit(
                            revision = json.optLong("revision", 0),
                            codes = codes,
                            revealedSlots = revealed.filter { it in codes.indices }.toSet(),
                        ),
                    )
                }
            }
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
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            KeyAlias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                }.generateKey()
        }

        private data class StoredKit(
            val revision: Long,
            val codes: List<String>,
            val revealedSlots: Set<Int>,
        )

        private companion object {
            const val PreferencesName = "admin-recovery-kits"
            const val EncryptedStateKey = "kits"
            const val AndroidKeyStore = "AndroidKeyStore"
            const val KeyAlias = "content-filter-admin-recovery-kits-v1"
            const val Transformation = "AES/GCM/NoPadding"
            const val IvLength = 12
            const val TagLengthBits = 128
            const val KitSize = 5
        }
    }
