package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glosh.remote.spike.guide.state.GuideStage;

import org.junit.Test;

public class GuidePresentationPhysicalUxTest {
    @Test
    public void buildNumberInstructionIsNotDuplicatedInTitleAndBody() {
        GuidePresentation presentation = GuidePresentation.forStage(
                GuideStage.DEV_BUILD_NUMBER,
                "Tocá 7 veces “Número de compilación”.");

        assertEquals("Tocá 7 veces “Número de compilación”", presentation.title());
        assertFalse(presentation.body().startsWith("Tocá 7 veces"));
        assertTrue(presentation.body().contains("verificará"));
    }

    @Test
    public void waitingStateKeepsCurrentStepAndUsesSpinnerCue() {
        GuidePresentation presentation = GuidePresentation.waiting(
                GuideStage.WIRELESS_DEBUGGING,
                "Verificando la pantalla…");

        assertEquals(3, presentation.step());
        assertEquals("Esperá…", presentation.title());
        assertEquals(GuidePresentation.Cue.WAIT, presentation.cue());
    }

    @Test
    public void recoveryRecognizesVerificationMessagesAsWaiting() {
        GuidePresentation presentation = GuidePresentation.recovery(
                GuideStage.AUTOPILOT_PROBE,
                "Verificando el cambio…");

        assertEquals("Esperá…", presentation.title());
        assertEquals(GuidePresentation.Cue.WAIT, presentation.cue());
    }

    @Test
    public void restrictedSettingsRecoveryNeverPromisesOverflowAlreadyExists() {
        GuidePresentation presentation = GuidePresentation.restrictedSettingsRecovery();

        assertEquals(1, presentation.step());
        assertEquals(GuidePresentation.Cue.ATTENTION, presentation.cue());
        assertTrue(presentation.body().contains("Si aparece ⋮"));
        assertTrue(presentation.body().contains("Si no aparece ⋮"));
        assertTrue(presentation.body().contains("Permitir configuración restringida"));
    }
}
