package com.glosh.remote.spike.guide.accessibility;

import com.glosh.remote.spike.guide.state.GuideStage;
import com.glosh.remote.spike.wizard.OemFamily;

import java.util.List;

public final class GuideTargetCatalog {
    private GuideTargetCatalog() {
    }

    public static TargetSpec forStage(OemFamily family, GuideStage stage) {
        return switch (stage) {
            case DEV_ABOUT_PHONE -> spec(
                    List.of("Acerca del teléfono", "About phone"),
                    List.of("Información del teléfono", "Phone information"),
                    List.of("Ajustes", "Settings"));
            case DEV_SOFTWARE_INFO -> family == OemFamily.SAMSUNG
                    ? spec(
                            List.of("Información de software", "Software information"),
                            List.of(),
                            List.of("Acerca del teléfono", "About phone"))
                    : family == OemFamily.XIAOMI_FAMILY
                            ? spec(
                                    List.of("Información detallada y especificaciones", "Detailed info and specs"),
                                    List.of(),
                                    List.of("Acerca del teléfono", "About phone"))
                            : spec(
                                    List.of("Número de compilación", "Build number"),
                                    List.of(),
                                    List.of("Acerca del teléfono", "About phone", "Sistema", "System"));
            case DEV_BUILD_NUMBER -> family == OemFamily.XIAOMI_FAMILY
                    ? spec(
                            List.of("Versión de OS", "Versión de MIUI", "Versión de HyperOS", "OS version", "MIUI version", "HyperOS version"),
                            List.of(),
                            List.of("Información detallada y especificaciones", "Detailed info and specs"))
                    : spec(
                            List.of("Número de compilación", "Build number"),
                            List.of(),
                            List.of("Información de software", "Software information", "Acerca del teléfono", "About phone"));
            case WIRELESS_DEBUGGING -> spec(
                    List.of("Depuración inalámbrica", "Wireless debugging"),
                    List.of(),
                    List.of("Opciones de desarrollador", "Developer options"));
            case PAIR_CODE_TARGET -> spec(
                    List.of("Emparejar dispositivo con código", "Pair device with pairing code"),
                    List.of("Emparejar con código", "Pair using pairing code"),
                    List.of("Depuración inalámbrica", "Wireless debugging"));
            default -> null;
        };
    }

    public static String instruction(GuideStage stage) {
        return switch (stage) {
            case DEV_ABOUT_PHONE -> "Tocá Acerca del teléfono";
            case DEV_SOFTWARE_INFO -> "Tocá Información de software";
            case DEV_BUILD_NUMBER -> "Tocá 7 veces Número de compilación";
            case WIRELESS_DEBUGGING -> "Activá Depuración inalámbrica";
            case PAIR_CODE_TARGET -> "Tocá Emparejar dispositivo con código";
            default -> "Volvamos al punto correcto";
        };
    }

    private static TargetSpec spec(List<String> exact, List<String> aliases, List<String> screens) {
        return new TargetSpec(
                exact,
                aliases,
                screens,
                List.of(),
                List.of(),
                List.of("title", "summary", "switch_text"),
                List.of("TextView", "Switch", "LinearLayout"),
                false);
    }
}
