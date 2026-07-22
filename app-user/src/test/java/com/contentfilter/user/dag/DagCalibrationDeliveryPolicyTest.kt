package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DagCalibrationDeliveryPolicyTest {
    @Test
    fun `only an explicit accepted acknowledgement confirms delivery`() {
        assertEquals(
            DagCalibrationDeliveryResult.Accepted,
            dagCalibrationAcknowledgement(accepted = true, reason = ""),
        )
        assertEquals(
            "clear_block",
            assertIs<DagCalibrationDeliveryResult.Rejected>(
                dagCalibrationAcknowledgement(accepted = false, reason = "clear_block"),
            ).reason,
        )
        assertEquals(
            "not_accepted",
            assertIs<DagCalibrationDeliveryResult.Rejected>(
                dagCalibrationAcknowledgement(accepted = false, reason = ""),
            ).reason,
        )
    }
}
