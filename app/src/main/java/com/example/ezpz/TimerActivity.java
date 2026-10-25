package com.example.ezpz;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.Random;

/**
 * Dedicated Timer Activity — completely separate view hierarchy from MainActivity.
 * Includes ADHD 2-Minute Micro-Sprint ("⚡ 2m Sprint"), "+5m" extension, and synthesized
 * soothing Brown Noise ("🌧️ Focus Sound") with zero external audio assets.
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

    // Brown noise ambient sound state
    private AudioTrack brownNoiseTrack;
    private boolean ambientPlaying = false;
    private TextView btnAmbientSound;

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

        if (isContinue && savedTime > 0) {
            timeLeftMillis  = savedTime;
            totalTimeMillis = savedTotal > 0 ? savedTotal : savedTime;
        } else {
            int minutes = parseMinutes(taskSubtitle);
            if (minutes <= 0) minutes = 25;
            timeLeftMillis  = minutes * 60 * 1000L;
            totalTimeMillis = timeLeftMillis;
        }

        showNextUpPreview();

        timerProgress.setProgress(0);
        updateTimerText();

        // Inject ADHD Micro-Sprint & Brown Noise row below timerButtonRowTimer
        setupAdhdTimerBoostRow();

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

        toggleZenMode(true);
        resumeTimer();

        btnCompleteTask.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timerRunning = false;
            onTimerComplete();
        });

        btnSaveQuit.setOnClickListener(v -> saveAndQuit());

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

    private void setupAdhdTimerBoostRow() {
        ConstraintLayout root = findViewById(R.id.timerRoot);
        View btnRow = findViewById(R.id.timerButtonRowTimer);
        if (root == null || btnRow == null) return;

        float d = getResources().getDisplayMetrics().density;
        LinearLayout boostRow = new LinearLayout(this);
        boostRow.setOrientation(LinearLayout.HORIZONTAL);
        boostRow.setGravity(Gravity.CENTER);

        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topToBottom = btnRow.getId();
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topMargin = (int) (14 * d);
        boostRow.setLayoutParams(lp);

        // 1) ⚡ 2m Sprint (Just Start Micro-Commitment)
        TextView btnSprint = createBoostPill("\u26A1 2m Sprint", d, false);
        btnSprint.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timeLeftMillis = 2 * 60 * 1000L;
            totalTimeMillis = timeLeftMillis;
            timerProgress.setProgress(0);
            updateTimerText();
            resumeTimer();
            Toast.makeText(this, "\u26A1 2-Min Micro-Sprint! Just start — you can stop after 120s.", Toast.LENGTH_SHORT).show();
        });
        boostRow.addView(btnSprint);

        // 2) +5m Extend
        TextView btnPlus5 = createBoostPill("+5m", d, false);
        btnPlus5.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timeLeftMillis += 5 * 60 * 1000L;
            totalTimeMillis += 5 * 60 * 1000L;
            updateTimerText();
            updateProgress();
            if (timerRunning) resumeTimer();
            Toast.makeText(this, "+5 minutes added!", Toast.LENGTH_SHORT).show();
        });
        boostRow.addView(btnPlus5);

        // 3) 🌧️ Brown Noise Focus Sound
        btnAmbientSound = createBoostPill("\uD83C\uDF27\uFE0F Brown Noise", d, false);
        btnAmbientSound.setOnClickListener(v -> toggleAmbientSound(d));
        boostRow.addView(btnAmbientSound);

        root.addView(boostRow);
    }

    private TextView createBoostPill(String text, float d, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(active ? 0xFFFFFFFF : getResources().getColor(R.color.text_primary));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18f * d);
        bg.setColor(active ? 0xFF7C4DFF : 0x337C4DFF);
        bg.setStroke((int) (1f * d), 0x887C4DFF);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (8 * d));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void toggleAmbientSound(float d) {
        if (ambientPlaying) {
            stopAmbientSound();
            if (btnAmbientSound != null) {
                btnAmbientSound.setText("\uD83C\uDF27\uFE0F Brown Noise");
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18f * d);
                bg.setColor(0x337C4DFF);
                bg.setStroke((int) (1f * d), 0x887C4DFF);
                btnAmbientSound.setBackground(bg);
                btnAmbientSound.setTextColor(getResources().getColor(R.color.text_primary));
            }
            Toast.makeText(this, "Focus sound OFF", Toast.LENGTH_SHORT).show();
        } else {
            startAmbientSound();
            if (btnAmbientSound != null) {
                btnAmbientSound.setText("\uD83D\uDD0A Brown Noise ON");
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18f * d);
                bg.setColor(0xFF7C4DFF);
                bg.setStroke((int) (1f * d), 0xFFFFFFFF);
                btnAmbientSound.setBackground(bg);
                btnAmbientSound.setTextColor(0xFFFFFFFF);
            }
            Toast.makeText(this, "\uD83C\uDF27\uFE0F Soothing Brown Noise playing", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAmbientSound() {
        stopAmbientSound();
        try {
            int sampleRate = 22050;
            int numSamples = sampleRate * 3; // 3-second seamless loop
            short[] samples = new short[numSamples];
            Random rng = new Random(42);
            double lastOut = 0.0;
            for (int i = 0; i < numSamples; i++) {
                double white = rng.nextDouble() * 2.0 - 1.0;
                lastOut = (lastOut + (0.02 * white)) / 1.02;
                double clamped = Math.max(-1.0, Math.min(1.0, lastOut * 3.2));
                samples[i] = (short) (clamped * 4200);
            }
            // Crossfade edges for click-free looping
            int fade = 500;
            for (int i = 0; i < fade; i++) {
                float w = (float) i / fade;
                samples[numSamples - fade + i] = (short) (samples[numSamples - fade + i] * (1f - w) + samples[i] * w);
            }
            brownNoiseTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.length * 2,
                    AudioTrack.MODE_STATIC);
            brownNoiseTrack.write(samples, 0, samples.length);
            brownNoiseTrack.setLoopPoints(0, numSamples - fade, -1);
            brownNoiseTrack.play();
            ambientPlaying = true;
        } catch (Exception ignored) {
            ambientPlaying = false;
        }
    }

    private void stopAmbientSound() {
        ambientPlaying = false;
        if (brownNoiseTrack != null) {
            try {
                brownNoiseTrack.stop();
            } catch (Exception ignored) {}
            try {
                brownNoiseTrack.release();
            } catch (Exception ignored) {}
            brownNoiseTrack = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopAmbientSound();
        super.onDestroy();
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
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
        stopAmbientSound();
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
        stopAmbientSound();
        clearSavedTimer();
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

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
        stopAmbientSound();
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

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
            nextUpTask1.setText(todayItems.get(0).getTitle() + " \u2022 " + todayItems.get(0).getSubtitle());
            nextUpTask1.setVisibility(View.VISIBLE);
        } else {
            nextUpTask1.setVisibility(View.GONE);
        }
        if (todayItems.size() >= 2) {
            nextUpTask2.setText(todayItems.get(1).getTitle() + " \u2022 " + todayItems.get(1).getSubtitle());
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
