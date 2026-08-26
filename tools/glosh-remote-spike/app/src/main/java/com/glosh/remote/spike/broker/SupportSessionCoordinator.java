package com.glosh.remote.spike.broker;

import android.content.Context;
import android.os.Build;

import com.glosh.remote.spike.wizard.DeviceProfile;
import com.glosh.remote.spike.wizard.OemDetector;
import com.glosh.remote.spike.wizard.OnboardingState;

/** Owns the single linear broker rendezvous used by the notification-only flow. */
public final class SupportSessionCoordinator {
    public interface Listener {
        void onStateChanged();
    }

    private static SupportSessionCoordinator instance;

    private final OnboardingState onboarding = new OnboardingState();
    private final SupportSessionBrokerClient broker;
    private final DeviceProfile profile;
    private Listener listener;
    private String descriptor;

    private SupportSessionCoordinator(Context context) {
        broker = new SupportSessionBrokerClient(BrokerConfig.baseUrl());
        profile = OemDetector.detect(
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT);
    }

    public static synchronized SupportSessionCoordinator get(Context context) {
        if (instance == null) {
            instance = new SupportSessionCoordinator(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized OnboardingState.Step step() {
        return onboarding.step();
    }

    public synchronized void attach(Listener value) {
        listener = value;
    }

    public synchronized void detach(Listener value) {
        if (listener == value) {
            listener = null;
        }
    }

    public void requestSupport() {
        synchronized (this) {
            if (onboarding.step() != OnboardingState.Step.HOME
                    && onboarding.step() != OnboardingState.Step.UNAVAILABLE) {
                return;
            }
            descriptor = null;
            onboarding.requestSupport();
        }
        notifyChanged();
        broker.discover(new SupportSessionBrokerClient.AvailabilityListener() {
            @Override
            public void onAvailable() {
                synchronized (SupportSessionCoordinator.this) {
                    if (onboarding.step() != OnboardingState.Step.CHECKING_SUPPORT) {
                        return;
                    }
                    onboarding.directSupportAvailable();
                }
                notifyChanged();
                requestDescriptor();
            }

            @Override
            public void onUnavailable() {
                markUnavailable();
            }

            @Override
            public void onError() {
                markUnavailable();
            }
        });
    }

    private void requestDescriptor() {
        broker.request(
                new SupportSessionBrokerClient.DeviceMetadata(
                        profile.manufacturer(),
                        profile.model(),
                        profile.androidVersion()),
                new SupportSessionBrokerClient.Listener() {
                    @Override
                    public void onPending(String requestId) {
                        notifyChanged();
                    }

                    @Override
                    public void onSessionReady(String value) {
                        synchronized (SupportSessionCoordinator.this) {
                            if (onboarding.step() != OnboardingState.Step.REQUESTING_SUPPORT) {
                                return;
                            }
                            descriptor = value;
                            onboarding.sessionReady();
                        }
                        notifyChanged();
                    }

                    @Override
                    public void onUnavailable() {
                        markUnavailable();
                    }

                    @Override
                    public void onError() {
                        markUnavailable();
                    }
                });
    }

    /** Atomically consumes the one-time descriptor before the foreground service is dispatched. */
    public synchronized String takeDescriptor() {
        if (onboarding.step() != OnboardingState.Step.WIRELESS_DEBUGGING || descriptor == null) {
            return null;
        }
        String value = descriptor;
        descriptor = null;
        onboarding.sessionStarted();
        notifyChanged();
        return value;
    }

    public synchronized void reset() {
        broker.cancel();
        descriptor = null;
        onboarding.reset();
        notifyChanged();
    }

    private void markUnavailable() {
        synchronized (this) {
            if (onboarding.step() != OnboardingState.Step.CHECKING_SUPPORT
                    && onboarding.step() != OnboardingState.Step.REQUESTING_SUPPORT) {
                return;
            }
            descriptor = null;
            onboarding.unavailable();
        }
        notifyChanged();
    }

    private void notifyChanged() {
        Listener current;
        synchronized (this) {
            current = listener;
        }
        if (current != null) {
            current.onStateChanged();
        }
    }
}
