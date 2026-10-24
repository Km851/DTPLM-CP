package com.example.ezpz;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StreakManager {

    private static final String PREFS_NAME = "ezpz_streak";
    private static final String KEY_STREAK = "streak_count";
    private static final String KEY_LAST_DATE = "last_date";
    private static final String KEY_COMPLETED = "completed_today";
    private static final String KEY_TOTAL = "total_today";
    private static final String KEY_LAST_HAD_TASKS = "last_had_tasks";

    private SharedPreferences prefs;

    public StreakManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String getYesterday() {
        long oneDayMs = 24 * 60 * 60 * 1000L;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(System.currentTimeMillis() - oneDayMs));
    }

    // Call this on app launch to evaluate day changes
    public void checkDayRollover(int currentTotalTasks) {
        String today = getToday();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (lastDate.equals(today)) {
            // Same day, no rollover needed
            return;
        }

        if (!lastDate.isEmpty()) {
            // Day changed - evaluate yesterday's performance
            int yesterdayCompleted = prefs.getInt(KEY_COMPLETED, 0);
            int yesterdayTotal = prefs.getInt(KEY_TOTAL, 0);
            int currentStreak = prefs.getInt(KEY_STREAK, 0);

            // Calculate how many days were skipped
            long daysBetween = daysBetween(lastDate, today);

            if (yesterdayTotal == 0) {
                // Last active day had no tasks - neutral
                // But if multiple days skipped with tasks still in file, that's still neutral
            } else if (yesterdayCompleted * 2 > yesterdayTotal) {
                // Completed more than half - streak increases!
                currentStreak++;
                prefs.edit().putInt(KEY_STREAK, currentStreak).apply();
            } else {
                // Failed to complete half - streak resets
                prefs.edit().putInt(KEY_STREAK, 0).apply();
            }

            // If user skipped multiple days, intermediate days are neutral
            // (no break, no increase) - this is handled by only evaluating lastDate
        }

        // Reset daily counters for new day
        prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_COMPLETED, 0)
                .putInt(KEY_TOTAL, currentTotalTasks)
                .apply();
    }

    // Call when a task is completed
    public void taskCompleted() {
        int completed = prefs.getInt(KEY_COMPLETED, 0) + 1;
        prefs.edit().putInt(KEY_COMPLETED, completed).apply();
    }

    // Call when tasks are added/changed
    public void updateTotalTasks(int total) {
        prefs.edit().putInt(KEY_TOTAL, total).apply();
    }

    private long daysBetween(String dateStr1, String dateStr2) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long d1 = sdf.parse(dateStr1).getTime();
            long d2 = sdf.parse(dateStr2).getTime();
            return Math.abs(d2 - d1) / (24 * 60 * 60 * 1000L);
        } catch (Exception e) {
            return 1;
        }
    }

    public int getStreak() {
        return prefs.getInt(KEY_STREAK, 0);
    }

    public int getCompletedToday() {
        return prefs.getInt(KEY_COMPLETED, 0);
    }

    public int getTotalToday() {
        return prefs.getInt(KEY_TOTAL, 0);
    }

    public String getRankName() {
        int streak = getStreak();
        if (streak >= 60) return "Legend";
        if (streak >= 30) return "Diamond";
        if (streak >= 14) return "Gold";
        if (streak >= 7) return "Silver";
        if (streak >= 3) return "Bronze";
        return "Novice";
    }

    public int getRankColor() {
        int streak = getStreak();
        if (streak >= 60) return 0xFFC26BFF; // Purple (Brand)
        if (streak >= 30) return 0xFF00E5FF; // Diamond (Cyan)
        if (streak >= 14) return 0xFFFFD700; // Gold
        if (streak >= 7) return 0xFFC0C0C0;  // Silver
        if (streak >= 3) return 0xFFCD7F32;  // Bronze
        return 0xFF9E9E9E; // Novice (Gray)
    }
}