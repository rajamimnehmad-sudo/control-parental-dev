package com.contentfilter.core.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*

enum class ProductIcon {
    Back,
    ChevronRight,
    Home,
    People,
    Settings,
    UserPlus,
    Search,
    Refresh,
    Bell,
    ShieldCheck,
    ShieldAlert,
    Panel,
    Update,
    Web,
    Apps,
    Requests,
    Star,
    Trash,
    ChevronDown,
    List,
    Person,
}

@Composable
fun ProductGlyph(
    icon: ProductIcon,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon.phosphorVector(),
        contentDescription = contentDescription,
        tint = color,
        modifier = modifier,
    )
}

fun productVector(icon: ProductIcon): ImageVector = icon.phosphorVector()

private fun ProductIcon.phosphorVector(): ImageVector =
    when (this) {
        ProductIcon.Back -> PhosphorIcons.Regular.ArrowLeft
        ProductIcon.ChevronRight -> PhosphorIcons.Regular.CaretRight
        ProductIcon.Home -> PhosphorIcons.Regular.House
        ProductIcon.People -> PhosphorIcons.Regular.UsersThree
        ProductIcon.Settings -> PhosphorIcons.Regular.Gear
        ProductIcon.UserPlus -> PhosphorIcons.Regular.UserPlus
        ProductIcon.Search -> PhosphorIcons.Regular.MagnifyingGlass
        ProductIcon.Refresh -> PhosphorIcons.Regular.ArrowsClockwise
        ProductIcon.Bell -> PhosphorIcons.Regular.Bell
        ProductIcon.ShieldCheck -> PhosphorIcons.Regular.ShieldCheck
        ProductIcon.ShieldAlert -> PhosphorIcons.Regular.ShieldWarning
        ProductIcon.Panel -> PhosphorIcons.Regular.List
        ProductIcon.Update -> PhosphorIcons.Regular.ArrowClockwise
        ProductIcon.Web -> PhosphorIcons.Regular.Globe
        ProductIcon.Apps -> PhosphorIcons.Regular.SquaresFour
        ProductIcon.Requests -> PhosphorIcons.Regular.ClipboardText
        ProductIcon.Star -> PhosphorIcons.Regular.Star
        ProductIcon.Trash -> PhosphorIcons.Regular.Trash
        ProductIcon.ChevronDown -> PhosphorIcons.Regular.CaretDown
        ProductIcon.List -> PhosphorIcons.Regular.List
        ProductIcon.Person -> PhosphorIcons.Regular.Person
    }
