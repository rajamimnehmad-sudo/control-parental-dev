package com.glosh.remote.spike.wizard;

import java.util.List;

public record OemGuideRecipe(
        OemFamily family,
        String familyLabel,
        OemGuideStep developerOptions,
        OemGuideStep wirelessDebugging) {
    public static OemGuideRecipe forProfile(DeviceProfile profile) {
        OemGuideStep developer = switch (profile.family()) {
            case SAMSUNG -> new OemGuideStep(
                    "Preparemos tu Samsung",
                    "Te voy a mostrar exactamente dónde tocar. No cambies nada más.",
                    List.of("Acerca del teléfono", "Información de software", "Número de compilación"),
                    0,
                    true,
                    new RescueHelp(
                            "Volvé a Acerca del teléfono y buscá Información de software.",
                            "Tocá Información de software. Después tocá 7 veces Número de compilación."));
            case MOTOROLA -> new OemGuideStep(
                    "Preparemos tu Motorola",
                    "Te voy a mostrar exactamente dónde tocar. No cambies nada más.",
                    List.of("Sistema (si aparece)", "Acerca del teléfono", "Número de compilación"),
                    0,
                    true,
                    new RescueHelp(
                            "Si no aparece directamente, buscá Sistema → Acerca del teléfono.",
                            "Abrí Acerca del teléfono. Después tocá 7 veces Número de compilación."));
            case XIAOMI_FAMILY -> new OemGuideStep(
                    "Preparemos tu teléfono",
                    "Te voy a mostrar exactamente dónde tocar. No cambies nada más.",
                    List.of("Acerca del teléfono", "Información detallada y especificaciones", "Versión de OS o MIUI"),
                    0,
                    true,
                    new RescueHelp(
                            "Volvé una pantalla y buscá Información detallada y especificaciones.",
                            "Abrí Información detallada y especificaciones. Tocá varias veces la versión de OS o MIUI."));
            case GENERIC -> new OemGuideStep(
                    "Preparemos tu teléfono",
                    "Te voy a mostrar exactamente dónde tocar. No cambies nada más.",
                    List.of("Acerca del teléfono", "Información del software", "Número de compilación"),
                    0,
                    true,
                    new RescueHelp(
                            "Volvé a Acerca del teléfono y buscá Número de compilación.",
                            "Buscá Número de compilación y tocalo 7 veces."));
        };
        OemGuideStep wireless = new OemGuideStep(
                "Abramos la conexión",
                "Android te va a mostrar un código temporal de 6 números.",
                profile.family() == OemFamily.XIAOMI_FAMILY
                        ? List.of(
                                "Ajustes adicionales",
                                "Opciones de desarrollador",
                                "Depuración inalámbrica",
                                "Emparejar dispositivo con código")
                        : List.of(
                                "Opciones de desarrollador",
                                "Depuración inalámbrica",
                                "Emparejar dispositivo con código"),
                0,
                false,
                new RescueHelp(
                        "Buscá Depuración inalámbrica dentro de Opciones de desarrollador.",
                        "Activá Depuración inalámbrica. Después tocá Emparejar dispositivo con código."));
        return new OemGuideRecipe(
                profile.family(),
                label(profile.family()),
                developer,
                wireless);
    }

    private static String label(OemFamily family) {
        return switch (family) {
            case SAMSUNG -> "Samsung";
            case MOTOROLA -> "Motorola";
            case XIAOMI_FAMILY -> "Xiaomi, Redmi o POCO";
            case GENERIC -> "Android";
        };
    }
}
