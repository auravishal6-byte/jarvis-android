package com.jarvis.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStream;
import android.webkit.JavascriptInterface;
import android.util.Base64;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 1001;
    private static final int PERM_REQUEST = 1002;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private boolean captureMode;
    private boolean pendingCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new PdfBridge(), "Android");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                captureMode = params.isCaptureEnabled();
                if (captureMode) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                            && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        pendingCamera = true;
                        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERM_REQUEST);
                        return true;
                    }
                    launchCamera();
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    String[] types = params.getAcceptTypes();
                    if (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) {
                        intent.setType(types[0]);
                    } else {
                        intent.setType("*/*");
                    }
                    startPicker(intent);
                }
                return true;
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void startPicker(Intent intent) {
        try {
            startActivityForResult(intent, FILE_REQUEST);
        } catch (Exception e) {
            failCallback();
        }
    }

    private void launchCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "jarvis_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        try {
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            cameraUri = null;
        }
        if (cameraUri == null) {
            failCallback();
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), "photo", cameraUri));
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(intent, 0)) {
            grantUriPermission(ri.activityInfo.packageName, cameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        try {
            startActivityForResult(intent, FILE_REQUEST);
        } catch (Exception e) {
            cleanupCamera(true);
            failCallback();
        }
    }

    private void cleanupCamera(boolean deleteRow) {
        if (cameraUri != null) {
            try {
                revokeUriPermission(cameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (deleteRow) {
                    getContentResolver().delete(cameraUri, null, null);
                }
            } catch (Exception ignored) {
            }
            cameraUri = null;
        }
    }


    private class PdfBridge {
        @JavascriptInterface
        public String savePdf(String base64, String filename) {
            try {
                if (base64 == null || base64.length() > 20000000) {
                    return "ERROR: PDF too large.";
                }

                String name = filename == null ? "JARVIS_Document.pdf" : filename;
                name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";

                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (bytes.length == 0 || bytes.length > 15 * 1024 * 1024) {
                    return "ERROR: Invalid PDF size.";
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/JARVIS");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "ERROR: Cannot create download.";

                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("Cannot open PDF.");
                        out.write(bytes);
                    }

                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "ERROR: Storage unavailable.";
                    if (!dir.exists() && !dir.mkdirs()) {
                        return "ERROR: Cannot create folder.";
                    }
                    try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
                        out.write(bytes);
                    }
                }
                return "OK:" + name;
            } catch (Exception e) {
                return "ERROR: " + e.getMessage();
            }
        }
    }

    private void failCallback() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQUEST) {
            if (pendingCamera && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                failCallback();
            }
            pendingCamera = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (captureMode && cameraUri != null) {
                    results = new Uri[]{cameraUri};
                }
            }
            if (captureMode) {
                cleanupCamera(results == null);
            }
            captureMode = false;
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
