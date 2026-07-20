package com.contentfilter.core.network.dto

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteDeviceActivationDtoTest {
    @Test
    fun `legacy activation response defaults to normal pairing`() {
        val dto = RemoteDeviceActivationDto.fromJson(baseJson())

        assertFalse(dto.relinkPending)
    }

    @Test
    fun `relink response marks transition pending`() {
        val dto = RemoteDeviceActivationDto.fromJson(baseJson().put("relink_pending", true))

        assertTrue(dto.relinkPending)
    }

    private fun baseJson() =
        JSONObject()
            .put("account_id", "account")
            .put("device_id", "device")
            .put("activation_id", "activation")
            .put("device_token", "token")
}
