package com.example.ezpz;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Dedicated Timer Activity — completely separate view hierarchy from MainActivity.
 * Receives task info via Intent extras, runs the countdown, and returns a result
 * (completed / saved / cancelled) back to the caller.
 */
public class TimerActivity extends AppCompatActivity {

    public static final int RESULT_COMPLETED = 10;
    public static final int RESULT_SAVED     = 11;
    public static final int RESULT_CANCELLED = 12;

    // UI
    private ProgressBar timerProgress;
    private TextView timerText;
    private TextView timerTaskName;
    private View btnCompleteTask;
    private View btnSaveQuit;
    private LinearLayout nextUpPreview;
    private TextView nextUpTask1;
    private TextView nextUpTask2;

    // Timer state
    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long timeLeftMillis = 0;
    private long totalTimeMillis = 0;
    private String taskTitle = "";
    private String taskSubtitle = "";
    private String taskDate = "";

    // Quick-tap cancel: 4 taps in 2 seconds
    private int cancelTapCount = 0;
    private long firstTapTime = 0;

    // Broadcast receiver for timer actions from notification
    private final android.content.BroadcastReceiver timerReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.ezpz.TIMER_COMPLETE_ACTION".equals(action)) {
                if (countDownTimer != null) countDownTimer.cancel();
                timerRunning = false;
                onTimerComplete();
            } else if ("com.example.ezpz.TIMER_PAUSED".equals(action)) {
                if (countDownTimer != null) countDownTimer.cancel();
                timerRunning = false;
                timerText.setAlpha(0.4f);
            } else if ("com.example.ezpz.TIMER_RESUMED".equals(action)) {
                long timeLeft = intent.getLongExtra("time_left", timeLeftMillis);
                timeLeftMillis = timeLeft;
                if (countDownTimer != null) countDownTimer.cancel();
                startCountdown();
            } else if ("com.example.ezpz.TIMER_DONE".equals(action)) {
                if (countDownTimer != null) countDownTimer.cancel();
                timerRunning = false;
                timeLeftMillis = 0;
                updateTimerText();
                timerProgress.setProgress(1000);
                onTimerComplete();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply dark mode preference
        android.content.SharedPreferences modePrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
        if (modePrefs.getBoolean("dark_mode", false)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        setContentView(R.layout.activity_timer);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.timerRoot), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Bind views
        timerProgress   = findViewById(R.id.timerProgressTimer);
        timerText       = findViewById(R.id.timerTextTimer);
        timerTaskName   = findViewById(R.id.timerTaskNameTimer);
        btnCompleteTask = findViewById(R.id.btnCompleteTaskTimer);
        btnSaveQuit     = findViewById(R.id.btnSaveQuitTimer);
        nextUpPreview   = findViewById(R.id.nextUpPreviewTimer);
        nextUpTask1     = findViewById(R.id.nextUpTask1Timer);
        nextUpTask2     = findViewById(R.id.nextUpTask2Timer);

        // Load profile photo
        loadProfilePhoto();

        // Read extras
        Intent i = getIntent();
        taskTitle    = i.getStringExtra("task_title");
        taskSubtitle = i.getStringExtra("task_subtitle");
        taskDate     = i.getStringExtra("task_date");

        boolean isContinue = i.getBooleanExtra("is_continue", false);
        long savedTime  = i.getLongExtra("saved_time", 0);
        long savedTotal = i.getLongExtra("saved_total", 0);

        if (taskTitle == null) taskTitle = "Timer";
        if (taskSubtitle == null) taskSubtitle = "";
        if (taskDate == null) taskDate = "";

        timerTaskName.setText(taskTitle);

        // Parse time
        if (isContinue && savedTime > 0) {
            timeLeftMillis  = savedTime;
            totalTimeMillis = savedTotal > 0 ? savedTotal : savedTime;
        } else {
            int minutes = parseMinutes(taskSubtitle);
            if (minutes <= 0) minutes = 25; // default 25 min pomodoro
            timeLeftMillis  = minutes * 60 * 1000L;
            totalTimeMillis = timeLeftMillis;
        }

        // Show next-up preview
        showNextUpPreview();

        // Init progress
        timerProgress.setProgress(0);
        updateTimerText();

        // Register broadcast receivers
        android.content.IntentFilter timerFilter = new android.content.IntentFilter();
        timerFilter.addAction("com.example.ezpz.TIMER_COMPLETE_ACTION");
        timerFilter.addAction("com.example.ezpz.TIMER_DONE");
        timerFilter.addAction("com.example.ezpz.TIMER_PAUSED");
        timerFilter.addAction("com.example.ezpz.TIMER_RESUMED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, timerFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timerReceiver, timerFilter);
        }

        // Enable Zen Mode
        toggleZenMode(true);

        // Start the timer
        resumeTimer();

        // Button listeners
        btnCompleteTask.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timerRunning = false;
            onTimerComplete();
        });

        btnSaveQuit.setOnClickListener(v -> saveAndQuit());

        // Tap container → pause / resume / quick-cancel
        View timerContainer = findViewById(R.id.timerContainerTimer);
        timerContainer.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - firstTapTime > 2000) {
                cancelTapCount = 0;
                firstTapTime = now;
            }
            cancelTapCount++;

            if (cancelTapCount >= 4) {
                cancelTimer();
                Toast.makeText(this, "Timer cancelled", Toast.LENGTH_SHORT).show();
                return;
            }

            if (timerRunning) {
                pauseTimer();
            } else {
                resumeTimer();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // Back = save & quit (don't lose progress)
        saveAndQuit();
    }

    // ==================== TIMER CONTROL ====================

    private void startCountdown() {
        countDownTimer = new CountDownTimer(timeLeftMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                updateTimerText();
                updateProgress();
            }

            @Override
            public void onFinish() {
                timeLeftMillis = 0;
                updateTimerText();
                timerProgress.setProgress(1000);
                timerRunning = false;
                try { startService(new Intent(TimerActivity.this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
                onTimerComplete();
            }
        }.start();
        timerRunning = true;
        timerText.setAlpha(1.0f);
    }

    private void resumeTimer() {
        // Start background service
        try {
            Intent svc = new Intent(this, TimerService.class);
            svc.putExtra("time_left", timeLeftMillis);
            svc.putExtra("total_time", totalTimeMillis);
            svc.putExtra("task_title", taskTitle);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {}

        startCountdown();
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        timerText.setAlpha(0.4f);
        try {
            Intent pauseSvc = new Intent(this, TimerService.class).setAction("PAUSE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(pauseSvc);
            } else {
                startService(pauseSvc);
            }
        } catch (Exception ignored) {}
    }

    private void cancelTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        cancelTapCount = 0;
        clearSavedTimer();
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

        setResult(RESULT_CANCELLED);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void onTimerComplete() {
        clearSavedTimer();
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

        // Haptic feedback
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } else if (vibrator != null) {
            vibrator.vibrate(200);
        }

        Toast.makeText(this, "Task complete!", Toast.LENGTH_SHORT).show();

        Intent result = new Intent();
        result.putExtra("task_title", taskTitle);
        result.putExtra("task_subtitle", taskSubtitle);
        result.putExtra("task_date", taskDate);
        setResult(RESULT_COMPLETED, result);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void saveAndQuit() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

        // Persist timer state
        getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit()
                .putLong("time_left", timeLeftMillis)
                .putLong("total_time", totalTimeMillis)
                .putString("task_title", taskTitle)
                .putString("task_subtitle", taskSubtitle)
                .putString("task_date", taskDate)
                .apply();

        Toast.makeText(this, "Timer saved!", Toast.LENGTH_SHORT).show();

        Intent result = new Intent();
        result.putExtra("task_title", taskTitle);
        result.putExtra("time_left", timeLeftMillis);
        setResult(RESULT_SAVED, result);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ==================== HELPERS ====================

    private void updateTimerText() {
        long totalSeconds = timeLeftMillis / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            timerText.setText(String.format("%d:%02d:%02d", hours, minutes, seconds));
        } else {
            timerText.setText(String.format("%d:%02d", minutes, seconds));
        }
    }

    private void updateProgress() {
        if (totalTimeMillis > 0) {
            long elapsed = totalTimeMillis - timeLeftMillis;
            int progress = (int) ((elapsed * 1000) / totalTimeMillis);
            timerProgress.setProgress(progress);
        }
    }

    private int parseMinutes(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) return 0;
        try {
            String lower = subtitle.toLowerCase();
            java.util.regex.Matcher hourMatch = java.util.regex.Pattern
                    .compile("(\\d+\\.?\\d*)\\s*(hours?|hrs?|h)\\b").matcher(lower);
            if (hourMatch.find()) {
                double hours = Double.parseDouble(hourMatch.group(1));
                int extraMin = 0;
                java.util.regex.Matcher extraMinMatch = java.util.regex.Pattern
                        .compile("(\\d+)\\s*(min|m)\\b").matcher(lower);
                if (extraMinMatch.find()) {
                    extraMin = Integer.parseInt(extraMinMatch.group(1));
                }
                return (int) (hours * 60) + extraMin;
            }
            java.util.regex.Matcher minMatch = java.util.regex.Pattern
                    .compile("(\\d+)\\s*(min|mins|m)\\b").matcher(lower);
            if (minMatch.find()) {
                return Integer.parseInt(minMatch.group(1));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void showNextUpPreview() {
        java.util.List<TimelineItem> allItems = TaskManager.loadTasks(this);
        java.util.List<TimelineItem> todayItems = new java.util.ArrayList<>();
        String today = TaskManager.todayDate();
        for (TimelineItem item : allItems) {
            if (today.equals(item.getDueDate()) && !item.getTitle().equals(taskTitle)) {
                todayItems.add(item);
            }
        }

        if (todayItems.isEmpty()) {
            nextUpPreview.setVisibility(View.GONE);
            return;
        }

        nextUpPreview.setVisibility(View.VISIBLE);
        if (todayItems.size() >= 1) {
            nextUpTask1.setText(todayItems.get(0).getTitle() + "  ·  " + todayItems.get(0).getSubtitle());
            nextUpTask1.setVisibility(View.VISIBLE);
        } else {
            nextUpTask1.setVisibility(View.GONE);
        }
        if (todayItems.size() >= 2) {
            nextUpTask2.setText(todayItems.get(1).getTitle() + "  ·  " + todayItems.get(1).getSubtitle());
            nextUpTask2.setVisibility(View.VISIBLE);
        } else {
            nextUpTask2.setVisibility(View.GONE);
        }
    }

    private void toggleZenMode(boolean enable) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            if (enable) {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            } else {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        }
    }

    private void clearSavedTimer() {
        getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit().clear().apply();
    }

    private void loadProfilePhoto() {
        android.content.SharedPreferences settingsPrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
        if (settingsPrefs.getBoolean("has_photo", false)) {
            java.io.File file = new java.io.File(getFilesDir(), "profile_photo.png");
            if (file.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                android.widget.ImageView avatar = findViewById(R.id.timerProfileImage);
                if (avatar != null && bmp != null) {
                    avatar.setImageBitmap(bmp);
                    avatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                }
            }
        }
    }
}
