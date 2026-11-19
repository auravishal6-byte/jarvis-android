package com.jarvis.batterysaver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 41;

    private TextView percent, state, temp, monitor;
    private TextView scanStatus;
    private LinearLayout unusedContainer;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);

        getWindow().setStatusBarColor(Color.rgb(5, 12, 26));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 26));

        makeScreen();
        refresh();
        scheduleUnusedAppScan();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanStatus != null) {
            scanStatus.setText(
                    UnusedAppsScanner.hasUsageAccess(this)
                            ? "Usage Access: GRANTED"
                            : "Usage Access: NOT GRANTED"
            );
        }
    }

    private void makeScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(5, 12, 26));
        scroll.addView(root);

        TextView brand = label("J.A.R.V.I.S.", 28, 0xff41cdff, true);
        brand.setLetterSpacing(.16f);
        root.addView(brand);

        TextView sub = label(
                "BATTERY INTELLIGENCE SYSTEM",
                11, 0xffb4d7ec, false);
        sub.setLetterSpacing(.12f);
        root.addView(sub);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(18), dp(22), dp(18), dp(22));
        panel.setBackground(shape(0xff0b1d34, 0xff146996, 2, 22));

        LinearLayout.LayoutParams pp =
                new LinearLayout.LayoutParams(-1, dp(205));
        pp.topMargin = dp(26);
        root.addView(panel, pp);

        percent = label("--%", 58, Color.WHITE, true);
        panel.addView(percent);

        state = label("Checking battery…", 14, 0xff6fd2ff, false);
        panel.addView(state);

        temp = label("Temperature: --", 13, 0xffd5e4ef, false);
        LinearLayout.LayoutParams tp =
                new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(12);
        panel.addView(temp, tp);

        TextView heading = label(
                "POWER MANAGEMENT", 12, 0xff41cdff, true);
        heading.setLetterSpacing(.12f);

        LinearLayout.LayoutParams hp =
                new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(25);
        root.addView(heading, hp);

        monitor = label(
                "Monitor is stopped.", 14, 0xffd5e4ef, false);

        LinearLayout.LayoutParams mp =
                new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(9);
        root.addView(monitor, mp);

        addButton(root, "START SMART BATTERY MONITOR",
                v -> startMonitor());

        addButton(root, "OPEN BATTERY SAVER SETTINGS",
                v -> openSetting(Settings.ACTION_BATTERY_SAVER_SETTINGS));

        addButton(root, "BATTERY OPTIMIZATION SETTINGS",
                v -> openSetting(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));

        addButton(root, "DISPLAY POWER SETTINGS",
                v -> openSetting(Settings.ACTION_DISPLAY_SETTINGS));

        addButton(root, "REFRESH BATTERY STATUS",
                v -> refresh());

        TextView unusedHeading = label(
                "5-DAY UNUSED APPS PROTECTION",
                16, 0xff41cdff, true);

        LinearLayout.LayoutParams uh =
                new LinearLayout.LayoutParams(-1, -2);
        uh.topMargin = dp(30);
        root.addView(unusedHeading, uh);

        TextView explanation = label(
                "JARVIS checks which launchable apps have not been used "
                + "for the last 5 days. Grant Usage Access to begin.",
                12, 0xffb4d7ec, false);

        LinearLayout.LayoutParams ep =
                new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(8);
        root.addView(explanation, ep);

        scanStatus = label(
                "Usage Access: CHECKING",
                13, 0xffffcc66, true);

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(12);
        root.addView(scanStatus, sp);

        addButton(root, "GRANT USAGE ACCESS",
                v -> openUsageAccessSettings());

        addButton(root, "SCAN UNUSED APPS (5 DAYS)",
                v -> scanUnusedApps());

        unusedContainer = new LinearLayout(this);
        unusedContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams up =
                new LinearLayout.LayoutParams(-1, -2);
        up.topMargin = dp(12);
        root.addView(unusedContainer, up);

        TextView disclaimer = label(
                "At 20% battery, JARVIS sends an alert with a shortcut "
                + "to Battery Saver. Android requires the user to confirm "
                + "protected system changes. JARVIS cannot silently force "
                + "Restricted battery mode on other apps. Review each "
                + "app and confirm its battery restriction in App Info.",
                11, 0xff96adc3, false);

        LinearLayout.LayoutParams np =
                new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(20);
        root.addView(disclaimer, np);

        setContentView(scroll);
    }


    private void scheduleUnusedAppScan() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        UnusedAppsWorker.class,
                        24, TimeUnit.HOURS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "JARVIS_UNUSED_APP_SCAN",
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception e) {
            openSetting(Settings.ACTION_SETTINGS);
        }
    }

    private void scanUnusedApps() {
        if (!UnusedAppsScanner.hasUsageAccess(this)) {
            scanStatus.setText(
                    "Please grant Usage Access first.");
            openUsageAccessSettings();
            return;
        }

        scanStatus.setText("Scanning apps...");

        unusedContainer.removeAllViews();

        List<UnusedAppsScanner.UnusedApp> apps =
                UnusedAppsScanner.findUnusedApps(this, 5);

        if (apps.isEmpty()) {
            scanStatus.setText(
                    "No unused apps found, or no usage data is available.");
            return;
        }

        scanStatus.setText(
                apps.size() + " unused app(s) found.");

        for (UnusedAppsScanner.UnusedApp app : apps) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(
                    shape(0xff0b1d34, 0xff146996, 1, 14));

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(10);
            unusedContainer.addView(card, cp);

            TextView name = label(
                    app.name, 15, Color.WHITE, true);
            card.addView(name);

            TextView detail = label(
                    app.packageName,
                    10, 0xff96adc3, false);

            LinearLayout.LayoutParams dp1 =
                    new LinearLayout.LayoutParams(-1, -2);
            dp1.topMargin = dp(5);
            card.addView(detail, dp1);

            Button info = new Button(this);
            info.setText("OPEN APP INFO / BATTERY SETTINGS");
            info.setTextColor(Color.WHITE);
            info.setTextSize(11);
            info.setAllCaps(false);
            info.setBackground(
                    shape(0xff0c4169, 0xff2bb1e6, 1, 12));

            info.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    scanStatus.setText(
                            "Could not open App Info.");
                }
            });

            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(-1, dp(48));
            bp.topMargin = dp(8);
            card.addView(info, bp);
        }
    }

    private void startMonitor() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
            monitor.setText(
                    "Allow notifications, then tap START again.");
            return;
        }

        Intent i = new Intent(this, BatteryMonitorService.class);

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }

        monitor.setText(
                "Smart monitor started. Keep its notification enabled "
                + "for low-battery alerts.");
    }

    private void refresh() {
        Intent i = registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (i == null || percent == null) return;

        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);

        int pct = level >= 0 && scale > 0
                ? Math.round(level * 100f / scale) : -1;

        int t = i.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, -1);

        int plugged = i.getIntExtra(
                BatteryManager.EXTRA_PLUGGED, 0);

        percent.setText(pct >= 0 ? pct + "%" : "--%");

        state.setText(
                plugged != 0
                        ? "CHARGING / POWER CONNECTED"
                        : (pct >= 0 && pct <= 20
                        ? "LOW BATTERY • SAVE POWER"
                        : "BATTERY DISCHARGING"));

        temp.setText(
                t >= 0
                        ? String.format(
                        java.util.Locale.getDefault(),
                        "Temperature: %.1f °C", t / 10f)
                        : "Temperature: Not available");
    }

    private void openSetting(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void addButton(
            LinearLayout root,
            String title,
            View.OnClickListener click) {

        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(
                shape(0xff0c4169, 0xff2bb1e6, 1, 14));
        b.setOnClickListener(click);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(10);
        root.addView(b, p);
    }

    private TextView label(
            String s, int size, int color, boolean bold) {

        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);

        if (bold) {
            v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return v;
    }

    private GradientDrawable shape(
            int color, int stroke, int width, int radius) {

        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));

        if (width > 0) {
            d.setStroke(dp(width), stroke);
        }

        return d;
    }

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density);
    }
}
