package com.example.ezpz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MorningBriefingWorker extends Worker {

    private static final String TAG = "MorningBriefingWorker";
    private static final String CHANNEL_ID = "ezpz_updates";

    public MorningBriefingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Executing Daily Morning Briefing WorkManager...");

        StreakManager streakManager = new StreakManager(getApplicationContext());
        int streak = streakManager.getStreak();
        String rankName = streakManager.getRankName();
        int rankColor = streakManager.getRankColor();

        // Fetch a motivational AI briefing in the background
        String tasksJson = TaskManager.getTasksAsJson(getApplicationContext());
        GeminiHelper.generateMorningBriefing(tasksJson, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                // Shorten the response for notification
                String shortMsg = response.length() > 60 ? response.substring(0, 57) + "..." : response;
                String title = "☀️ Morning Briefing • " + rankName + " Rank";
                String body = streak > 0 ? "🔥 " + streak + "-Day Streak! " + shortMsg : shortMsg;
                showNotification(title, body, rankColor);
                Log.d(TAG, "Morning Briefing succeeded.");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Morning Briefing failed: " + error);
                String title = "☀️ Morning Briefing • " + rankName + " Rank";
                showNotification(title, "Good morning! Check your tasks for today.", rankColor);
            }
        });

        // The Gemini call is asynchronous, but for simplicity of this Worker, we return SUCCESS immediately.
        return Result.success();
    }

    private void showNotification(String title, String message, int color) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Auto Updates", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setColor(color)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
