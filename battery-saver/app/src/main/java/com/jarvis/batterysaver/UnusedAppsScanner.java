package com.jarvis.batterysaver;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UnusedAppsScanner {

    private UnusedAppsScanner() {}

    public static class UnusedApp {
        public final String name;
        public final String packageName;

        public UnusedApp(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        if (appOps == null) return false;

        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );

        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static List<UnusedApp> findUnusedApps(
            Context context, int days) {

        List<UnusedApp> result = new ArrayList<>();

        if (days < 1 || !hasUsageAccess(context)) {
            return result;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - (days * 24L * 60L * 60L * 1000L);

        UsageStatsManager usageManager =
                (UsageStatsManager) context.getSystemService(
                        Context.USAGE_STATS_SERVICE);

        if (usageManager == null) return result;

        Map<String, Long> lastUsed = new HashMap<>();

        List<UsageStats> stats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                cutoff,
                now
        );

        if (stats != null) {
            for (UsageStats item : stats) {
                String pkg = item.getPackageName();
                long time = item.getLastTimeUsed();

                if (pkg != null) {
                    Long old = lastUsed.get(pkg);
                    if (old == null || time > old) {
                        lastUsed.put(pkg, time);
                    }
                }
            }
        }

        PackageManager pm = context.getPackageManager();

        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launchableApps =
                pm.queryIntentActivities(launchIntent, 0);

        for (ResolveInfo info : launchableApps) {
            if (info.activityInfo == null) continue;

            String pkg = info.activityInfo.packageName;

            if (pkg == null || pkg.equals(context.getPackageName())) {
                continue;
            }

            try {
                ApplicationInfo appInfo =
                        pm.getApplicationInfo(pkg, 0);

                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }

                // Newly installed apps are not marked unused.
                long installed = pm.getPackageInfo(pkg, 0)
                        .firstInstallTime;

                if (installed > cutoff) continue;

                Long used = lastUsed.get(pkg);

                // No recorded use in the selected period.
                if (used == null || used < cutoff) {
                    String label = pm.getApplicationLabel(appInfo).toString();

                    result.add(new UnusedApp(label, pkg));
                }

            } catch (PackageManager.NameNotFoundException ignored) {
                // App was removed while scanning.
            }
        }

        Collections.sort(result, new Comparator<UnusedApp>() {
            @Override
            public int compare(UnusedApp a, UnusedApp b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        return result;
    }
}
