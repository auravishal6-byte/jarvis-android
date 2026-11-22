package com.jarvis.batterysaver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 41;

    private TextView percent, state, temp, monitor;
    private TextView scanStatus, accessibilityStatus;
    private LinearLayout unusedContainer;
    private BatteryRing batteryRing;

    private final int BG = 0xff050b18;
    private final int BLUE = 0xff28baff;
    private final int WHITE = 0xfff3f8ff;
    private final int MUTED = 0xff9aafc8;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

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

        if (accessibilityStatus != null) {
            accessibilityStatus.setText(
                    isAccessibilityEnabled()
                    ? "● Accessibility Enabled"
                    : "● Accessibility Not Enabled");
            accessibilityStatus.setTextColor(
                    isAccessibilityEnabled() ? 0xff48e0a4 : 0xffffbd66);
        }

        if (scanStatus != null) {
            boolean granted = UnusedAppsScanner.hasUsageAccess(this);
            scanStatus.setText(granted
                    ? "● Usage Access Granted"
                    : "● Usage Access Not Granted");
            scanStatus.setTextColor(
                    granted ? 0xff48e0a4 : 0xffffbd66);
        }
    }

    private void makeScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        TextView brand = label("J.A.R.V.I.S.", 27, WHITE, true);
        brand.setLetterSpacing(0.12f);
        root.addView(brand);

        TextView subtitle = label(
                "SMART BATTERY INTELLIGENCE",
                10, BLUE, true);
        subtitle.setLetterSpacing(0.18f);
        addTopSpace(root, subtitle, 3);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = label("● SYSTEM ONLINE", 10, 0xff48e0a4, true);
        statusRow.addView(live);
        root.addView(statusRow, topParams(20));

        // Battery dashboard
        LinearLayout batteryCard = glassCard();
        batteryCard.setGravity(Gravity.CENTER);
        batteryCard.setPadding(dp(16), dp(20), dp(16), dp(20));
        batteryCard.setBackground(gradient(
                0xff102944, 0xff091629, 0xff1a83bd, 22));

        root.addView(batteryCard, topParams(18));

        TextView batteryTitle = label(
                "BATTERY OVERVIEW", 12, BLUE, true);
        batteryTitle.setLetterSpacing(0.14f);
        batteryCard.addView(batteryTitle);

        FrameLayout ringFrame = new FrameLayout(this);
        batteryRing = new BatteryRing();
        ringFrame.addView(batteryRing,
                new FrameLayout.LayoutParams(dp(190), dp(190)));

        percent = label("--%", 42, WHITE, true);
        percent.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams percentParams =
                new FrameLayout.LayoutParams(-1, -1);
        ringFrame.addView(percent, percentParams);

        LinearLayout.LayoutParams ringParams =
                new LinearLayout.LayoutParams(dp(190), dp(190));
        ringParams.topMargin = dp(12);
        batteryCard.addView(ringFrame, ringParams);

        state = label("Checking battery...", 13, BLUE, true);
        batteryCard.addView(state);

        temp = label("Temperature: --", 12, MUTED, false);
        batteryCard.addView(temp, topParams(8));

        // Power management
        addSection(root, "POWER MANAGEMENT",
                "Control and optimize your battery usage.");

        LinearLayout powerCard = glassCard();
        root.addView(powerCard, topParams(12));

        monitor = label("Monitor is stopped.", 12, MUTED, false);
        powerCard.addView(monitor);

        addButton(powerCard, "START SMART BATTERY MONITOR",
                v -> startMonitor(), true);

        addButton(powerCard, "BATTERY SAVER SETTINGS",
                v -> openSetting(Settings.ACTION_BATTERY_SAVER_SETTINGS), false);

        addButton(powerCard, "BATTERY OPTIMIZATION",
                v -> openSetting(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                false);

        addButton(powerCard, "DISPLAY POWER SETTINGS",
                v -> openSetting(Settings.ACTION_DISPLAY_SETTINGS), false);

        addButton(powerCard, "REFRESH BATTERY STATUS",
                v -> refresh(), false);

        // App protection
        addSection(root, "5-DAY APP PROTECTION",
                "Find apps that have not been used for 5 days.");

        LinearLayout protectionCard = glassCard();
        root.addView(protectionCard, topParams(12));

        scanStatus = label("● Usage Access: Checking",
                12, 0xffffbd66, true);
        protectionCard.addView(scanStatus);

        accessibilityStatus = label(
                "● Accessibility: Checking",
                12, 0xffffbd66, true);
        protectionCard.addView(accessibilityStatus, topParams(10));

        addButton(protectionCard, "ENABLE ACCESSIBILITY",
                v -> openAccessibilitySettings(), false);

        addButton(protectionCard, "GRANT USAGE ACCESS",
                v -> openUsageAccessSettings(), false);

        addButton(protectionCard, "SCAN UNUSED APPS",
                v -> scanUnusedApps(), true);

        unusedContainer = new LinearLayout(this);
        unusedContainer.setOrientation(LinearLayout.VERTICAL);
        protectionCard.addView(unusedContainer, topParams(12));

        TextView disclaimer = label(
                "JARVIS provides battery alerts and app protection "
                + "guidance. Android requires user confirmation for "
                + "protected system changes and battery restrictions.",
                10, 0xff7189a5, false);
        disclaimer.setGravity(Gravity.CENTER);
        disclaimer.setPadding(dp(8), dp(18), dp(8), dp(8));
        root.addView(disclaimer);

        setContentView(scroll);
    }

    private void addSection(
            LinearLayout root, String title, String description) {

        TextView heading = label(title, 14, BLUE, true);
        heading.setLetterSpacing(0.10f);
        root.addView(heading, topParams(26));

        TextView sub = label(description, 11, MUTED, false);
        root.addView(sub, topParams(5));
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(gradient(
                0xff13253a, 0xff0b1729, 0xff244969, 18));
        return card;
    }

    private GradientDrawable gradient(
            int first, int second, int stroke, int radius) {

        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{first, second});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private void addButton(
            LinearLayout root,
            String title,
            View.OnClickListener click,
            boolean primary) {

        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setLetterSpacing(0.02f);
        b.setBackground(gradient(
                primary ? 0xff087dbd : 0xff17344f,
                primary ? 0xff07528f : 0xff10263d,
                primary ? 0xff42c5ff : 0xff285274,
                13));
        b.setOnClickListener(click);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(50));
        p.topMargin = dp(10);
        root.addView(b, p);
    }

    private TextView label(
            String text, int size, int color, boolean bold) {

        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);

        if (bold) {
            v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return v;
    }

    private LinearLayout.LayoutParams topParams(int margin) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(margin);
        return p;
    }

    private void addTopSpace(
            LinearLayout root, View view, int margin) {
        root.addView(view, topParams(margin));
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
                request);
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
            scanStatus.setText("Please grant Usage Access first.");
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

        scanStatus.setText(apps.size() + " unused app(s) found.");

        for (UnusedAppsScanner.UnusedApp app : apps) {
            LinearLayout card = glassCard();
            card.setPadding(dp(13), dp(13), dp(13), dp(13));

            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(10);
            unusedContainer.addView(card, cp);

            card.addView(label(app.name, 14, WHITE, true));
            card.addView(label(
                    app.packageName, 10, MUTED, false), topParams(4));

            Button info = new Button(this);
            info.setText("OPEN APP INFO / TRY RESTRICTION");
            info.setTextColor(WHITE);
            info.setTextSize(10);
            info.setAllCaps(false);
            info.setBackground(gradient(
                    0xff07528f, 0xff10263d, 0xff42c5ff, 12));

            info.setOnClickListener(v -> {
                if (!isAccessibilityEnabled()) {
                    scanStatus.setText(
                            "Enable JARVIS Accessibility first.");
                    openAccessibilitySettings();
                    return;
                }

                getSharedPreferences(
                        "jarvis_restriction", MODE_PRIVATE)
                        .edit()
                        .putBoolean("flow_active", true)
                        .putBoolean("battery_page_open", false)
                        .putBoolean("pending_verify", false)
                        .putString("target_package", app.packageName)
                        .apply();

                try {
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    getSharedPreferences(
                            "jarvis_restriction", MODE_PRIVATE)
                            .edit().putBoolean("flow_active", false).apply();
                    scanStatus.setText("Could not open App Info.");
                }
            });

            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(-1, dp(46));
            bp.topMargin = dp(9);
            card.addView(info, bp);
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        String expected = new android.content.ComponentName(
                this, BackgroundRestrictionAccessibilityService.class)
                .flattenToString();

        return enabled != null && enabled.contains(expected);
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            openSetting(Settings.ACTION_SETTINGS);
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

        if (batteryRing != null) {
            batteryRing.setProgress(pct);
        }

        state.setText(
                plugged != 0
                        ? "CHARGING / POWER CONNECTED"
                        : (pct >= 0 && pct <= 20
                        ? "LOW BATTERY • SAVE POWER"
                        : "BATTERY DISCHARGING"));

        temp.setText(
                t >= 0
                        ? String.format(
                        Locale.getDefault(),
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

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density);
    }

    private class BatteryRing extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private int progress = 0;

        BatteryRing() {
            super(MainActivity.this);
        }

        void setProgress(int value) {
            progress = Math.max(0, Math.min(100, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = dp(77);
            float stroke = dp(9);

            oval.set(cx - radius, cy - radius,
                    cx + radius, cy + radius);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xff263c53);
            canvas.drawArc(oval, 135, 270, false, paint);

            paint.setShader(null);
            paint.setColor(BLUE);
            canvas.drawArc(oval, 135,
                    270f * progress / 100f, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff9aafc8);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("BATTERY", cx, cy + dp(35), paint);
        }
    }
}
