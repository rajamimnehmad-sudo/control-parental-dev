package com.glosh.remote.spike.wizard;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;

import com.glosh.remote.spike.GuideBubbleActivity;
import com.glosh.remote.spike.MainActivity;
import com.glosh.remote.spike.R;

import java.util.Collections;

/**
 * Persistent mirror of the Samsung guide and owner of the system-managed Bubble surface.
 * The Bubble is intentionally system-owned so Samsung Settings cannot hide it as a normal
 * TYPE_APPLICATION_OVERLAY window.
 */
public final class GuideNotification {
    private static final String PARENT_CHANNEL_ID = "glosh_remote_guide_parent_v2";
    private static final String CHANNEL_ID = "glosh_remote_guide_bubble_v2";
    private static final String SHORTCUT_ID = "glosh_remote_support_guide";
    private static final int NOTIFICATION_ID = 7399;
    private static final int REQUEST_OPEN = 73990;
    private static final int REQUEST_BACK = 73991;
    private static final int REQUEST_NEXT = 73992;
    private static final int REQUEST_BUBBLE = 73993;

    private final Context context;
    private final NotificationManager manager;

    public GuideNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = context.getSystemService(NotificationManager.class);
        ensureConversationShortcut();
        ensureChannels();
    }

    public void showStep(SamsungGuideStep step) {
        showStep(step, null, false);
    }

    public void showStep(SamsungGuideStep step, String overrideBody) {
        showStep(step, overrideBody, false);
    }

    public void showBubbleStep(SamsungGuideStep step, boolean autoExpand) {
        showStep(step, null, autoExpand);
    }

    public void showBubbleStep(SamsungGuideStep step, String overrideBody, boolean autoExpand) {
        showStep(step, overrideBody, autoExpand);
    }

    public void showWaiting(SamsungGuideStep step, String message) {
        showStep(step, message == null ? "Preparando la conexión segura…" : message, false);
    }

    public boolean canBubble() {
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        boolean appAllowed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appAllowed = manager.getBubblePreference() != NotificationManager.BUBBLE_PREFERENCE_NONE;
        } else {
            appAllowed = manager.areBubblesAllowed();
        }
        if (!appAllowed) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null || channel.canBubble();
    }

    public void openBubbleSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        try {
            activity.startActivity(intent);
        } catch (Throwable firstFailure) {
            Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            try {
                activity.startActivity(fallback);
            } catch (Throwable ignored) {
                // The caller keeps the user on the safe permission gate.
            }
        }
    }

    public void clear() {
        manager.cancel(NOTIFICATION_ID);
    }

    private void showStep(SamsungGuideStep step, String overrideBody, boolean autoExpand) {
        if (step == null || !manager.areNotificationsEnabled()) {
            return;
        }
        ensureConversationShortcut();
        ensureChannels();

        PendingIntent open = activityIntent(MainActivity.ACTION_GUIDE_OPEN, REQUEST_OPEN);
        Person glosh = gloshPerson();
        String body = overrideBody == null ? step.instruction() : overrideBody;

        Notification.MessagingStyle style = new Notification.MessagingStyle(glosh)
                .setConversationTitle("Asistente Samsung")
                .setGroupConversation(false)
                .addMessage(step.title() + "\n" + body, System.currentTimeMillis(), glosh);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_glosh_bubble)
                .setContentTitle("Glosh · Paso " + step.number() + " de " + SamsungGuideStep.TOTAL_STEPS)
                .setContentText(step.title())
                .setSubText("Guía Samsung")
                .setStyle(style)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_ID)
                .setProgress(SamsungGuideStep.TOTAL_STEPS, step.number(), false)
                .setBubbleMetadata(bubbleMetadata(autoExpand));

        if (step.canGoBack()) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Atrás",
                    activityIntent(MainActivity.ACTION_GUIDE_BACK, REQUEST_BACK)).build());
        }
        if (step.canAdvanceLocally()) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    shortActionLabel(step),
                    activityIntent(MainActivity.ACTION_GUIDE_NEXT, REQUEST_NEXT)).build());
        }
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private Notification.BubbleMetadata bubbleMetadata(boolean autoExpand) {
        Intent bubbleTarget = new Intent(context, GuideBubbleActivity.class)
                .setAction("com.glosh.remote.spike.OPEN_GUIDE_BUBBLE")
                .setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Bubbles are one of the platform cases that require a mutable PendingIntent so
            // SystemUI can apply the document/multi-task launch flags safely.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent bubbleIntent = PendingIntent.getActivity(
                context,
                REQUEST_BUBBLE,
                bubbleTarget,
                flags);
        Icon icon = Icon.createWithResource(context, R.drawable.ic_glosh_bubble);
        return new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                .setDesiredHeight(430)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();
    }

    private void ensureConversationShortcut() {
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        if (shortcuts == null) {
            return;
        }
        try {
            Intent open = new Intent(context, MainActivity.class)
                    .setAction(MainActivity.ACTION_GUIDE_OPEN)
                    .setPackage(context.getPackageName());
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                    .setShortLabel("Asistente Glosh")
                    .setLongLived(true)
                    .setPerson(gloshPerson())
                    .setCategories(Collections.singleton(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_glosh_bubble))
                    .setIntent(open)
                    .build();
            shortcuts.addDynamicShortcuts(Collections.singletonList(shortcut));
        } catch (Throwable ignored) {
            // The notification remains a safe fallback even if an OEM rejects shortcut refresh.
        }
    }

    private void ensureChannels() {
        NotificationChannel parent = manager.getNotificationChannel(PARENT_CHANNEL_ID);
        if (parent == null) {
            parent = new NotificationChannel(
                    PARENT_CHANNEL_ID,
                    "Guía Samsung de Glosh",
                    NotificationManager.IMPORTANCE_DEFAULT);
            parent.setDescription("Muestra el paso actual mientras configurás tu Samsung.");
            parent.setSound(null, null);
            parent.enableVibration(false);
            manager.createNotificationChannel(parent);
        }

        NotificationChannel conversation = manager.getNotificationChannel(CHANNEL_ID);
        if (conversation == null) {
            conversation = new NotificationChannel(
                    CHANNEL_ID,
                    "Asistente Samsung",
                    NotificationManager.IMPORTANCE_HIGH);
            conversation.setDescription("Burbuja de ayuda para la instalación remota autorizada.");
            conversation.setSound(null, null);
            conversation.enableVibration(false);
            conversation.setConversationId(PARENT_CHANNEL_ID, SHORTCUT_ID);
            manager.createNotificationChannel(conversation);
        }
    }

    private Person gloshPerson() {
        return new Person.Builder()
                .setName("Glosh")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_glosh_bubble))
                .setImportant(true)
                .build();
    }

    private PendingIntent activityIntent(String action, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(action)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static String shortActionLabel(SamsungGuideStep step) {
        return switch (step) {
            case ABOUT_PHONE, SOFTWARE_INFO -> "Ya lo abrí";
            case BUILD_NUMBER -> "Ya está activo";
            case DEVELOPER_OPTIONS -> "Ya estoy ahí";
            case WIRELESS_DEBUGGING -> "Ya la activé";
            case PAIR_DEVICE -> "Ya veo el código";
            case ENTER_CODE -> "Abrir Glosh";
        };
    }
}
