package com.contentfilter.feature.block

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLargeFeatureCard
import com.contentfilter.core.ui.ProductSun
import com.contentfilter.core.ui.ProductVisualPage

@Composable
fun BlockScreen(
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProductVisualPage(
        modifier = modifier,
        title = "Acceso bloqueado",
        subtitle = "Esta app o sitio está restringido",
    ) {
        ProductLargeFeatureCard(
            title = "Pedí permiso",
            subtitle = "Tu administrador puede aprobar acceso o darte tiempo extra.",
            accent = ProductSun,
        )
        ProductCard {
            Button(onClick = onRequestAccess) {
                Text("Solicitar acceso")
            }
        }
    }
}
