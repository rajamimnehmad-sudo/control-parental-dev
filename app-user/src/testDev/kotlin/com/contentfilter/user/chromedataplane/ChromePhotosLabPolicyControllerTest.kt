package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ChromePhotosLabPolicyControllerTest {
    @Test
    fun `managed Chrome policy uses exact Android Bundle scalar types`() {
        val values = ChromeStockMediaManagedPolicy.values

        assertEquals("[\"*\"]", assertIs<String>(values.getValue("URLBlocklist")))
        assertEquals(
            "[\"http://*\",\"https://*\",\"chrome://newtab\",\"chrome://settings/*\"," +
                "\"chrome://policy\",\"chrome://management\",\"chrome://version\",\"chrome://downloads\"," +
                "\"chrome://history\"]",
            assertIs<String>(values.getValue("URLAllowlist")),
        )
        listOf(
            "IncognitoModeAvailability",
            "SearchContentSharingSettings",
            "AIModeSettings",
            "FindsSettings",
        ).forEach { key -> assertIs<Int>(values.getValue(key)) }
        listOf(
            "NTPContentSuggestionsEnabled",
            "SearchSuggestEnabled",
            "ForceGoogleSafeSearch",
            "BackForwardCacheEnabled",
            "AllowBackForwardCacheForCacheControlNoStorePageEnabled",
            "DataUrlInSvgUseEnabled",
        ).forEach { key -> assertIs<Boolean>(values.getValue(key)) }
        assertTrue(ChromeStockMediaManagedPolicy.matchesValues(values))

        val wrongListType = values.toMutableMap().apply { put("URLBlocklist", arrayOf("*")) }
        val wrongIntegerType = values.toMutableMap().apply { put("AIModeSettings", 1L) }

        assertFalse(ChromeStockMediaManagedPolicy.matchesValues(wrongListType))
        assertFalse(ChromeStockMediaManagedPolicy.matchesValues(wrongIntegerType))
    }

    @Test
    fun `merge overrides only owned keys and preserves unrelated runtime types`() {
        val originalNames = arrayOf("alpha", "beta")
        val original =
            linkedMapOf<String, Any?>(
                "URLBlocklist" to "[\"existing.example\"]",
                "UnrelatedString" to "kept",
                "UnrelatedInt" to 7,
                "UnrelatedLong" to 7L,
                "UnrelatedStrings" to originalNames,
                "UnrelatedNested" to linkedMapOf("enabled" to true, "generation" to 4),
            )
        val overrides =
            linkedMapOf<String, Any?>("ProxySettings" to "{\"ProxyMode\":\"fixed_servers\"}").apply {
                putAll(ChromeStockMediaManagedPolicy.values)
            }

        val merged = ChromeRestrictionsSnapshotContract.merge(original, overrides)

        assertEquals("kept", merged["UnrelatedString"])
        assertIs<Int>(merged["UnrelatedInt"])
        assertIs<Long>(merged["UnrelatedLong"])
        assertEquals("[\"*\"]", merged["URLBlocklist"])
        assertEquals(overrides["ProxySettings"], merged["ProxySettings"])
        val copiedNames = assertIs<Array<*>>(merged["UnrelatedStrings"])
        assertEquals(String::class.java, copiedNames.javaClass.componentType)
        assertNotSame(originalNames, copiedNames)
        assertTrue(
            ChromeStockMediaManagedPolicy.matchesValues(
                merged.filterKeys(ChromeStockMediaManagedPolicy.keys::contains),
            ),
        )
        assertEquals("[\"existing.example\"]", original["URLBlocklist"])
    }

    @Test
    fun `restore copy removes transaction additions and preserves exact snapshot types`() {
        val originalArray = arrayOf("one", "two")
        val snapshot =
            linkedMapOf<String, Any?>(
                "ExistingProxySettings" to "direct",
                "Count" to 1,
                "LongCount" to 1L,
                "Names" to originalArray,
            )
        val transactionSnapshot = ChromeRestrictionsSnapshotContract.copyOf(snapshot)
        val applied =
            ChromeRestrictionsSnapshotContract.merge(
                transactionSnapshot,
                mapOf("ProxySettings" to "fixed", "Count" to 2),
            )
        val restored = ChromeRestrictionsSnapshotContract.copyOf(transactionSnapshot)

        assertTrue(ChromeRestrictionsSnapshotContract.exactMatch(snapshot, restored))
        assertFalse(restored.containsKey("ProxySettings"))
        assertEquals(2, applied["Count"])
        assertEquals(1, restored["Count"])
        assertIs<Long>(restored["LongCount"])
        val restoredArray = assertIs<Array<*>>(restored["Names"])
        assertEquals(String::class.java, restoredArray.javaClass.componentType)
        assertNotSame(originalArray, restoredArray)
    }

    @Test
    fun `exact comparison distinguishes runtime types absence and array component type`() {
        assertFalse(
            ChromeRestrictionsSnapshotContract.exactMatch(
                mapOf("value" to 1),
                mapOf("value" to 1L),
            ),
        )
        assertFalse(
            ChromeRestrictionsSnapshotContract.exactMatch(
                mapOf("value" to null),
                emptyMap(),
            ),
        )
        assertFalse(
            ChromeRestrictionsSnapshotContract.exactMatch(
                mapOf("value" to arrayOf("one")),
                mapOf("value" to arrayOf<Any?>("one")),
            ),
        )
        assertEquals(
            ChromeRestrictionsSnapshotContract.canonical(linkedMapOf("b" to 2, "a" to 1)),
            ChromeRestrictionsSnapshotContract.canonical(linkedMapOf("a" to 1, "b" to 2)),
        )
    }
}
