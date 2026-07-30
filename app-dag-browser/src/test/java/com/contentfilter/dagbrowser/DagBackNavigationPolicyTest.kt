package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals

class DagBackNavigationPolicyTest {
    @Test
    fun `editing address closes keyboard before consuming navigation`() {
        assertEquals(
            DagBackAction.CloseKeyboard,
            decide(addressEditing = true, tabSwitcherOpen = true, canGoBack = true),
        )
    }

    @Test
    fun `tab switcher closes before page navigation`() {
        assertEquals(
            DagBackAction.CloseTabSwitcher,
            decide(tabSwitcherOpen = true, canGoBack = true),
        )
    }

    @Test
    fun `page history wins before returning home`() {
        assertEquals(DagBackAction.GoBackInPage, decide(canGoBack = true))
    }

    @Test
    fun `page without history returns home`() {
        assertEquals(DagBackAction.GoHome, decide())
    }

    @Test
    fun `home is the only tab state that exits browser`() {
        assertEquals(DagBackAction.ExitBrowser, decide(isHome = true))
        assertEquals(
            DagBackAction.ExitBrowser,
            decide(hasActiveTab = false, isHome = true),
        )
    }

    private fun decide(
        addressEditing: Boolean = false,
        tabSwitcherOpen: Boolean = false,
        hasActiveTab: Boolean = true,
        canGoBack: Boolean = false,
        isHome: Boolean = false,
    ) = DagBackNavigationPolicy.decide(
        addressEditing = addressEditing,
        tabSwitcherOpen = tabSwitcherOpen,
        hasActiveTab = hasActiveTab,
        canGoBackInPage = canGoBack,
        isHome = isHome,
    )
}
