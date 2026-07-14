package com.contentfilter.core.domain.model

enum class ProtectionAlertType(val remoteValue: String) {
    WebDisabled("web_disabled"),
    AppsDisabled("apps_disabled"),
    AdminDisabled("admin_disabled"),
    TamperAttempt("tamper_attempt"),
    MaintenanceRequested("maintenance_requested"),
    Incomplete("incomplete"),
}
