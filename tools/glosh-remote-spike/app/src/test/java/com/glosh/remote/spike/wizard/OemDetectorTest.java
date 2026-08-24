package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OemDetectorTest {
    @Test
    public void detectsInitialOemFamilies() {
        assertFamily(OemFamily.SAMSUNG, "Samsung", "samsung", "SM-S908E");
        assertFamily(OemFamily.MOTOROLA, "motorola", "moto", "XT2343");
        assertFamily(OemFamily.XIAOMI_FAMILY, "Xiaomi", "xiaomi", "2312DRA50G");
        assertFamily(OemFamily.XIAOMI_FAMILY, "Xiaomi", "Redmi", "Redmi Note");
        assertFamily(OemFamily.XIAOMI_FAMILY, "Xiaomi", "POCO", "POCO F6");
        assertFamily(OemFamily.GENERIC, "Nothing", "Nothing", "A015");
    }

    @Test
    public void brandCanIdentifyFamilyWhenManufacturerIsGeneric() {
        assertFamily(OemFamily.XIAOMI_FAMILY, "unknown", "POCO", "F6");
    }

    private static void assertFamily(OemFamily expected, String manufacturer, String brand, String model) {
        DeviceProfile profile = OemDetector.detect(manufacturer, brand, model, "16", 36);
        assertEquals(expected, profile.family());
        assertEquals("16", profile.androidVersion());
        assertEquals(36, profile.sdk());
    }
}
