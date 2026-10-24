package com.example.ezpz;

import android.content.Context;

import org.json.JSONArray;
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

public class TaskManager {

    private static final String FILE_NAME = "tasks.json";

    public static String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static void saveTasks(Context context, List<TimelineItem> tasks) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (TimelineItem task : tasks) {
                JSONObject obj = new JSONObject();
                obj.put("title", task.getTitle());
                obj.put("subtitle", task.getSubtitle());
                obj.put("startTime", extractStartTime(task.getSubtitle()));
                obj.put("duration", extractDuration(task.getSubtitle()));
                obj.put("active", task.isActive());
                obj.put("dueDate", task.getDueDate());
                obj.put("priority", task.getPriority());
                obj.put("description", task.getDescription());
                obj.put("category", task.getCategory());
                obj.put("energyLevel", task.getEnergyLevel());
                obj.put("deadline", task.getDeadline());
                obj.put("reminderMinutes", task.getReminderMinutes());
                obj.put("shiftedFromDate", task.getShiftedFromDate());
                jsonArray.put(obj);
            }

            JSONObject root = new JSONObject();
            root.put("tasks", jsonArray);

            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            fos.write(root.toString(2).getBytes("UTF-8"));
            fos.close();
            GoogleCalendarHelper.syncTasksToCalendar(context, tasks, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<TimelineItem> loadTasks(Context context) {
        List<TimelineItem> tasks = new ArrayList<>();
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray jsonArray = root.getJSONArray("tasks");

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                tasks.add(new TimelineItem(
                        obj.getString("title"),
                        obj.optString("subtitle", ""),
                        obj.optBoolean("active", false),
                        safeDueDate(obj.optString("dueDate", obj.optString("date", ""))),
                        obj.optString("priority", "medium"),
                        obj.optString("description", ""),
                        obj.optString("category", "Academics"),
                        obj.optString("energyLevel", "M"),
                        obj.optString("deadline", ""),
                        obj.optInt("reminderMinutes", 5),
                        obj.optString("shiftedFromDate", "")
                ));
            }
        } catch (Exception e) {
            // File doesn't exist yet or parse error
        }
                boolean autoReschedule = context.getSharedPreferences("ezpz_settings", Context.MODE_PRIVATE)
                .getBoolean("auto_reschedule", true);
        boolean modified = false;
        String today = todayDate();
        for (TimelineItem task : tasks) {
            String due = safeDueDate(task.getDueDate());
            if (due.compareTo(today) < 0) {
                if (autoReschedule) {
                    String deadline = task.getDeadline();
                    if (deadline != null && !deadline.isEmpty() && !deadline.equals("MISSED") && deadline.compareTo(today) < 0) {
                        GoogleCalendarHelper.deleteTaskFromCalendar(context, task);
                        ReminderHelper.sendDeadlineMissed(context, task);
                        task.setDeadline("MISSED");
                        modified = true;
                    } else if (!"MISSED".equals(deadline) && (deadline == null || deadline.isEmpty() || deadline.compareTo(today) >= 0)) {
                        GoogleCalendarHelper.deleteTaskFromCalendar(context, task);
                        task.setDueDate(today);
                        
                        String prevShifted = task.getShiftedFromDate();
                        if (prevShifted == null || prevShifted.isEmpty()) {
                            task.setShiftedFromDate(due);
                        } else if (!prevShifted.contains(due)) {
                            task.setShiftedFromDate(prevShifted + "," + due);
                        }
                        
                        modified = true;
                    }
                }
            }
        }
        if (modified) {
            saveTasks(context, tasks);
        }
        return tasks;
    }

    public static String getTasksAsJson(Context context) {
        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "{\"tasks\":[]}";
        }
    }

    public static List<TimelineItem> parseJsonResponse(String json) {
        List<TimelineItem> tasks = new ArrayList<>();
        try {
            json = json.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JSONObject root = new JSONObject(json);
            JSONArray jsonArray = root.getJSONArray("tasks");

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String rawSubtitle = obj.optString("subtitle", "").trim();
                String startTime = obj.optString("startTime", "").trim();
                String duration = obj.optString("duration", obj.optString("timeToComplete", "")).trim();
                String finalSubtitle = buildSubtitle(rawSubtitle, startTime, duration);

                tasks.add(new TimelineItem(
                        obj.getString("title"),
                        finalSubtitle,
                        obj.optBoolean("active", false),
                        safeDueDate(obj.optString("dueDate", obj.optString("date", ""))),
                        normalizePriority(obj.optString("priority", "medium")),
                        obj.optString("description", ""),
                        normalizeCategory(obj.optString("category", "Academics")),
                        normalizeEnergy(obj.optString("energyLevel", obj.optString("energy", "M"))),
                        obj.optString("deadline", ""),
                        parseReminderMinutes(obj.opt("reminderMinutes"), obj.opt("reminder")),
                        obj.optString("shiftedFromDate", "")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tasks;
    }

    private static String buildSubtitle(String rawSubtitle, String startTime, String duration) {
        String st = !startTime.isEmpty() ? startTime : extractStartTime(rawSubtitle);
        String dur = !duration.isEmpty() ? duration : extractDuration(rawSubtitle);
        if (!st.isEmpty() || !dur.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!st.isEmpty()) sb.append(st);
            if (!dur.isEmpty()) {
                if (sb.length() > 0) sb.append(" \u00b7 ");
                sb.append(dur);
            }
            return sb.toString();
        }
        return rawSubtitle;
    }

    private static String extractStartTime(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM|am|pm))").matcher(subtitle);
        if (m.find()) {
            String st = m.group(1).trim().toUpperCase(Locale.US);
            if (!st.contains(":")) {
                st = st.replaceFirst("(\\d{1,2})\\s*(AM|PM)", "$1:00 $2");
            }
            return st;
        }
        return "";
    }

    private static String extractDuration(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)\\s*(hours|hour|hrs|hr|mins|min|h|m)\\b").matcher(subtitle.toLowerCase(Locale.US));
        if (m.find()) return m.group(0);
        return "";
    }

    private static String normalizePriority(String p) {
        if (p == null) return "medium";
        String v = p.trim().toLowerCase(Locale.US);
        if (v.startsWith("h") || v.equals("urgent")) return "high";
        if (v.startsWith("l")) return "low";
        return "medium";
    }

    private static String normalizeCategory(String c) {
        if (c == null) return "Academics";
        String v = c.trim().toLowerCase(Locale.US);
        if (v.contains("health") || v.contains("fitness") || v.contains("workout")) return "Health";
        if (v.contains("personal") || v.contains("home") || v.contains("life")) return "Personal";
        return "Academics";
    }

    private static String normalizeEnergy(String e) {
        if (e == null) return "M";
        String v = e.trim().toUpperCase(Locale.US);
        if (v.startsWith("L")) return "L";
        if (v.startsWith("H")) return "H";
        return "M";
    }

    private static int parseReminderMinutes(Object primary, Object fallback) {
        Object val = primary != null ? primary : fallback;
        if (val == null) return 5;
        if (val instanceof Number) return ((Number) val).intValue();
        String s = val.toString().trim().toLowerCase(Locale.US);
        if (s.contains("at time") || s.equals("0")) return 0;
        if (s.contains("hour")) return 60;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return 5;
    }
    public static void sortTasksSmartly(List<TimelineItem> tasks) {
        if (tasks == null || tasks.size() <= 1) return;
        java.util.Collections.sort(tasks, (a, b) -> {
            // Keep tasks grouped by dueDate chronologically first, or compare same-day tasks by 5-tier rules
            String dateA = safeDueDate(a.getDueDate());
            String dateB = safeDueDate(b.getDueDate());
            int dateCmp = dateA.compareTo(dateB);
            if (dateCmp != 0) return dateCmp;

            // Tier 1: Priority (high = 3, medium = 2, low = 1)
            int prioCmp = Integer.compare(priorityWeight(b.getPriority()), priorityWeight(a.getPriority()));
            if (prioCmp != 0) return prioCmp;

            // Tier 2: Hard Deadline (earlier deadline today comes first; tasks with deadline beat tasks without)
            int dlA = parseTimeMinutes(a.getDeadline());
            int dlB = parseTimeMinutes(b.getDeadline());
            if (dlA != dlB) return Integer.compare(dlA, dlB);

            // Tier 3: Scheduled Start Time in subtitle (earlier start time comes first)
            int stA = parseTimeMinutes(extractStartTime(a.getSubtitle()));
            int stB = parseTimeMinutes(extractStartTime(b.getSubtitle()));
            if (stA != stB) return Integer.compare(stA, stB);

            // Tier 4: Rolled-over / Shifted tasks beat newer unscheduled tasks
            boolean shA = a.getShiftedFromDate() != null && !a.getShiftedFromDate().isEmpty();
            boolean shB = b.getShiftedFromDate() != null && !b.getShiftedFromDate().isEmpty();
            if (shA != shB) return shA ? -1 : 1;

            // Tier 5: Duration / Quick Wins (shorter defined duration comes before longer or undefined duration)
            int durA = parseDurationMinutes(a.getSubtitle());
            int durB = parseDurationMinutes(b.getSubtitle());
            if (durA != durB) return Integer.compare(durA, durB);

            return 0;
        });
    }

    private static int priorityWeight(String priority) {
        String p = normalizePriority(priority);
        if ("high".equals(p)) return 3;
        if ("low".equals(p)) return 1;
        return 2;
    }

    private static int parseTimeMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "MISSED".equalsIgnoreCase(timeStr.trim())) {
            return Integer.MAX_VALUE;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(timeStr.toLowerCase(Locale.US));
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);
            if ("pm".equals(ampm) && h != 12) h += 12;
            if ("am".equals(ampm) && h == 12) h = 0;
            return h * 60 + min;
        }
        return Integer.MAX_VALUE;
    }

    private static int parseDurationMinutes(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return Integer.MAX_VALUE;
        String lower = subtitle.toLowerCase(Locale.US);
        java.util.regex.Matcher hm = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)\\s*(hours|hour|hrs|hr|h)\\b").matcher(lower);
        if (hm.find()) {
            try { return (int)(Double.parseDouble(hm.group(1)) * 60); } catch (Exception ignored) {}
        }
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)\\s*(mins|min|m)\\b").matcher(lower);
        if (mm.find()) {
            try { return (int)Double.parseDouble(mm.group(1)); } catch (Exception ignored) {}
        }
        return Integer.MAX_VALUE;
    }

    private static String safeDueDate(String dueDate) {
        if (dueDate == null || dueDate.trim().isEmpty()) {
            return todayDate();
        }
        return dueDate.trim();
    }
}
