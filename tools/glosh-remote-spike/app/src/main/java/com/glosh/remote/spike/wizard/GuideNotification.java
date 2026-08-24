package com.glosh.remote.spike.wizard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.glosh.remote.spike.MainActivity;

public final class GuideNotification {
    private static final String CHANNEL_ID = "glosh_remote_guide";
    private static final int NOTIFICATION_ID = 7399;
    private final Context context;
    private final NotificationManager manager;

    public GuideNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Guía de instalación Glosh",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    public void show(String progress, String copy) {
        if (!manager.areNotificationsEnabled()) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 7399, open, flags);
        Notification.Action back = new Notification.Action.Builder(
                android.R.drawable.ic_menu_revert,
                "Volver a Glosh",
                pending).build();
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Glosh · " + progress)
                .setContentText(copy)
                .setStyle(new Notification.BigTextStyle().bigText(copy))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .addAction(back)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    public void clear() {
        manager.cancel(NOTIFICATION_ID);
    }
}
