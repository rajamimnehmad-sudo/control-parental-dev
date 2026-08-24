package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;

import java.util.List;

public class TargetMatcherTest {
    private final TargetMatcher matcher = new TargetMatcher();

    @Test
    public void samsungClickEventCanMatchLabelInsideClickableParent() {
        TargetSpec spec = GuideTargetCatalog.forStage(
                com.glosh.remote.spike.wizard.OemFamily.SAMSUNG,
                com.glosh.remote.spike.guide.state.GuideStage.DEV_SOFTWARE_INFO);
        TargetCandidate parent = candidate(
                "", "Acerca del teléfono", "", true, "LinearLayout", null);
        TargetCandidate label = candidate(
                "Información de software", "Acerca del teléfono", "", false, "TextView", null);

        TargetMatcher.Match match = matcher.best(spec, List.of(parent, label));

        assertTrue(match.actionable());
        assertEquals(label, match.candidate());
    }

    private final TargetSpec spec = new TargetSpec(
            List.of("Depuración inalámbrica"),
            List.of("Wireless debugging"),
            List.of("Opciones de desarrollador"),
            List.of("Redes"),
            List.of(),
            List.of("wireless_debugging"),
            List.of("TextView"),
            true);

    @Test
    public void normalizationHandlesCaseWhitespaceUnicodeAndAccents() {
        assertEquals("depuracion inalambrica", TargetMatcher.normalize("  DEPURACIÓN   inalámbrica "));
        assertEquals(
                "depuracion inalambrica",
                TargetMatcher.normalize("\u200eDEPURACIÓN\u00a0inalámbrica\u200f"));
    }

    @Test
    public void exactContextAndRoleAreHighConfidence() {
        assertEquals(TargetMatcher.Confidence.HIGH, matcher.score(spec, candidate(
                "Depuración inalámbrica", "Opciones de desarrollador", "Redes", true, "TextView", null)));
    }

    @Test
    public void stableViewIdAndExactLabelAreHighConfidence() {
        assertEquals(TargetMatcher.Confidence.HIGH, matcher.score(spec, candidate(
                "Depuración inalámbrica", "Opciones de desarrollador", "", true,
                "TextView", "com.android.settings:id/wireless_debugging")));
    }

    @Test
    public void exactWithoutRequiredContextIsNotActionable() {
        TargetMatcher.Match result = matcher.best(spec, List.of(candidate(
                "Depuración inalámbrica", "Opciones de desarrollador", "", false, "TextView", null)));
        assertFalse(result.actionable());
    }

    @Test
    public void wrongScreenIsRejected() {
        assertEquals(TargetMatcher.Confidence.NONE, matcher.score(spec, candidate(
                "Depuración inalámbrica", "Privacidad", "Redes", true, "TextView", null)));
    }

    @Test
    public void ambiguousHighMatchesNeverAct() {
        TargetCandidate value = candidate(
                "Depuración inalámbrica", "Opciones de desarrollador", "Redes", true, "TextView", null);
        TargetMatcher.Match result = matcher.best(spec, List.of(value, value));
        assertTrue(result.ambiguous());
        assertFalse(result.actionable());
    }

    @Test
    public void uniqueVisibleLabelWinsOverSamsungSwitchDescriptionDuplicate() {
        TargetSpec wirelessSpec = GuideTargetCatalog.forStage(
                com.glosh.remote.spike.wizard.OemFamily.SAMSUNG,
                com.glosh.remote.spike.guide.state.GuideStage.WIRELESS_DEBUGGING);
        TargetCandidate label = candidate(
                "Depuración inalámbrica", "Opciones de desarrollador", "", false,
                "TextView", null);
        TargetCandidate switchDescription = new TargetCandidate(
                "", "Depuración inalámbrica", "android:id/switch_widget",
                "Opciones de desarrollador", "", "", "Switch", true,
                new Rect(800, 0, 1000, 100));

        TargetMatcher.Match result = matcher.best(
                wirelessSpec, List.of(label, switchDescription));

        assertTrue(result.actionable());
        assertEquals(label, result.candidate());
    }

    @Test
    public void samsungVisibleBuildNumberIsHighWithoutClickableTextNode() {
        TargetSpec samsungBuild = GuideTargetCatalog.forStage(
                com.glosh.remote.spike.wizard.OemFamily.SAMSUNG,
                com.glosh.remote.spike.guide.state.GuideStage.DEV_BUILD_NUMBER);
        TargetCandidate buildNumber = candidate(
                "Número de compilación", "Información de software", "", false, "TextView", null);
        TargetMatcher.Match result = matcher.best(samsungBuild, List.of(buildNumber));
        assertTrue(result.actionable());
        assertEquals(TargetMatcher.Confidence.HIGH, result.confidence());
    }

    @Test
    public void samsungBuildNumberOnWrongScreenCannotTriggerFalseHighlight() {
        TargetSpec samsungBuild = GuideTargetCatalog.forStage(
                com.glosh.remote.spike.wizard.OemFamily.SAMSUNG,
                com.glosh.remote.spike.guide.state.GuideStage.DEV_BUILD_NUMBER);
        TargetMatcher.Match result = matcher.best(samsungBuild, List.of(candidate(
                "Número de compilación", "Privacidad", "", true, "TextView", null)));
        assertFalse(result.actionable());
        assertEquals(TargetMatcher.Confidence.NONE, result.confidence());
    }

    private TargetCandidate candidate(
            String text, String screen, String parent, boolean clickable, String role, String id) {
        return new TargetCandidate(text, "", id, screen, parent, "", role, clickable, new Rect(0, 0, 10, 10));
    }
}
