package com.glosh.remote.spike.wizard;

/** Pure decision table for the two-level Android Bubble permission gate. */
final class BubblePermissionPolicy {
    enum Gate {
        APP_BUBBLES,
        CONVERSATION_BUBBLE,
        READY,
    }

    private BubblePermissionPolicy() {}

    static Gate evaluate(boolean appBubblesAllowed, boolean conversationBubbleAllowed) {
        if (!appBubblesAllowed) {
            return Gate.APP_BUBBLES;
        }
        if (!conversationBubbleAllowed) {
            return Gate.CONVERSATION_BUBBLE;
        }
        return Gate.READY;
    }
}
