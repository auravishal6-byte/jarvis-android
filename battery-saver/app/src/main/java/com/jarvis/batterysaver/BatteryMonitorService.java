package com.jarvis.batterysaver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

public class BatteryMonitorService extends Service {
    private static final String CHANNEL = "jarvis_battery_monitor";
    private static final int ONGOING_ID = 7001;
    private static final int LOW_ID = 7002;
    private boolean lowAlertSent = false;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (plugged != 0 || pct > 23) lowAlertSent = false;
            if (pct >= 0 && pct <= 20 && plugged == 0 && !lowAlertSent) {
                lowAlertSent = true;
                sendLowAlert(pct);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, f);
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ONGOING_ID, ongoingNotification());
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Battery monitoring", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("JARVIS ongoing battery monitoring and low-battery alerts");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification.Builder builder() {
        return Build.VERSION.SDK_INT >= 26 ?
            new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
    }

    private Notification ongoingNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        return builder().setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("JARVIS Battery Monitor")
            .setContentText("Smart low-battery monitoring is active")
            .setContentIntent(pi).setOngoing(true).build();
    }

    private void sendLowAlert(int pct) {
        Intent settings = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        PendingIntent pi = PendingIntent.getActivity(this, 2, settings,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification n = builder().setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("JARVIS: Battery is low")
            .setContentText("Battery is " + pct + "%. Tap to open Battery Saver settings.")
            .setColor(Color.rgb(35, 175, 235)).setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_lock_power_off, "OPEN BATTERY SAVER", pi)
            .build();
        getSystemService(NotificationManager.class).notify(LOW_ID, n);
    }

    @Override public void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
