package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PairingCodeControllerTest {
    @Test
    public void fiveDigitsDoNotSubmitAndSixAutoSubmitOnce() {
        List<String> submissions = new ArrayList<>();
        PairingCodeController controller = new PairingCodeController(submissions::add);
        assertTrue(controller.accept("12345"));
        assertTrue(submissions.isEmpty());
        assertTrue(controller.accept("123456"));
        assertEquals(List.of("123456"), submissions);
        assertTrue(controller.accept("654321"));
        assertEquals(1, submissions.size());
    }

    @Test
    public void sevenDigitsAndNonNumericValuesAreRejected() {
        PairingCodeController controller = new PairingCodeController(code -> { });
        assertFalse(controller.accept("1234567"));
        assertFalse(controller.accept("12a456"));
    }

    @Test
    public void failureAllowsOneNewCode() {
        List<String> submissions = new ArrayList<>();
        PairingCodeController controller = new PairingCodeController(submissions::add);
        controller.accept("123456");
        controller.allowRetry();
        controller.accept("654321");
        assertEquals(List.of("123456", "654321"), submissions);
    }
}
