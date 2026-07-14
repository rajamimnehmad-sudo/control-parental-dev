package com.contentfilter.user.dag

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagContentClassifierTest {
    private val blockedDomains = mutableMapOf<String, String>()
    private val classifier =
        DagContentClassifier(
            domainBlocklist =
                object : DynamicDomainBlocklist {
                    override fun categoryFor(domain: String): String? = blockedDomains[domain]
                },
        )

    @Test
    fun `ordinary searches are allowed in all initial languages`() {
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("horario del banco").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("weather tomorrow").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("זמני תפילה היום").decision)
    }

    @Test
    fun `explicit unsafe intent is blocked in all initial languages`() {
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("video porno").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("online casino").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("פורנו").decision)
    }

    @Test
    fun `medical religious and educational sensitive contexts remain uncertain`() {
        assertEquals(DagClassification.Uncertain, classifier.classifyQuery("educación sexual médica").decision)
        assertEquals(DagClassification.Uncertain, classifier.classifyQuery("sexual health education").decision)
        assertEquals(DagClassification.Uncertain, classifier.classifyQuery("הלכה רפואה מין").decision)
    }

    @Test
    fun `known blocked domain overrides harmless metadata`() {
        blockedDomains["unsafe.example"] = "adult"

        val result = classifier.classifyResult("Página de ejemplo", "Texto general", "https://unsafe.example/a")

        assertEquals(DagClassification.Blocked, result.decision)
        assertEquals("adult", result.category)
        assertEquals(1f, result.confidence)
    }

    @Test
    fun `obfuscated explicit terms fail closed`() {
        val result = classifier.classifyQuery("p0rn0")

        assertEquals(DagClassification.Blocked, result.decision)
        assertTrue(result.confidence > 0.9f)
    }
}
