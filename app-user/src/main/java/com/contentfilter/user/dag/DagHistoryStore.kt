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

        @Synchronized
        fun hasFreshPageApproval(
            url: String,
            fingerprint: String,
            policyVersion: Long,
            modelVersion: String,
            nowEpochMillis: Long = System.currentTimeMillis(),
        ): Boolean =
            loadPageApprovals(nowEpochMillis).any {
                it.url == url &&
                    it.fingerprint == fingerprint &&
                    it.policyVersion == policyVersion &&
                    it.modelVersion == modelVersion
            }

        @Synchronized
        fun savePageApproval(
            url: String,
            fingerprint: String,
            policyVersion: Long,
            modelVersion: String,
            nowEpochMillis: Long = System.currentTimeMillis(),
        ) {
            val entry =
                DagPageApproval(
                    url = url.take(MaxValueCharacters),
                    fingerprint = fingerprint,
                    policyVersion = policyVersion,
                    modelVersion = modelVersion,
                    approvedAtEpochMillis = nowEpochMillis,
                    expiresAtEpochMillis = nowEpochMillis + PageApprovalLifetimeMillis,
                )
            val retained =
                loadPageApprovals(nowEpochMillis)
                    .filterNot { it.url == entry.url }
            persistPageApprovals((listOf(entry) + retained).take(MaxPageApprovals))
        }

        @Synchronized
        fun clearPageApprovals() {
            preferences.edit().remove(EncryptedPageApprovalsKey).commit()
        }

        @Synchronized
        internal fun loadTabSession(): DagSavedTabSession? {
            val encoded = preferences.getString(EncryptedTabsKey, null) ?: return null
            return runCatching { decodeTabSession(decrypt(encoded)) }
                .getOrElse {
                    preferences.edit().remove(EncryptedTabsKey).commit()
                    null
                }
        }

        @Synchronized
        internal fun saveTabSession(session: DagSavedTabSession) {
            runCatching {
                preferences.edit().putString(EncryptedTabsKey, encrypt(encodeTabSession(session))).commit()
            }.onFailure {
                preferences.edit().remove(EncryptedTabsKey).commit()
            }
        }

        @Synchronized
        fun clearTabSession() {
            preferences.edit().remove(EncryptedTabsKey).commit()
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

        private fun loadPageApprovals(nowEpochMillis: Long): List<DagPageApproval> {
            val encoded = preferences.getString(EncryptedPageApprovalsKey, null) ?: return emptyList()
            return runCatching {
                decodePageApprovals(decrypt(encoded)).filter {
                    nowEpochMillis >= it.approvedAtEpochMillis && it.expiresAtEpochMillis > nowEpochMillis
                }
            }.getOrElse {
                preferences.edit().remove(EncryptedPageApprovalsKey).commit()
                emptyList()
            }
        }

        private fun persistPageApprovals(entries: List<DagPageApproval>) {
            runCatching {
                preferences
                    .edit()
                    .putString(EncryptedPageApprovalsKey, encrypt(encodePageApprovals(entries)))
                    .commit()
            }.onFailure {
                preferences.edit().remove(EncryptedPageApprovalsKey).commit()
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

            internal fun encodePageApprovals(entries: List<DagPageApproval>): String =
                JSONArray().apply {
                    entries.forEach { entry ->
                        put(
                            JSONObject()
                                .put("url", entry.url)
                                .put("fingerprint", entry.fingerprint)
                                .put("policy_version", entry.policyVersion)
                                .put("model_version", entry.modelVersion)
                                .put("approved_at", entry.approvedAtEpochMillis)
                                .put("expires_at", entry.expiresAtEpochMillis),
                        )
                    }
                }.toString()

            internal fun decodePageApprovals(value: String): List<DagPageApproval> {
                val array = JSONArray(value)
                return (0 until array.length()).map { index ->
                    val json = array.getJSONObject(index)
                    DagPageApproval(
                        url = json.getString("url"),
                        fingerprint = json.getString("fingerprint"),
                        policyVersion = json.getLong("policy_version"),
                        modelVersion = json.getString("model_version"),
                        approvedAtEpochMillis = json.getLong("approved_at"),
                        expiresAtEpochMillis = json.getLong("expires_at"),
                    )
                }.take(MaxPageApprovals)
            }

            internal fun encodeTabSession(session: DagSavedTabSession): String =
                JSONObject()
                    .put("active_tab_id", session.activeTabId)
                    .put(
                        "tabs",
                        JSONArray().apply {
                            session.tabs.take(MaxSavedTabs).forEach { tab ->
                                put(
                                    JSONObject()
                                        .put("id", tab.id)
                                        .put("last_used_at", tab.lastUsedAtEpochMillis)
                                        .put("snapshot", encodeTabSnapshot(tab.snapshot)),
                                )
                            }
                        },
                    ).toString()

            internal fun decodeTabSession(value: String): DagSavedTabSession {
                val root = JSONObject(value)
                val tabsJson = root.getJSONArray("tabs")
                val tabs =
                    (0 until tabsJson.length()).map { index ->
                        val json = tabsJson.getJSONObject(index)
                        DagSavedTab(
                            id = json.getString("id"),
                            snapshot = decodeTabSnapshot(json.getJSONObject("snapshot")),
                            lastUsedAtEpochMillis = json.optLong("last_used_at", (tabsJson.length() - index).toLong()),
                        )
                    }.take(MaxSavedTabs)
                require(tabs.isNotEmpty())
                val requestedActiveId = root.getString("active_tab_id")
                return DagSavedTabSession(
                    activeTabId = requestedActiveId.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
                    tabs = tabs,
                )
            }

            private fun encodeTabSnapshot(snapshot: DagTabSnapshot): JSONObject =
                JSONObject()
                    .put("address", snapshot.address.take(MaxValueCharacters))
                    .put("view", snapshot.view.name)
                    .put("search_query", snapshot.searchQuery.take(MaxValueCharacters))
                    .put("search_page", snapshot.searchPage)
                    .put("can_load_more", snapshot.canLoadMoreResults)
                    .put("requested_url", snapshot.requestedUrl?.take(MaxValueCharacters) ?: JSONObject.NULL)
                    .put(
                        "results",
                        JSONArray().apply {
                            snapshot.results.take(MaxSavedResults).forEach { result ->
                                put(
                                    JSONObject()
                                        .put("title", result.title.take(MaxTitleCharacters))
                                        .put("url", result.url.take(MaxValueCharacters))
                                        .put("domain", result.domain.take(MaxTitleCharacters))
                                        .put("description", result.description.take(MaxDescriptionCharacters))
                                        .put("decision", result.classification.decision.name)
                                        .put("category", result.classification.category.take(MaxTitleCharacters))
                                        .put("confidence", result.classification.confidence.toDouble())
                                        .put(
                                            "model_version",
                                            result.classification.modelVersion.take(MaxTitleCharacters),
                                        ),
                                )
                            }
                        },
                    )

            private fun decodeTabSnapshot(json: JSONObject): DagTabSnapshot {
                val requestedView = DagView.valueOf(json.getString("view"))
                val requestedUrl = json.optString("requested_url").takeIf { it.isNotBlank() && it != "null" }
                val safeView =
                    when {
                        requestedView == DagView.Browser && requestedUrl == null -> DagView.Start
                        requestedView == DagView.History -> DagView.Start
                        else -> requestedView
                    }
                val resultsJson = json.getJSONArray("results")
                val results =
                    (0 until resultsJson.length()).map { index ->
                        val result = resultsJson.getJSONObject(index)
                        DagSearchResult(
                            title = result.getString("title"),
                            url = result.getString("url"),
                            domain = result.getString("domain"),
                            description = result.getString("description"),
                            classification =
                                DagClassificationResult(
                                    decision = DagClassification.valueOf(result.getString("decision")),
                                    category = result.getString("category"),
                                    confidence = result.getDouble("confidence").toFloat(),
                                    modelVersion = result.getString("model_version"),
                                ),
                        )
                    }.take(MaxSavedResults)
                return DagTabSnapshot(
                    address = json.getString("address"),
                    view = safeView,
                    pageStatus = if (safeView == DagView.Browser) DagPageStatus.Loading else DagPageStatus.Idle,
                    results = if (safeView == DagView.Results || safeView == DagView.Browser) results else emptyList(),
                    searchQuery = json.optString("search_query"),
                    searchPage = json.optInt("search_page", 0),
                    canLoadMoreResults = json.optBoolean("can_load_more", false),
                    requestedUrl = requestedUrl.takeIf { safeView == DagView.Browser },
                )
            }

            private const val PreferencesName = "dag-history"
            private const val EncryptedHistoryKey = "entries"
            private const val EncryptedPageApprovalsKey = "page-approvals"
            private const val EncryptedTabsKey = "tabs"
            private const val AndroidKeyStore = "AndroidKeyStore"
            private const val KeyAlias = "content-filter-dag-history-v1"
            private const val Transformation = "AES/GCM/NoPadding"
            private const val IvLength = 12
            private const val TagLengthBits = 128
            private const val MaxEntries = 200
            private const val MaxPageApprovals = 200
            private const val MaxSavedTabs = 8
            private const val MaxSavedResults = 20
            private const val PageApprovalLifetimeMillis = 7L * 24L * 60L * 60L * 1_000L
            private const val MaxValueCharacters = 2_048
            private const val MaxTitleCharacters = 180
            private const val MaxDescriptionCharacters = 500
        }
    }
