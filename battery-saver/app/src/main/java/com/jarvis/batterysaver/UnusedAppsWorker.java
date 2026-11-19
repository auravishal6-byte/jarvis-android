package com.jarvis.batterysaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class UnusedAppsWorker extends Worker {

    private static final String CHANNEL_ID = "jarvis_unused_apps";

    public UnusedAppsWorker(
            Context context,
            WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {

        Context context = getApplicationContext();

        if (!UnusedAppsScanner.hasUsageAccess(context)) {
            return Result.success();
        }

        List<UnusedAppsScanner.UnusedApp> apps =
                UnusedAppsScanner.findUnusedApps(context, 5);

        if (apps.isEmpty()) {
            return Result.success();
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            return Result.success();
        }

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "JARVIS Unused Apps",
                            NotificationManager.IMPORTANCE_DEFAULT);

            channel.setDescription(
                    "Alerts about apps unused for five days");

            manager.createNotificationChannel(channel);
        }

        String message = apps.size()
                + " app(s) have not been used in the last 5 days.";

        Notification notification;

        if (Build.VERSION.SDK_INT >= 26) {
            notification = new Notification.Builder(
                    context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("JARVIS Battery Protection")
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(message
                                    + " Review them and choose battery "
                                    + "restriction manually."))
                    .setAutoCancel(true)
                    .build();
        } else {
            notification = new Notification.Builder(context)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("JARVIS Battery Protection")
                    .setContentText(message)
                    .setAutoCancel(true)
                    .build();
        }

        manager.notify(5105, notification);

        return Result.success();
    }
}
