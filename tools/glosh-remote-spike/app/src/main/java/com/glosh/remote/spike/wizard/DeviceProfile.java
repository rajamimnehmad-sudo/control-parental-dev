package com.glosh.remote.spike.wizard;

public record DeviceProfile(
        String manufacturer,
        String brand,
        String model,
        String androidVersion,
        int sdk,
        String oemVersion,
        OemFamily family) {
}
