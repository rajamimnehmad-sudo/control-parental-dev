package com.contentfilter.user.dag2

import com.contentfilter.core.network.security.PublicNetworkDestinationGuard
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2NetworkGuardTest {
    private val guard = DagV2NetworkGuard(PublicNetworkDestinationGuard())

    @Test
    fun `private loopback link local cgnat and documentation literals are rejected`() =
        runBlocking {
            val blocked =
                listOf(
                    "https://127.0.0.1/",
                    "https://10.1.2.3/",
                    "https://169.254.1.2/",
                    "https://100.64.1.2/",
                    "https://192.0.2.1/",
                    "https://[2001:db8::1]/",
                    "https://[fc00::1]/",
                )

            blocked.forEach { url ->
                assertEquals(DagV2SiteDecision.Block, guard.validate(url).decision, url)
            }
        }

    @Test
    fun `known public literal passes the canonical range guard`() =
        runBlocking {
            assertEquals(DagV2SiteDecision.Allow, guard.validate("https://8.8.8.8/").decision)
        }
}
