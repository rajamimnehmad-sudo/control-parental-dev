package com.contentfilter.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ProductVisualPage(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ProductAppBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ProductPageHeader(title = title, subtitle = subtitle, onBack = onBack)
        content()
    }
}

@Composable
fun ProductPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        onBack?.let {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.92f), CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = ProductInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = ProductInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ProductMutedInk)
        }
    }
}

@Composable
fun ProductHeroPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    mascot: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(ProductTeal, Color(0xFF5B6CFF), Color(0xFF1E2A4A)),
                        ),
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(22.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = if (mascot == null) 0.dp else 124.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.86f))
        }
        mascot?.let {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd),
                contentAlignment = Alignment.Center,
            ) {
                it()
            }
        }
    }
}

@Composable
fun ProductFeatureTile(
    icon: ProductIcon,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(icon = icon, color = accent, modifier = Modifier.size(28.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ProductInk)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ProductMutedInk)
            }
            ProductGlyph(icon = ProductIcon.ChevronRight, color = accent, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun ProductLargeFeatureCard(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ProductMiniIllustration(accent = accent)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = ProductInk)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ProductMutedInk)
            }
        }
    }
}

@Composable
fun ProductStatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = ProductInk)
        Text(label, style = MaterialTheme.typography.bodySmall, color = ProductMutedInk)
    }
}

@Composable
fun ProductNavGlyph(
    icon: ProductIcon,
    selected: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(if (selected) 34.dp else 30.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        ProductGlyph(
            icon = icon,
            color = if (selected) Color.White else ProductMutedInk,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProductMiniIllustration(accent: Color) {
    Box(modifier = Modifier.size(46.dp)) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(38.dp)
                    .background(Color.White, RoundedCornerShape(12.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(accent, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .width(28.dp)
                    .height(10.dp)
                    .background(accent.copy(alpha = 0.65f), CircleShape),
        )
    }
}

val ProductAppBackground = Color(0xFFF2F8F7)
val ProductInk = Color(0xFF162235)
val ProductMutedInk = Color(0xFF68758A)
val ProductTeal = Color(0xFF13BFAE)
val ProductSky = Color(0xFF2C9AF4)
val ProductSun = Color(0xFFFFC849)
val ProductMint = Color(0xFF55E1B8)
val ProductViolet = Color(0xFF6C63FF)
