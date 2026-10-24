package com.example.ezpz;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {

    private static final String PREFS_NAME = "ezpz_auth";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String error);
    }

    public ApiClient(Context context) {
        this.context = context;
    }

    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return getPrefs().getString("server_url", "");
    }

    public void setServerUrl(String url) {
        getPrefs().edit().putString("server_url", url).apply();
    }

    public String getToken() {
        return getPrefs().getString("token", "");
    }

    public void saveAuth(String token, String name, String email) {
        getPrefs().edit()
                .putString("token", token)
                .putString("user_name", name)
                .putString("user_email", email)
                .putBoolean("logged_in", true)
                .apply();
    }

    public void clearAuth() {
        String serverUrl = getServerUrl(); // preserve server URL
        getPrefs().edit().clear().apply();
        if (!serverUrl.isEmpty()) {
            setServerUrl(serverUrl);
        }
    }

    public boolean isLoggedIn() {
        return getPrefs().getBoolean("logged_in", false);
    }

    public String getUserName() {
        return getPrefs().getString("user_name", "User");
    }

    public String getUserEmail() {
        return getPrefs().getString("user_email", "");
    }

    // ─── HTTP ───

    public void login(String name, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                JSONObject res = post("/login", body, false);
                if (res.has("error")) {
                    mainHandler.post(() -> callback.onError(res.optString("error", "Unknown error")));
                } else {
                    saveAuth(res.optString("token", ""), res.optString("name", ""), "");
                    mainHandler.post(() -> callback.onSuccess(res));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Connection failed: " + e.getMessage()));
            }
        });
    }

    public void register(String name, Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                JSONObject res = post("/register", body, false);
                if (res.has("error")) {
                    mainHandler.post(() -> callback.onError(res.optString("error", "Unknown error")));
                } else {
                    saveAuth(res.optString("token", ""), res.optString("name", ""), "");
                    mainHandler.post(() -> callback.onSuccess(res));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Connection failed: " + e.getMessage()));
            }
        });
    }

    public void syncTasks(List<TimelineItem> tasks, Callback callback) {
        executor.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (TimelineItem t : tasks) {
                    JSONObject obj = new JSONObject();
                    obj.put("title", t.getTitle());
                    obj.put("subtitle", t.getSubtitle());
                    obj.put("description", t.getDescription() != null ? t.getDescription() : "");
                    obj.put("priority", t.getPriority() != null ? t.getPriority() : "medium");
                    obj.put("category", t.getCategory() != null ? t.getCategory() : "");
                    obj.put("energyLevel", t.getEnergyLevel() != null ? t.getEnergyLevel() : "");
                    obj.put("deadline", t.getDeadline() != null ? t.getDeadline() : "");
                    obj.put("dueDate", t.getDueDate() != null ? t.getDueDate() : "");
                    obj.put("reminderMinutes", t.getReminderMinutes());
                    arr.put(obj);
                }
                JSONObject body = new JSONObject();
                body.put("tasks", arr);
                JSONObject res = post("/tasks/sync", body, true);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(res));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    public void fetchTasks(Callback callback) {
        executor.execute(() -> {
            try {
                JSONObject res = get("/tasks");
                mainHandler.post(() -> callback.onSuccess(res));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ─── HTTP helpers ───

    private JSONObject post(String path, JSONObject body, boolean auth) throws Exception {
        URL url = new URL(getServerUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (auth) {
            conn.setRequestProperty("Authorization", "Bearer " + getToken());
        }
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        return readResponse(conn);
    }

    private JSONObject get(String path) throws Exception {
        URL url = new URL(getServerUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + getToken());
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return readResponse(conn);
    }

    private JSONObject readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return new JSONObject(sb.toString());
    }
}