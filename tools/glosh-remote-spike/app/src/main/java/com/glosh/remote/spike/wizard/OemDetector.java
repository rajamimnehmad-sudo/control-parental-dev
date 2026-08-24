package com.glosh.remote.spike.wizard;

import java.util.Locale;

public final class OemDetector {
    private OemDetector() {
    }

    public static DeviceProfile detect(
            String manufacturer,
            String brand,
            String model,
            String androidVersion,
            int sdk) {
        String maker = clean(manufacturer);
        String deviceBrand = clean(brand);
        String identity = (maker + " " + deviceBrand).toLowerCase(Locale.ROOT);
        OemFamily family;
        if (identity.contains("samsung")) {
            family = OemFamily.SAMSUNG;
        } else if (identity.contains("motorola") || identity.contains("moto")) {
            family = OemFamily.MOTOROLA;
        } else if (identity.contains("xiaomi")
                || identity.contains("redmi")
                || identity.contains("poco")) {
            family = OemFamily.XIAOMI_FAMILY;
        } else {
            family = OemFamily.GENERIC;
        }
        return new DeviceProfile(
                maker,
                deviceBrand,
                clean(model),
                clean(androidVersion),
                sdk,
                "",
                family);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
    }
}
