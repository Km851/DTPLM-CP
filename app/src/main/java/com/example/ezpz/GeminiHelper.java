package com.example.ezpz;

import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiHelper {
    private static final String TAG = "GeminiHelper";

    private static final String[] API_KEYS = {
            "YOUR_GEMINI_API_KEY",
            "YOUR_GEMINI_API_KEY",
            "YOUR_GEMINI_API_KEY",
            "YOUR_GEMINI_API_KEY"
    };
    private static int currentKeyIndex = 0;
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String[] MODELS = {
            "gemini-3-flash-preview",
            "gemini-3.1-flash-lite-preview",
            "gemma-4-26b-a4b-it",
            "gemini-flash-lite-latest"
    };
    private static final int MAX_RETRIES = 20;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface GeminiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

        public static void generateMorningBriefing(String currentTasksJson, GeminiCallback callback) {
        executor.execute(() -> {
            if (API_KEYS.length == 0 || API_KEYS[0].trim().isEmpty()) {
                callback.onError("Gemini API key is missing.");
                return;
            }
            String prompt = "You are a motivational AI assistant for a productivity app named EZPZ. "
                    + "Here is the user's task list for today in JSON format:\n"
                    + currentTasksJson + "\n\n"
                    + "Write a short, highly energetic, 2-to-3 sentence morning pep-talk summarizing what they have to do today and motivating them. "
                    + "Do not use JSON formatting. Just write plain text.";

                        String finalResult = null;
            for (String model : MODELS) {
                finalResult = tryModel(model, prompt);
                if (finalResult != null) break;
            }

            if (finalResult != null) {
                final String res = finalResult;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(res));
            } else {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("Failed to reach Gemini after all retries."));
            }
        });
    }

    public static void editTaskList(String userMessage, String currentTasksJson, GeminiCallback callback) {
        executor.execute(() -> {
            if (API_KEYS.length == 0 || API_KEYS[0].trim().isEmpty()) {
                callback.onError("Gemini API key is missing.");
                return;
            }

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String dayOfWeek = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
            String prompt = "You are a task management AI inside an app called EZPZ (FocusFlow). "
                    + "Today is " + dayOfWeek + ", " + today + ". "
                    + "Here is the current task list in JSON format:\n"
                    + currentTasksJson + "\n\n"
                    + "The user said: \"" + userMessage + "\"\n\n"
                    + "Update the JSON according to the user's intent. You have FULL control over all 10 task properties and can:\n"
                    + "- Add new tasks at any position (put first in the array if asked to add at the top)\n"
                    + "- Remove/delete tasks\n"
                    + "- Reorder tasks\n"
                    + "- Break a task into subtasks\n"
                    + "- Edit ANY task field: title, description, startTime, duration (time to complete), subtitle, dueDate, deadline, reminderMinutes, priority, category, and energyLevel\n\n"
                    + "Rules & Schema:\n"
                    + "- Return ONLY the updated JSON object, nothing else (no markdown code fences, no explanation before or after)\n"
                    + "- Keep this exact JSON structure for every task in the \"tasks\" array:\n"
                    + "{\"tasks\":[{\"title\":\"...\",\"description\":\"...\",\"startTime\":\"...\",\"duration\":\"...\",\"subtitle\":\"...\",\"dueDate\":\"yyyy-MM-dd\",\"deadline\":\"...\",\"reminderMinutes\":5,\"priority\":\"medium\",\"category\":\"Academics\",\"energyLevel\":\"M\"}]}\n"
                    + "- Field specifications:\n"
                    + "  1. \"title\": String (required, task title).\n"
                    + "  2. \"description\": String (task description / notes / what 'done' looks like; \"\" if none).\n"
                    + "  3. \"startTime\": String in 12-hour format \"h:mm AM/PM\" like \"2:00 PM\" or \"9:30 AM\" (\"\" if not set).\n"
                    + "  4. \"duration\": String for time to complete like \"15 min\", \"30 min\", \"45 min\", \"1 hour\", \"2 hours\" (\"\" if not set).\n"
                    + "  5. \"subtitle\": String combining startTime and duration separated by \" \u00b7 \" (e.g. \"2:00 PM \u00b7 45 min\", or just \"2:00 PM\" if only startTime is set, or just \"45 min\" if only duration is set, or \"\" if neither). Always keep subtitle in sync with startTime and duration.\n"
                    + "  6. \"dueDate\": String in \"yyyy-MM-dd\" format (required; resolve natural dates like today/tomorrow/Friday/next week relative to " + dayOfWeek + " " + today + "; if no date is mentioned for a new task, use " + today + ").\n"
                    + "  7. \"deadline\": String for deadline time like \"5:00 PM\" or \"11:30 PM\" (\"\" if not set).\n"
                    + "  8. \"reminderMinutes\": Integer minutes before task to remind (e.g. 0 for at time of event, 5, 10, 15, 30, 60; default 5).\n"
                    + "  9. \"priority\": String, MUST be one of \"low\", \"medium\", or \"high\" (lowercase; default \"medium\").\n"
                    + "  10. \"category\": String, MUST be one of \"Academics\", \"Health\", or \"Personal\" (for new tasks, infer appropriately from context: study/coding/assignment/exam -> \"Academics\", workout/gym/run/meditation/sleep/health -> \"Health\", chores/shopping/errands/personal -> \"Personal\"; default \"Academics\").\n"
                    + "  11. \"energyLevel\": String, MUST be one of \"L\" (Low), \"M\" (Medium), or \"H\" (High) (default \"M\").\n"
                    + "- Preserve all existing tasks and all of their existing fields (description, priority, category, energyLevel, deadline, reminderMinutes, dueDate, subtitle, startTime, duration, active, shiftedFromDate) unless the user asks to change or remove them.";

            for (String model : MODELS) {
                String result = tryModel(model, prompt);
                final String finalResult = result;
                if (finalResult != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(finalResult));
                    return;
                }
            }

            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError("All models are currently busy. Please try again in a moment."));
        });
    }
    private static String tryModel(String model, String prompt) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(500);
                }

                String apiKey = API_KEYS[currentKeyIndex];
                String apiUrl = BASE_URL + model + ":generateContent";

                JSONObject requestBody = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();

                part.put("text", prompt);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                requestBody.put("contents", contents);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", apiKey);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();

                BufferedReader reader;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        return jsonResponse
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim();
                    } catch (Exception parseEx) {
                        Log.e(TAG, "Failed to parse 200 OK response: " + response.toString(), parseEx);
                        break; // Stop retrying if we got a 200 but parsing failed
                    }
                }

                if (responseCode == 429) {
                    Log.e(TAG, "Rate limited (429), rotating key.");
                    rotateApiKey();
                    continue;
                }
                if (responseCode == 503) {
                    Log.e(TAG, "Service unavailable (503).");
                    continue;
                }
                Log.e(TAG, "Gemini API error: " + responseCode + " - " + response.toString());
                break;

            } catch (Exception e) {
                Log.e(TAG, "Exception during tryModel: " + e.getMessage());
                if (attempt == MAX_RETRIES - 1) break;
            }
        }
        return null;
    }

    private static void rotateApiKey() {
        if (API_KEYS.length > 1) {
            currentKeyIndex = (currentKeyIndex + 1) % API_KEYS.length;
        }
    }
}
