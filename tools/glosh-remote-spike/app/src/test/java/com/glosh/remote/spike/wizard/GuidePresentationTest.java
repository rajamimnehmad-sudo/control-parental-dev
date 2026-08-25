package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glosh.remote.spike.guide.state.GuideStage;

import org.junit.Test;

public class GuidePresentationTest {
    @Test
    public void customerJourneyUsesFourStableSteps() {
        GuidePresentation accessibility = GuidePresentation.forStage(
                GuideStage.GUIDE_PERMISSION,
                null);
        GuidePresentation developer = GuidePresentation.forStage(
                GuideStage.AUTOPILOT_PROBE,
                null);
        GuidePresentation wireless = GuidePresentation.forStage(
                GuideStage.WIRELESS_DEBUGGING,
                null);
        GuidePresentation pairing = GuidePresentation.forStage(
                GuideStage.PAIR_CODE_TARGET,
                null);
        GuidePresentation connected = GuidePresentation.forStage(
                GuideStage.CONNECTED,
                null);

        assertEquals(1, accessibility.step());
        assertEquals(GuidePresentation.Cue.TOGGLE, accessibility.cue());
        assertEquals(2, developer.step());
        assertEquals(GuidePresentation.Cue.TOGGLE, developer.cue());
        assertEquals(3, wireless.step());
        assertEquals(GuidePresentation.Cue.TOGGLE, wireless.cue());
        assertEquals(4, pairing.step());
        assertEquals(GuidePresentation.Cue.TAP, pairing.cue());
        assertTrue(connected.terminal());
        assertEquals("Completado", connected.progressLabel());
    }

    @Test
    public void contextualInstructionsSelectTheRightMicroAnimation() {
        assertEquals(
                GuidePresentation.Cue.TAP,
                GuidePresentation.forStage(
                        GuideStage.WIRELESS_DEBUGGING,
                        "Tocá Permitir para usar esta red Wi‑Fi.").cue());
        assertEquals(
                GuidePresentation.Cue.MULTI_TAP,
                GuidePresentation.forStage(GuideStage.DEV_BUILD_NUMBER, null).cue());
        assertEquals(
                GuidePresentation.Cue.CODE,
                GuidePresentation.forStage(
                        GuideStage.PAIR_CODE_TARGET,
                        "Glosh intenta leer los seis dígitos automáticamente.").cue());
        assertEquals(
                GuidePresentation.Cue.WAIT,
                GuidePresentation.forStage(
                        GuideStage.PAIR_CODE_TARGET,
                        "Código detectado. Esperando la sesión segura de soporte…").cue());
    }

    @Test
    public void normalInstructionsAreNotPresentedAsErrors() {
        GuidePresentation normal = GuidePresentation.recovery(
                GuideStage.WIRELESS_DEBUGGING,
                "Activá Depuración inalámbrica. Glosh detectará el cambio.");
        GuidePresentation warning = GuidePresentation.recovery(
                GuideStage.WIRELESS_DEBUGGING,
                "No encuentro Depuración inalámbrica. Volvé a Glosh.");

        assertEquals(GuidePresentation.Cue.TOGGLE, normal.cue());
        assertEquals(GuidePresentation.Cue.ATTENTION, warning.cue());
        assertFalse(normal.terminal());
    }

    @Test
    public void preparationBeforeStepOneHasNoFakeStepNumber() {
        GuidePresentation preparing = GuidePresentation.preparing(
                "Buscando soporte",
                "Estamos comprobando disponibilidad.");
        assertEquals("Preparando", preparing.progressLabel());
        assertEquals(0, preparing.progressValue());
    }
}
