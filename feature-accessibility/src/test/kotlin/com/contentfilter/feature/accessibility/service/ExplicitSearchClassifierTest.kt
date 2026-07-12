package com.contentfilter.feature.accessibility.service

import kotlin.test.Test
import kotlin.test.assertEquals

class ExplicitSearchClassifierTest {
    private val classifier = ExplicitSearchClassifier()

    @Test
    fun `blocks explicit searches after normalization`() {
        assertEquals(ExplicitSearchDecision.BlockExplicit, classifier.classify("Videos PÓRNO gratis"))
    }

    @Test
    fun `allows educational context`() {
        assertEquals(ExplicitSearchDecision.Allow, classifier.classify("educacion sexual para adolescentes"))
    }

    @Test
    fun `allows uncertain searches initially`() {
        assertEquals(ExplicitSearchDecision.Allow, classifier.classify("historia del arte moderno"))
    }
}
