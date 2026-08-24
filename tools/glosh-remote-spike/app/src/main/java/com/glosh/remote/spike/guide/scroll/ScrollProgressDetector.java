package com.glosh.remote.spike.guide.scroll;

import java.util.Objects;

public final class ScrollProgressDetector {
    private String previous;
    private int unchanged;

    public boolean record(String fingerprint) {
        if (Objects.equals(previous, fingerprint)) {
            unchanged++;
        } else {
            previous = fingerprint;
            unchanged = 0;
        }
        return unchanged < 2;
    }

    public void reset() {
        previous = null;
        unchanged = 0;
    }
}
