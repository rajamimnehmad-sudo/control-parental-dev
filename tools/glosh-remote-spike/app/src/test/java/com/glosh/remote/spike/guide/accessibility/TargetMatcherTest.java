package com.glosh.remote.spike.guide.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;

import java.util.List;

public class TargetMatcherTest {
    private final TargetMatcher matcher = new TargetMatcher();
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

    private TargetCandidate candidate(
            String text, String screen, String parent, boolean clickable, String role, String id) {
        return new TargetCandidate(text, "", id, screen, parent, "", role, clickable, new Rect(0, 0, 10, 10));
    }
}
