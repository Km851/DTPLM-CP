package com.example.ezpz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WeeklySummaryReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "weekly_summary";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("ezpz_settings", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("weekly_summary", false)) return;

        List<TimelineItem> tasks = TaskManager.loadTasks(context);
        StreakManager streak = new StreakManager(context);

        // Count tasks due this week
        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String startDate = fmt.format(weekStart.getTime());

        int totalThisWeek = 0;
        for (TimelineItem t : tasks) {
            if (t.getDueDate() != null && t.getDueDate().compareTo(startDate) >= 0) {
                totalThisWeek++;
            }
        }

        int completed = streak.getCompletedToday();
        int streakDays = streak.getStreak();

        String title = "\uD83D\uDCCA Weekly Progress";
        String body;
        if (totalThisWeek == 0 && completed == 0) {
            body = "No tasks this week. Time to plan ahead for next week! \uD83D\uDDD3";
        } else if (streakDays >= 5) {
            body = streakDays + "-day streak! \uD83D\uDD25 You had " + totalThisWeek + " tasks this week. Keep it up!";
        } else {
            body = "You had " + totalThisWeek + " tasks this week. " + (totalThisWeek > 5 ? "Busy week!" : "Nice and steady!") + " \uD83D\uDCAA";
        }

        // Create notification
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Weekly Summary",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Weekly progress summary every Sunday");
            mgr.createNotificationChannel(ch);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_report)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mgr.notify(9999, builder.build());
    }

    public static void schedule(Context context) {
        android.app.AlarmManager alarmMgr = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WeeklySummaryReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 9999, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Sunday 7 PM
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.before(Calendar.getInstance())) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }

        alarmMgr.setRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                android.app.AlarmManager.INTERVAL_DAY * 7, pi);
    }

    public static void cancel(Context context) {
        android.app.AlarmManager alarmMgr = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WeeklySummaryReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(context, 9999, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        alarmMgr.cancel(pi);
    }
}