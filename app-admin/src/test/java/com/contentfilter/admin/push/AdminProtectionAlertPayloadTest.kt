package com.contentfilter.admin.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminProtectionAlertPayloadTest {
    @Test
    fun parsesProtectionAlertNavigationData() {
        val payload =
            parseAdminProtectionAlertPayload(
                mapOf(
                    DataTypeKey to ProtectionAlertType,
                    EventIdKey to "event-1",
                    DeviceIdKey to "device-1",
                    DeviceNameKey to "Usuario",
                    AlertTypeKey to "admin_disabled",
                ),
            )

        assertEquals("event-1", payload?.eventId)
        assertEquals("device-1", payload?.deviceId)
        assertEquals("Usuario", payload?.deviceName)
        assertEquals("admin_disabled", payload?.alertType)
    }

    @Test
    fun rejectsOtherMessagesAndAlertsWithoutEventId() {
        assertNull(parseAdminProtectionAlertPayload(mapOf(DataTypeKey to "other")))
        assertNull(parseAdminProtectionAlertPayload(mapOf(DataTypeKey to ProtectionAlertType)))
    }
}
