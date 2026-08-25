package com.glosh.remote.spike.wizard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BubblePermissionPolicyTest {
    @Test
    public void appBubblesDisabled_requiresAppGate() {
        assertEquals(
                BubblePermissionPolicy.Gate.APP_BUBBLES,
                BubblePermissionPolicy.evaluate(false, false));
    }

    @Test
    public void selectedAppWithoutConversation_requiresConversationGate() {
        assertEquals(
                BubblePermissionPolicy.Gate.CONVERSATION_BUBBLE,
                BubblePermissionPolicy.evaluate(true, false));
    }

    @Test
    public void approvedConversation_isReady() {
        assertEquals(
                BubblePermissionPolicy.Gate.READY,
                BubblePermissionPolicy.evaluate(true, true));
    }
}
