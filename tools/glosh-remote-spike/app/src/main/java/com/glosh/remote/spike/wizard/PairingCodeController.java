package com.glosh.remote.spike.wizard;

public final class PairingCodeController {
    public interface Listener {
        void onComplete(String code);
    }

    private final Listener listener;
    private boolean submitted;

    public PairingCodeController(Listener listener) {
        this.listener = listener;
    }

    public boolean accept(String value) {
        String code = value == null ? "" : value;
        if (code.length() > 6 || !code.matches("[0-9]*")) {
            return false;
        }
        if (code.length() == 6 && !submitted) {
            submitted = true;
            listener.onComplete(code);
        }
        return true;
    }

    public void allowRetry() {
        submitted = false;
    }

    public boolean submitted() {
        return submitted;
    }
}
