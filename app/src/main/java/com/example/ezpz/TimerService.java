package com.example.ezpz;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class TimerService extends Service {

    private static final String CHANNEL_ID = "ezpz_timer";
    private static final int NOTIF_ID = 42;

    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_COMPLETE = "COMPLETE";

    private CountDownTimer timer;
    private long timeLeftMs;
    private long totalTimeMs;
    private String taskName = "Timer";

    public static boolean running = false;
    public static boolean paused = false;
    public static long remainingMs = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Active Timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows countdown while timer is running");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case ACTION_STOP:
                    stopTimer();
                    return START_NOT_STICKY;
                case ACTION_PAUSE:
                    pauseTimer();
                    return START_NOT_STICKY;
                case ACTION_RESUME:
                    resumeTimer();
                    return START_NOT_STICKY;
                case ACTION_COMPLETE:
                    completeTimer();
                    return START_NOT_STICKY;
            }
        }

        timeLeftMs = intent.getLongExtra("time_left", 0);
        totalTimeMs = intent.getLongExtra("total_time", timeLeftMs);
        taskName = intent.getStringExtra("task_title");
        if (taskName == null) taskName = "Timer";

        // Save state for app restore
        saveTimerState();

        startForeground(NOTIF_ID, buildNotif(fmt(timeLeftMs), true));
        running = true;
        paused = false;
        remainingMs = timeLeftMs;

        startCountdown();
        return START_NOT_STICKY;
    }

    private void startCountdown() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(timeLeftMs, 1000) {
            @Override
            public void onTick(long ms) {
                timeLeftMs = ms;
                remainingMs = ms;
                saveTimerState();
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIF_ID, buildNotif(fmt(ms), true));
            }

            @Override
            public void onFinish() {
                running = false;
                paused = false;
                remainingMs = 0;
                clearTimerState();
                Intent doneBC = new Intent("com.example.ezpz.TIMER_DONE");
                doneBC.setPackage(getPackageName());
                sendBroadcast(doneBC);

                NotificationCompat.Builder b = new NotificationCompat.Builder(TimerService.this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("Timer Complete!")
                        .setContentText(taskName + " is done!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL);

                Intent open = new Intent(TimerService.this, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                open.putExtra("timer_completed", true);
                PendingIntent pi = PendingIntent.getActivity(TimerService.this, 99, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                b.setContentIntent(pi);

                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIF_ID + 1, b.build());

                stopForeground(true);
                stopSelf();
            }
        };
        timer.start();
    }

    private void pauseTimer() {
        if (timer != null) timer.cancel();
        paused = true;
        running = true;
        saveTimerState();
        Intent bc = new Intent("com.example.ezpz.TIMER_PAUSED");
        bc.setPackage(getPackageName());
        sendBroadcast(bc);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotif(fmt(timeLeftMs) + " (Paused)", false));
    }

    private void resumeTimer() {
        paused = false;
        running = true;
        saveTimerState();
        Intent bc = new Intent("com.example.ezpz.TIMER_RESUMED");
        bc.setPackage(getPackageName());
        bc.putExtra("time_left", timeLeftMs);
        sendBroadcast(bc);
        startCountdown();
    }

    private void completeTimer() {
        if (timer != null) timer.cancel();
        running = false;
        paused = false;
        remainingMs = 0;
        clearTimerState();
        Intent completeBC = new Intent("com.example.ezpz.TIMER_COMPLETE_ACTION");
                completeBC.setPackage(getPackageName());
                sendBroadcast(completeBC);
        stopForeground(true);
        stopSelf();
    }

    private void stopTimer() {
        if (timer != null) timer.cancel();
        running = false;
        paused = false;
        clearTimerState();
        stopForeground(true);
        stopSelf();
    }

    private void saveTimerState() {
        getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).edit()
                .putLong("time_left", timeLeftMs)
                .putLong("total_time", totalTimeMs)
                .putString("task_title", taskName)
                .putBoolean("is_running", running)
                .putBoolean("is_paused", paused)
                .putLong("last_update", System.currentTimeMillis())
                .apply();
    }

    private void clearTimerState() {
        getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).edit().clear().apply();
    }

    private Notification buildNotif(String time, boolean isRunning) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("from_timer", true);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(isRunning ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentTitle(taskName)
                .setContentText(time + " remaining")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi);

        // Pause/Resume action
        if (isRunning) {
            Intent pauseIntent = new Intent(this, TimerService.class).setAction(ACTION_PAUSE);
            PendingIntent pausePi = PendingIntent.getService(this, 1, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePi);
        } else {
            Intent resumeIntent = new Intent(this, TimerService.class).setAction(ACTION_RESUME);
            PendingIntent resumePi = PendingIntent.getService(this, 2, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePi);
        }

        // Complete action
        Intent completeIntent = new Intent(this, TimerService.class).setAction(ACTION_COMPLETE);
        PendingIntent completePi = PendingIntent.getService(this, 3, completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_menu_send, "Complete", completePi);

        return builder.build();
    }

    private String fmt(long ms) {
        long s = ms / 1000;
        long h = s / 3600; long m = (s % 3600) / 60; s = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        if (timer != null) timer.cancel();
        running = false;
        super.onDestroy();
    }
}