package com.contentfilter.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProductVisualPage(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null || banner != null) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(ProductAppBackground)
                    .statusBarsPadding(),
        ) {
            ProductPageHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                modifier = Modifier.padding(horizontal = GloshSpacing.PageHorizontal, vertical = 18.dp),
            )
            banner?.let { bannerContent ->
                Box(modifier = Modifier.padding(horizontal = GloshSpacing.PageHorizontal)) {
                    bannerContent()
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = GloshSpacing.PageHorizontal,
                            top = GloshSpacing.Section,
                            end = GloshSpacing.PageHorizontal,
                            bottom = 28.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(GloshSpacing.Section),
                content = content,
            )
        }
        return
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ProductAppBackground)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = GloshSpacing.PageHorizontal, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(GloshSpacing.Section),
    ) {
        ProductPageHeader(title = title, subtitle = subtitle, onBack = onBack)
        content()
    }
}

@Composable
fun ProductLazyVisualPage(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    banner: (@Composable () -> Unit)? = null,
    itemSpacing: Dp = GloshSpacing.Section,
    content: LazyListScope.() -> Unit,
) {
    if (onBack != null || banner != null) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(ProductAppBackground)
                    .statusBarsPadding(),
        ) {
            ProductPageHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                modifier = Modifier.padding(horizontal = GloshSpacing.PageHorizontal, vertical = 18.dp),
            )
            banner?.let { bannerContent ->
                Box(modifier = Modifier.padding(horizontal = GloshSpacing.PageHorizontal)) {
                    bannerContent()
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding =
                    PaddingValues(
                        start = GloshSpacing.PageHorizontal,
                        top = GloshSpacing.Section,
                        end = GloshSpacing.PageHorizontal,
                        bottom = 28.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                content = content,
            )
        }
        return
    }
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(ProductAppBackground)
                .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = GloshSpacing.PageHorizontal, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        item(key = "product-page-header") {
            ProductPageHeader(title = title, subtitle = subtitle, onBack = onBack)
        }
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
                        .background(GloshColors.Surface, CircleShape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(
                    icon = ProductIcon.Back,
                    color = ProductInk,
                    contentDescription = "Volver",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
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
                .height(190.dp)
                .background(GloshColors.Graphite, GloshShapes.LargeCard)
                .padding(20.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .width(42.dp)
                    .height(5.dp)
                    .background(GloshColors.Lime, GloshShapes.Pill),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = if (mascot == null) 0.dp else 112.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.76f))
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
        shape = GloshShapes.Card,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(accent.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ProductGlyph(icon = icon, color = GloshColors.Graphite, modifier = Modifier.size(24.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ProductInk)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ProductMutedInk)
            }
            ProductGlyph(icon = ProductIcon.ChevronRight, color = ProductMutedInk, modifier = Modifier.size(22.dp))
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
        shape = GloshShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = GloshColors.Surface),
        border = BorderStroke(1.dp, GloshColors.Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(accent.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                ProductMiniIllustration(accent = accent)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                .background(GloshColors.Surface, GloshShapes.Card)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(8.dp).background(accent, CircleShape),
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
    GloshNavigationGlyph(icon = icon, selected = selected)
}

@Composable
private fun ProductMiniIllustration(accent: Color) {
    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(28.dp).background(GloshColors.Surface, CircleShape),
        )
        Box(
            modifier = Modifier.align(Alignment.TopEnd).size(11.dp).background(accent, CircleShape),
        )
    }
}

val ProductAppBackground = GloshColors.Bone
val ProductInk = GloshColors.Graphite
val ProductMutedInk = GloshColors.Muted
val ProductTeal = GloshColors.Positive
val ProductSky = GloshColors.GraphiteSoft
val ProductSun = GloshColors.Warning
val ProductMint = GloshColors.Lime
val ProductViolet = GloshColors.Graphite
