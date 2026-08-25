package com.glosh.remote.spike.guide.autopilot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

public class GuidedAssistantArchitectureTest {
    @Test
    public void coordinatorOwnsNoSettingsClickOrScrollExecutor() {
        for (Field field : AdaptiveInstallCoordinator.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName();
            assertFalse("guided coordinator must not own a click executor",
                    type.contains("ClickExecutor"));
            assertFalse("guided coordinator must not own a scroll executor",
                    type.contains("ScrollExecutor"));
        }
    }

    @Test
    public void hostContractIsOpenObserveExplainOnly() {
        var methods = Arrays.stream(AdaptiveInstallCoordinator.Host.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertTrue(methods.contains("openSettings"));
        assertTrue(methods.contains("showInstruction"));
        assertTrue(methods.contains("showRecovery"));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("click")));
        assertFalse(methods.stream().anyMatch(name -> name.toLowerCase().contains("scroll")));
    }
}
