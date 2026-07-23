package com.contentfilter.user.dag

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `uncertain pages open with additional protection instead of requiring admin review`() {
        val uncertain = DagClassificationResult(DagClassification.Uncertain, "sensitive_context", 0.64f, "test")
        val weakSemanticBlock = DagClassificationResult(DagClassification.Blocked, "semantic_sexual", 0.70f, "test")

        assertEquals(DagAdaptivePageDecision.Protected, dagAdaptivePageDecision(uncertain))
        assertEquals(DagAdaptivePageDecision.Blocked, dagAdaptivePageDecision(weakSemanticBlock))
    }

    @Test
    fun `explicit policy and strong unsafe evidence still block pages`() {
        val explicit = DagClassificationResult(DagClassification.Blocked, "sexual", 0.97f, "test")
        val strongSemantic = DagClassificationResult(DagClassification.Blocked, "semantic_sexual", 0.91f, "test")
        val safe = DagClassificationResult(DagClassification.Allowed, "general", 0.82f, "test")

        assertEquals(DagAdaptivePageDecision.Blocked, dagAdaptivePageDecision(explicit))
        assertEquals(DagAdaptivePageDecision.Blocked, dagAdaptivePageDecision(strongSemantic))
        assertEquals(DagAdaptivePageDecision.Allowed, dagAdaptivePageDecision(safe))
    }

    @Test
    fun `explicit unsafe intent is blocked in all initial languages`() {
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("video porno").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("online casino").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("NSFW photo gallery").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("sex toys online store").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("sportsbook signup bonus").decision)
        assertEquals(DagClassification.Blocked, classifier.classifyQuery("ver cómo matan a una persona").decision)
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
    fun `explicit unsafe policy is never softened by educational or retail context`() {
        assertEquals(
            DagClassification.Blocked,
            classifier.classifyQuery("educación sobre casino online").decision,
        )
        assertEquals(
            DagClassification.Blocked,
            classifier.classifyPage(
                url = "https://shop.example/",
                title = "Videos porno educativos",
                text = "Ropa, carrito, envíos, cambios, devoluciones y precios.",
            ).decision,
        )
    }

    @Test
    fun `uncertain searches remain eligible for result filtering`() {
        listOf(
            "educación sexual médica",
            "sexual health education",
            "הלכה רפואה מין",
        ).forEach { query ->
            assertTrue(classifier.classifyQuery(query).decision != DagClassification.Blocked, query)
        }
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

    @Test
    fun `staged classifier keeps a clear compact safe query off neural runtime`() =
        runBlocking {
            val neural = RecordingNeuralStage { error("clear safe query must not reach MiniLM") }
            val staged = classifierWith(neural)

            val result = staged.classifyQueryStaged("horario del banco")

            assertEquals(DagClassification.Allowed, result.decision)
            assertTrue(result.modelVersion.contains("/compact:"))
            assertTrue(neural.batches.isEmpty())
        }

    @Test
    fun `staged search decisions batch ambiguous and risky text once`() =
        runBlocking {
            val neural =
                RecordingNeuralStage { text ->
                    if ("sin ropa" in text) {
                        prediction("sexual", confidence = 0.91f, margin = 0.80f)
                    } else {
                        prediction("general", confidence = 0.88f, margin = 0.72f)
                    }
                }
            val staged = classifierWith(neural)

            val results =
                staged.classifyResultsWithReasonStaged(
                    listOf(
                        DagSearchClassificationInput(
                            title = "Banco Nación",
                            description = "Horarios y turnos bancarios",
                            url = "https://banco.example/turnos",
                        ),
                        DagSearchClassificationInput(
                            title = "Consejos de pareja",
                            description = "Orientación para la vida cotidiana",
                            url = "https://familia.example/consejos",
                        ),
                        DagSearchClassificationInput(
                            title = "Galería",
                            description = "Galería de chicas sin ropa",
                            url = "https://gallery.example/private",
                        ),
                    ),
                )

            assertEquals(1, neural.batches.size)
            assertEquals(2, neural.batches.single().size)
            assertEquals(DagClassification.Allowed, results[0].classification.decision)
            assertEquals(DagClassification.Uncertain, results[1].classification.decision)
            assertEquals(DagClassification.Blocked, results[2].classification.decision)
            assertTrue(results[2].classification.modelVersion.contains("/neural:"))
        }

    @Test
    fun `compact unsafe block skips the neural stage`() =
        runBlocking {
            val neural =
                RecordingNeuralStage {
                    prediction("general", confidence = 0.92f, margin = 0.85f)
                }
            val staged = classifierWith(neural)

            val result = staged.classifyQueryStaged("galería de chicas sin ropa")

            assertEquals(DagClassification.Blocked, result.decision)
            assertTrue(neural.batches.isEmpty())
        }

    @Test
    fun `page metadata and body risks fuse monotonically`() {
        val allowed = DagClassificationResult(DagClassification.Allowed, "general", 0.9f, "test")
        val uncertain = DagClassificationResult(DagClassification.Uncertain, "dating", 0.5f, "test")
        val blocked = DagClassificationResult(DagClassification.Blocked, "sexual", 0.8f, "test")

        assertEquals(uncertain, dagStrictestClassification(allowed, uncertain))
        assertEquals(uncertain, dagStrictestClassification(uncertain, allowed))
        assertEquals(blocked, dagStrictestClassification(uncertain, blocked))
        assertEquals(blocked, dagStrictestClassification(blocked, allowed))
    }

    @Test
    fun `retail boilerplate cannot dilute unsafe metadata`() {
        val retail =
            "Ropa para niños, carrito, envíos, cambios, devoluciones, precios y cuotas."

        val riskyMetadata =
            classifier.classifyPage(
                url = "https://shop.example/",
                title = "People nearby tonight",
                text = retail,
            )
        assertTrue(riskyMetadata.decision != DagClassification.Allowed)
    }

    @Test
    fun `benign retail uncertainty stays protected when models disagree`() =
        runBlocking {
            val neural =
                RecordingNeuralStage {
                    prediction("general", confidence = 0.92f, margin = 0.85f)
                }
            val staged = classifierWith(neural)

            val result =
                staged.classifyPageStaged(
                    url = "https://www.cheeky.example/",
                    title = "Cheeky Tienda Oficial - Ropa para Niños, Niñas y Bebés",
                    text =
                        "Bebés niñas ropa bodies remeras vestidos pantalones pijamas accesorios. " +
                            "Niñas de 1 a 14 años, ropa interior, calzado y ofertas. " +
                            "Carrito, envíos a todo el país, cambios y devoluciones.",
                )

            assertEquals(1, neural.batches.size)
            assertEquals(DagClassification.Uncertain, result.decision)
            assertEquals(DagAdaptivePageDecision.Protected, dagAdaptivePageDecision(result))
            assertTrue(result.modelVersion.contains("/neural:"))
        }

    @Test
    fun `compact allow plus neural sensitive context opens protected`() =
        runBlocking {
            val input = "texto totalmente desconocido qwerty zxcv"
            val neural =
                RecordingNeuralStage {
                    prediction("sensitive_context", confidence = 0.52f, margin = 0.11f)
                }
            val staged = classifierWith(neural)

            assertEquals(DagClassification.Allowed, classifier.classifyQuery(input).decision)

            val result = staged.classifyQueryStaged(input)

            assertEquals(1, neural.batches.size)
            assertEquals(DagClassification.Uncertain, result.decision)
            assertEquals(DagAdaptivePageDecision.Protected, dagAdaptivePageDecision(result))
            assertEquals("semantic_sensitive_context", result.category)
            assertTrue(result.modelVersion.contains("/neural:"))
        }

    @Test
    fun `compact allow plus intermediate neural unsafe confidence opens protected`() =
        runBlocking {
            val input = "texto totalmente desconocido qwerty zxcv"
            val neural =
                RecordingNeuralStage {
                    prediction("sexual", confidence = 0.40f, margin = 0.10f)
                }
            val staged = classifierWith(neural)

            assertEquals(DagClassification.Allowed, classifier.classifyQuery(input).decision)

            val result = staged.classifyQueryStaged(input)

            assertEquals(1, neural.batches.size)
            assertEquals(DagClassification.Uncertain, result.decision)
            assertEquals(DagAdaptivePageDecision.Protected, dagAdaptivePageDecision(result))
            assertEquals("semantic_sexual", result.category)
            assertTrue(result.modelVersion.contains("/neural:"))
        }

    @Test
    fun `neural unsafe block vetoes compact allow for otherwise commercial metadata`() =
        runBlocking {
            val neural =
                RecordingNeuralStage {
                    prediction("sexual", confidence = 0.91f, margin = 0.80f)
                }
            val staged = classifierWith(neural)

            val result =
                staged.classifyPageStaged(
                    url = "https://www.cheeky.example/",
                    title = "Cheeky Tienda Oficial - Ropa para Niños, Niñas y Bebés",
                    text =
                        "Bebés niñas ropa bodies remeras vestidos pantalones pijamas accesorios. " +
                            "Niñas de 1 a 14 años, ropa interior, calzado y ofertas. " +
                            "Carrito, envíos a todo el país, cambios y devoluciones.",
                )

            assertEquals(1, neural.batches.size)
            assertEquals(DagClassification.Blocked, result.decision)
            assertTrue(result.modelVersion.contains("/neural:"))
        }

    @Test
    fun `retail evidence never overrides explicit adult or gambling content`() =
        runBlocking {
            val neural =
                RecordingNeuralStage {
                    prediction("sexual", confidence = 0.40f, margin = 0.10f)
                }
            val staged = classifierWith(neural)
            val retailScaffold =
                "Ropa y calzado, carrito, envíos, cambios y devoluciones, precios, ofertas y cuotas."

            val adult =
                staged.classifyPageStaged(
                    url = "https://shop.example/adult",
                    title = "Adult store de sex toys y juguetes sexuales",
                    text = retailScaffold,
                )
            val gambling =
                staged.classifyPageStaged(
                    url = "https://shop.example/bets",
                    title = "Casino online",
                    text = retailScaffold,
                )

            assertEquals(DagClassification.Blocked, adult.decision)
            assertEquals(DagClassification.Blocked, gambling.decision)
            assertTrue(neural.batches.isEmpty())
        }

    @Test
    fun `retail semantic uncertainty stays protected when neural stage is unavailable null or fails`() =
        runBlocking {
            val title = "Cheeky Tienda Oficial - Ropa para Niños, Niñas y Bebés"
            val text =
                "Bebés niñas ropa bodies remeras vestidos pantalones pijamas accesorios. " +
                    "Niñas de 1 a 14 años, ropa interior, calzado y ofertas. " +
                    "Carrito, envíos a todo el país, cambios y devoluciones."
            val compact =
                classifier.classifyPage(
                    url = "https://www.cheeky.example/",
                    title = title,
                    text = text,
                )
            val unavailable =
                classifier.classifyPageStaged(
                    url = "https://www.cheeky.example/",
                    title = title,
                    text = text,
                )
            val nullPrediction =
                classifierWith(RecordingNeuralStage { null }).classifyPageStaged(
                    url = "https://www.cheeky.example/",
                    title = title,
                    text = text,
                )
            val failed =
                classifierWith(RecordingNeuralStage { error("MiniLM failed") }).classifyPageStaged(
                    url = "https://www.cheeky.example/",
                    title = title,
                    text = text,
                )

            assertEquals(DagClassification.Uncertain, compact.decision)
            assertEquals(compact, unavailable)
            assertEquals(compact, nullPrediction)
            assertEquals(compact, failed)
        }

    @Test
    fun `government domain never overrides explicit unsafe page content`() =
        runBlocking {
            val neural = RecordingNeuralStage { error("explicit block must not reach MiniLM") }
            val staged = classifierWith(neural)

            val result =
                staged.classifyPageStaged(
                    url = "https://servicios.argentina.gob.ar/example",
                    title = "Videos porno",
                    text =
                        "Tu perfil digital para gestionar turnos y llevar tus credenciales. " +
                            "Consultá tu receta electrónica e ingresá con Face ID.",
                )

            assertEquals(DagClassification.Blocked, result.decision)
            assertTrue(neural.batches.isEmpty())
        }

    @Test
    fun `compact uncertainty cannot be downgraded by neural general prediction`() =
        runBlocking {
            val neural =
                RecordingNeuralStage {
                    prediction("general", confidence = 0.92f, margin = 0.85f)
                }
            val staged = classifierWith(neural)

            val result = staged.classifyQueryStaged("consejos de pareja")

            assertEquals(1, neural.batches.size)
            assertEquals(DagClassification.Uncertain, result.decision)
        }

    @Test
    fun `missing neural stage falls back to the compact decision without blocking`() =
        runBlocking {
            val neural = RecordingNeuralStage { null }
            val staged = classifierWith(neural)

            val result = staged.classifyQueryStaged("texto totalmente desconocido qwerty zxcv")

            assertEquals(DagClassification.Allowed, result.decision)
            assertTrue(result.modelVersion.contains("/compact:"))
        }

    @Test
    fun `explicit unsafe rule blocks before neural stage`() =
        runBlocking {
            val neural = RecordingNeuralStage { error("explicit block must not reach MiniLM") }
            val staged = classifierWith(neural)

            val result = staged.classifyQueryStaged("video porno")

            assertEquals(DagClassification.Blocked, result.decision)
            assertFalse(result.category.startsWith("semantic_"))
            assertTrue(neural.batches.isEmpty())
        }

    private fun classifierWith(neural: DagNeuralTextStage): DagContentClassifier =
        DagContentClassifier(
            domainBlocklist =
                object : DynamicDomainBlocklist {
                    override fun categoryFor(domain: String): String? = blockedDomains[domain]
                },
            semanticClassifier = DagSemanticTextClassifier(modelBytes()),
        ).apply {
            neuralTextStageOverride = neural
        }

    private fun prediction(
        category: String,
        confidence: Float,
        margin: Float,
    ) = DagSemanticPrediction(
        category = category,
        confidence = confidence,
        margin = margin,
        modelVersion = DagNeuralTextClassifier.ModelVersion,
    )

    private class RecordingNeuralStage(
        private val prediction: (String) -> DagSemanticPrediction?,
    ) : DagNeuralTextStage {
        val batches = mutableListOf<List<String>>()

        override suspend fun classifyBatch(texts: List<String>): List<DagSemanticPrediction?> {
            batches += texts
            return texts.map(prediction)
        }
    }

    private fun modelBytes(): ByteArray {
        val relative = "src/main/assets/dag/dag_text_intent_v1.bin"
        val candidates = listOf(File(relative), File("app-user/$relative"))
        return candidates.first(File::isFile).readBytes()
    }
}
