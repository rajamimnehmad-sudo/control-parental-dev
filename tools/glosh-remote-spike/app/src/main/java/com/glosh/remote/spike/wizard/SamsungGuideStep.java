package com.glosh.remote.spike.wizard;

import java.util.List;

/**
 * Customer-visible Samsung guide. Each step is intentionally explicit and user-confirmed.
 * No Accessibility, automatic Settings click, scroll or coordinate gesture is required.
 */
public enum SamsungGuideStep {
    ABOUT_PHONE(
            1,
            "Abrí “Acerca del teléfono”",
            "En Ajustes, bajá hasta “Acerca del teléfono” y tocalo.",
            "YA LO ABRÍ",
            SettingsTarget.ABOUT_PHONE,
            visual("Ajustes", "Acerca del teléfono", false)),
    SOFTWARE_INFO(
            2,
            "Abrí “Información de software”",
            "Dentro de Acerca del teléfono, tocá “Información de software”.",
            "YA LO ABRÍ",
            SettingsTarget.NONE,
            visual("Acerca del teléfono", "Información de software", false)),
    BUILD_NUMBER(
            3,
            "Tocá 7 veces “Número de compilación”",
            "Si Android pide tu PIN, ingresalo. Glosh nunca lo ve ni lo guarda.",
            "MODO DESARROLLADOR ACTIVADO",
            SettingsTarget.NONE,
            visual("Información de software", "Número de compilación", true)),
    DEVELOPER_OPTIONS(
            4,
            "Abrí “Opciones de desarrollador”",
            "Volvé a Ajustes y entrá en “Opciones de desarrollador”.",
            "YA ESTOY AHÍ",
            SettingsTarget.DEVELOPER_OPTIONS,
            visual("Ajustes", "Opciones de desarrollador", false)),
    WIRELESS_DEBUGGING(
            5,
            "Activá “Depuración inalámbrica”",
            "Entrá en Depuración inalámbrica y activá el interruptor. Si Android pregunta por esta red Wi‑Fi, tocá Permitir.",
            "YA LA ACTIVÉ",
            SettingsTarget.WIRELESS_DEBUGGING,
            visual("Opciones de desarrollador", "Depuración inalámbrica", false)),
    PAIR_DEVICE(
            6,
            "Tocá “Vincular dispositivo con código”",
            "Dejá abierta la pantalla donde Android muestra los 6 números. Glosh preparará el ingreso del código.",
            "YA VEO LOS 6 NÚMEROS",
            SettingsTarget.NONE,
            visual("Depuración inalámbrica", "Vincular dispositivo con código", false)),
    ENTER_CODE(
            7,
            "Ingresá los 6 números",
            "Mirá el código que muestra Android y respondé la notificación de Glosh. Si volvés a la app, también podés escribirlo acá.",
            "ABRIR GLOSH",
            SettingsTarget.NONE,
            visual("Código de vinculación", "6 números", false));

    public static final int TOTAL_STEPS = 7;

    public enum SettingsTarget {
        NONE,
        ABOUT_PHONE,
        DEVELOPER_OPTIONS,
        WIRELESS_DEBUGGING
    }

    private final int number;
    private final String title;
    private final String instruction;
    private final String confirmLabel;
    private final SettingsTarget settingsTarget;
    private final OemGuideStep visual;

    SamsungGuideStep(
            int number,
            String title,
            String instruction,
            String confirmLabel,
            SettingsTarget settingsTarget,
            OemGuideStep visual) {
        this.number = number;
        this.title = title;
        this.instruction = instruction;
        this.confirmLabel = confirmLabel;
        this.settingsTarget = settingsTarget;
        this.visual = visual;
    }

    public int number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String instruction() {
        return instruction;
    }

    public String confirmLabel() {
        return confirmLabel;
    }

    public SettingsTarget settingsTarget() {
        return settingsTarget;
    }

    public OemGuideStep visual() {
        return visual;
    }

    public boolean canGoBack() {
        return ordinal() > 0;
    }

    public SamsungGuideStep previous() {
        return canGoBack() ? values()[ordinal() - 1] : this;
    }

    public boolean canAdvanceLocally() {
        return this != ENTER_CODE;
    }

    public SamsungGuideStep next() {
        return ordinal() < values().length - 1 ? values()[ordinal() + 1] : this;
    }

    public boolean startsBrokerRequest() {
        return this == BUILD_NUMBER;
    }

    public boolean startsPairingService() {
        return this == WIRELESS_DEBUGGING;
    }

    private static OemGuideStep visual(String first, String target, boolean sevenTaps) {
        return new OemGuideStep(
                target,
                "Tutorial Samsung",
                List.of(first, target),
                1,
                sevenTaps,
                new RescueHelp("Volvé al paso anterior.", "Seguí el dibujo y tocá únicamente la opción resaltada."));
    }
}
