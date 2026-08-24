package com.glosh.remote.spike.broker;

import android.content.Context;
import android.os.Build;

import com.glosh.remote.spike.protocol.JoinDescriptor;
import com.glosh.remote.spike.wizard.DeveloperGuidePhase;
import com.glosh.remote.spike.wizard.DeviceProfile;
import com.glosh.remote.spike.wizard.OemDetector;
import com.glosh.remote.spike.wizard.OemGuideRecipe;
import com.glosh.remote.spike.wizard.OnboardingState;
import com.glosh.remote.spike.wizard.WizardPersistence;
import com.glosh.remote.spike.wizard.WizardSnapshot;

public final class SupportSessionCoordinator {
    public interface Listener {
        void onStateChanged();
    }

    private static SupportSessionCoordinator instance;

    private final OnboardingState onboarding = new OnboardingState();
    private final SupportSessionBrokerClient broker;
    private final WizardPersistence persistence;
    private final DeviceProfile profile;
    private final OemGuideRecipe recipe;
    private Listener listener;
    private String descriptor;
    private DeveloperGuidePhase developerPhase;
    private boolean developerConfirmed;
    private boolean wirelessHelp;

    private SupportSessionCoordinator(Context context) {
        broker = new SupportSessionBrokerClient(BrokerConfig.baseUrl());
        persistence = new WizardPersistence(context);
        DeviceProfile detected = OemDetector.detect(
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT);
        WizardSnapshot restored = persistence.load(detected.family());
        profile = new DeviceProfile(
                detected.manufacturer(),
                detected.brand(),
                detected.model(),
                detected.androidVersion(),
                detected.sdk(),
                detected.oemVersion(),
                restored.family());
        recipe = OemGuideRecipe.forProfile(profile);
        onboarding.restore(restored.step());
        developerPhase = restored.developerPhase();
        developerConfirmed = restored.developerConfirmed();
        wirelessHelp = restored.wirelessHelp();
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

    public synchronized DeviceProfile profile() {
        return profile;
    }

    public synchronized OemGuideRecipe recipe() {
        return recipe;
    }

    public synchronized DeveloperGuidePhase developerPhase() {
        return developerPhase;
    }

    public synchronized boolean developerConfirmed() {
        return developerConfirmed;
    }

    public synchronized boolean wirelessHelp() {
        return wirelessHelp;
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
            developerPhase = DeveloperGuidePhase.GUIDE;
            developerConfirmed = false;
            wirelessHelp = false;
            persist();
        }
        notifyChanged();
        broker.discover(new SupportSessionBrokerClient.AvailabilityListener() {
            @Override
            public void onAvailable() {
                synchronized (SupportSessionCoordinator.this) {
                    if (onboarding.step() != OnboardingState.Step.CHECKING_SUPPORT) {
                        return;
                    }
                    onboarding.supportAvailable();
                    persist();
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

    public void confirmDeveloperOptions() {
        synchronized (this) {
            if (onboarding.step() != OnboardingState.Step.DEVELOPER_OPTIONS) {
                return;
            }
            onboarding.developerOptionsReady();
            developerConfirmed = true;
            developerPhase = DeveloperGuidePhase.CONFIRMATION;
            persist();
        }
        notifyChanged();
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
                            wirelessHelp = false;
                            persist();
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

    public synchronized void openedDeveloperSettings() {
        if (onboarding.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            developerPhase = DeveloperGuidePhase.CONFIRMATION;
            persist();
            notifyChanged();
        }
    }

    public synchronized void showDeveloperHelp() {
        if (onboarding.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            developerPhase = DeveloperGuidePhase.HELP;
            persist();
            notifyChanged();
        }
    }

    public synchronized void showDeveloperGuide() {
        if (onboarding.step() == OnboardingState.Step.DEVELOPER_OPTIONS) {
            developerPhase = DeveloperGuidePhase.GUIDE;
            persist();
            notifyChanged();
        }
    }

    public synchronized void showWirelessHelp() {
        if (onboarding.step() == OnboardingState.Step.WIRELESS_DEBUGGING) {
            wirelessHelp = true;
            persist();
            notifyChanged();
        }
    }

    public synchronized void showWirelessGuide() {
        if (onboarding.step() == OnboardingState.Step.WIRELESS_DEBUGGING) {
            wirelessHelp = false;
            persist();
            notifyChanged();
        }
    }

    public synchronized void seedDebugDescriptor(String raw) {
        if (onboarding.step() != OnboardingState.Step.HOME) {
            return;
        }
        JoinDescriptor parsed = JoinDescriptor.parse(raw);
        parsed.destroy();
        descriptor = raw;
        onboarding.sessionReady();
        persist();
        notifyChanged();
    }

    public synchronized String markSessionStarted() {
        if (onboarding.step() != OnboardingState.Step.WIRELESS_DEBUGGING || descriptor == null) {
            return null;
        }
        String value = descriptor;
        descriptor = null;
        onboarding.sessionStarted();
        persist();
        notifyChanged();
        return value;
    }

    public synchronized void reset() {
        broker.cancel();
        descriptor = null;
        onboarding.reset();
        developerPhase = DeveloperGuidePhase.GUIDE;
        developerConfirmed = false;
        wirelessHelp = false;
        persistence.clear();
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
            persist();
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

    private synchronized void persist() {
        persistence.save(new WizardSnapshot(
                profile.family(),
                onboarding.step(),
                developerPhase,
                developerConfirmed,
                wirelessHelp));
    }
}
