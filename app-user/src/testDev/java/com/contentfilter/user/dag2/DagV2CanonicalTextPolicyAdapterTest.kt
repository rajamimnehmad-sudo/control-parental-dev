package com.contentfilter.user.dag2

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import com.contentfilter.user.dag.DagClassification
import com.contentfilter.user.dag.DagContentClassifier
import com.contentfilter.user.dag.DagSemanticTextClassifier
import kotlin.test.Test
import kotlin.test.assertEquals

class DagV2CanonicalTextPolicyAdapterTest {
    private val blockedDomains = mapOf("blocked-adult.invalid" to "adult")
    private val classifier =
        DagContentClassifier(
            domainBlocklist =
                object : DynamicDomainBlocklist {
                    override fun categoryFor(domain: String): String? = blockedDomains[domain]
                },
            semanticClassifier = DagSemanticTextClassifier(byteArrayOf(1, 2, 3)),
        )
    private val adapter = DagV2CanonicalTextPolicyAdapter(classifier)

    @Test
    fun `query corpus remains in parity with the canonical dag policy`() {
        listOf(
            "video porno",
            "online casino",
            "פורנו",
            "educación sexual médica",
            "Coca-Cola",
            "yeshrun instagram",
        ).forEach { query ->
            assertEquals(
                classifier.classifyQuery(query).decision.toDagV2Decision(),
                adapter.classifyQuery(query).decision,
                query,
            )
        }
    }

    @Test
    fun `result and domain corpus remains in parity with the canonical dag policy`() {
        val cases =
            listOf(
                DagV2SearchResult(
                    title = "Página general",
                    description = "Contenido ordinario",
                    url = "https://blocked-adult.invalid/a",
                ),
                DagV2SearchResult(
                    title = "Videos porno",
                    description = "Contenido explícito",
                    url = "https://example.com/a",
                ),
                DagV2SearchResult(
                    title = "Tienda",
                    description = "Productos generales",
                    url = "https://example.com/products",
                ),
            )

        cases.forEach { result ->
            assertEquals(
                classifier
                    .classifyResult(result.title, result.description, result.url)
                    .decision
                    .toDagV2Decision(),
                adapter.classifyResult(result).decision,
                result.url,
            )
        }
    }

    private fun DagClassification.toDagV2Decision(): DagV2CanonicalTextDecision =
        when (this) {
            DagClassification.Allowed -> DagV2CanonicalTextDecision.Allowed
            DagClassification.Blocked -> DagV2CanonicalTextDecision.Blocked
            DagClassification.Uncertain -> DagV2CanonicalTextDecision.Uncertain
        }
}
