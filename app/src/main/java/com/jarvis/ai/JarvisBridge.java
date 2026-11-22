package com.jarvis.ai;

import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class JarvisBridge {
    private final Activity activity;
    private final WebView webView;
    private SpeechRecognizer recognizer;
    private String pendingAction;

    private static final int MIC_PERMISSION = 2001;
    private static final int CAMERA_PERMISSION = 2002;

    public JarvisBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void startVoiceRecognition() {
        activity.runOnUiThread(() -> {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            } else {
                startListening();
            }
        });
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            callback("onJarvisVoiceStatus", "Speech recognition available nahi hai.");
            return;
        }

        stopListening();

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    callback("onJarvisVoiceStatus", "Sun raha hoon...");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    callback("onJarvisVoiceStatus", "Processing...");
                }
                @Override public void onError(int error) {
                    stopListening();
                    callback("onJarvisVoiceStatus", "Voice nahi samajh aayi. Dobara try karein.");
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    stopListening();
                    if (matches != null && !matches.isEmpty()) {
                        callback("onJarvisVoiceResult", matches.get(0));
                    } else {
                        callback("onJarvisVoiceStatus", "Kuch sunai nahi diya.");
                    }
                }
                @Override public void onPartialResults(Bundle results) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizer.startListening(intent);
        } catch (Exception e) {
            stopListening();
            callback("onJarvisVoiceStatus", "Microphone start nahi ho paya.");
        }
    }

    @JavascriptInterface
    public void stopVoiceRecognition() {
        activity.runOnUiThread(this::stopListening);
    }

    public void stopListening() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            recognizer.destroy();
            recognizer = null;
        }
    }

    @JavascriptInterface
    public void performAction(String action) {
        activity.runOnUiThread(() -> {
            if ("torch_on".equals(action) || "torch_off".equals(action)) {
                if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingAction = action;
                    activity.requestPermissions(
                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
                    return;
                }
            }
            executeAction(action);
        });
    }

    private void executeAction(String action) {
        String result;
        try {
            if ("torch_on".equals(action) || "torch_off".equals(action)) {
                CameraManager camera = (CameraManager)
                    activity.getSystemService(Context.CAMERA_SERVICE);
                String[] ids = camera.getCameraIdList();
                String flashId = null;

                for (String id : ids) {
                    if (camera.getCameraCharacteristics(id).get(
                            android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                        flashId = id;
                        break;
                    }
                }

                if (flashId == null) {
                    result = "Is phone mein torch available nahi hai.";
                } else {
                    camera.setTorchMode(flashId, "torch_on".equals(action));
                    result = "torch_on".equals(action) ? "Torch chalu ho gayi." : "Torch band ho gayi.";
                }
            } else if ("volume_up".equals(action) || "volume_down".equals(action)) {
                AudioManager audio = (AudioManager)
                    activity.getSystemService(Context.AUDIO_SERVICE);
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    "volume_up".equals(action)
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
                result = "Volume adjust kar diya.";
            } else if ("battery".equals(action)) {
                Intent battery = activity.registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery == null) {
                    result = "Battery status nahi mil paya.";
                } else {
                    int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int percent = scale > 0 ? (level * 100 / scale) : level;
                    result = "Battery " + percent + "% hai.";
                }
            } else {
                result = "Ye command abhi supported nahi hai.";
            }
        } catch (Exception e) {
            result = "Action failed: " + e.getMessage();
        }
        callback("onJarvisActionResult", result);
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == MIC_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                callback("onJarvisVoiceStatus", "Microphone permission nahi mili.");
            }
        } else if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    pendingAction != null) {
                String action = pendingAction;
                pendingAction = null;
                executeAction(action);
            } else {
                pendingAction = null;
                callback("onJarvisActionResult", "Torch ke liye camera permission zaroori hai.");
            }
        }
    }

    private void callback(String function, String value) {
        String safe = JSONObject.quote(value);
        webView.post(() -> webView.evaluateJavascript(
            "if(window." + function + ")window." + function + "(" + safe + ");", null));
    }

    public void release() {
        stopListening();
    }
}
