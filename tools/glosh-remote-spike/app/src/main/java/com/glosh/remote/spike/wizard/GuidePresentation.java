package com.glosh.remote.spike.wizard;

import com.glosh.remote.spike.guide.state.GuideStage;

import java.util.Locale;

/** Shared copy and motion contract for the app, notification and floating coach. */
public record GuidePresentation(
        int step,
        int totalSteps,
        String title,
        String body,
        Cue cue,
        boolean terminal) {
    public enum Cue {
        TAP,
        TOGGLE,
        MULTI_TAP,
        CODE,
        WAIT,
        SUCCESS,
        ATTENTION
    }

    public String progressLabel() {
        if (terminal) {
            return "Completado";
        }
        return step <= 0 ? "Preparando" : "Paso " + step + " de " + totalSteps;
    }

    public int progressValue() {
        return terminal ? totalSteps : Math.max(0, Math.min(totalSteps, step));
    }

    public static GuidePresentation preparing(String title, String body) {
        return new GuidePresentation(0, 4, title, body, Cue.WAIT, false);
    }

    public static GuidePresentation waiting(GuideStage stage, String body) {
        GuidePresentation base = forStage(stage, null);
        return new GuidePresentation(
                base.step(),
                base.totalSteps(),
                "Esperá…",
                preferred(clean(body), "Verificando el siguiente paso."),
                Cue.WAIT,
                false);
    }

    public static GuidePresentation restrictedSettings() {
        return new GuidePresentation(
                1,
                4,
                "Primero permití el acceso",
                "En Información de la app tocá ⋮ y elegí “Permitir configuración restringida”. Después volvé a Glosh.",
                Cue.ATTENTION,
                false);
    }

    public static GuidePresentation forStage(GuideStage stage, String instruction) {
        GuideStage safeStage = stage == null ? GuideStage.OFF : stage;
        String safeInstruction = clean(instruction);
        return switch (safeStage) {
            case GUIDE_PERMISSION -> presentation(
                    1,
                    "Activá Glosh Remote",
                    secondary(safeInstruction,
                            "Activá Glosh Remote",
                            "Usá el interruptor de esta pantalla. Cuando quede activo, Glosh continúa solo."),
                    Cue.TOGGLE);
            case AUTOPILOT_PROBE, AUTOPILOT_CREDENTIAL -> presentation(
                    2,
                    safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                            ? "Confirmá tu bloqueo de pantalla"
                            : "Prepará las opciones de desarrollador",
                    safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                            ? secondary(safeInstruction,
                                    "Confirmá tu bloqueo de pantalla",
                                    "Ingresá tu PIN, patrón o contraseña. Glosh nunca lo lee.")
                            : secondary(safeInstruction,
                                    "Prepará las opciones de desarrollador",
                                    "Glosh verificará el estado y abrirá el próximo paso automáticamente."),
                    safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                            ? Cue.ATTENTION
                            : Cue.TOGGLE);
            case DEV_ABOUT_PHONE -> presentation(
                    2,
                    "Tocá “Acerca del teléfono”",
                    secondary(safeInstruction,
                            "Tocá “Acerca del teléfono”",
                            "Después Glosh buscará Información de software."),
                    Cue.TAP);
            case DEV_SOFTWARE_INFO -> presentation(
                    2,
                    "Tocá “Información de software”",
                    secondary(safeInstruction,
                            "Tocá “Información de software”",
                            "Después Glosh te señalará Número de compilación."),
                    Cue.TAP);
            case DEV_BUILD_NUMBER -> presentation(
                    2,
                    "Tocá 7 veces “Número de compilación”",
                    secondary(safeInstruction,
                            "Tocá 7 veces “Número de compilación”",
                            "Android te avisará cuando quede habilitado. Glosh lo verificará automáticamente."),
                    Cue.MULTI_TAP);
            case SUPPORT_PREPARING -> preparing(
                    "Preparando la conexión",
                    preferred(safeInstruction, "Glosh está coordinando la sesión segura con soporte."));
            case WIRELESS_DEBUGGING -> presentation(
                    3,
                    titleForWireless(safeInstruction),
                    bodyForWireless(safeInstruction),
                    cueForWireless(safeInstruction));
            case PAIR_CODE_TARGET -> presentation(
                    4,
                    titleForPairing(safeInstruction),
                    bodyForPairing(safeInstruction),
                    cueForPairing(safeInstruction));
            case PAIRING -> presentation(
                    4,
                    "Completando la conexión",
                    secondary(safeInstruction,
                            "Completando la conexión",
                            "Glosh está sincronizando ADB local con la sesión segura de la Mac."),
                    Cue.WAIT);
            case CONNECTED -> new GuidePresentation(
                    4,
                    4,
                    "Conectado con soporte",
                    secondary(safeInstruction,
                            "Conectado con soporte",
                            "La conexión temporal ya está activa."),
                    Cue.SUCCESS,
                    true);
            case AUTOPILOT_FALLBACK -> presentation(
                    2,
                    "Te acompañamos manualmente",
                    secondary(safeInstruction,
                            "Te acompañamos manualmente",
                            "Seguí la indicación visible. Glosh no tocará nada por vos."),
                    Cue.ATTENTION);
            case OFF -> presentation(
                    1,
                    "Soporte remoto Glosh",
                    secondary(safeInstruction,
                            "Soporte remoto Glosh",
                            "Abrí la conexión y te guiamos paso a paso."),
                    Cue.WAIT);
        };
    }

    public static GuidePresentation recovery(GuideStage stage, String message) {
        String cleanMessage = clean(message);
        if (looksLikeWaiting(cleanMessage)) {
            return waiting(stage, cleanMessage);
        }
        if (!looksLikeWarning(cleanMessage)) {
            return forStage(stage, cleanMessage);
        }
        GuidePresentation base = forStage(stage, null);
        return new GuidePresentation(
                base.step(),
                base.totalSteps(),
                base.title(),
                preferred(cleanMessage, base.body()),
                Cue.ATTENTION,
                false);
    }

    private static GuidePresentation presentation(int step, String title, String body, Cue cue) {
        return new GuidePresentation(step, 4, title, body, cue, false);
    }

    private static String titleForWireless(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("permitir")) {
            return "Confirmá “Permitir”";
        }
        if (normalized.contains("busca") || normalized.contains("toca depuracion")) {
            return "Tocá “Depuración inalámbrica”";
        }
        if (normalized.contains("opciones de desarrollador")) {
            return "Activá las opciones de desarrollador";
        }
        return "Activá “Depuración inalámbrica”";
    }

    private static String bodyForWireless(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("permitir")) {
            return secondary(instruction,
                    "Confirmá “Permitir”",
                    "Android necesita confirmar esta red Wi‑Fi una sola vez.");
        }
        if (normalized.contains("busca") || normalized.contains("toca depuracion")) {
            return secondary(instruction,
                    "Tocá “Depuración inalámbrica”",
                    "Samsung no abrió esta pantalla directamente; tocala en la lista y Glosh continuará.");
        }
        if (normalized.contains("opciones de desarrollador")) {
            return secondary(instruction,
                    "Activá las opciones de desarrollador",
                    "Cuando queden activas, Glosh avanzará automáticamente.");
        }
        return secondary(instruction,
                "Activá “Depuración inalámbrica”",
                "Tocá el interruptor. Glosh detectará el cambio y seguirá solo.");
    }

    private static Cue cueForWireless(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("permitir") || normalized.contains("busca")) {
            return Cue.TAP;
        }
        return Cue.TOGGLE;
    }

    private static String titleForPairing(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("detectado")) {
            return "Código detectado";
        }
        if (normalized.contains("obteniendo")
                || normalized.contains("leyendo")
                || normalized.contains("leer")) {
            return "Obteniendo el código";
        }
        if (normalized.contains("vencio") || normalized.contains("nuevo")) {
            return "Ingresá un código nuevo";
        }
        if (normalized.contains("ingresa") || normalized.contains("escribi")) {
            return "Ingresá los 6 números";
        }
        return "Tocá “Vincular dispositivo con código”";
    }

    private static String bodyForPairing(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("detectado") || normalized.contains("esperando")) {
            return secondary(instruction,
                    "Código detectado",
                    "Glosh lo conserva en memoria hasta que soporte esté listo.");
        }
        if (normalized.contains("obteniendo")
                || normalized.contains("leyendo")
                || normalized.contains("leer")) {
            return secondary(instruction,
                    "Obteniendo el código",
                    "Glosh lo procesa localmente y continúa automáticamente.");
        }
        if (normalized.contains("ingresa")
                || normalized.contains("escribi")
                || normalized.contains("nuevo")
                || normalized.contains("vencio")) {
            return secondary(instruction,
                    "Ingresá los 6 números",
                    "También podés responder directamente desde la notificación.");
        }
        return secondary(instruction,
                "Tocá “Vincular dispositivo con código”",
                "Android mostrará seis dígitos; Glosh intentará leerlos automáticamente.");
    }

    private static Cue cueForPairing(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("detectado") || normalized.contains("esperando")) {
            return Cue.WAIT;
        }
        if (normalized.isEmpty()
                || normalized.contains("vincular")
                || normalized.contains("emparejar")) {
            return Cue.TAP;
        }
        return Cue.CODE;
    }

    private static boolean looksLikeWaiting(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("espera")
                || normalized.startsWith("verificando")
                || normalized.startsWith("abriendo")
                || normalized.startsWith("preparando")
                || normalized.startsWith("leyendo")
                || normalized.contains("detecte un cambio");
    }

    private static boolean looksLikeWarning(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("no ")
                || normalized.startsWith("esta no")
                || normalized.contains("no pude")
                || normalized.contains("no encuentro")
                || normalized.contains("volve a glosh")
                || normalized.contains("fallo")
                || normalized.contains("error");
    }

    private static String secondary(String instruction, String title, String fallback) {
        String cleanInstruction = clean(instruction);
        if (cleanInstruction.isEmpty()) {
            return fallback;
        }
        String normalizedInstruction = normalize(cleanInstruction)
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "");
        String normalizedTitle = normalize(title)
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "");
        if (normalizedInstruction.equals(normalizedTitle)
                || normalizedInstruction.equals(normalizedTitle + ".")) {
            return fallback;
        }
        return cleanInstruction;
    }

    private static String preferred(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim();
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u');
    }
}
