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

    public static GuidePresentation forStage(GuideStage stage, String instruction) {
        GuideStage safeStage = stage == null ? GuideStage.OFF : stage;
        String safeInstruction = clean(instruction);
        return switch (safeStage) {
            case GUIDE_PERMISSION -> presentation(
                    1,
                    "Activá Glosh Remote",
                    preferred(safeInstruction,
                            "Usá el interruptor de esta pantalla. Cuando quede activo, Glosh continúa solo."),
                    Cue.TOGGLE);
            case AUTOPILOT_PROBE, AUTOPILOT_CREDENTIAL -> presentation(
                    2,
                    safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                            ? "Confirmá tu bloqueo de pantalla"
                            : "Prepará las opciones de desarrollador",
                    preferred(safeInstruction,
                            safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                                    ? "Ingresá el PIN, patrón o contraseña que Android te pide. Glosh nunca lo lee."
                                    : "Si están apagadas, activalas. Glosh abrirá automáticamente el próximo paso."),
                    safeStage == GuideStage.AUTOPILOT_CREDENTIAL
                            ? Cue.ATTENTION
                            : Cue.TOGGLE);
            case DEV_ABOUT_PHONE -> presentation(
                    2,
                    "Tocá “Acerca del teléfono”",
                    preferred(safeInstruction, "Está dentro de Ajustes. Glosh te acompaña en pantalla."),
                    Cue.TAP);
            case DEV_SOFTWARE_INFO -> presentation(
                    2,
                    "Tocá “Información de software”",
                    preferred(safeInstruction, "Después te mostramos el último paso para activar desarrollo."),
                    Cue.TAP);
            case DEV_BUILD_NUMBER -> presentation(
                    2,
                    "Tocá 7 veces “Número de compilación”",
                    preferred(safeInstruction, "Android avisará cuando las opciones de desarrollador estén listas."),
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
                    preferred(safeInstruction, "Glosh está sincronizando ADB local con la sesión segura de la Mac."),
                    Cue.WAIT);
            case CONNECTED -> new GuidePresentation(
                    4,
                    4,
                    "Conectado con soporte",
                    preferred(safeInstruction, "La conexión temporal ya está activa."),
                    Cue.SUCCESS,
                    true);
            case AUTOPILOT_FALLBACK -> presentation(
                    2,
                    "Te acompañamos manualmente",
                    preferred(safeInstruction, "Seguí la indicación visible. Glosh no tocará nada por vos."),
                    Cue.ATTENTION);
            case OFF -> presentation(
                    1,
                    "Soporte remoto Glosh",
                    preferred(safeInstruction, "Abrí la conexión y te guiamos paso a paso."),
                    Cue.WAIT);
        };
    }

    public static GuidePresentation recovery(GuideStage stage, String message) {
        String cleanMessage = clean(message);
        if (!looksLikeWarning(cleanMessage)) {
            return forStage(stage, cleanMessage);
        }
        GuidePresentation base = forStage(stage, cleanMessage);
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
        if (normalized.contains("opciones de desarrollador")) {
            return "Activá las opciones de desarrollador";
        }
        return "Activá “Depuración inalámbrica”";
    }

    private static String bodyForWireless(String instruction) {
        String normalized = normalize(instruction);
        if (normalized.contains("permitir")) {
            return preferred(instruction, "Android necesita confirmar esta red Wi‑Fi una sola vez.");
        }
        if (normalized.contains("opciones de desarrollador")) {
            return preferred(instruction, "Cuando queden activas, Glosh abrirá esta pantalla nuevamente.");
        }
        return preferred(instruction, "Tocá el interruptor. Glosh detectará el cambio y seguirá solo.");
    }

    private static Cue cueForWireless(String instruction) {
        return normalize(instruction).contains("permitir") ? Cue.TAP : Cue.TOGGLE;
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
            return preferred(instruction, "Glosh lo conserva en pantalla hasta que soporte esté listo.");
        }
        if (normalized.contains("obteniendo")
                || normalized.contains("leyendo")
                || normalized.contains("leer")) {
            return preferred(instruction, "Glosh lo procesa localmente y continúa automáticamente.");
        }
        if (normalized.contains("ingresa")
                || normalized.contains("escribi")
                || normalized.contains("nuevo")
                || normalized.contains("vencio")) {
            return preferred(instruction, "También podés responder directamente desde la notificación.");
        }
        return preferred(instruction, "Android mostrará seis dígitos; Glosh intentará leerlos automáticamente.");
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

    private static boolean looksLikeWarning(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("no ")
                || normalized.startsWith("esta no")
                || normalized.contains("no pude")
                || normalized.contains("no encuentro")
                || normalized.contains("volvé a glosh")
                || normalized.contains("fallo")
                || normalized.contains("error");
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
