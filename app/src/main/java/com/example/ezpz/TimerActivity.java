package com.example.ezpz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dedicated Timer Activity with:
 * - [#2]  🧠 'Park a Thought' Button
 * - [#6]  🎧 Built-in Brown Noise + Volume Slider
 * - [#13] ♾️ 'Hyperfocus Overtime' Stopwatch (counts UP at 0:00)
 * - [#14] 📳 Halfway & 5-Min Haptic Nudges
 * - [#15] 📌 'Where Was I?' Pause Note on Save & Quit
 * - [#18] 🛑 'Doomscroll Interceptor' Notification when leaving app mid-timer
 * - [#26] 📊 Estimated vs. Actual Time Check
 * - [#27] 🎵 40Hz Gamma Focus Tone + Volume Slider
 */
public class TimerActivity extends AppCompatActivity {

    public static final int RESULT_COMPLETED = 10;
    public static final int RESULT_SAVED     = 11;
    public static final int RESULT_CANCELLED = 12;

    private static final String DOOMSCROLL_CHANNEL_ID = "ezpz_doomscroll_guard";
    private static final int DOOMSCROLL_NOTIF_ID = 909;

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
    private String pauseNote = "";

    // [#26] Estimated vs Actual tracking
    private int estimatedMinutes = 25;
    private long actualElapsedSeconds = 0;

    // [#13] Hyperfocus Overtime Stopwatch
    private boolean inOvertime = false;
    private long overtimeSeconds = 0;
    private final Handler overtimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable overtimeTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (inOvertime && timerRunning) {
                overtimeSeconds++;
                actualElapsedSeconds++;
                long mins = overtimeSeconds / 60;
                long secs = overtimeSeconds % 60;
                timerText.setText(String.format(Locale.US, "+%02d:%02d", mins, secs));
                timerText.setTextColor(0xFF00E676);
                overtimeHandler.postDelayed(this, 1000);
            }
        }
    };

    // [#14] Halfway & 5-Min Haptic Nudges
    private boolean halfwayNudged = false;
    private boolean fiveMinNudged = false;

    // [#18] Doomscroll Interceptor
    private boolean isUserInForeground = true;
    private final Handler doomscrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable doomscrollRunnable = () -> {
        if (!isUserInForeground && timerRunning && !isFinishing()) {
            fireDoomscrollNotification();
        }
    };

    // [#6] & [#27] Audio synthesis + Volume Control
    private static final int AUDIO_OFF = 0;
    private static final int AUDIO_BROWN = 1;
    private static final int AUDIO_GAMMA_40HZ = 2;
    private int currentAudioMode = AUDIO_OFF;
    private float currentVolume = 0.65f;
    private AudioTrack focusAudioTrack;
    private TextView btnBrownNoise;
    private TextView btnGammaTone;
    private LinearLayout volumeRow;

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
                overtimeHandler.removeCallbacks(overtimeTickRunnable);
                timerRunning = false;
                onTimerComplete();
            } else if ("com.example.ezpz.TIMER_PAUSED".equals(action)) {
                if (countDownTimer != null) countDownTimer.cancel();
                overtimeHandler.removeCallbacks(overtimeTickRunnable);
                timerRunning = false;
                timerText.setAlpha(0.4f);
            } else if ("com.example.ezpz.TIMER_RESUMED".equals(action)) {
                long timeLeft = intent.getLongExtra("time_left", timeLeftMillis);
                timeLeftMillis = timeLeft;
                if (countDownTimer != null) countDownTimer.cancel();
                startCountdown();
            } else if ("com.example.ezpz.TIMER_DONE".equals(action)) {
                if (!inOvertime) {
                    enterHyperfocusOvertime();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences modePrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
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

        timerProgress   = findViewById(R.id.timerProgressTimer);
        timerText       = findViewById(R.id.timerTextTimer);
        timerTaskName   = findViewById(R.id.timerTaskNameTimer);
        btnCompleteTask = findViewById(R.id.btnCompleteTaskTimer);
        btnSaveQuit     = findViewById(R.id.btnSaveQuitTimer);
        nextUpPreview   = findViewById(R.id.nextUpPreviewTimer);
        nextUpTask1     = findViewById(R.id.nextUpTask1Timer);
        nextUpTask2     = findViewById(R.id.nextUpTask2Timer);

        if (nextUpPreview != null) {
            float d = getResources().getDisplayMetrics().density;
            nextUpPreview.setBackgroundResource(R.drawable.bg_gradient_light_rounded);
            int pH = (int) (16 * d);
            int pV = (int) (12 * d);
            nextUpPreview.setPadding(pH, pV, pH, pV);
        }
        if (btnSaveQuit instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView sqCard = (androidx.cardview.widget.CardView) btnSaveQuit;
            sqCard.setCardBackgroundColor(getColor(R.color.btn_secondary_bg));
            if (sqCard.getChildAt(0) instanceof TextView) {
                ((TextView) sqCard.getChildAt(0)).setTextColor(getColor(R.color.btn_secondary_text));
            }
        }
        if (btnCompleteTask instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView cpCard = (androidx.cardview.widget.CardView) btnCompleteTask;
            if (cpCard.getChildAt(0) instanceof TextView) {
                ((TextView) cpCard.getChildAt(0)).setText("Complete \u2713");
            }
        }

        loadProfilePhoto();

        Intent i = getIntent();
        taskTitle    = i.getStringExtra("task_title");
        taskSubtitle = i.getStringExtra("task_subtitle");
        taskDate     = i.getStringExtra("task_date");
        pauseNote    = i.getStringExtra("pause_note");

        boolean isContinue = i.getBooleanExtra("is_continue", false);
        long savedTime  = i.getLongExtra("saved_time", 0);
        long savedTotal = i.getLongExtra("saved_total", 0);

        if (taskTitle == null) taskTitle = "Timer";
        if (taskSubtitle == null) taskSubtitle = "";
        if (taskDate == null) taskDate = "";
        if (pauseNote == null) pauseNote = "";

        if (!pauseNote.isEmpty()) {
            timerTaskName.setText(taskTitle + " \u2022 \uD83D\uDCCC " + pauseNote);
        } else {
            timerTaskName.setText(taskTitle);
        }

        int parsedMins = parseMinutes(taskSubtitle);
        estimatedMinutes = parsedMins > 0 ? parsedMins : 25;

        if (isContinue && savedTime > 0) { actualElapsedSeconds = Math.max(0, ((savedTotal > 0 ? savedTotal : savedTime) - savedTime) / 1000L);
            timeLeftMillis  = savedTime;
            totalTimeMillis = savedTotal > 0 ? savedTotal : savedTime;
        } else {
            timeLeftMillis  = estimatedMinutes * 60 * 1000L;
            totalTimeMillis = timeLeftMillis;
        }

        showNextUpPreview();

        timerProgress.setProgress(0);
        updateTimerText();

        setupAdhdTimerControls();

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

        toggleZenMode(true); if (i.getBooleanExtra("from_active_service", false) && savedTime <= 0) { totalTimeMillis = savedTotal > 0 ? savedTotal : (estimatedMinutes * 60 * 1000L); timeLeftMillis = 0; actualElapsedSeconds = (totalTimeMillis + Math.abs(savedTime)) / 1000L; enterHyperfocusOvertime(); overtimeSeconds = Math.abs(savedTime) / 1000L; } else if (i.getBooleanExtra("is_paused_restore", false)) { timerRunning = false; timerText.setAlpha(0.4f); updateTimerText(); updateProgress(); } else
        resumeTimer();

        btnCompleteTask.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            overtimeHandler.removeCallbacks(overtimeTickRunnable);
            timerRunning = false;
            onTimerComplete();
        });

        // [#15] 'Where Was I?' Pause Note dialog when tapping Save & Quit
        btnSaveQuit.setOnClickListener(v -> promptPauseNoteAndQuit());

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

    private void setupAdhdTimerControls() {
        ConstraintLayout root = findViewById(R.id.timerRoot);
        View btnRow = findViewById(R.id.timerButtonRowTimer);
        if (root == null || btnRow == null) return;

        float d = getResources().getDisplayMetrics().density;

        LinearLayout masterCol = new LinearLayout(this);
        masterCol.setOrientation(LinearLayout.VERTICAL);
        masterCol.setGravity(Gravity.CENTER_HORIZONTAL);
        ConstraintLayout.LayoutParams colLp = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colLp.topToBottom = btnRow.getId();
        colLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        colLp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        colLp.topMargin = (int) (12 * d);
        masterCol.setLayoutParams(colLp);

        // Row 1: 🧠 Park Thought (#2) | +5m | 🌧️ Brown Noise (#6) | 🎵 40Hz Gamma (#27)
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER);

        TextView btnPark = createBoostPill("\uD83E\uDDE0 Park Thought", d, false);
        btnPark.setOnClickListener(v -> showParkThoughtDialog());
        pillRow.addView(btnPark);

        TextView btnPlus5 = createBoostPill("+5m", d, false);
        btnPlus5.setOnClickListener(v -> {
            if (inOvertime) {
                Toast.makeText(this, "Already in Hyperfocus Overtime!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (countDownTimer != null) countDownTimer.cancel();
            timeLeftMillis += 5 * 60 * 1000L;
            totalTimeMillis += 5 * 60 * 1000L;
            updateTimerText();
            updateProgress();
            if (timerRunning) resumeTimer();
            Toast.makeText(this, "+5 minutes added!", Toast.LENGTH_SHORT).show();
        });
        pillRow.addView(btnPlus5);

        btnBrownNoise = createBoostPill("\uD83C\uDF27\uFE0F Brown Noise", d, false);
        btnBrownNoise.setOnClickListener(v -> selectAudioMode(currentAudioMode == AUDIO_BROWN ? AUDIO_OFF : AUDIO_BROWN, d));
        pillRow.addView(btnBrownNoise);

        btnGammaTone = createBoostPill("\uD83C\uDFB5 40Hz Gamma", d, false);
        btnGammaTone.setOnClickListener(v -> selectAudioMode(currentAudioMode == AUDIO_GAMMA_40HZ ? AUDIO_OFF : AUDIO_GAMMA_40HZ, d));
        pillRow.addView(btnGammaTone);

        masterCol.addView(pillRow);

        // Row 2: [#6] Volume Control SeekBar for Brown Noise & 40Hz Gamma
        volumeRow = new LinearLayout(this);
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        volumeRow.setPadding((int) (36 * d), (int) (8 * d), (int) (36 * d), 0);
        volumeRow.setVisibility(View.GONE);

        TextView volIconLow = new TextView(this);
        volIconLow.setText("\uD83D\uDD09");
        volIconLow.setTextSize(14f);
        volumeRow.addView(volIconLow);

        SeekBar volumeSlider = new SeekBar(this);
        volumeSlider.setMax(100);
        volumeSlider.setProgress((int) (currentVolume * 100));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        volumeSlider.setLayoutParams(sbLp);
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentVolume = Math.max(0.02f, progress / 100f);
                applyAudioVolume();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        volumeRow.addView(volumeSlider);

        TextView volIconHigh = new TextView(this);
        volIconHigh.setText("\uD83D\uDD0A");
        volIconHigh.setTextSize(14f);
        volumeRow.addView(volIconHigh);

        masterCol.addView(volumeRow);
        root.addView(masterCol);
    }

    /**
     * [#2] 'Park a Thought' Button in Timer
     */
    private void showParkThoughtDialog() {
        float d = getResources().getDisplayMetrics().density;
        SharedPreferences sp = getSharedPreferences("ezpz_parked", MODE_PRIVATE);
        String existing = sp.getString("thoughts", "");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText input = new EditText(this);
        input.setHint("Dump a random thought or distraction...");
        layout.addView(input);

        if (!existing.isEmpty()) {
            TextView prev = new TextView(this);
            prev.setText("\nParked earlier:\n" + existing);
            prev.setTextSize(12f);
            prev.setPadding(0, (int) (8 * d), 0, 0);
            layout.addView(prev);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("\uD83E\uDDE0 Park a Thought (Timer keeps running)")
                .setView(layout)
                .setPositiveButton("Park It", (dialog, which) -> {
                    String thought = input.getText().toString().trim();
                    if (!thought.isEmpty()) {
                        String time = new SimpleDateFormat("h:mm a", Locale.US).format(new Date());
                        String updated = existing.isEmpty() ? ("\u2022 " + thought + " (" + time + ")") : (existing + "\n\u2022 " + thought + " (" + time + ")");
                        sp.edit().putString("thoughts", updated).apply();
                        Toast.makeText(this, "\uD83E\uDDE0 Thought parked! Stay in your flow.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Add as Task", (dialog, which) -> {
                    String thought = input.getText().toString().trim();
                    if (!thought.isEmpty()) {
                        List<TimelineItem> tasks = TaskManager.loadTasks(this);
                        tasks.add(new TimelineItem(thought, "15 min", false, TaskManager.todayDate(), "Low", "", "Personal", "Low", "", 0));
                        TaskManager.saveTasks(this, tasks);
                        Toast.makeText(this, "\u2705 Added to your task list for later!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * [#15] 'Where Was I?' Pause Note on Save & Quit
     */
    private void promptPauseNoteAndQuit() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        layout.setPadding(pad, pad / 2, pad, 0);

        final EditText input = new EditText(this);
        input.setHint("e.g., Finished Q1-Q3, next check line 85...");
        if (!pauseNote.isEmpty()) input.setText(pauseNote);
        layout.addView(input);

        new android.app.AlertDialog.Builder(this)
                .setTitle("\uD83D\uDCCC Where Was I? (Pause Note)")
                .setMessage("Jot a quick 1-line note so you know exactly where to resume:")
                .setView(layout)
                .setPositiveButton("Save & Quit", (dialog, which) -> {
                    pauseNote = input.getText().toString().trim();
                    saveAndQuit();
                })
                .setNegativeButton("Keep Focusing", null)
                .show();
    }

    private TextView createBoostPill(String text, float d, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11.5f);
        tv.setTextColor(active ? 0xFFFFFFFF : getResources().getColor(R.color.text_primary));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int) (11 * d), (int) (7 * d), (int) (11 * d), (int) (7 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f * d);
        bg.setColor(active ? 0xFF7C4DFF : 0x337C4DFF);
        bg.setStroke((int) (1f * d), 0x887C4DFF);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (6 * d));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void styleBoostPill(TextView tv, boolean active, float d) {
        if (tv == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f * d);
        bg.setColor(active ? 0xFF7C4DFF : 0x337C4DFF);
        bg.setStroke((int) (1f * d), active ? 0xFFFFFFFF : 0x887C4DFF);
        tv.setBackground(bg);
        tv.setTextColor(active ? 0xFFFFFFFF : getResources().getColor(R.color.text_primary));
    }

    /**
     * [#6] Brown Noise & [#27] 40Hz Gamma Focus Tone with live Volume Control
     */
    private void selectAudioMode(int mode, float d) {
        stopAmbientSound();
        currentAudioMode = mode;
        styleBoostPill(btnBrownNoise, mode == AUDIO_BROWN, d);
        styleBoostPill(btnGammaTone, mode == AUDIO_GAMMA_40HZ, d);

        if (mode == AUDIO_OFF) {
            if (volumeRow != null) volumeRow.setVisibility(View.GONE);
            Toast.makeText(this, "Focus sound OFF", Toast.LENGTH_SHORT).show();
            return;
        }

        if (volumeRow != null) volumeRow.setVisibility(View.VISIBLE);
        startSynthesizedAudio(mode);
        Toast.makeText(this, mode == AUDIO_BROWN ? "\uD83C\uDF27\uFE0F Brown Noise ON (adjust volume below)" : "\uD83C\uDFB5 40Hz Gamma Focus Tone ON", Toast.LENGTH_SHORT).show();
    }

    private void startSynthesizedAudio(int mode) {
        stopAmbientSound();
        try {
            int sampleRate = 22050;
            int numSamples = sampleRate * 3;
            short[] samples = new short[numSamples];
            Random rng = new Random(42);
            double lastOut = 0.0;

            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / sampleRate;
                if (mode == AUDIO_BROWN) {
                    double white = rng.nextDouble() * 2.0 - 1.0;
                    lastOut = (lastOut + (0.02 * white)) / 1.02;
                    double clamped = Math.max(-1.0, Math.min(1.0, lastOut * 3.2));
                    samples[i] = (short) (clamped * 6000);
                } else {
                    // [#27] 40Hz Gamma Focus Tone: 40Hz fundamental + 200Hz/240Hz 40Hz-beating warm harmonic
                    double white = rng.nextDouble() * 2.0 - 1.0; lastOut = (lastOut + (0.012 * white)) / 1.012; double deepWash = Math.max(-1.0, Math.min(1.0, lastOut * 2.6)); double warmHum = Math.sin(2.0 * Math.PI * 128.0 * t); double env40Hz = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 40.0 * t); samples[i] = (short) (((0.88 * deepWash + 0.12 * warmHum) * env40Hz) * 3400);
                }
            }

            int fade = 500;
            for (int i = 0; i < fade; i++) {
                float w = (float) i / fade;
                samples[numSamples - fade + i] = (short) (samples[numSamples - fade + i] * (1f - w) + samples[i] * w);
            }

            focusAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    samples.length * 2,
                    AudioTrack.MODE_STATIC);
            focusAudioTrack.write(samples, 0, samples.length);
            focusAudioTrack.setLoopPoints(0, numSamples - fade, -1);
            applyAudioVolume();
            focusAudioTrack.play();
        } catch (Exception ignored) {
            currentAudioMode = AUDIO_OFF;
        }
    }

    private void applyAudioVolume() {
        if (focusAudioTrack != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    focusAudioTrack.setVolume(currentVolume);
                } else {
                    focusAudioTrack.setStereoVolume(currentVolume, currentVolume);
                }
            } catch (Exception ignored) {}
        }
    }

    private void stopAmbientSound() {
        if (focusAudioTrack != null) {
            try { focusAudioTrack.stop(); } catch (Exception ignored) {}
            try { focusAudioTrack.release(); } catch (Exception ignored) {}
            focusAudioTrack = null;
        }
    }

    /**
     * [#18] Doomscroll Interceptor Notification when user leaves app mid-timer
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (timerRunning && !isFinishing()) {
            isUserInForeground = false;
            doomscrollHandler.removeCallbacks(doomscrollRunnable);
            doomscrollHandler.postDelayed(doomscrollRunnable, 15000); // 15s grace period
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isUserInForeground = true;
        doomscrollHandler.removeCallbacks(doomscrollRunnable);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(DOOMSCROLL_NOTIF_ID);
    }

    private void fireDoomscrollNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    DOOMSCROLL_CHANNEL_ID, "Doomscroll Interceptor", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Nudges you back if you leave the app while a focus timer is running");
            nm.createNotificationChannel(ch);
        }

        Intent backIntent = new Intent(this, TimerActivity.class);
        backIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 909, backIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, DOOMSCROLL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("\uD83D\uDED1 Doomscroll Interceptor!")
                .setContentText("Your timer for \"" + taskTitle + "\" is still running. Tap to jump back in!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(DOOMSCROLL_NOTIF_ID, b.build());
    }

    @Override
    protected void onDestroy() {
        stopAmbientSound();
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
        doomscrollHandler.removeCallbacks(doomscrollRunnable);
        super.onDestroy();
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        promptPauseNoteAndQuit();
    }

    // ==================== TIMER CONTROL ====================

    private void startCountdown() {
        if (inOvertime) {
            timerRunning = true;
            timerText.setAlpha(1.0f);
            overtimeHandler.removeCallbacks(overtimeTickRunnable);
            overtimeHandler.postDelayed(overtimeTickRunnable, 1000);
            return;
        }

        countDownTimer = new CountDownTimer(timeLeftMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                actualElapsedSeconds++;
                updateTimerText();
                updateProgress();
                checkHapticNudges(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                timeLeftMillis = 0;
                timerProgress.setProgress(1000);
                try { startService(new Intent(TimerActivity.this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
                // [#13] Enter Hyperfocus Overtime Stopwatch instead of abruptly closing!
                enterHyperfocusOvertime();
            }
        }.start();
        timerRunning = true;
        timerText.setAlpha(1.0f);
    }

    /**
     * [#14] Halfway & 5-Min Haptic Nudges
     */
    private void checkHapticNudges(long msLeft) {
        if (!halfwayNudged && totalTimeMillis >= 120_000L && msLeft <= totalTimeMillis / 2) {
            halfwayNudged = true;
            pulseHaptic(new long[]{0, 80, 70, 80});
            Toast.makeText(this, "\uD83D\uDCF3 Halfway mark! Keep flowing.", Toast.LENGTH_SHORT).show();
        }
        if (!fiveMinNudged && totalTimeMillis > 300_000L && msLeft <= 300_000L) {
            fiveMinNudged = true;
            pulseHaptic(new long[]{0, 90, 60, 90, 60, 120});
            Toast.makeText(this, "\uD83D\uDCF3 5 minutes remaining!", Toast.LENGTH_SHORT).show();
        }
    }

    private void pulseHaptic(long[] pattern) {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(200);
        }
    }

    /**
     * [#13] 'Hyperfocus Overtime' Stopwatch
     */
    private void enterHyperfocusOvertime() {
        if (countDownTimer != null) countDownTimer.cancel();
        inOvertime = true;
        timerRunning = true;
        overtimeSeconds = 0;
        pulseHaptic(new long[]{0, 120, 80, 180});
        timerTaskName.setText("\u267E\uFE0F Hyperfocus Overtime: " + taskTitle);
        timerText.setText("+00:00");
        timerText.setTextColor(0xFF00E676);
        Toast.makeText(this, "\u267E\uFE0F 0:00 reached! Counting UP in Hyperfocus Overtime — tap Complete when done.", Toast.LENGTH_LONG).show();
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
        overtimeHandler.postDelayed(overtimeTickRunnable, 1000);
    }

    private void resumeTimer() {
        if (!inOvertime) {
            try {
                Intent svc = new Intent(this, TimerService.class);
                svc.putExtra("time_left", timeLeftMillis);
                svc.putExtra("total_time", totalTimeMillis);
                svc.putExtra("task_title", taskTitle); svc.putExtra("task_subtitle", taskSubtitle); svc.putExtra("task_date", taskDate);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            } catch (Exception ignored) {}
        }
        startCountdown();
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
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
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
        doomscrollHandler.removeCallbacks(doomscrollRunnable);
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
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
        doomscrollHandler.removeCallbacks(doomscrollRunnable);
        clearSavedTimer();
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

        // [#26] Record Estimated vs Actual Time Check in SharedPreferences
        int actualSecs = (int) Math.max(1, actualElapsedSeconds);
        String today = TaskManager.todayDate();
        SharedPreferences paceSp = getSharedPreferences("ezpz_pace_stats", MODE_PRIVATE);
        String prevLog = paceSp.getString("pace_log_" + today, "");
        String entry = String.format(Locale.US, "\u2022 %s: Est %dm vs Actual %dm %ds",
                taskTitle, estimatedMinutes, actualSecs / 60, actualSecs % 60);
        paceSp.edit().putString("pace_log_" + today, prevLog.isEmpty() ? entry : (prevLog + "\n" + entry)).apply();

        Intent result = new Intent();
        result.putExtra("task_title", taskTitle);
        result.putExtra("task_subtitle", taskSubtitle);
        result.putExtra("task_date", taskDate);
        result.putExtra("estimated_minutes", estimatedMinutes);
        result.putExtra("actual_seconds", actualSecs);
        setResult(RESULT_COMPLETED, result);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void saveAndQuit() {
        stopAmbientSound();
        if (countDownTimer != null) countDownTimer.cancel();
        overtimeHandler.removeCallbacks(overtimeTickRunnable);
        doomscrollHandler.removeCallbacks(doomscrollRunnable);
        timerRunning = false;
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        toggleZenMode(false);

        TimerService.running = false; TimerService.paused = false; getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).edit().clear().apply(); getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit()
                .putLong("time_left", timeLeftMillis)
                .putLong("total_time", totalTimeMillis)
                .putString("task_title", taskTitle)
                .putString("task_subtitle", taskSubtitle)
                .putString("task_date", taskDate)
                .putString("pause_note", pauseNote)
                .apply();

        Toast.makeText(this, pauseNote.isEmpty() ? "Timer saved!" : ("Timer & note saved: \uD83D\uDCCC " + pauseNote), Toast.LENGTH_SHORT).show();

        Intent result = new Intent();
        result.putExtra("task_title", taskTitle);
        result.putExtra("time_left", timeLeftMillis);
        result.putExtra("pause_note", pauseNote);
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
            String lower = subtitle.toLowerCase(Locale.US);
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
        TimerService.running = false; TimerService.paused = false; getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).edit().clear().apply(); TimerService.running = false; TimerService.paused = false; getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).edit().clear().apply(); getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit().clear().apply();
    }

    private void loadProfilePhoto() {
        SharedPreferences settingsPrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
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
