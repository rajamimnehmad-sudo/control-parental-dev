package com.glosh.remote.spike.guide.pairing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public class PairingCodeDetectorTest {
    private final PairingCodeDetector detector = new PairingCodeDetector();

    @Test
    public void acceptsUniqueSixDigitsOnlyInPairingContext() {
        assertEquals("123456", detector.detect(List.of(
                value("Código de emparejamiento", "Emparejar dispositivo con código"),
                value("123456", "Emparejar dispositivo con código")), true));
    }

    @Test
    public void rejectsRandomSixDigitsOnWrongScreen() {
        assertNull(detector.detect(List.of(value("123456", "Acerca del teléfono")), true));
    }

    @Test
    public void rejectsMultipleCandidates() {
        assertNull(detector.detect(List.of(
                value("123456", "Wireless debugging pairing code"),
                value("654321", "Wireless debugging pairing code")), true));
    }

    @Test
    public void rejectsWrongLengthNonNumericAndUnexpectedStage() {
        assertNull(detector.detect(List.of(value("12345", "Pairing code")), true));
        assertNull(detector.detect(List.of(value("12A456", "Pairing code")), true));
        assertNull(detector.detect(List.of(value("123456", "Pairing code")), false));
    }

    private PairingCodeDetector.VisibleText value(String text, String screen) {
        return new PairingCodeDetector.VisibleText(text, "", screen);
    }
}
