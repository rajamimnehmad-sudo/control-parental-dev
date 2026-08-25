package com.glosh.remote.spike.wizard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.glosh.remote.spike.MainActivity;

/** Persistent native mirror of the Samsung guide while Settings is in front. */
public final class GuideNotification {
    private static final String CHANNEL_ID = "glosh_remote_guide";
    private static final int NOTIFICATION_ID = 7399;
    private static final int REQUEST_OPEN = 73990;
    private static final int REQUEST_BACK = 73991;
    private static final int REQUEST_NEXT = 73992;

    private final Context context;
    private final NotificationManager manager;

    public GuideNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Guía Samsung de Glosh",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Muestra el paso actual mientras configurás tu Samsung.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    public void showStep(SamsungGuideStep step) {
        showStep(step, null);
    }

    public void showStep(SamsungGuideStep step, String overrideBody) {
        if (step == null || !manager.areNotificationsEnabled()) {
            return;
        }
        PendingIntent open = activityIntent(MainActivity.ACTION_GUIDE_OPEN, REQUEST_OPEN);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Glosh · Paso " + step.number() + " de " + SamsungGuideStep.TOTAL_STEPS)
                .setContentText(step.title())
                .setSubText("Guía Samsung")
                .setStyle(new Notification.BigTextStyle().bigText(
                        step.title() + "\n" + (overrideBody == null ? step.instruction() : overrideBody)))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(SamsungGuideStep.TOTAL_STEPS, step.number(), false);

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

    public void showWaiting(SamsungGuideStep step, String message) {
        showStep(step, message == null ? "Preparando la conexión segura…" : message);
    }

    public void clear() {
        manager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent activityIntent(String action, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(action)
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
