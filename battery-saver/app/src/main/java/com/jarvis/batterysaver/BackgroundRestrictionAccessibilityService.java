package com.jarvis.batterysaver;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BackgroundRestrictionAccessibilityService
        extends AccessibilityService {

    private static final String PREF = "jarvis_restriction";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        android.content.SharedPreferences prefs =
                getSharedPreferences(PREF, MODE_PRIVATE);

        if (!prefs.getBoolean("flow_active", false) ||
                prefs.getBoolean("pending_verify", false)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !isTargetAppVisible(root)) return;

        int step = prefs.getInt("flow_step", 0);

        if (step == 0) {
            clickNext(root, "Battery usage", 1);
        } else if (step == 1) {
            clickNext(root, "Background power control", 2);
        } else if (step == 2) {
            applyRestriction(root);
        }
    }

    private void clickNext(
            AccessibilityNodeInfo root, String text, int nextStep) {

        AccessibilityNodeInfo label = findText(root, text);
        if (label == null) return;

        AccessibilityNodeInfo target = findClickableAncestor(label);

        if (target == null ||
                !target.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK)) {
            finishFlow(false);
            return;
        }

        getSharedPreferences(PREF, MODE_PRIVATE)
                .edit().putInt("flow_step", nextStep).apply();
    }

    private void applyRestriction(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo label =
                findText(root, "Restrict background power usage");

        if (label == null) return;

        AccessibilityNodeInfo control = findAssociatedControl(label);
        if (control == null) return;

        if (control.isChecked()) {
            finishFlow(true);
            return;
        }

        AccessibilityNodeInfo target = findClickableAncestor(control);

        if (target == null ||
                !target.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK)) {
            finishFlow(false);
            return;
        }

        getSharedPreferences(PREF, MODE_PRIVATE)
                .edit().putBoolean("pending_verify", true).apply();

        Toast.makeText(this, "Checking restriction status...",
                Toast.LENGTH_SHORT).show();

        handler.postDelayed(this::verifyRestriction, 1200);
    }

    private boolean isTargetAppVisible(AccessibilityNodeInfo root) {
        String packageName = getSharedPreferences(PREF, MODE_PRIVATE)
                .getString("target_package", null);

        if (packageName == null) return false;

        try {
            CharSequence appLabel = getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(
                                    packageName, 0));

            return containsExactText(root, normalize(appLabel.toString()));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsExactText(
            AccessibilityNodeInfo node, String expected) {
        if (node == null) return false;

        if (node.getText() != null &&
                normalize(node.getText().toString()).equals(expected)) {
            return true;
        }

        if (node.getContentDescription() != null &&
                normalize(node.getContentDescription().toString())
                        .equals(expected)) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsExactText(node.getChild(i), expected)) return true;
        }

        return false;
    }

    private AccessibilityNodeInfo findText(
            AccessibilityNodeInfo node, String expected) {
        if (node == null) return null;

        if (matches(node.getText(), expected) ||
                matches(node.getContentDescription(), expected)) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found =
                    findText(node.getChild(i), expected);
            if (found != null) return found;
        }

        return null;
    }

    private boolean matches(CharSequence value, String expected) {
        return value != null &&
                normalize(value.toString()).equals(normalize(expected));
    }

    private AccessibilityNodeInfo findClickableAncestor(
            AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;

        for (int i = 0; current != null && i < 7; i++) {
            if (current.isClickable()) return current;
            current = current.getParent();
        }

        return node;
    }

    private AccessibilityNodeInfo findAssociatedControl(
            AccessibilityNodeInfo label) {
        AccessibilityNodeInfo current = label;

        for (int i = 0; current != null && i < 4; i++) {
            List<AccessibilityNodeInfo> controls = new ArrayList<>();
            collectCheckable(current, controls);

            if (controls.size() == 1) return controls.get(0);
            if (controls.size() > 1) return null;

            current = current.getParent();
        }

        return null;
    }

    private void collectCheckable(
            AccessibilityNodeInfo node,
            List<AccessibilityNodeInfo> found) {
        if (node == null || found.size() > 1) return;

        if (node.isCheckable()) {
            found.add(node);
            return;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectCheckable(node.getChild(i), found);
            if (found.size() > 1) return;
        }
    }

    private void verifyRestriction() {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        boolean verified = root != null &&
                isTargetAppVisible(root);

        if (verified) {
            AccessibilityNodeInfo label = findText(
                    root, "Restrict background power usage");

            AccessibilityNodeInfo control =
                    label == null ? null : findAssociatedControl(label);

            verified = control != null && control.isChecked();
        }

        finishFlow(verified);
    }

    private void finishFlow(boolean verified) {
        getSharedPreferences(PREF, MODE_PRIVATE)
                .edit()
                .putBoolean("flow_active", false)
                .putBoolean("pending_verify", false)
                .putInt("flow_step", 0)
                .apply();

        Toast.makeText(this,
                verified ? "Restriction verified ON."
                        : "Could not verify. Please check manually.",
                Toast.LENGTH_LONG).show();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
