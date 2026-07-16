package com.contentfilter.user

import com.contentfilter.core.domain.model.Device
import com.contentfilter.core.domain.model.DeviceActivation
import kotlin.test.Test
import kotlin.test.assertEquals

class UserHomeGreetingTest {
    @Test
    fun `uses the active device display name`() {
        assertEquals(
            "Hola, Miriam",
            resolveUserGreeting(
                activation = activation(deviceId = "active"),
                devices = listOf(device(id = "other", name = "Otra"), device(id = "active", name = " Miriam ")),
            ),
        )
    }

    @Test
    fun `does not expose another device name while active device is missing`() {
        assertEquals(
            "Hola",
            resolveUserGreeting(
                activation = activation(deviceId = "missing"),
                devices = listOf(device(id = "other", name = "Otra persona")),
            ),
        )
    }

    @Test
    fun `uses neutral fallback for blank name or missing activation`() {
        assertEquals("Hola", resolveUserGreeting(activation("active"), listOf(device("active", "   "))))
        assertEquals("Hola", resolveUserGreeting(null, listOf(device("active", "Miriam"))))
    }

    private fun activation(deviceId: String) =
        DeviceActivation(
            id = "activation",
            accountId = "account",
            deviceId = deviceId,
            activatedAtEpochMillis = 1L,
        )

    private fun device(
        id: String,
        name: String,
    ) = Device(id = id, accountId = "account", displayName = name)
}
