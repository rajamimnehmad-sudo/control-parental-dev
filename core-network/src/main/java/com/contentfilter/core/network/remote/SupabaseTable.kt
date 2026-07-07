package com.contentfilter.core.network.remote

enum class SupabaseTable(val tableName: String) {
    Accounts("accounts"),
    Devices("devices"),
    DeviceApps("device_apps"),
    Policies("policies"),
    PolicyRules("policy_rules"),
    DailyLimits("daily_limits"),
    AccessRequests("access_requests"),
    ExtraTimeGrants("extra_time_grants"),
}
