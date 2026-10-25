package com.example.ezpz;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class StreakManager {

    private static final String PREFS_NAME = "ezpz_streak";
    private static final String KEY_STREAK = "streak_count";
    private static final String KEY_LAST_DATE = "last_date";
    private static final String KEY_COMPLETED = "completed_today";
    private static final String KEY_TOTAL = "total_today";
    private static final String KEY_SHIELDS = "streak_shields";
    private static final String KEY_SHIELD_PROGRESS = "shield_progress";
    private static final String KEY_XP = "total_xp";
    private static final String KEY_LAST_CRIT = "last_was_crit";

    private final SharedPreferences prefs;
    private final Random rng = new Random();

    public StreakManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SHIELDS)) {
            prefs.edit().putInt(KEY_SHIELDS, 1).apply();
        }
    }

    private String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public void checkDayRollover(int currentTotalTasks) {
        String today = getToday();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");

        if (lastDate.equals(today)) {
            return;
        }

        if (!lastDate.isEmpty()) {
            int yesterdayCompleted = prefs.getInt(KEY_COMPLETED, 0);
            int yesterdayTotal = prefs.getInt(KEY_TOTAL, 0);
            int currentStreak = prefs.getInt(KEY_STREAK, 0);
            int shields = getShields();

            if (yesterdayTotal == 0) {
                // Neutral day
            } else if (yesterdayCompleted * 2 > yesterdayTotal) {
                currentStreak++;
                prefs.edit().putInt(KEY_STREAK, currentStreak).apply();
            } else if (currentStreak > 0 && shields > 0) {
                // [#7] ADHD Streak Shield protects your streak from resetting on 1 bad day
                prefs.edit().putInt(KEY_SHIELDS, shields - 1).apply();
            } else {
                prefs.edit().putInt(KEY_STREAK, 0).apply();
            }
        }

        prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_COMPLETED, 0)
                .putInt(KEY_TOTAL, currentTotalTasks)
                .apply();
    }

    /**
     * Records a completed task, rolls a 25% chance for [#22] '2x XP Critical Hit!',
     * and returns true if a new [#7] Streak Shield was earned.
     */
    public boolean taskCompleted() {
        boolean isCrit = (rng.nextInt(4) == 0); // 25% chance of 2x XP Critical Hit
        int increment = isCrit ? 2 : 1;

        int completed = prefs.getInt(KEY_COMPLETED, 0) + 1;
        int prog = prefs.getInt(KEY_SHIELD_PROGRESS, 0) + increment;
        int xp = prefs.getInt(KEY_XP, 0) + (isCrit ? 20 : 10);
        int shields = getShields();
        boolean earnedShield = false;

        while (prog >= 3) {
            prog -= 3;
            if (shields < 3) {
                shields++;
                earnedShield = true;
            }
        }

        prefs.edit()
                .putInt(KEY_COMPLETED, completed)
                .putInt(KEY_SHIELD_PROGRESS, prog)
                .putInt(KEY_SHIELDS, shields)
                .putInt(KEY_XP, xp)
                .putBoolean(KEY_LAST_CRIT, isCrit)
                .apply();
        return earnedShield;
    }

    public boolean wasLastCriticalHit() {
        return prefs.getBoolean(KEY_LAST_CRIT, false);
    }

    public int getXp() {
        return prefs.getInt(KEY_XP, 0);
    }

    public int getShields() {
        return prefs.getInt(KEY_SHIELDS, 1);
    }

    public void updateTotalTasks(int total) {
        prefs.edit().putInt(KEY_TOTAL, total).apply();
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
        int xp = getXp();
        int effective = streak + (xp / 50);
        if (effective >= 60) return "Legend";
        if (effective >= 30) return "Diamond";
        if (effective >= 14) return "Gold";
        if (effective >= 7) return "Silver";
        if (effective >= 3) return "Bronze";
        return "Novice";
    }

    public int getRankColor() {
        String rank = getRankName();
        switch (rank) {
            case "Legend": return 0xFFC26BFF;
            case "Diamond": return 0xFF00E5FF;
            case "Gold": return 0xFFFFD700;
            case "Silver": return 0xFFC0C0C0;
            case "Bronze": return 0xFFCD7F32;
            default: return 0xFF9E9E9E;
        }
    }
}
