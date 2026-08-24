package com.glosh.remote.spike.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SessionStartPolicyTest {
    private static final String START = "start";

    @Test
    public void duplicateStartCannotRestartActiveSession() {
        assertFalse(SessionState.shouldIgnoreStart(START, START, SessionState.IDLE));
        assertTrue(SessionState.shouldIgnoreStart(START, START, SessionState.PREPARING));
        assertTrue(SessionState.shouldIgnoreStart(START, START, SessionState.CONNECTED));
        assertFalse(SessionState.shouldIgnoreStart("stop", START, SessionState.CONNECTED));
    }
}
