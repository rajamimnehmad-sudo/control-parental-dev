package com.contentfilter.core.domain.model

enum class ProtectionAlertType(val remoteValue: String) {
    WebDisabled("web_disabled"),
    AppsDisabled("apps_disabled"),
    Incomplete("incomplete"),
}
