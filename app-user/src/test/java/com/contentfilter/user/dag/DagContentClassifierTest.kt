package com.contentfilter.user.dag

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import java.io.File
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
            semanticClassifier = DagSemanticTextClassifier(modelBytes()),
        )

    @Test
    fun `ordinary searches are allowed in all initial languages`() {
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("horario del banco").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("weather tomorrow").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("זמני תפילה היום").decision)
    }

    @Test
    fun `coca cola commercial searches are safe without masking risky additions`() {
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("Coca-Cola").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("comprar coca cola cerca").decision)
        assertEquals(DagClassification.Uncertain, classifier.classifyQuery("coca cola y cocaína").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("coca cola y videos porno").decision)
    }

    @Test
    fun `yeshurun social profile lookup is safe without masking risky additions`() {
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("yeshrun instagram").decision)
        assertEquals(DagClassification.Allowed, classifier.classifyQuery("Yeshurun página oficial").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("yeshrun instagram videos porno").decision)
    }

    @Test
    fun `low margin unsafe guess does not block an ordinary brand search`() {
        val prediction =
            DagSemanticPrediction(
                category = "drugs",
                confidence = 0.304f,
                margin = 0.008f,
                modelVersion = DagNeuralTextClassifier.ModelVersion,
            )

        assertEquals(DagClassification.Allowed, dagSemanticDecision(prediction))
    }

    @Test
    fun `meaningful unsafe guess still requires review`() {
        val prediction =
            DagSemanticPrediction(
                category = "drugs",
                confidence = 0.40f,
                margin = 0.10f,
                modelVersion = DagNeuralTextClassifier.ModelVersion,
            )

        assertEquals(DagClassification.Uncertain, dagSemanticDecision(prediction))
    }

    @Test
    fun `explicit unsafe intent is blocked in all initial languages`() {
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("video porno").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("online casino").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("פורנו").decision)
    }

    @Test
    fun `intimate retail page stays open for selective image filtering`() {
        assertEquals(
            DagClassification.Allowed,
            classifier.classifyResult(
                title = "Calvin Klein Underwear",
                description = "Catálogo de lencería y ropa íntima para mujer",
                url = "https://www.calvinklein.example/women/underwear",
            ).decision,
        )
        assertEquals(
            DagClassification.Allowed,
            classifier.classifyPage(
                url = "https://shop.example/intimates",
                title = "Nueva colección",
                text = "Catálogo de lencería y ropa íntima para mujer",
            ).decision,
        )
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("lencería con videos porno").decision)
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
    fun `external search portals cannot bypass DAG search`() {
        assertEquals(
            DagClassification.Blocked,
            classifier.classifyDirectUrl("https://google.com").decision,
        )
        assertEquals(
            DagClassification.Blocked,
            classifier.classifyDirectUrl("https://google.com/search?q=test").decision,
        )
        assertEquals(
            DagClassification.Allowed,
            classifier.classifyDirectUrl("https://maps.google.com/place/test").decision,
        )
        listOf(
            "https://duckduckgo.com/",
            "https://search.brave.com/search?q=test",
            "https://yandex.com/search/?text=test",
            "https://ecosia.org/search?q=test",
            "https://startpage.com/search?q=test",
            "https://google.com.ar/search?q=test",
        ).forEach { url ->
            assertEquals(DagClassification.Blocked, classifier.classifyDirectUrl(url).decision, url)
        }
    }

    @Test
    fun `obfuscated explicit terms fail closed`() {
        val result = classifier.classifyQuery("p0rn0")

        assertEquals(DagClassification.Blocked, result.decision)
        assertTrue(result.confidence > 0.9f)
    }

    @Test
    fun `semantic unsafe intent without exact keywords is blocked in all initial languages`() {
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("galería de chicas sin ropa").decision)
        assertEquals(
            DagClassification.Blocked,
            classifier.classifyQuery("meet someone tonight without commitment").decision,
        )
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("לשים כסף על תוצאת משחק").decision)
    }

    @Test
    fun `semantic medical context remains uncertain instead of blocked`() {
        val result = classifier.classifyQuery("guía médica para tratar consumo de cocaína")

        assertEquals(DagClassification.Uncertain, result.decision)
    }

    @Test
    fun `missing or corrupt semantic model fails closed`() {
        val unavailable =
            DagContentClassifier(
                domainBlocklist =
                    object : DynamicDomainBlocklist {
                        override fun categoryFor(domain: String): String? = null
                    },
                semanticClassifier = DagSemanticTextClassifier(byteArrayOf(1, 2, 3)),
            )

        val result = unavailable.classifyQuery("horario del banco")

        assertEquals(DagClassification.Uncertain, result.decision)
        assertEquals("model_unavailable", result.category)
    }

    @Test
    fun `semantic model never overrides a known blocked domain`() {
        blockedDomains["blocked.example"] = "adult"

        val result =
            classifier.classifyResult(
                "Tienda de muebles",
                "Productos para el hogar",
                "https://blocked.example",
            )

        assertEquals(DagClassification.Blocked, result.decision)
        assertEquals("adult", result.category)
    }

    @Test
    fun `reported unsafe visual platform is blocked before loading`() {
        val result = classifier.classifyDirectUrl("https://gallery.imgsrc.ru/example")

        assertEquals(DagClassification.Blocked, result.decision)
        assertEquals("unsafe_visual_platform", result.category)
    }

    @Test
    fun `ordinary retail domain remains allowed`() {
        assertEquals(DagClassification.Allowed, classifier.classifyDirectUrl("https://easy.com.ar").decision)
    }

    @Test
    fun `unsafe images are isolated without closing an otherwise safe store`() {
        val result =
            classifier.classifyPage(
                url = "https://images.example",
                title = "Galería",
                text = "Contenido general",
                images = DagImagePageSummary(allowed = 1, blocked = 4, uncertain = 0),
            )

        assertEquals(DagClassification.Allowed, result.decision)
    }

    @Test
    fun `page image signal does not penalize a mostly safe page`() {
        val result =
            classifier.classifyPage(
                url = "https://shop.example",
                title = "Tienda",
                text = "Productos para el hogar",
                images = DagImagePageSummary(allowed = 8, blocked = 1, uncertain = 1),
            )

        assertEquals(DagClassification.Allowed, result.decision)
    }

    @Test
    fun `single incidental ambiguous term does not force review of an otherwise ordinary page`() {
        val result =
            classifier.classifyPage(
                url = "https://shop.example",
                title = "Tienda para el hogar",
                text = "Ofertas para vos y tu pareja. Envíos, muebles y electrodomésticos.",
            )

        assertEquals(DagClassification.Allowed, result.decision)
        assertEquals(DagClassification.Uncertain, classifier.classifyQuery("consejos de pareja").decision)
    }

    @Test
    fun `unreadable images stay hidden without blocking otherwise safe page text`() {
        val result =
            classifier.classifyPage(
                url = "https://shop.example",
                title = "Tienda",
                text = "Productos y ofertas del supermercado",
                images = DagImagePageSummary(allowed = 0, blocked = 0, uncertain = 8),
            )

        assertEquals(DagClassification.Allowed, result.decision)
    }

    private fun modelBytes(): ByteArray {
        val relative = "src/main/assets/dag/dag_text_intent_v1.bin"
        val candidates = listOf(File(relative), File("app-user/$relative"))
        return candidates.first(File::isFile).readBytes()
    }
}
