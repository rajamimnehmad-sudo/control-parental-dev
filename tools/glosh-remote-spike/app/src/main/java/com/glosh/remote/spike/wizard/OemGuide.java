package com.glosh.remote.spike.wizard;

import java.util.Locale;

public record OemGuide(String title, String instructions) {
    public static OemGuide forDevice(String manufacturer, String model) {
        String normalized = manufacturer == null ? "" : manufacturer.toLowerCase(Locale.ROOT);
        if (normalized.contains("samsung")) {
            return new OemGuide(
                    "Preparemos tu teléfono",
                    "1. Abrí “Acerca del teléfono”.\n"
                            + "2. Entrá en “Información de software”.\n"
                            + "3. Tocá 7 veces “Número de compilación”.\n"
                            + "4. Android puede pedirte el PIN del teléfono.");
        }
        String device = model == null || model.trim().isEmpty() ? "teléfono" : model;
        return new OemGuide(
                "Preparemos tu teléfono",
                "En " + device + ", abrí la información del teléfono y buscá “Número de compilación”. "
                        + "Tocalo 7 veces. Android puede pedirte el PIN del teléfono.");
    }
}
