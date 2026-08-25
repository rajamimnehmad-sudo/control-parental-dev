package com.glosh.remote.spike.wizard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.glosh.remote.spike.MainActivity;
import com.glosh.remote.spike.guide.state.GuideStage;

/** Persistent mirror of the floating coach while the customer remains inside Android Settings. */
public final class GuideNotification {
    private static final String CHANNEL_ID = "glosh_remote_guide";
    private static final int NOTIFICATION_ID = 7399;
    private final Context context;
    private final NotificationManager manager;

    public GuideNotification(Context context) {
        this.context = context.getApplicationContext();
        manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Guía de instalación Glosh",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Pasos simples para preparar la conexión remota de Glosh.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    public void show(GuideStage stage, String instruction) {
        show(GuidePresentation.forStage(stage, instruction));
    }

    /** Legacy compatibility for existing call sites while the guided flow is being migrated. */
    public void show(String progress, String copy) {
        String title = progress == null || progress.trim().isEmpty()
                ? "Glosh Remote"
                : progress;
        show(new GuidePresentation(
                1,
                4,
                title,
                copy == null ? "Seguí la indicación en Ajustes." : copy,
                GuidePresentation.Cue.TAP,
                false));
    }

    public void show(GuidePresentation presentation) {
        if (!manager.areNotificationsEnabled()) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open, flags);
        Notification.Action back = new Notification.Action.Builder(
                android.R.drawable.ic_menu_view,
                "Abrir Glosh",
                pending).build();
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Glosh · " + presentation.progressLabel())
                .setContentText(presentation.title())
                .setSubText("Guía de conexión")
                .setStyle(new Notification.BigTextStyle().bigText(
                        presentation.title() + "\n" + presentation.body()))
                .setContentIntent(pending)
                .setOnlyAlertOnce(true)
                .setOngoing(!presentation.terminal())
                .setAutoCancel(presentation.terminal())
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(
                        presentation.totalSteps(),
                        presentation.progressValue(),
                        false)
                .addAction(back)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    public void clear() {
        manager.cancel(NOTIFICATION_ID);
    }
}
