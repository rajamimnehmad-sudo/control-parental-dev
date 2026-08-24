package com.glosh.remote.spike.guide.state;

import android.content.Context;

import com.glosh.remote.spike.wizard.OemFamily;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LiveGuideRuntime {
    public interface Listener {
        void onGuideStateChanged(GuideStage stage, boolean active);
    }

    private static final GuideStateMachine MACHINE = new GuideStateMachine();
    private static WeakReference<Listener> listener = new WeakReference<>(null);
    private static GuideStateStore store;
    private static OemFamily family = OemFamily.GENERIC;
    private static boolean active;
    private static Set<String> settingsPackages = Set.of();

    private LiveGuideRuntime() {
    }

    public static synchronized void initialize(Context context, OemFamily fallback) {
        if (store != null) {
            return;
        }
        store = new GuideStateStore(context.getApplicationContext());
        GuideStateStore.Snapshot snapshot = store.load(fallback);
        family = snapshot.family();
        active = snapshot.onboardingActive();
        MACHINE.restore(snapshot.stage());
    }

    public static synchronized void beginPermission(OemFamily value, Set<String> packages) {
        family = value;
        settingsPackages = immutable(packages);
        active = true;
        MACHINE.beginPermission();
        persistAndNotify();
    }

    public static synchronized void guideEnabled() {
        if (MACHINE.stage() == GuideStage.GUIDE_PERMISSION) {
            MACHINE.guideEnabled();
            persistAndNotify();
        }
    }

    public static synchronized void setStage(GuideStage stage) {
        if (stage == GuideStage.OFF) {
            reset();
            return;
        }
        if (!active) {
            return;
        }
        MACHINE.restore(stage);
        persistAndNotify();
    }

    public static synchronized void connected() {
        MACHINE.connected();
        active = false;
        if (store != null) {
            store.clear();
        }
        notifyListener();
    }

    public static synchronized void reset() {
        MACHINE.reset();
        active = false;
        settingsPackages = Set.of();
        if (store != null) {
            store.clear();
        }
        notifyListener();
    }

    public static synchronized GuideStage stage() {
        return MACHINE.stage();
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized OemFamily family() {
        return family;
    }

    public static synchronized Set<String> settingsPackages() {
        return settingsPackages;
    }

    public static synchronized void updateSettingsPackages(Set<String> packages) {
        settingsPackages = immutable(packages);
        notifyListener();
    }

    public static synchronized void register(Listener value) {
        listener = new WeakReference<>(value);
        notifyListener();
    }

    public static synchronized void unregister(Listener value) {
        if (listener.get() == value) {
            listener.clear();
        }
    }

    private static void persistAndNotify() {
        if (store != null) {
            store.save(new GuideStateStore.Snapshot(MACHINE.stage(), family, active));
        }
        notifyListener();
    }

    private static void notifyListener() {
        Listener current = listener.get();
        if (current != null) {
            current.onGuideStateChanged(MACHINE.stage(), active);
        }
    }

    private static Set<String> immutable(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(values));
    }
}
