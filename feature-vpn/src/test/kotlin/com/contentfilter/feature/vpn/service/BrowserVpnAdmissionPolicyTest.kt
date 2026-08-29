package com.contentfilter.feature.vpn.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserVpnAdmissionPolicyTest {
    @Test
    fun `full tunnel requires exact Chrome admission`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                BrowserVpnAdmissionPolicy.admit(
                    browserPackages = listOf(OtherPackage, ChromePackage),
                    chromePackage = ChromePackage,
                    requireChrome = true,
                    addAllowedApplication = { packageName ->
                        if (packageName == ChromePackage) throw IllegalArgumentException("not admitted")
                    },
                )
            }

        assertEquals("not admitted", error.message)
    }

    @Test
    fun `full tunnel records Chrome admission only after successful callback`() {
        val admitted = mutableListOf<String>()

        val result =
            BrowserVpnAdmissionPolicy.admit(
                browserPackages = listOf(ChromePackage, OtherPackage),
                chromePackage = ChromePackage,
                requireChrome = true,
                addAllowedApplication = { packageName -> admitted += packageName },
            )

        assertEquals(listOf(ChromePackage, OtherPackage), admitted)
        assertEquals(2, result.admittedCount)
        assertTrue(result.chromeAdmitted)
    }

    @Test
    fun `legacy mode preserves best effort browser admission`() {
        val admitted = mutableListOf<String>()

        val result =
            BrowserVpnAdmissionPolicy.admit(
                browserPackages = listOf(ChromePackage, OtherPackage),
                chromePackage = ChromePackage,
                requireChrome = false,
                addAllowedApplication = { packageName ->
                    if (packageName == ChromePackage) throw IllegalArgumentException("not installed")
                    admitted += packageName
                },
            )

        assertEquals(listOf(OtherPackage), admitted)
        assertEquals(1, result.admittedCount)
        assertFalse(result.chromeAdmitted)
    }

    @Test
    fun `missing Chrome entry fails closed when required`() {
        assertFailsWith<IllegalStateException> {
            BrowserVpnAdmissionPolicy.admit(
                browserPackages = listOf(OtherPackage),
                chromePackage = ChromePackage,
                requireChrome = true,
                addAllowedApplication = {},
            )
        }
    }

    private companion object {
        const val ChromePackage = "com.android.chrome"
        const val OtherPackage = "com.example.browser"
    }
}
