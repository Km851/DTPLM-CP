package com.example.ezpz;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryManager {

    private static final String FILE_NAME = "completed_tasks.json";

    public static void saveCompletedTask(Context context, TimelineItem task) {
        List<TimelineItem> history = getHistory(context);
        
        // When saving to history, we record the actual completion date in the dueDate field (or we could make a new field, but dueDate is easiest)
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        
        TimelineItem completed = new TimelineItem(
                task.getTitle(),
                task.getSubtitle(),
                true, // isCompleted
                today, // Force the date to today (when it was actually completed)
                task.getPriority(),
                task.getDescription(),
                task.getCategory(),
                task.getEnergyLevel(),
                task.getDeadline(),
                task.getReminderMinutes()
        );
        
        for (TimelineItem ex : history) { if (today.equals(ex.getDueDate()) && ex.getTitle() != null && ex.getTitle().trim().equalsIgnoreCase(task.getTitle().trim())) return; } history.add(completed);
        saveHistory(context, history);
    }

    public static List<TimelineItem> getHistory(Context context) {
        List<TimelineItem> history = new ArrayList<>();
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                history.add(new TimelineItem(
                        obj.optString("title", ""),
                        obj.optString("subtitle", ""),
                        obj.optBoolean("isCompleted", true),
                        obj.optString("dueDate", ""),
                        obj.optString("priority", "Medium"),
                        obj.optString("description", ""),
                        obj.optString("category", "Personal"),
                        obj.optString("energyLevel", "Medium"),
                        obj.optString("deadline", ""),
                        obj.optInt("reminderMinutes", 0)
                ));
            }
        } catch (Exception e) {
            // File might not exist yet, which is fine
        }
        return history;
    }

    private static void saveHistory(Context context, List<TimelineItem> history) {
        try {
            JSONArray array = new JSONArray();
            for (TimelineItem item : history) {
                JSONObject obj = new JSONObject();
                obj.put("title", item.getTitle());
                obj.put("subtitle", item.getSubtitle());
                obj.put("isCompleted", true);
                obj.put("dueDate", item.getDueDate());
                obj.put("priority", item.getPriority());
                obj.put("description", item.getDescription());
                obj.put("category", item.getCategory());
                obj.put("energyLevel", item.getEnergyLevel());
                obj.put("deadline", item.getDeadline());
                obj.put("reminderMinutes", item.getReminderMinutes());
                array.put(obj);
            }

            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            fos.write(array.toString().getBytes());
            fos.close();
        } catch (JSONException | java.io.IOException e) {
            e.printStackTrace();
        }
    }
}
