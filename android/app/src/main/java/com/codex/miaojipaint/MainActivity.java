package com.codex.miaojipaint;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String SITE_HOME = "https://omnidoc.com.cn/";
    private static final String SITE_LOGIN = "https://omnidoc.com.cn/?auth=login&ret=/";
    private static final String SITE_DEVELOPER = "https://omnidoc.com.cn/developer";
    private static final String GITHUB_REPO = "c6823821-sketch/miaoji-paint-android";
    private static final String GITHUB_REPO_URL = "https://github.com/" + GITHUB_REPO;
    private static final String GITHUB_LATEST_RELEASE =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String API_ROOT = "https://omnidoc.com.cn/api/v1";
    private static final String APP_PREFS = "miaoji_paint";
    private static final String KEY_API = "api_key";
    private static final String KEY_PENDING_TASK = "pending_task";
    private static final int SAVE_PERMISSION_REQUEST = 41;
    private static final int FILE_CHOOSER_REQUEST = 42;
    private static final int MAX_POLL_MILLIS = 45 * 60 * 1000;

    private WebView webView;
    private SharedPreferences preferences;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<String> pollingTasks =
            Collections.synchronizedSet(new HashSet<String>());

    private String pendingSaveUrl;
    private String pendingSaveMime;
    private String pendingSaveName;
    private String pendingSaveCallId;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        configureSystemBars();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F4F1E8"));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        configureWebView(webView);
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#F4F1E8"));
        window.setNavigationBarColor(Color.parseColor("#F4F1E8"));
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MiaoJiPaint/1.0");
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择参考图片"),
                            FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileChooserCallback = null;
                    return false;
                }
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/android_asset/")) {
                    return false;
                }
                openExternal(uri);
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                Uri uri = Uri.parse(url);
                if ("file".equalsIgnoreCase(uri.getScheme())
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/android_asset/")) {
                    return false;
                }
                openExternal(uri);
                return true;
            }
        });
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void emit(String callId, String event, String message, JSONObject data) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("callId", callId);
            payload.put("event", event);
            payload.put("message", message == null ? "" : message);
            payload.put("data", data == null ? new JSONObject() : data);
        } catch (JSONException ignored) {
            return;
        }
        final String json = payload.toString();
        runOnUiThread(() -> {
            if (webView == null) return;
            webView.evaluateJavascript(
                    "window.__miaoNative(" + JSONObject.quote(json) + ");",
                    null
            );
        });
    }

    private String apiKey() {
        return preferences.getString(KEY_API, "");
    }

    private void showToast(String text) {
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public boolean hasKey() {
            return !apiKey().trim().isEmpty();
        }

        @JavascriptInterface
        public void saveKey(String key) {
            String clean = key == null ? "" : key.trim();
            preferences.edit().putString(KEY_API, clean).apply();
        }

        @JavascriptInterface
        public void clearKey() {
            preferences.edit().remove(KEY_API).apply();
        }

        @JavascriptInterface
        public void openRecharge() {
            openExternal(Uri.parse(SITE_LOGIN));
        }

        @JavascriptInterface
        public void openLogin() {
            openExternal(Uri.parse(SITE_LOGIN));
        }

        @JavascriptInterface
        public void openGithub() {
            openExternal(Uri.parse(GITHUB_REPO_URL));
        }

        @JavascriptInterface
        public String currentVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception error) {
                return "1.9.0";
            }
        }

        @JavascriptInterface
        public void openUpdate(String url) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                        || !(host.endsWith("github.com") || host.endsWith("githubusercontent.com"))) {
                    throw new IllegalArgumentException("更新地址不受信任");
                }
                openExternal(uri);
            } catch (Exception error) {
                showToast("更新地址无效");
            }
        }

        @JavascriptInterface
        public void checkForUpdate(String callId) {
            executor.execute(() -> runUpdateCheck(callId));
        }

        @JavascriptInterface
        public void openDeveloper() {
            openExternal(Uri.parse(SITE_DEVELOPER));
        }

        @JavascriptInterface
        public void openApiDocs() {
            openExternal(Uri.parse(SITE_HOME + "docs/api"));
        }

        @JavascriptInterface
        public void loadModels(String callId) {
            executor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(SITE_HOME + "api/app/bootstrap?page=home");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "MiaoJiPaint/1.0");

                    int status = connection.getResponseCode();
                    String body = readResponse(connection);
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("模型列表加载失败（HTTP " + status + "）");
                    }
                    JSONObject root = new JSONObject(body);
                    JSONObject data = root.optJSONObject("data");
                    JSONArray items = data == null ? null : data.optJSONArray("ai_image_models");
                    if (items == null) throw new IllegalStateException("模型列表格式异常");
                    JSONObject payload = new JSONObject();
                    payload.put("items", items);
                    emit(callId, "models", "模型列表已更新", payload);
                } catch (Exception error) {
                    emit(callId, "models_error", friendlyError(error), null);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }

        @JavascriptInterface
        public void generate(String prompt, String model, String size, String resolution,
                             String quality, int count, String referenceImagesJson,
                             String callId) {
            final String key = apiKey().trim();
            if (key.isEmpty()) {
                emit(callId, "error", "请先填写妙极 API Key", errorData("NO_API_KEY", false));
                return;
            }
            if (prompt == null || prompt.trim().isEmpty()) {
                emit(callId, "error", "请先输入要生成的内容", null);
                return;
            }
            if (prompt.length() > 10000) {
                emit(callId, "error", "提示词不能超过 10000 字", null);
                return;
            }

            emit(callId, "status", "请求已收到，正在准备照片…", null);
            executor.execute(() -> runGeneration(
                    key,
                    prompt.trim(),
                    safe(model, "gpt-image-2.5-flare"),
                    safe(size, "1:1"),
                    safe(resolution, "1k"),
                    quality == null ? "" : quality.trim(),
                    Math.max(1, count),
                    referenceImagesJson == null ? "[]" : referenceImagesJson,
                    callId
            ));
        }

        @JavascriptInterface
        public String getPendingTask() {
            JSONObject pending = loadPendingTask();
            return pending == null ? "" : pending.toString();
        }

        @JavascriptInterface
        public void resumePendingTask(String callId) {
            final String key = apiKey().trim();
            JSONObject pending = loadPendingTask();
            String taskId = pending == null ? "" : pending.optString("taskId", "");
            if (taskId.isEmpty()) {
                emit(callId, "pending_none", "没有等待恢复的任务", null);
                return;
            }
            emit(callId, "resume", "发现已提交的云端任务", pending);
            if (key.isEmpty()) {
                emit(callId, "error", "请重新填写妙极 API Key 后恢复任务",
                        errorData("NO_API_KEY", false));
                return;
            }
            final JSONObject task = pending;
            executor.execute(() -> {
                if ("succeeded".equals(task.optString("state", ""))
                        && !task.optString("imageUrl", "").isEmpty()) {
                    emit(callId, "result", "已恢复上次的生成结果",
                            resultFromPending(task));
                    return;
                }
                pollTask(key, taskId, callId, task);
            });
        }

        @JavascriptInterface
        public void clearPendingTask(String taskId) {
            clearPendingTaskInternal(taskId);
        }

        @JavascriptInterface
        public void refreshHistoryItem(String taskId, String callId) {
            String key = apiKey().trim();
            if (key.isEmpty()) {
                emit(callId, "history_error", "请先填写妙极 API Key", null);
                return;
            }
            if (taskId == null || taskId.trim().isEmpty()) {
                emit(callId, "history_error", "任务编号为空", null);
                return;
            }
            final String cleanTaskId = taskId.trim();
            executor.execute(() -> {
                try {
                    JSONObject task = queryTask(key, cleanTaskId);
                    String status = task.optString("status", "");
                    if (!"succeeded".equals(status)) {
                        emit(callId, "history_error",
                                "任务当前状态：" + status, null);
                        return;
                    }
                    JSONObject result = resultFromTask(task, cleanTaskId, "", "", "",
                            "", 0);
                    JSONObject pending = loadPendingTask();
                    if (pending != null
                            && cleanTaskId.equals(pending.optString("taskId", ""))) {
                        result.put("mode", pending.optString("mode", "image"));
                        result.put("createdAt", pending.optLong("createdAt",
                                System.currentTimeMillis()));
                    }
                    emit(callId, "history_item", "已从云端刷新图片", result);
                } catch (Exception error) {
                    emit(callId, "history_error", friendlyError(error), null);
                }
            });
        }

        @JavascriptInterface
        public void saveImage(String imageUrl, String mimeType, String suggestedName,
                              String callId) {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                emit(callId, "save_error", "图片地址为空", null);
                return;
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingSaveUrl = imageUrl;
                pendingSaveMime = mimeType;
                pendingSaveName = suggestedName;
                pendingSaveCallId = callId;
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        SAVE_PERMISSION_REQUEST
                );
                return;
            }
            executor.execute(() -> downloadAndSave(
                    imageUrl,
                    mimeType,
                    suggestedName,
                    callId
            ));
        }
    }

    private void runGeneration(String key, String prompt, String model, String size,
                               String resolution, String quality, int count,
                               String referenceImagesJson, String callId) {
        HttpURLConnection connection = null;
        try {
            JSONObject request = new JSONObject();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("size", size);
            request.put("resolution", resolution);
            request.put("n", count);
            if (!quality.isEmpty()) request.put("quality", quality);
            JSONArray referenceImages = referenceImagesJson == null
                    || referenceImagesJson.trim().isEmpty()
                    ? new JSONArray()
                    : new JSONArray(referenceImagesJson);
            if (referenceImages.length() > 0) {
                JSONArray uploadedReferences = new JSONArray();
                for (int index = 0; index < referenceImages.length(); index++) {
                    String dataUrl = referenceImages.optString(index, "");
                    if (dataUrl.isEmpty()) continue;
                    emit(callId, "status",
                            "正在压缩并上传参考图 " + (index + 1) + "/"
                                    + referenceImages.length() + "…", null);
                    String referenceId = "";
                    try {
                        referenceId = uploadReferenceImage(key, dataUrl);
                    } catch (Exception uploadError) {
                        emit(callId, "status",
                                "参考图 " + (index + 1) + " 压缩失败，使用压缩后的本地图继续…",
                                null);
                    }
                    uploadedReferences.put(referenceId.isEmpty() ? dataUrl : referenceId);
                }
                request.put("images", uploadedReferences);
            }

            emit(callId, "status", "正在提交任务…", null);
            URL url = new URL(API_ROOT + "/images/generations");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MiaoJiPaint/1.0");

            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int httpStatus = connection.getResponseCode();
            String responseText = readResponse(connection);
            JSONObject root = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            if (httpStatus < 200 || httpStatus >= 300) {
                String message = root.optString("message", "提交失败（HTTP " + httpStatus + "）");
                String code = root.optString("code", "HTTP_" + httpStatus);
                boolean recharge = httpStatus == 402 || "IMAGE_BALANCE_NOT_ENOUGH".equals(code);
                emit(callId, "error", message, errorData(code, recharge));
                return;
            }

            JSONObject data = root.optJSONObject("data");
            String taskId = data == null ? root.optString("task_id", "") : data.optString("task_id", "");
            if (taskId.isEmpty()) throw new IllegalStateException("服务器没有返回任务编号");
            int estimated = data == null ? 0 : data.optInt("estimated_cents", 0);
            String shortTaskId = taskId.length() > 8 ? taskId.substring(0, 8) : taskId;
            JSONObject pending = new JSONObject();
            pending.put("taskId", taskId);
            pending.put("mode", modeForCallId(callId));
            pending.put("createdAt", System.currentTimeMillis());
            pending.put("estimatedCents", estimated);
            pending.put("model", model);
            pending.put("size", size);
            pending.put("resolution", resolution);
            pending.put("quality", quality);
            pending.put("state", "submitted");
            savePendingTask(pending);

            JSONObject statusData = new JSONObject();
            statusData.put("taskId", taskId);
            statusData.put("mode", pending.optString("mode", "image"));
            statusData.put("createdAt", pending.optLong("createdAt",
                    System.currentTimeMillis()));
            emit(callId, "status",
                    "任务 " + shortTaskId + " 已提交，任务编号已保存；离开应用也会继续保留。",
                    statusData);
            pollTask(key, taskId, callId, pending);
        } catch (Exception error) {
            emit(callId, "error", friendlyError(error), null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void pollTask(String key, String taskId, String callId, JSONObject pending) {
        if (!pollingTasks.add(taskId)) return;
        String mode = pending.optString("mode", modeForCallId(callId));
        long createdAt = pending.optLong("createdAt", System.currentTimeMillis());
        long deadline = System.currentTimeMillis() + MAX_POLL_MILLIS;
        String shortTaskId = taskId.length() > 8 ? taskId.substring(0, 8) : taskId;
        int lastProgress = -1;
        long lastEmitSeconds = -10;
        int networkFailures = 0;
        try {
            while (System.currentTimeMillis() < deadline) {
                sleep(3000);
                if (Thread.currentThread().isInterrupted()) return;

                JSONObject task;
                try {
                    task = queryTask(key, taskId);
                    networkFailures = 0;
                } catch (Exception queryError) {
                    networkFailures++;
                    long waitedSeconds = (System.currentTimeMillis() - createdAt) / 1000;
                    if (waitedSeconds - lastEmitSeconds >= 10) {
                        lastEmitSeconds = waitedSeconds;
                        JSONObject data = taskStatusData(taskId, mode, createdAt);
                        data.put("networkRetry", networkFailures);
                        emit(callId, "status",
                                "任务 " + shortTaskId + " 已保存在云端，网络暂时中断，正在自动重试…",
                                data);
                    }
                    continue;
                }

                String status = task.optString("status", "unknown");
                int progress = task.optInt("progress", 0);
                long waitedSeconds = (System.currentTimeMillis() - createdAt) / 1000;
                boolean heartbeatDue = waitedSeconds - lastEmitSeconds >= 10;
                if (progress != lastProgress || heartbeatDue) {
                    lastProgress = progress;
                    lastEmitSeconds = waitedSeconds;
                    String label;
                    if ("queued".equals(status)) {
                        label = "任务 " + shortTaskId + " · 已排队 · 已等待 "
                                + waitedSeconds + " 秒";
                    } else if ("running".equals(status)) {
                        label = "任务 " + shortTaskId + " · 正在生成 " + progress
                                + "% · 已等待 " + waitedSeconds + " 秒";
                    } else {
                        label = "任务 " + shortTaskId + " · 正在处理结果 · 已等待 "
                                + waitedSeconds + " 秒";
                    }
                    emit(callId, "status", label, taskStatusData(taskId, mode, createdAt));
                }

                if ("succeeded".equals(status)) {
                    JSONObject result = resultFromTask(task, taskId, "", "", "", "", 0);
                    result.put("mode", mode);
                    result.put("createdAt", createdAt);

                    pending.put("state", "succeeded");
                    pending.put("imageUrl", result.optString("imageUrl", ""));
                    pending.put("chargedCents", result.optInt("chargedCents", 0));
                    pending.put("estimatedCents", result.optInt("estimatedCents", 0));
                    pending.put("model", result.optString("model", ""));
                    pending.put("size", result.optString("size", ""));
                    pending.put("resolution", result.optString("resolution", ""));
                    pending.put("quality", result.optString("quality", ""));
                    savePendingTask(pending);
                    emit(callId, "result", "图片已从云端取回", result);
                    return;
                }

                if ("failed".equals(status) || "cancelled".equals(status)) {
                    JSONObject failure = task.optJSONObject("error");
                    String code = failure == null ? status : failure.optString("code", status);
                    String message = failure == null
                            ? "任务生成失败，预扣金额会自动退回"
                            : failure.optString("message", "任务生成失败");

                    // 妙极会约在 15 分钟内自动恢复 upstream_timeout 的已出图任务。
                    if ("upstream_timeout".equals(code)) {
                        emit(callId, "status", "暂时超时，系统正在后台恢复…",
                                taskStatusData(taskId, mode, createdAt));
                        continue;
                    }
                    clearPendingTaskInternal(taskId);
                    JSONObject data = errorData(code, false);
                    data.put("taskId", taskId);
                    data.put("mode", mode);
                    emit(callId, "error", message, data);
                    return;
                }
            }

            JSONObject data = taskStatusData(taskId, mode, createdAt);
            emit(callId, "recoverable",
                    "任务仍在云端处理，已保留任务记录；重新打开应用会自动继续恢复。",
                    data);
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) return;
            JSONObject data = taskStatusData(taskId, mode, createdAt);
            try {
                data.put("code", "PENDING_RECOVERABLE");
            } catch (JSONException ignored) {
            }
            emit(callId, "recoverable",
                    "任务已提交，暂时没有取回结果；任务记录已经保留，可在“最近生成”里继续恢复。",
                    data);
        } finally {
            pollingTasks.remove(taskId);
        }
    }

    private JSONObject resultFromTask(JSONObject task, String taskId, String model,
                                      String size, String resolution, String quality,
                                      int estimated) throws JSONException {
        JSONArray images = task.optJSONArray("images");
        JSONObject image = images != null && images.length() > 0
                ? images.optJSONObject(0) : null;
        String imageUrl = image == null ? "" : image.optString("url", "");
        if (imageUrl.isEmpty()) {
            throw new IllegalStateException("任务已成功，但没有图片地址");
        }
        JSONObject result = new JSONObject();
        result.put("taskId", taskId);
        result.put("imageUrl", imageUrl);
        result.put("mimeType", image == null
                ? "image/png" : safe(image.optString("mime_type", ""), "image/png"));
        result.put("chargedCents", task.optInt("charged_cents", 0));
        result.put("estimatedCents", task.optInt("estimated_cents", estimated));
        result.put("model", task.optString("model", model));
        result.put("size", task.optString("size", size));
        result.put("resolution", task.optString("resolution", resolution));
        result.put("quality", task.optString("quality", quality));
        return result;
    }

    private JSONObject resultFromPending(JSONObject pending) {
        JSONObject result = new JSONObject();
        try {
            result.put("taskId", pending.optString("taskId", ""));
            result.put("imageUrl", pending.optString("imageUrl", ""));
            result.put("mimeType", "image/png");
            result.put("chargedCents", pending.optInt("chargedCents", 0));
            result.put("estimatedCents", pending.optInt("estimatedCents", 0));
            result.put("model", pending.optString("model", ""));
            result.put("size", pending.optString("size", ""));
            result.put("resolution", pending.optString("resolution", ""));
            result.put("quality", pending.optString("quality", ""));
            result.put("mode", pending.optString("mode", "image"));
            result.put("createdAt", pending.optLong("createdAt",
                    System.currentTimeMillis()));
        } catch (JSONException ignored) {
        }
        return result;
    }

    private JSONObject taskStatusData(String taskId, String mode, long createdAt) {
        JSONObject data = new JSONObject();
        try {
            data.put("taskId", taskId);
            data.put("mode", mode);
            data.put("createdAt", createdAt);
        } catch (JSONException ignored) {
        }
        return data;
    }

    private synchronized JSONObject loadPendingTask() {
        String raw = preferences.getString(KEY_PENDING_TASK, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (JSONException error) {
            preferences.edit().remove(KEY_PENDING_TASK).apply();
            return null;
        }
    }

    private synchronized void savePendingTask(JSONObject pending) {
        if (pending == null) return;
        preferences.edit().putString(KEY_PENDING_TASK, pending.toString()).apply();
    }

    private synchronized void clearPendingTaskInternal(String taskId) {
        JSONObject pending = loadPendingTask();
        if (pending == null) return;
        String expected = pending.optString("taskId", "");
        if (taskId == null || taskId.trim().isEmpty() || taskId.equals(expected)) {
            preferences.edit().remove(KEY_PENDING_TASK).apply();
        }
    }

    private static String modeForCallId(String callId) {
        if (callId != null && callId.startsWith("beauty")) return "beauty";
        if (callId != null && callId.startsWith("effect")) return "effect";
        return "image";
    }

    private void runUpdateCheck(String callId) {
        if (GITHUB_REPO.startsWith("OWNER/")) {
            emit(callId, "update_error", "GitHub 仓库还没有配置", null);
            return;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(GITHUB_LATEST_RELEASE);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MiaoJiPaint/" + currentVersionName());

            int status = connection.getResponseCode();
            String response = readResponse(connection);
            if (status == 404) {
                emit(callId, "update_error", "GitHub 仓库或 Release 尚未发布", null);
                return;
            }
            if (status < 200 || status >= 300) {
                emit(callId, "update_error",
                        "检查更新失败（GitHub HTTP " + status + "）", null);
                return;
            }

            JSONObject release = new JSONObject(response);
            String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
            String current = currentVersionName();
            JSONArray assets = release.optJSONArray("assets");
            String downloadUrl = "";
            if (assets != null) {
                for (int index = 0; index < assets.length(); index++) {
                    JSONObject asset = assets.optJSONObject(index);
                    if (asset == null) continue;
                    String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "");
                        if (name.contains("miaoji")) break;
                    }
                }
            }

            if (compareVersions(tag, current) > 0) {
                JSONObject data = new JSONObject();
                data.put("version", tag);
                data.put("currentVersion", current);
                data.put("notes", shortReleaseNotes(release.optString("body", "")));
                data.put("downloadUrl", downloadUrl.isEmpty()
                        ? release.optString("html_url", GITHUB_REPO_URL) : downloadUrl);
                data.put("releaseUrl", release.optString("html_url", GITHUB_REPO_URL));
                emit(callId, "update_available", "发现新版本", data);
            } else {
                emit(callId, "update_none", "已经是最新版本", null);
            }
        } catch (Exception error) {
            emit(callId, "update_error", friendlyError(error), null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String uploadReferenceImage(String key, String dataUrl) throws Exception {
        int comma = dataUrl.indexOf(',');
        int semicolon = dataUrl.indexOf(';');
        if (comma < 0 || semicolon < 0 || semicolon > comma) {
            throw new IllegalArgumentException("参考图数据格式不正确");
        }
        String mime = safe(dataUrl.substring(5, semicolon), "image/jpeg");
        byte[] imageBytes = android.util.Base64.decode(
                dataUrl.substring(comma + 1),
                android.util.Base64.DEFAULT
        );
        String extension = extensionFor(mime);
        String boundary = "----MiaoJiPaint" + System.currentTimeMillis();
        String fileHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + "reference." + extension + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        String ending = "\r\n--" + boundary + "--\r\n";

        ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
        bodyBuffer.write(fileHeader.getBytes(StandardCharsets.UTF_8));
        bodyBuffer.write(imageBytes);
        bodyBuffer.write(ending.getBytes(StandardCharsets.UTF_8));
        byte[] body = bodyBuffer.toByteArray();

        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_ROOT + "/uploads/images");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty(
                    "Content-Type", "multipart/form-data; boundary=" + boundary
            );
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MiaoJiPaint/1.0");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            String response = readResponse(connection);
            JSONObject root = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(root.optString("message",
                        "参考图上传失败（HTTP " + status + "）"));
            }
            JSONObject data = root.optJSONObject("data");
            String id = data == null ? "" : data.optString("id", "");
            if (id.isEmpty()) throw new IllegalStateException("参考图上传后没有返回编号");
            return id;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String currentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "1.9.0";
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.replaceAll("[^0-9.]", "").split("\\.");
        String[] b = right.replaceAll("[^0-9.]", "").split("\\.");
        int count = Math.max(a.length, b.length);
        for (int index = 0; index < count; index++) {
            int av = index < a.length && !a[index].isEmpty()
                    ? Integer.parseInt(a[index]) : 0;
            int bv = index < b.length && !b[index].isEmpty()
                    ? Integer.parseInt(b[index]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static String shortReleaseNotes(String body) {
        String notes = body == null ? "" : body
                .replaceAll("[#*_`>\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (notes.length() > 160) notes = notes.substring(0, 160) + "…";
        return notes;
    }

    private JSONObject queryTask(String key, String taskId) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_ROOT + "/images/tasks/" + taskId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MiaoJiPaint/1.0");
            int status = connection.getResponseCode();
            String body = readResponse(connection);
            JSONObject root = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(root.optString("message",
                        "查询任务失败（HTTP " + status + "）"));
            }
            JSONObject data = root.optJSONObject("data");
            if (data == null) throw new IllegalStateException("任务结果格式异常");
            return data;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void downloadAndSave(String imageUrl, String mimeType, String suggestedName,
                                 String callId) {
        HttpURLConnection connection = null;
        try {
            emit(callId, "saving", "正在保存到手机相册…", null);
            byte[] imageBytes;
            String contentType = safe(mimeType, "image/png");
            if (imageUrl.startsWith("data:")) {
                int comma = imageUrl.indexOf(',');
                int semicolon = imageUrl.indexOf(';');
                if (comma < 0 || !imageUrl.substring(0, comma).contains(";base64")) {
                    throw new IllegalStateException("图片数据格式异常");
                }
                if (semicolon > 5 && semicolon < comma) {
                    contentType = imageUrl.substring(5, semicolon);
                }
                imageBytes = android.util.Base64.decode(
                        imageUrl.substring(comma + 1),
                        android.util.Base64.DEFAULT
                );
            } else {
                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(120000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "MiaoJiPaint/1.0");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("图片下载失败（HTTP " + status + "）");
                }
                contentType = safe(connection.getContentType(), contentType).split(";")[0].trim();
                imageBytes = readBytes(connection.getInputStream());
            }

            String extension = extensionFor(contentType);
            String name = safeFileName(suggestedName, extension);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(imageBytes, name, contentType);
            } else {
                saveLegacy(imageBytes, name, contentType);
            }

            JSONObject result = new JSONObject();
            result.put("filename", name);
            emit(callId, "saved", "已保存到相册：妙极画图/" + name, result);
            showToast("已保存到手机相册");
        } catch (Exception error) {
            emit(callId, "save_error", friendlyError(error), null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void saveWithMediaStore(byte[] imageBytes, String name, String mime)
            throws Exception {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/妙极画图");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IllegalStateException("无法创建相册文件");
        try {
            try (OutputStream output = resolver.openOutputStream(item)) {
                if (output == null) throw new IllegalStateException("无法写入相册");
                output.write(imageBytes);
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(item, complete, null, null);
        } catch (Exception error) {
            resolver.delete(item, null, null);
            throw error;
        }
    }

    @SuppressWarnings("deprecation")
    private void saveLegacy(byte[] imageBytes, String name, String mime)
            throws Exception {
        File folder = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "妙极画图"
        );
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("无法创建相册目录");
        }
        File file = new File(folder, name);
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(imageBytes);
        }
        MediaScannerConnection.scanFile(
                this,
                new String[]{file.getAbsolutePath()},
                new String[]{mime},
                null
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != SAVE_PERMISSION_REQUEST) return;
        String url = pendingSaveUrl;
        String mime = pendingSaveMime;
        String name = pendingSaveName;
        String callId = pendingSaveCallId;
        pendingSaveUrl = null;
        pendingSaveMime = null;
        pendingSaveName = null;
        pendingSaveCallId = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            executor.execute(() -> downloadAndSave(url, mime, name, callId));
        } else {
            emit(callId, "save_error", "没有存储权限，无法保存到相册", null);
        }
    }

    private static JSONObject errorData(String code, boolean recharge) {
        JSONObject data = new JSONObject();
        try {
            data.put("code", code);
            data.put("openRecharge", recharge);
        } catch (JSONException ignored) {
        }
        return data;
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        InputStream input;
        int status = connection.getResponseCode();
        input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) return "";
        return new String(readBytes(input), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (BufferedInputStream buffered = new BufferedInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = buffered.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String friendlyError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "网络请求失败，请检查网络后重试";
        }
        if (message.contains("Unable to resolve host")
                || message.contains("Failed to connect")
                || message.contains("Connection reset")) {
            return "网络连接失败，请检查手机网络后重试";
        }
        return message;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String extensionFor(String mime) {
        if ("image/jpeg".equalsIgnoreCase(mime)) return "jpg";
        if ("image/webp".equalsIgnoreCase(mime)) return "webp";
        String guessed = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return guessed == null || guessed.isEmpty() ? "png" : guessed;
    }

    private static String safeFileName(String suggested, String extension) {
        String base = suggested == null ? "" : suggested.trim();
        if (base.isEmpty()) {
            base = "妙极画图_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                    .format(new Date());
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        if (base.length() > 80) base = base.substring(0, 80);
        if (!base.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            base += "." + extension;
        }
        return base;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST) return;
        if (fileChooserCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                result = new Uri[]{uri};
            } else if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                result = new Uri[clip.getItemCount()];
                for (int index = 0; index < clip.getItemCount(); index++) {
                    result[index] = clip.getItemAt(index).getUri();
                }
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
