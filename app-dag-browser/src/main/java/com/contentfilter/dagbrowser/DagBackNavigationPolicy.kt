package com.contentfilter.dagbrowser

internal enum class DagBackAction {
    CloseKeyboard,
    CloseTabSwitcher,
    GoBackInPage,
    GoHome,
    ExitBrowser,
}

internal object DagBackNavigationPolicy {
    fun decide(
        addressEditing: Boolean,
        tabSwitcherOpen: Boolean,
        hasActiveTab: Boolean,
        canGoBackInPage: Boolean,
        isHome: Boolean,
    ): DagBackAction =
        when {
            addressEditing -> DagBackAction.CloseKeyboard
            tabSwitcherOpen -> DagBackAction.CloseTabSwitcher
            hasActiveTab && canGoBackInPage -> DagBackAction.GoBackInPage
            hasActiveTab && !isHome -> DagBackAction.GoHome
            else -> DagBackAction.ExitBrowser
        }
}
