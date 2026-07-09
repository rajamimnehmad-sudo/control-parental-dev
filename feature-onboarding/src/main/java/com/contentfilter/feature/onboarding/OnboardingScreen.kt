package com.contentfilter.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductTeal
import com.contentfilter.core.ui.ProductVisualPage

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    ProductVisualPage(
        modifier = modifier,
        title = "Content Filter",
        subtitle = "Prepará protección, permisos y sincronización",
    ) {
        ProductLargeFeatureCard(
            title = "Primer inicio",
            subtitle = "La configuración guía activación, accesibilidad, sincronización y actualizaciones.",
            accent = ProductTeal,
        )
    }
}
