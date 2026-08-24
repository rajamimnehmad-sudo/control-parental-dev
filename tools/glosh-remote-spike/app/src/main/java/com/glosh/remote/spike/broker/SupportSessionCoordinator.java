package com.glosh.remote.spike.broker;

import android.content.Context;
import android.os.Build;

import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.wizard.OnboardingState;

public final class SupportSessionCoordinator {
    public interface Listener {
        void onStateChanged();
    }

    private static SupportSessionCoordinator instance;

    private final OnboardingState onboarding = new OnboardingState();
    private final SupportSessionBrokerClient broker;
    private Listener listener;
    private String descriptor;

    private SupportSessionCoordinator(Context context) {
        broker = new SupportSessionBrokerClient(BrokerConfig.baseUrl());
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

    public synchronized String descriptor() {
        return descriptor;
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
            onboarding.requestSupport();
        }
        notifyChanged();
        broker.request(
                new SupportSessionBrokerClient.DeviceMetadata(
                        Build.MANUFACTURER,
                        Build.MODEL,
                        Build.VERSION.RELEASE),
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

    public synchronized void seedDebugDescriptor(String raw) {
        if (onboarding.step() != OnboardingState.Step.HOME) {
            return;
        }
        JoinDescriptor parsed = JoinDescriptor.parse(raw);
        parsed.destroy();
        descriptor = raw;
        onboarding.sessionReady();
        notifyChanged();
    }

    public synchronized void continueToWirelessDebugging() {
        if (onboarding.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            onboarding.developerOptionsReady();
            notifyChanged();
        }
    }

    public synchronized String markSessionStarted() {
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
            if (onboarding.step() != OnboardingState.Step.REQUESTING_SUPPORT) {
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
