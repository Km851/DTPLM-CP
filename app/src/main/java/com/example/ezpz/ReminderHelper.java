package com.example.ezpz;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReminderHelper {

    private static final String TAG = "ReminderHelper";
    private static final int REMINDER_OFFSET_MINUTES = 5; // remind 5 min before

    /**
     * Schedule reminders for all tasks. Call this whenever tasks are saved.
     * - Tasks with a specific time (e.g. "2 PM", "4:30 PM") get reminded 5 min before.
     * - Tasks without a time get a morning reminder at 8:00 AM on the due date.
     */
    public static void scheduleAll(Context context, List<TimelineItem> tasks) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Cancel all existing reminders first
        for (int i = 0; i < 100; i++) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, i, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }

        // If reminders are disabled in settings, stop here (all cancelled above)
        boolean remindersEnabled = context.getSharedPreferences("ezpz_settings", Context.MODE_PRIVATE)
                .getBoolean("task_reminders", true);
        if (!remindersEnabled) {
            Log.d(TAG, "Reminders disabled in settings - all cancelled");
            return;
        }

        // Schedule new reminders
        int requestCode = 0;
        for (TimelineItem task : tasks) {
            long triggerTime = computeTriggerTime(task);
            if (triggerTime > System.currentTimeMillis()) {
                Intent intent = new Intent(context, ReminderReceiver.class);
                intent.putExtra("task_title", task.getTitle());
                intent.putExtra("task_subtitle", task.getSubtitle());
                PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pi);
                }

                Log.d(TAG, "Scheduled reminder for \"" + task.getTitle() + "\" at "
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(triggerTime)));
                requestCode++;
            }
        }
    }

    /**
     * Parse the task's time and due date to compute when to fire the reminder.
     */
    private static long computeTriggerTime(TimelineItem task) {
        String dueDate = task.getDueDate();
        String subtitle = task.getSubtitle();

        // Parse due date
        Calendar cal = Calendar.getInstance();
        try {
            if (dueDate != null && !dueDate.isEmpty()) {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDate);
                cal.setTime(date);
            }
        } catch (Exception e) {
            // Use today if parse fails
        }

        // Try to extract time from subtitle
        int[] hourMin = parseTime(subtitle);
        if (hourMin != null) {
            cal.set(Calendar.HOUR_OF_DAY, hourMin[0]);
            cal.set(Calendar.MINUTE, hourMin[1]);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            // Remind N minutes before (from task settings)
            cal.add(Calendar.MINUTE, -task.getReminderMinutes());
        } else {
            // No specific time - remind at 8 AM on the due date
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        return cal.getTimeInMillis();
    }

    /**
     * Parse time from subtitle strings like:
     * "2 PM", "2:30 PM", "4:30 PM Ã‚Â· 30 min", "14:30", "6:00 PM Ã‚Â· due today"
     * Returns int[]{hour24, minute} or null if no time found.
     */
    private static int[] parseTime(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return null;
        String s = subtitle.toLowerCase(Locale.ROOT).trim();

        // Pattern 1: "2:30 pm", "4:30 PM", "12:00 am"
        Matcher m1 = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(am|pm)").matcher(s);
        if (m1.find()) {
            int hour = Integer.parseInt(m1.group(1));
            int min = Integer.parseInt(m1.group(2));
            String ampm = m1.group(3);
            if (ampm.equals("pm") && hour != 12) hour += 12;
            if (ampm.equals("am") && hour == 12) hour = 0;
            return new int[]{hour, min};
        }

        // Pattern 2: "2 PM", "6 AM" (no minutes)
        Matcher m2 = Pattern.compile("(\\d{1,2})\\s*(am|pm)").matcher(s);
        if (m2.find()) {
            int hour = Integer.parseInt(m2.group(1));
            String ampm = m2.group(2);
            if (ampm.equals("pm") && hour != 12) hour += 12;
            if (ampm.equals("am") && hour == 12) hour = 0;
            return new int[]{hour, 0};
        }

        // Pattern 3: 24-hour "14:30", "08:00"
        Matcher m3 = Pattern.compile("(\\d{1,2}):(\\d{2})(?!.*(?:am|pm))").matcher(s);
        if (m3.find()) {
            int hour = Integer.parseInt(m3.group(1));
            int min = Integer.parseInt(m3.group(2));
            if (hour >= 0 && hour <= 23 && min >= 0 && min <= 59) {
                return new int[]{hour, min};
            }
        }

        return null;
    }
    public static void sendDeadlineMissed(Context context, TimelineItem task) {
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        
        String channelId = "ezpz_reminders";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Task Reminders", android.app.NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, (int)System.currentTimeMillis(), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(task.getTitle() + ", deadline missed ?")
                .setContentText("You missed the deadline for this task!")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify((task.getTitle() + "_missed").hashCode(), builder.build());
    }
}
