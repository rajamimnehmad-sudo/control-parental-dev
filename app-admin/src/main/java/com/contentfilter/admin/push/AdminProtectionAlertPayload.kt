package com.contentfilter.admin.push

data class AdminProtectionAlertPayload(
    val eventId: String,
    val deviceId: String,
    val deviceName: String,
    val alertType: String,
)

fun parseAdminProtectionAlertPayload(data: Map<String, String>): AdminProtectionAlertPayload? {
    if (data[DataTypeKey] != ProtectionAlertType) return null
    val eventId = data[EventIdKey].orEmpty()
    if (eventId.isBlank()) return null
    return AdminProtectionAlertPayload(
        eventId = eventId,
        deviceId = data[DeviceIdKey].orEmpty(),
        deviceName = data[DeviceNameKey].orEmpty(),
        alertType = data[AlertTypeKey].orEmpty(),
    )
}

const val DataTypeKey = "type"
const val EventIdKey = "event_id"
const val DeviceIdKey = "device_id"
const val DeviceNameKey = "device_name"
const val AlertTypeKey = "alert_type"
const val TitleKey = "title"
const val BodyKey = "body"
const val ProtectionAlertType = "protection_alert"
