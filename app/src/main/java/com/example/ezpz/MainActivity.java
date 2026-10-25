package com.example.ezpz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.os.Build;
import android.os.CountDownTimer;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private boolean isDraggingCard = false;
    private long lastDragEndTime = 0L;
    private String currentFilter = "All";
    private boolean lowBatteryFilter = false;
    private boolean snowballSortMode = false;
    private boolean lockScreenPinned = false; private boolean isLaunchingActiveTimer = false; private void purgeCompletedDuplicates() { String today = TaskManager.todayDate(); List<TimelineItem> history = HistoryManager.getHistory(this); if (history == null || history.isEmpty() || allItems == null || allItems.isEmpty()) return; java.util.Set<String> doneTitles = new java.util.HashSet<>(); for (TimelineItem h : history) { if (today.equals(h.getDueDate()) && h.getTitle() != null) doneTitles.add(h.getTitle().trim().toLowerCase(Locale.US)); } if (doneTitles.isEmpty()) return; boolean changed = false; java.util.Iterator<TimelineItem> it = allItems.iterator(); while (it.hasNext()) { TimelineItem t = it.next(); if (t.getTitle() != null && today.equals(t.getDueDate()) && doneTitles.contains(t.getTitle().trim().toLowerCase(Locale.US))) { it.remove(); changed = true; } } if (changed) TaskManager.saveTasks(this, allItems); } private boolean checkAndResumeActiveTimer() { if (isLaunchingActiveTimer) return true; android.content.SharedPreferences sp = getSharedPreferences("ezpz_active_timer", MODE_PRIVATE); boolean isRunning = sp.getBoolean("is_running", false); if (!isRunning && !TimerService.running) return false; String title = sp.getString("task_title", ""); String subtitle = sp.getString("task_subtitle", ""); String date = sp.getString("task_date", TaskManager.todayDate()); long timeLeft = sp.getLong("time_left", 0); long totalTime = sp.getLong("total_time", timeLeft); boolean isPaused = sp.getBoolean("is_paused", false); long lastUpdate = sp.getLong("last_update", 0); if (title == null || title.isEmpty()) return false; if (!isPaused && lastUpdate > 0) timeLeft = timeLeft - (System.currentTimeMillis() - lastUpdate); isLaunchingActiveTimer = true; Intent ti = new Intent(this, TimerActivity.class); ti.putExtra("task_title", title); ti.putExtra("task_subtitle", subtitle); ti.putExtra("task_date", date); ti.putExtra("is_continue", true); ti.putExtra("from_active_service", true); ti.putExtra("is_paused_restore", isPaused); ti.putExtra("saved_time", timeLeft); ti.putExtra("saved_total", totalTime > 0 ? totalTime : 25 * 60 * 1000L); startActivityForResult(ti, 200); overridePendingTransition(0, 0); return true; }
    private static final int REQUEST_VOICE_TASK = 42;
    private static final String LOCK_PIN_CHANNEL_ID = "ezpz_lockscreen_pin";
    private static final int LOCK_PIN_NOTIF_ID = 777;

    private View move;

    private List<TimelineItem> allItems = new ArrayList<>();
    private List<TimelineItem> visibleItems = new ArrayList<>();
    private TimelineAdapter adapter;

    // Inline breakdown sheet
    private LinearLayout inlineBreakdownSheet;
    private TextView textSheetTitle;
    private TextView textSheetContent;
    private Button btnCancelSheet;
    private Button btnAcceptSheet;

    // Timer
    private FrameLayout timerContainer;
    private ProgressBar timerProgress;
    private TextView timerText;
    private TextView timerTaskName;
    private View btnCompleteTask;
    private View btnSaveQuit;
    private LinearLayout timerButtonRow;
    private LinearLayout nextUpPreview;
    private TextView nextUpTask1;
    private TextView nextUpTask2;
    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long timeLeftMillis = 0;
    private long totalTimeMillis = 0;
    private TimelineItem currentTimerTask;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

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
                countDownTimer = new CountDownTimer(timeLeftMillis, 1000) {
                    @Override
                    public void onTick(long ms) {
                        timeLeftMillis = ms;
                        updateTimerText();
                        updateProgress();
                    }
                    @Override
                    public void onFinish() {
                        timeLeftMillis = 0;
                        updateTimerText();
                        timerProgress.setProgress(1000);
                        timerRunning = false;
                        onTimerComplete();
                    }
                }.start();
                timerRunning = true;
                timerText.setAlpha(1.0f);
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

    private int cancelTapCount = 0;
    private long firstTapTime = 0;

    // Views to toggle
    private TextView upNextLabel;
    private RecyclerView recyclerTimeline;
    private View aCard;
    private View cardView3;

    // Stats
    private StreakManager streakManager;
    private TextView statsText;
    private TextView rankText;
    private TextView streakText;
    private View inputBar;

    // Free time quest
    private TextView questTitle;
    private Button questAcceptBtn;

    // Store the last AI JSON response
    private String lastAiJson = "";
    private boolean lastAiKeepOrder = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences modePrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
        if (modePrefs.getBoolean("dark_mode", false)) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (getSharedPreferences("ezpz_settings", MODE_PRIVATE).getBoolean("morning_briefing", true)) {
            java.util.Calendar currentDate = java.util.Calendar.getInstance();
            java.util.Calendar dueDate = java.util.Calendar.getInstance();
            dueDate.set(java.util.Calendar.HOUR_OF_DAY, 8);
            dueDate.set(java.util.Calendar.MINUTE, 0);
            dueDate.set(java.util.Calendar.SECOND, 0);
            if (dueDate.before(currentDate)) {
                dueDate.add(java.util.Calendar.HOUR_OF_DAY, 24);
            }
            long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();

            androidx.work.PeriodicWorkRequest dailyWorkRequest =
                    new androidx.work.PeriodicWorkRequest.Builder(MorningBriefingWorker.class, 24, java.util.concurrent.TimeUnit.HOURS)
                            .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .build();

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "MorningBriefingWorker",
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    dailyWorkRequest);
        } else {
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("MorningBriefingWorker");
        }
        lockScreenPinned = getSharedPreferences("ezpz_settings", MODE_PRIVATE).getBoolean("lock_screen_pinned", false); setupFilterChips();

        android.view.View navIndicator = findViewById(R.id.navIndicator);
        android.view.View bottomNavBar = findViewById(R.id.bottomNavBar);
        if (navIndicator != null && bottomNavBar != null) {
            int prevTab = getIntent().getIntExtra("prev_tab", 0);
            bottomNavBar.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    bottomNavBar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int navWidth = bottomNavBar.getWidth();
                    if (navWidth == 0) return;
                    int pad = (int)(6 * getResources().getDisplayMetrics().density);
                    int usableWidth = navWidth - (pad * 2);
                    int tabW = usableWidth / 4;

                    android.view.ViewGroup.LayoutParams lp = navIndicator.getLayoutParams();
                    lp.width = tabW;
                    navIndicator.setLayoutParams(lp);

                    float startPos = pad + (prevTab * tabW);
                    float endPos = pad + (0 * tabW);

                    navIndicator.setTranslationX(startPos);
                    if (prevTab != 0) {
                        navIndicator.animate().translationX(endPos).setDuration(250)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                }
            });
        }

        move = findViewById(R.id.navCalendar);
        move.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            intent.putExtra("prev_tab", 0);
            startActivityForResult(intent, 100);
            overridePendingTransition(0, 0);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 200);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadProfilePhoto();

        EditText editText = findViewById(R.id.editText3);
        editText.setOnTouchListener((v, event) -> {
            if (v.getId() == R.id.editText3 && editText.getLineCount() > 4) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        allItems = TaskManager.loadTasks(this); purgeCompletedDuplicates();

        RecyclerView recyclerView = findViewById(R.id.recyclerTimeline);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        android.view.animation.LayoutAnimationController animation = android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_anim_slide_up);
        recyclerView.setLayoutAnimation(animation);

        updateVisibleItems();
        adapter = new TimelineAdapter(visibleItems);
        recyclerView.setAdapter(adapter);

        // Swipe to Delete (Left) and Complete (Right) with Undo Snackbar + [#22] 2x XP Critical Hit
        new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(androidx.recyclerview.widget.RecyclerView rv, androidx.recyclerview.widget.RecyclerView.ViewHolder vh, androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                int fromPos = vh.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                if (fromPos < 0 || toPos < 0 || fromPos >= visibleItems.size() || toPos >= visibleItems.size()) {
                    return false;
                }
                int realFrom = findTaskIndex(visibleItems.get(fromPos));
                int realTo = findTaskIndex(visibleItems.get(toPos));
                java.util.Collections.swap(visibleItems, fromPos, toPos);
                if (realFrom >= 0 && realTo >= 0 && realFrom != realTo) {
                    java.util.Collections.swap(allItems, realFrom, realTo);
                }
                adapter.notifyItemMoved(fromPos, toPos);
                rv.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return true;
            }

            @Override
            public void onSelectedChanged(androidx.recyclerview.widget.RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE) {
                    lastDragEndTime = System.currentTimeMillis();
                }
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    isDraggingCard = true;
                    vh.itemView.cancelPendingInputEvents();
                    vh.itemView.setPressed(false);
                    vh.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    vh.itemView.animate().scaleX(1.03f).scaleY(1.03f).alpha(0.92f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(androidx.recyclerview.widget.RecyclerView rv, androidx.recyclerview.widget.RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                lastDragEndTime = System.currentTimeMillis();
                rv.postDelayed(() -> isDraggingCard = false, 250);
                vh.itemView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start();
                TaskManager.saveTasks(MainActivity.this, allItems);
                ReminderHelper.scheduleAll(MainActivity.this, allItems);
                rv.post(() -> {
                    if (!rv.isComputingLayout()) {
                        updateVisibleItems();
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onSwiped(androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos >= 0 && pos < visibleItems.size()) {
                    final TimelineItem removed = visibleItems.get(pos);
                    final int realIdx = findTaskIndex(removed);
                    if (realIdx >= 0) {
                        allItems.remove(realIdx);
                        GoogleCalendarHelper.deleteTaskFromCalendar(MainActivity.this, removed);
                        final boolean wasCompleted = (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT);
                        if (wasCompleted) {
                            HistoryManager.saveCompletedTask(MainActivity.this, removed);
                            boolean earnedShield = streakManager.taskCompleted();
                            boolean crit = streakManager.wasLastCriticalHit();
                            triggerCompletionHaptics(crit);
                            showCompletionFlash(crit);
                            if (crit && earnedShield) {
                                Toast.makeText(MainActivity.this, "\uD83C\uDFB0 2x XP CRITICAL HIT! + \uD83D\uDEE1\uFE0F Earned Streak Shield!", Toast.LENGTH_LONG).show();
                            } else if (crit) {
                                Toast.makeText(MainActivity.this, "\uD83C\uDFB0 2x XP CRITICAL HIT! (+20 XP & 2x Shield Progress)", Toast.LENGTH_SHORT).show();
                            } else if (earnedShield) {
                                Toast.makeText(MainActivity.this, "\uD83D\uDEE1\uFE0F Earned +1 Streak Shield!", Toast.LENGTH_SHORT).show();
                            }
                        }
                        TaskManager.saveTasks(MainActivity.this, allItems);
                        ReminderHelper.scheduleAll(MainActivity.this, allItems);
                        updateVisibleItems();
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        updateStats();

                        View rootView = findViewById(R.id.main);
                        if (rootView != null) {
                            Snackbar.make(rootView,
                                    wasCompleted ? ("\u2705 Completed: " + removed.getTitle()) : ("\uD83D\uDDD1\uFE0F Deleted: " + removed.getTitle()),
                                    Snackbar.LENGTH_LONG)
                                    .setAction("UNDO", v -> {
                                        int insertPos = Math.min(realIdx, allItems.size());
                                        allItems.add(insertPos, removed);
                                        TaskManager.saveTasks(MainActivity.this, allItems);
                                        ReminderHelper.scheduleAll(MainActivity.this, allItems);
                                        updateVisibleItems();
                                        adapter.notifyDataSetChanged();
                                        updateEmptyState();
                                        updateStats();
                                    })
                                    .setActionTextColor(0xFFFFD700)
                                    .show();
                        }
                    }
                }
            }

            @Override
            public void onChildDraw(android.graphics.Canvas c, androidx.recyclerview.widget.RecyclerView rv,
                    androidx.recyclerview.widget.RecyclerView.ViewHolder vh, float dX, float dY,
                    int actionState, boolean isCurrentlyActive) {
                View itemView = vh.itemView;
                float radius = 24f;
                android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(0xFFFFFFFF);
                textPaint.setTextSize(32f);
                textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                android.graphics.Paint iconPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                iconPaint.setTextSize(28f);
                iconPaint.setTextAlign(android.graphics.Paint.Align.CENTER);

                if (dX < 0) {
                    float left = itemView.getRight() + dX;
                    float top = itemView.getTop() + 4;
                    float right = (float) itemView.getRight();
                    float bottom = itemView.getBottom() - 4;
                    android.graphics.Paint bgPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    bgPaint.setShader(new android.graphics.LinearGradient(right, top, left, bottom, 0xFFFF416C, 0xFFFF4B2B, android.graphics.Shader.TileMode.CLAMP));
                    c.drawRoundRect(new android.graphics.RectF(left, top, right, bottom), radius, radius, bgPaint);
                    float centerX = right - 60;
                    float centerY = (top + bottom) / 2f;
                    c.drawText("\uD83D\uDDD1\uFE0F", centerX, centerY - 10, iconPaint);
                    c.drawText("Delete", centerX, centerY + 26, textPaint);
                } else if (dX > 0) {
                    float left = (float) itemView.getLeft();
                    float top = itemView.getTop() + 4;
                    float right = itemView.getLeft() + dX;
                    float bottom = itemView.getBottom() - 4;
                    android.graphics.Paint bgPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    bgPaint.setShader(new android.graphics.LinearGradient(left, top, right, bottom, 0xFF0BA360, 0xFF3CB371, android.graphics.Shader.TileMode.CLAMP));
                    c.drawRoundRect(new android.graphics.RectF(left, top, right, bottom), radius, radius, bgPaint);
                    float centerX = left + 60;
                    float centerY = (top + bottom) / 2f;
                    c.drawText("\u2705", centerX, centerY - 10, iconPaint);
                    c.drawText("Done", centerX, centerY + 26, textPaint);
                }

                float alpha = 1.0f - Math.abs(dX) / (float) itemView.getWidth();
                itemView.setAlpha(Math.max(0.2f, alpha));
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }
        }).attachToRecyclerView(recyclerView);

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

        timerContainer = findViewById(R.id.timerContainer);
        timerProgress = findViewById(R.id.timerProgress);
        timerText = findViewById(R.id.timerText);
        timerTaskName = findViewById(R.id.timerTaskName);
        btnCompleteTask = findViewById(R.id.btnCompleteTask);
        btnSaveQuit = findViewById(R.id.btnSaveQuit);
        timerButtonRow = findViewById(R.id.timerButtonRow);
        nextUpPreview = findViewById(R.id.nextUpPreview);
        nextUpTask1 = findViewById(R.id.nextUpTask1);
        nextUpTask2 = findViewById(R.id.nextUpTask2);

        btnCompleteTask.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timerRunning = false;
            onTimerComplete();
        });

        btnSaveQuit.setOnClickListener(v -> saveAndQuitTimer());

        adapter.setOnEditClickListener((item, position) -> {
            int realIndex = findTaskIndex(item);
            Intent editIntent = new Intent(MainActivity.this, TaskEditActivity.class);
            editIntent.putExtra("task_title", item.getTitle());
            editIntent.putExtra("task_subtitle", item.getSubtitle());
            editIntent.putExtra("task_index", realIndex);
            editIntent.putExtra("task_description", item.getDescription());
            editIntent.putExtra("task_priority", item.getPriority());
            editIntent.putExtra("task_category", item.getCategory());
            editIntent.putExtra("task_energy", item.getEnergyLevel());
            editIntent.putExtra("task_deadline", item.getDeadline());
            editIntent.putExtra("task_due_date", item.getDueDate());
            editIntent.putExtra("task_reminder", item.getReminderMinutes());
            startActivityForResult(editIntent, 100);
            overridePendingTransition(0, 0);
        });

        // 1-Tap AI "3-Step Micro-Splitter" with description check!
        adapter.setOnSplitClickListener((item, position) -> splitTaskInto3Steps(item));

        adapter.setOnLongClickListener(null);

        // Continue saved timer -> launch TimerActivity with saved time and pause_note (#15)
        adapter.setOnContinueClickListener((item, position) -> {
            android.content.SharedPreferences sp = getSharedPreferences("ezpz_timer", MODE_PRIVATE);
            long savedTime = sp.getLong("time_left", 0);
            long savedTotal = sp.getLong("total_time", savedTime);
            String pauseNote = sp.getString("pause_note", "");
            clearSavedTimer();
            adapter.clearSavedTimer();
            cancelLockScreenPinNotification(); Intent timerIntent = new Intent(this, TimerActivity.class);
            timerIntent.putExtra("task_title", item.getTitle());
            timerIntent.putExtra("task_subtitle", item.getSubtitle());
            timerIntent.putExtra("task_date", item.getDueDate());
            timerIntent.putExtra("is_continue", true);
            timerIntent.putExtra("saved_time", savedTime);
            timerIntent.putExtra("saved_total", savedTotal);
            timerIntent.putExtra("pause_note", pauseNote);
            startActivityForResult(timerIntent, 200);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        adapter.setOnRestartClickListener((item, position) -> {
            clearSavedTimer();
            adapter.clearSavedTimer();
            startTimer(item);
        });

        adapter.setOnStartClickListener((item, position) -> {
            int allItemsIndex = findTaskIndex(item);
            if (allItemsIndex > 0 && allItemsIndex < allItems.size()) {
                allItems.remove(allItemsIndex);
                allItems.add(0, item);
                TaskManager.saveTasks(this, allItems);
                ReminderHelper.scheduleAll(this, allItems);
                updateVisibleItems();
                adapter.notifyDataSetChanged();
            }
            startTimer(item);
        });

        inlineBreakdownSheet = findViewById(R.id.inlineBreakdownSheet);
        textSheetTitle = findViewById(R.id.textSheetTitle);
        textSheetContent = findViewById(R.id.textSheetContent);
        btnCancelSheet = findViewById(R.id.btnCancelSheet);
        btnAcceptSheet = findViewById(R.id.btnAcceptSheet);

        statsText = findViewById(R.id.textView5);
        rankText = findViewById(R.id.rankText);
        streakText = findViewById(R.id.textView6);
        inputBar = findViewById(R.id.cardView19);
        android.content.SharedPreferences streakPrefs = getSharedPreferences("ezpz_streak", MODE_PRIVATE);
        if (streakPrefs.getInt("streak_count", 0) == 3 || !streakPrefs.getBoolean("streak_fresh_init", false)) {
            streakPrefs.edit().clear().putBoolean("streak_fresh_init", true).apply();
        }
        streakManager = new StreakManager(this);
        streakManager.checkDayRollover(countTasksDueToday());
        updateStats();

        questTitle = findViewById(R.id.textView);
        questAcceptBtn = findViewById(R.id.button);
        loadFreeTimeQuest();

        upNextLabel = findViewById(R.id.textView7);
        if (upNextLabel != null) {
            upNextLabel.setText("Up next \u21C5");
            upNextLabel.setOnClickListener(v -> showReorderTodayDialog());
        }
        recyclerTimeline = findViewById(R.id.recyclerTimeline);
        aCard = findViewById(R.id.Acard);
        cardView3 = findViewById(R.id.cardView3);
        if (cardView3 != null) {
            float d = getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable streakBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    new int[]{getColor(R.color.purple_primary_start), getColor(R.color.purple_primary_end)});
            streakBg.setCornerRadius(22f * d);
            streakBg.setStroke((int) (1.2f * d), 0x55FFFFFF);
            cardView3.setBackground(streakBg);
            cardView3.setElevation(8f * d);
            cardView3.setOnClickListener(v -> showDoneTodayReceiptDialog());
        }

        findViewById(R.id.navSettings).setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class); i.putExtra("prev_tab", 0); startActivity(i);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.navReport).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class); i.putExtra("prev_tab", 0); startActivity(i);
            overridePendingTransition(0, 0);
        });

        findViewById(R.id.btnBriefing).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(MainActivity.this);
                android.view.View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_ai_briefing, null);
                bottomSheetDialog.setContentView(sheetView);

                android.widget.TextView tvAiResponse = sheetView.findViewById(R.id.tvAiResponse);
                android.widget.Button btnLetsGo = sheetView.findViewById(R.id.btnLetsGo);

                btnLetsGo.setOnClickListener(btnV -> bottomSheetDialog.dismiss());

                bottomSheetDialog.show();

                String tasksJson = TaskManager.getTasksAsJson(MainActivity.this);
                GeminiHelper.generateMorningBriefing(tasksJson, new GeminiHelper.GeminiCallback() {
                    @Override
                    public void onSuccess(String response) {
                        runOnUiThread(() -> {
                            tvAiResponse.setText("");
                            final String finalResponse = response;
                            final int[] index = {0};
                            final android.os.Handler handler = new android.os.Handler();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (index[0] < finalResponse.length() && tvAiResponse.isAttachedToWindow()) {
                                        tvAiResponse.append(String.valueOf(finalResponse.charAt(index[0])));
                                        index[0]++;
                                        handler.postDelayed(this, (long) (Math.random() * 20 + 10));
                                    }
                                }
                            });
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvAiResponse.setText("Could not generate briefing: " + error));
                    }
                });
            }).start();
        });

        findViewById(R.id.btnAddTask).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                Intent addIntent = new Intent(MainActivity.this, TaskEditActivity.class);
                addIntent.putExtra("task_index", -1);
                addIntent.putExtra("task_due_date", TaskManager.todayDate());
                startActivityForResult(addIntent, 100);
                overridePendingTransition(0, 0);
            }).start();
        });

        android.view.View sendButton = findViewById(R.id.cardView17);
        android.view.View voiceButton = findViewById(R.id.cardView18);
        EditText taskInput = findViewById(R.id.editText3);

        voiceButton.setOnClickListener(v -> startVoiceInput());

        sendButton.setOnClickListener(v -> {
            String userMessage = taskInput.getText().toString().trim();
            if (!userMessage.isEmpty()) {
                taskInput.setText("");

                if (userMessage.startsWith("+") || userMessage.startsWith("!")) {
                    String quickTitle = userMessage.substring(1).trim();
                    if (!quickTitle.isEmpty()) {
                        TimelineItem quickTask = new TimelineItem(quickTitle, "", false, TaskManager.todayDate());
                        allItems.add(quickTask);
                        TaskManager.saveTasks(this, allItems);
                        ReminderHelper.scheduleAll(this, allItems);
                        updateVisibleItems();
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        updateStats();
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        android.widget.Toast.makeText(this, "Task added!", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                // [#12] Multi-Task Brain Dump AI support
                String brainDumpInstruction = userMessage
                        + "\n(Note: If my message mentions multiple tasks or a brain-dump list, extract and add EVERY task separately with smart durations, categories, and energy levels.)";
                runAiTaskEdit(brainDumpInstruction, "Updating Tasks...");
            }
        });

        btnCancelSheet.setOnClickListener(v -> {
            lastAiJson = "";
            hideBreakdownSheet();
        });

        btnAcceptSheet.setOnClickListener(v -> {
            if (!lastAiJson.isEmpty()) {
                List<TimelineItem> newTasks = TaskManager.parseJsonResponse(lastAiJson);
                if (!lastAiKeepOrder) {
                    TaskManager.sortTasksSmartly(newTasks);
                }
                if (!newTasks.isEmpty() || lastAiJson.contains("\"tasks\"")) {
                    allItems.clear();
                    allItems.addAll(newTasks);
                    TaskManager.saveTasks(this, allItems);
                    ReminderHelper.scheduleAll(this, allItems);
                    updateVisibleItems();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    streakManager.updateTotalTasks(countTasksDueToday() + streakManager.getCompletedToday());
                    updateStats();
                    Toast.makeText(this, "Tasks updated!", Toast.LENGTH_SHORT).show();
                }
                lastAiJson = "";
            }
            hideBreakdownSheet();
        });
    }

    private void runAiTaskEdit(String userMessage, String sheetHeader) {
        showBreakdownSheet();
        textSheetTitle.setText(sheetHeader);
        textSheetContent.setText("Thinking...");

        String currentJson = TaskManager.getTasksAsJson(this);

        GeminiHelper.editTaskList(userMessage, currentJson, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                lastAiJson = response;

                List<TimelineItem> newTasks = TaskManager.parseJsonResponse(response);
                lastAiKeepOrder = userMessage.toLowerCase(Locale.US).matches(".*\\b(reorder|move|top|first|bottom|last|swap|before|after|micro-steps|split)\\b.*");
                if (!lastAiKeepOrder) {
                    TaskManager.sortTasksSmartly(newTasks);
                }
                StringBuilder preview = new StringBuilder();
                if (newTasks.isEmpty()) {
                    preview.append("All tasks cleared.");
                } else {
                    for (int i = 0; i < newTasks.size(); i++) {
                        TimelineItem t = newTasks.get(i);
                        preview.append((i + 1)).append(". ").append(t.getTitle());
                        if (!t.getSubtitle().isEmpty()) {
                            preview.append(" \u2014 ").append(t.getSubtitle());
                        }
                        preview.append("\n   [")
                               .append(t.getCategory()).append(" \u00b7 ")
                               .append(t.getPriority()).append(" \u00b7 \u26a1")
                               .append(t.getEnergyLevel()).append(" \u00b7 ")
                               .append(t.getDueDate()).append("]");
                        if (!t.getDeadline().isEmpty()) {
                            preview.append(" (Deadline: ").append(t.getDeadline()).append(")");
                        }
                        if (!t.getDescription().isEmpty()) {
                            preview.append("\n   \"").append(t.getDescription()).append("\"");
                        }
                        preview.append("\n");
                    }
                }

                runOnUiThread(() -> {
                    textSheetTitle.setText("Updated Task List");
                    textSheetContent.setText(preview.toString().trim());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    textSheetTitle.setText("Error");
                    textSheetContent.setText(error);
                });
            }
        });
    }

    /**
     * [#5] Smarter 3-Step Splitter:
     * If the task has no description, asks the user for 1 line of context first so AI can divide it accurately!
     */
    private void splitTaskInto3Steps(TimelineItem item) {
        String existingDesc = item.getDescription() != null ? item.getDescription().trim() : "";
        if (existingDesc.isEmpty()) {
            float d = getResources().getDisplayMetrics().density;
            EditText input = new EditText(this);
            input.setHint("e.g., Write intro, draw block diagram, test code");
            int pad = (int) (16 * d);
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setPadding(pad, pad / 2, pad, 0);
            wrap.addView(input);

            new android.app.AlertDialog.Builder(this)
                    .setTitle("\u2728 Add Context for 3 Steps")
                    .setMessage("\"" + item.getTitle() + "\" doesn't have a description yet.\nAdd a quick 1-line note on what needs to be done so AI can split it into 3 accurate steps:")
                    .setView(wrap)
                    .setPositiveButton("Split into 3 Steps", (dialog, which) -> {
                        String userContext = input.getText().toString().trim();
                        execute3StepSplit(item, userContext);
                    })
                    .setNeutralButton("Use Title Only", (dialog, which) -> execute3StepSplit(item, ""))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            execute3StepSplit(item, existingDesc);
        }
    }

    private void execute3StepSplit(TimelineItem item, String contextDesc) {
        String contextClause = (contextDesc != null && !contextDesc.isEmpty())
                ? (" with details/topic: \"" + contextDesc + "\"")
                : "";
        String prompt = "Break the task \"" + item.getTitle() + "\"" + contextClause
                + " (due " + item.getDueDate() + ", category " + item.getCategory() + ", priority " + item.getPriority() + ") "
                + "into 3 specific, concrete, actionable micro-steps placed right at the top of the list so I can start immediately. "
                + "Replace the original task \"" + item.getTitle() + "\" with those 3 micro-steps and keep all other tasks intact.";
        runAiTaskEdit(prompt, "\u2728 Splitting into 3 Micro-Steps...");
    }

    @Override
    protected void onResume() {
        super.onResume();
        allItems = TaskManager.loadTasks(this); purgeCompletedDuplicates(); if (checkAndResumeActiveTimer()) return;
        GoogleCalendarHelper.importTasksFromCalendar(this, () -> {
            allItems = TaskManager.loadTasks(MainActivity.this); purgeCompletedDuplicates();
            updateVisibleItems();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            updateStats();
        });
        ReminderHelper.scheduleAll(this, allItems);
        checkSavedTimer();
        updateVisibleItems();
        adapter.notifyDataSetChanged();
        streakManager.updateTotalTasks(countTasksDueToday() + streakManager.getCompletedToday());
        updateStats();
        updateEmptyState();

        boolean inTimerMode = currentTimerTask != null;
        boolean inBreakdownMode = inlineBreakdownSheet != null && inlineBreakdownSheet.getVisibility() == View.VISIBLE;
        if (!inTimerMode && !inBreakdownMode) {
            setActionButtonsVisibility(View.VISIBLE);
        }
    }

    // ==================== TIMER ====================

    private void startTimer(TimelineItem item) { android.content.SharedPreferences activeSp = getSharedPreferences("ezpz_active_timer", MODE_PRIVATE); if (activeSp.getBoolean("is_running", false) || TimerService.running) { String prevTitle = activeSp.getString("task_title", ""); long prevLeft = activeSp.getLong("time_left", 0); long prevTotal = activeSp.getLong("total_time", prevLeft); String prevSub = activeSp.getString("task_subtitle", ""); String prevDate = activeSp.getString("task_date", TaskManager.todayDate()); if (!prevTitle.isEmpty() && !prevTitle.equalsIgnoreCase(item.getTitle()) && prevLeft > 0) { getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit().putLong("time_left", prevLeft).putLong("total_time", prevTotal).putString("task_title", prevTitle).putString("task_subtitle", prevSub).putString("task_date", prevDate).apply(); } TimerService.running = false; TimerService.paused = false; activeSp.edit().clear().apply(); try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {} } isLaunchingActiveTimer = true;
        if (isDraggingCard || (System.currentTimeMillis() - lastDragEndTime < 400)) return;
        cancelLockScreenPinNotification(); Intent timerIntent = new Intent(this, TimerActivity.class);
        timerIntent.putExtra("task_title", item.getTitle());
        timerIntent.putExtra("task_subtitle", item.getSubtitle());
        timerIntent.putExtra("task_date", item.getDueDate());
        startActivityForResult(timerIntent, 200);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void onTimerComplete() {
        clearSavedTimer();
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        completeTask();
        Toast.makeText(this, "Task complete!", Toast.LENGTH_SHORT).show();
        exitTimerMode();
    }

    private void saveAndQuitTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        try { startService(new Intent(this, TimerService.class).setAction("STOP")); } catch (Exception ignored) {}
        getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit()
                .putLong("time_left", timeLeftMillis)
                .putLong("total_time", totalTimeMillis)
                .putString("task_title", currentTimerTask != null ? currentTimerTask.getTitle() : "")
                .putString("task_subtitle", currentTimerTask != null ? currentTimerTask.getSubtitle() : "")
                .putString("task_date", currentTimerTask != null ? currentTimerTask.getDueDate() : "")
                .apply();
        Toast.makeText(this, "Timer saved!", Toast.LENGTH_SHORT).show();
        adapter.setSavedTimer(currentTimerTask != null ? currentTimerTask.getTitle() : "", timeLeftMillis);
        exitTimerMode();
        adapter.notifyDataSetChanged();
    }

    private void checkSavedTimer() {
        if (adapter == null) return;
        android.content.SharedPreferences sp = getSharedPreferences("ezpz_timer", MODE_PRIVATE);
        long savedTime = sp.getLong("time_left", 0);
        String savedTitle = sp.getString("task_title", "");
        String pauseNote = sp.getString("pause_note", "");
        if (savedTime > 0 && !savedTitle.isEmpty()) {
            adapter.setSavedTimer(savedTitle, savedTime, pauseNote);
        } else {
            adapter.clearSavedTimer();
        }
        adapter.notifyDataSetChanged();
    }

    private void clearSavedTimer() {
        getSharedPreferences("ezpz_timer", MODE_PRIVATE).edit().clear().apply();
        if (adapter != null) {
            adapter.clearSavedTimer();
            adapter.notifyDataSetChanged();
        }
    }

    private void toggleZenMode(boolean enable) {
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            if (enable) {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                android.widget.Toast.makeText(this, "Zen Mode ON", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL);
                android.widget.Toast.makeText(this, "Zen Mode OFF", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void exitTimerMode() {
        currentTimerTask = null;
        toggleZenMode(false);
        timerContainer.setVisibility(View.GONE);
        timerButtonRow.setVisibility(View.GONE);
        setActionButtonsVisibility(View.VISIBLE);
        android.view.View btnAdd = findViewById(R.id.btnAddTask);
        android.view.View btnBrief = findViewById(R.id.btnBriefing);
        if (btnAdd != null && btnAdd.getVisibility() == View.VISIBLE) btnAdd.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse));
        if (btnBrief != null && btnBrief.getVisibility() == View.VISIBLE) btnBrief.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse));

        nextUpPreview.setVisibility(View.GONE);
        timerTaskName.setVisibility(View.GONE);
        timerText.setAlpha(1.0f);
        upNextLabel.setText("Up next \u21C5");
        inputBar.setVisibility(View.VISIBLE);
        recyclerTimeline.setVisibility(View.VISIBLE);
        aCard.setVisibility(View.VISIBLE);
        cardView3.setVisibility(View.VISIBLE);
        findViewById(R.id.filterScrollView).setVisibility(View.VISIBLE);
        updateEmptyState();
    }

    private void updateTimerText() {
        long totalSeconds = timeLeftMillis / 1000;
        long hours = totalSeconds / 3600;
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
        if (subtitle == null || subtitle.isEmpty()) return 25;
        try {
            String lower = subtitle.toLowerCase(Locale.US);
            Matcher hourMatch = Pattern.compile("(\\d+\\.?\\d*)\\s*(hours?|hrs?|h)\\b").matcher(lower);
            if (hourMatch.find()) {
                double hours = Double.parseDouble(hourMatch.group(1));
                int extraMin = 0;
                Matcher extraMinMatch = Pattern.compile("(\\d+)\\s*(min|m)\\b").matcher(lower);
                if (extraMinMatch.find()) {
                    extraMin = Integer.parseInt(extraMinMatch.group(1));
                }
                return Math.max(1, (int) (hours * 60) + extraMin);
            }
            Matcher minMatch = Pattern.compile("(\\d+)\\s*(min|mins|m)\\b").matcher(lower);
            if (minMatch.find()) {
                return Math.max(1, Integer.parseInt(minMatch.group(1)));
            }
        } catch (Exception ignored) {}
        return 25;
    }

    private void triggerCompletionHaptics(boolean criticalHit) {
        android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (criticalHit) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 90, 60, 140, 60, 220}, -1));
            } else {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } else {
            vibrator.vibrate(criticalHit ? 400 : 200);
        }
    }

    private void showCompletionFlash(boolean criticalHit) {
        android.widget.TextView flash = new android.widget.TextView(this);
        flash.setText(criticalHit ? "\uD83C\uDFB0 2x XP!" : "\u2705");
        flash.setTextSize(criticalHit ? 56 : 100);
        flash.setTextColor(0xFFFFD700);
        flash.setTypeface(null, android.graphics.Typeface.BOLD);
        flash.setGravity(android.view.Gravity.CENTER);
        android.widget.FrameLayout root = (android.widget.FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(flash, lp);
        flash.setAlpha(0f);
        flash.setScaleX(0.2f);
        flash.setScaleY(0.2f);
        flash.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(400)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> flash.animate().alpha(0f).scaleX(1.8f).scaleY(1.8f).setDuration(350).setStartDelay(250)
                        .withEndAction(() -> root.removeView(flash)).start()).start();
    }

    // ==================== BREAKDOWN SHEET ====================

    private void showBreakdownSheet() {
        upNextLabel.setVisibility(View.GONE);
        recyclerTimeline.setVisibility(View.GONE);
        aCard.setVisibility(View.GONE);
        cardView3.setVisibility(View.GONE);
        findViewById(R.id.filterScrollView).setVisibility(View.GONE);
        setActionButtonsVisibility(View.GONE);
        if (findViewById(R.id.emptyStateLayout) != null) findViewById(R.id.emptyStateLayout).setVisibility(View.GONE);
        inlineBreakdownSheet.setVisibility(View.VISIBLE);
    }

    private void hideBreakdownSheet() {
        inlineBreakdownSheet.setVisibility(View.GONE);
        upNextLabel.setVisibility(View.VISIBLE);
        recyclerTimeline.setVisibility(View.VISIBLE);
        aCard.setVisibility(View.VISIBLE);
        cardView3.setVisibility(View.VISIBLE);
        findViewById(R.id.filterScrollView).setVisibility(View.VISIBLE);
        setActionButtonsVisibility(View.VISIBLE);
        updateEmptyState();
    }

    // ==================== TASK MANAGEMENT ====================

    private void loadFreeTimeQuest() {
        if (questAcceptBtn == null || questTitle == null) return;
        questAcceptBtn.setOnClickListener(v -> {
            String fullText = questTitle.getText().toString();
            if (fullText.startsWith("Start early: ")) {
                String taskTitleToStart = fullText.substring("Start early: ".length());
                for (TimelineItem item : allItems) {
                    if (item.getTitle().equals(taskTitleToStart)) {
                        startTimer(item);
                        return;
                    }
                }
            }
        });
        fetchNewQuest();

        handleCalendarIntent(getIntent());
        checkSavedTimer();

        boolean inTimerAtStart = currentTimerTask != null;
        boolean inBreakdownAtStart = inlineBreakdownSheet != null && inlineBreakdownSheet.getVisibility() == View.VISIBLE;
        if (!inTimerAtStart && !inBreakdownAtStart) {
            setActionButtonsVisibility(View.VISIBLE);
            android.view.View btnAdd = findViewById(R.id.btnAddTask);
            android.view.View btnBrief = findViewById(R.id.btnBriefing);
            if (btnAdd != null && btnAdd.getVisibility() == View.VISIBLE) btnAdd.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse));
            if (btnBrief != null && btnBrief.getVisibility() == View.VISIBLE) btnBrief.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCalendarIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle TimerActivity results
        if (requestCode == 200) { isLaunchingActiveTimer = false;
            if (resultCode == TimerActivity.RESULT_COMPLETED && data != null) {
                String title = data.getStringExtra("task_title");
                String date = data.getStringExtra("task_date");
                int estMins = data.getIntExtra("estimated_minutes", 25);
                int actSecs = data.getIntExtra("actual_seconds", 0);
                for (int i = 0; i < allItems.size(); i++) {
                    TimelineItem item = allItems.get(i);
                    if (title != null && item.getTitle() != null && item.getTitle().trim().equalsIgnoreCase(title.trim())) {
                        allItems.remove(i);
                        GoogleCalendarHelper.deleteTaskFromCalendar(this, item);
                        HistoryManager.saveCompletedTask(this, item);
                        boolean earnedShield = streakManager.taskCompleted();
                        boolean crit = streakManager.wasLastCriticalHit();
                        triggerCompletionHaptics(crit);
                        showCompletionFlash(crit);
                        String paceMsg = String.format(Locale.US, "\uD83D\uDCCA Est: %dm \u2022 Actual: %dm %ds", estMins, actSecs / 60, actSecs % 60);
                        if (crit) {
                            Toast.makeText(this, "\uD83C\uDFB0 2x XP CRITICAL HIT! " + paceMsg, Toast.LENGTH_LONG).show();
                        } else if (earnedShield) {
                            Toast.makeText(this, "\uD83D\uDEE1\uFE0F +1 Streak Shield! " + paceMsg, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Task complete! " + paceMsg, Toast.LENGTH_LONG).show();
                        }
                        break;
                    }
                }
                TaskManager.saveTasks(this, allItems);
                ReminderHelper.scheduleAll(this, allItems);
                updateVisibleItems();
                adapter.notifyDataSetChanged();
                updateEmptyState();
                updateStats();
            } else if (resultCode == TimerActivity.RESULT_SAVED && data != null) {
                String title = data.getStringExtra("task_title");
                long timeLeft = data.getLongExtra("time_left", 0);
                String pauseNote = data.getStringExtra("pause_note");
                if (title != null && timeLeft > 0) {
                    adapter.setSavedTimer(title, timeLeft, pauseNote);
                } else {
                    adapter.clearSavedTimer();
                }
                adapter.notifyDataSetChanged();
            } else {
                clearSavedTimer();
                adapter.notifyDataSetChanged();
            }
            return;
        }

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String taskTitle = data.getStringExtra("start_task_title");
            if (data.getBooleanExtra("tasks_changed", false) || taskTitle != null) {
                allItems.clear();
                allItems.addAll(TaskManager.loadTasks(this));
                updateVisibleItems();
                adapter.notifyDataSetChanged();
                streakManager.updateTotalTasks(countTasksDueToday() + streakManager.getCompletedToday());
                updateEmptyState();
                updateStats();
            }
            if (taskTitle != null) {
                for (TimelineItem item : allItems) {
                    if (item.getTitle().equals(taskTitle)) {
                        startTimer(item);
                        return;
                    }
                }
            }
        }
        if (requestCode == REQUEST_VOICE_TASK && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                EditText taskInput = findViewById(R.id.editText3);
                taskInput.setText(matches.get(0));
                taskInput.setSelection(taskInput.getText().length());
            }
        }
    }

    private void handleCalendarIntent(Intent intent) {
        if (intent != null && intent.hasExtra("start_task_title")) {
            String taskTitle = intent.getStringExtra("start_task_title");
            intent.removeExtra("start_task_title");
            for (TimelineItem item : allItems) {
                if (item.getTitle().equals(taskTitle)) {
                    startTimer(item);
                    return;
                }
            }
        }
    }

    private void fetchNewQuest() {
        if (questTitle == null) return;

        List<TimelineItem> todayItems = getTodayItems();
        List<TimelineItem> eligibleTasks = new java.util.ArrayList<>();

        if (todayItems.size() > 3) {
            eligibleTasks.addAll(todayItems.subList(3, todayItems.size()));
        }

        for (TimelineItem item : allItems) {
            if (item.getDueDate().compareTo(TaskManager.todayDate()) > 0) {
                eligibleTasks.add(item);
            }
        }

        if (eligibleTasks.isEmpty()) {
            questTitle.setText("Only these tasks remain! Relax!");
            if (questAcceptBtn != null) questAcceptBtn.setVisibility(View.GONE);
            return;
        }

        if (questAcceptBtn != null) questAcceptBtn.setVisibility(View.VISIBLE);
        questTitle.setText("Analyzing schedule...");
        questAcceptBtn.setText("Start");

        try {
            org.json.JSONObject payload = new org.json.JSONObject();
            org.json.JSONArray tasksArray = new org.json.JSONArray();
            for (TimelineItem item : eligibleTasks) {
                org.json.JSONObject taskObj = new org.json.JSONObject();
                taskObj.put("title", item.getTitle());
                taskObj.put("subtitle", item.getSubtitle());
                taskObj.put("dueDate", item.getDueDate());
                tasksArray.put(taskObj);
            }
            payload.put("tasks", tasksArray);

            String prompt = "Here are my upcoming tasks: " + payload.toString() + "\n\n"
                    + "Analyze the time to complete (subtitle), deadlines, and implied priorities of these tasks. "
                    + "Suggest exactly ONE task from this list for me to start working on early. "
                    + "Return a JSON object containing exactly 1 task in the 'tasks' array, matching the chosen task exactly.";

            GeminiHelper.editTaskList(prompt, "{\"tasks\":[]}", new GeminiHelper.GeminiCallback() {
                @Override
                public void onSuccess(String response) {
                    java.util.List<TimelineItem> items = TaskManager.parseJsonResponse(response);
                    if (!items.isEmpty()) {
                        TimelineItem chosen = items.get(0);
                        runOnUiThread(() -> {
                            questTitle.setText("Start early: " + chosen.getTitle());
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        questTitle.setText("Start early: " + eligibleTasks.get(0).getTitle());
                    });
                }
            });
        } catch (Exception e) {
            questTitle.setText("Start early: " + eligibleTasks.get(0).getTitle());
        }
    }

    private void showNextUpPreview() {
        nextUpPreview.setVisibility(View.VISIBLE);
        List<TimelineItem> todayItems = getTodayItems();
        if (todayItems.size() > 1) {
            nextUpTask1.setText(todayItems.get(1).getTitle() + " \u2022 " + todayItems.get(1).getSubtitle());
            nextUpTask1.setVisibility(View.VISIBLE);
        } else {
            nextUpTask1.setText("No more tasks \u2014 nice!");
            nextUpTask1.setVisibility(View.VISIBLE);
        }
        if (todayItems.size() > 2) {
            nextUpTask2.setText(todayItems.get(2).getTitle() + " \u2022 " + todayItems.get(2).getSubtitle());
            nextUpTask2.setVisibility(View.VISIBLE);
        } else {
            nextUpTask2.setVisibility(View.GONE);
        }
    }

    private void updateStats() {
        int completed = streakManager.getCompletedToday();
        int total = countTasksDueToday() + completed;
        if (statsText != null) {
            statsText.setText(completed + " of " + total + " done \uD83E\uDDFE");
        }
        if (streakText != null) {
            int streak = streakManager.getStreak();
            int shields = streakManager.getShields();
            streakText.setText("\uD83D\uDD25 " + streak + "d \u2022 \uD83D\uDEE1\uFE0F" + shields);
            if (rankText != null) {
                rankText.setText(streakManager.getRankName() + " \u2022 " + streakManager.getXp() + " XP");
                rankText.setTextColor(streakManager.getRankColor());
            }
            View streakCard = findViewById(R.id.cardView3);
            if (streakCard != null && streak >= 3) {
                streakCard.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse));
            } else if (streakCard != null) {
                streakCard.clearAnimation();
            }
        }
        if (lockScreenPinned) {
            updateLockScreenPinNotification();
        }
    }

    private void loadProfilePhoto() {
        android.content.SharedPreferences settingsPrefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
        if (settingsPrefs.getBoolean("has_photo", false)) {
            java.io.File file = new java.io.File(getFilesDir(), "profile_photo.png");
            if (file.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                ImageView avatar = findViewById(R.id.imageView);
                if (avatar != null && bmp != null) {
                    avatar.setImageBitmap(bmp);
                    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            }
        }
    }

    private void setActionButtonsVisibility(int visibility) {
        if (visibility == View.VISIBLE) {
            boolean inTimer = currentTimerTask != null;
            boolean inBreakdown = inlineBreakdownSheet != null && inlineBreakdownSheet.getVisibility() == View.VISIBLE;
            if (inTimer || inBreakdown) {
                android.view.View g = findViewById(R.id.actionButtonsGroup);
                android.view.View b1 = findViewById(R.id.btnAddTask);
                android.view.View b2 = findViewById(R.id.btnBriefing);
                if (g != null) g.setVisibility(View.GONE);
                if (b1 != null) b1.setVisibility(View.GONE);
                if (b2 != null) b2.setVisibility(View.GONE);
                return;
            }
        }
        android.view.View group = findViewById(R.id.actionButtonsGroup);
        android.view.View btnAdd = findViewById(R.id.btnAddTask);
        android.view.View btnBrief = findViewById(R.id.btnBriefing);
        if (group != null) group.setVisibility(visibility);
        if (btnAdd != null) btnAdd.setVisibility(visibility);
        if (btnBrief != null) btnBrief.setVisibility(visibility);
    }

    private void updateEmptyState() {
        boolean inTimer = currentTimerTask != null;
        boolean inBreakdown = inlineBreakdownSheet != null && inlineBreakdownSheet.getVisibility() == View.VISIBLE;
        if (inTimer || inBreakdown) return;

        android.view.View emptyState = findViewById(R.id.emptyStateLayout);
        android.widget.TextView emptyEmoji = findViewById(R.id.emptyEmoji);
        if (emptyState != null && recyclerTimeline != null) {
            if (visibleItems.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerTimeline.setVisibility(View.GONE);
                if (emptyEmoji != null && emptyEmoji.getAnimation() == null) {
                    emptyEmoji.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.float_up_down));
                }
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerTimeline.setVisibility(View.VISIBLE);
                if (emptyEmoji != null) {
                    emptyEmoji.clearAnimation();
                }
            }
        }

        if (countTasksDueToday() == 0 && upNextLabel != null) {
            upNextLabel.setText("\uD83C\uDF89 All done for today!");
        } else if (upNextLabel != null) {
            StringBuilder sb = new StringBuilder("Up next \u21C5");
            if (adapter != null && adapter.isTunnelVisionMode()) sb.append(" \u2022 \uD83D\uDD26 1-Card");
            if (snowballSortMode) sb.append(" \u2022 \u26C4 Snowball");
            if (lowBatteryFilter) sb.append(" \u2022 \uD83E\uDEAB Low");
            upNextLabel.setText(sb.toString());
        }
    }

    private void setupFilterChips() {
        int colorActiveText = getResources().getColor(R.color.white);
        int colorInactiveText = getResources().getColor(R.color.text_secondary);

        int[] cardIds = {R.id.filterAll, R.id.filterAcademics, R.id.filterPersonal, R.id.filterHealth};
        String[] filters = {"All", "Academics", "Personal", "Health"};

        for (int i = 0; i < cardIds.length; i++) {
            final String filterName = filters[i];
            android.widget.FrameLayout card = findViewById(cardIds[i]);
            if (card != null) {
                card.setOnClickListener(v -> {
                    currentFilter = filterName;

                    for (int j = 0; j < cardIds.length; j++) {
                        android.widget.FrameLayout c = findViewById(cardIds[j]);
                        TextView t = (TextView) c.getChildAt(0);
                        if (filters[j].equals(currentFilter)) {
                            c.setBackgroundResource(R.drawable.bg_gradient_primary_rounded);
                            t.setTextColor(colorActiveText);
                            t.setTypeface(null, android.graphics.Typeface.BOLD);
                            c.animate().scaleX(1.05f).scaleY(1.05f).alpha(1.0f).setDuration(150).start();
                        } else {
                            c.setBackgroundResource(R.drawable.bg_pill_inactive);
                            t.setTextColor(colorInactiveText);
                            t.setTypeface(null, android.graphics.Typeface.NORMAL);
                            c.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.75f).setDuration(150).start();
                        }
                    }

                    updateVisibleItems();
                    adapter.notifyDataSetChanged();

                    RecyclerView rv = findViewById(R.id.recyclerTimeline);
                    if (rv != null) rv.scheduleLayoutAnimation();
                });
            }
        }

        for (int j = 0; j < cardIds.length; j++) {
            android.widget.FrameLayout c = findViewById(cardIds[j]);
            if (c != null) {
                if (filters[j].equals(currentFilter)) {
                    c.setScaleX(1.05f);
                    c.setScaleY(1.05f);
                    c.setAlpha(1.0f);
                } else {
                    c.setScaleX(0.95f);
                    c.setScaleY(0.95f);
                    c.setAlpha(0.75f);
                }
            }
        }

        // Inject ADHD Power Chips into filterScrollView row:
        // 1) [#9]  🔦 1-Card (Tunnel Vision)
        // 2) [#10] ⛄ Snowball (Shortest First Sort)
        // 3) [#8]  🎲 Pick 1 (Dice Roulette — without 2m sprint!)
        // 4) [#21] 🔒 Pin #1 (Lock-Screen Active Task Pin)
        // 5) [#3]  🪫 Low Energy
        // 6) [#23/#26] 🧾 Receipt & Pace
        View healthChip = findViewById(R.id.filterHealth);
        if (healthChip != null && healthChip.getParent() instanceof LinearLayout) {
            LinearLayout chipRow = (LinearLayout) healthChip.getParent();
            float d = getResources().getDisplayMetrics().density;

            final FrameLayout chipTunnel = createPowerChip("\uD83D\uDD26 1-Card", d);
            chipTunnel.setOnClickListener(v -> {
                if (adapter == null) return;
                boolean next = !adapter.isTunnelVisionMode();
                adapter.setTunnelVisionMode(next);
                stylePowerChip(chipTunnel, next);
                updateEmptyState();
                Toast.makeText(this, next ? "\uD83D\uDD26 Tunnel Vision ON: Showing only #1 task" : "Showing Top 3 tasks", Toast.LENGTH_SHORT).show();
            });
            chipRow.addView(chipTunnel);

            final FrameLayout chipSnowball = createPowerChip("\u26C4 Snowball", d);
            chipSnowball.setOnClickListener(v -> {
                snowballSortMode = !snowballSortMode;
                stylePowerChip(chipSnowball, snowballSortMode);
                if (snowballSortMode) {
                    Collections.sort(allItems, (a, b) -> Integer.compare(parseMinutes(a.getSubtitle()), parseMinutes(b.getSubtitle())));
                }
                updateVisibleItems();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(this, snowballSortMode ? "\u26C4 Snowball Sort ON: Shortest tasks first!" : "Snowball Sort OFF", Toast.LENGTH_SHORT).show();
            });
            chipRow.addView(chipSnowball);

            final FrameLayout chipRoulette = createPowerChip("\uD83C\uDFB2 Pick 1", d);
            chipRoulette.setOnClickListener(v -> spinTaskRoulette());
            chipRow.addView(chipRoulette);

            final FrameLayout chipLockPin = createPowerChip("\uD83D\uDD12 Pin #1", d);
            chipLockPin.setOnClickListener(v -> {
                lockScreenPinned = !lockScreenPinned; getSharedPreferences("ezpz_settings", MODE_PRIVATE).edit().putBoolean("lock_screen_pinned", lockScreenPinned).apply();
                stylePowerChip(chipLockPin, lockScreenPinned);
                if (lockScreenPinned) {
                    updateLockScreenPinNotification();
                    Toast.makeText(this, "\uD83D\uDD12 #1 Task pinned to Lock Screen & Notification Bar!", Toast.LENGTH_SHORT).show();
                } else {
                    cancelLockScreenPinNotification();
                    Toast.makeText(this, "Lock-screen task unpinned", Toast.LENGTH_SHORT).show();
                }
            });
            stylePowerChip(chipLockPin, lockScreenPinned); chipRow.addView(chipLockPin);

            final FrameLayout chipLowBat = createPowerChip("\uD83E\uDEAB Low Energy", d);
            chipLowBat.setOnClickListener(v -> {
                lowBatteryFilter = !lowBatteryFilter;
                stylePowerChip(chipLowBat, lowBatteryFilter);
                updateVisibleItems();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(this, lowBatteryFilter ? "\uD83E\uDEAB Low Battery Mode: Showing easy/low-energy tasks" : "All energy levels shown", Toast.LENGTH_SHORT).show();
            });
            chipRow.addView(chipLowBat);

            final FrameLayout chipReceipt = createPowerChip("\uD83E\uDDFE Receipt", d);
            chipReceipt.setOnClickListener(v -> showDoneTodayReceiptDialog());
            chipRow.addView(chipReceipt);

            boolean isDarkNow = getSharedPreferences("ezpz_settings", MODE_PRIVATE).getBoolean("dark_mode", false);
            final FrameLayout chipTheme = createPowerChip(isDarkNow ? "\u2600\uFE0F Light" : "\uD83C\uDF19 Dark", d);
            chipTheme.setOnClickListener(v -> {
                android.content.SharedPreferences sp = getSharedPreferences("ezpz_settings", MODE_PRIVATE);
                boolean nextDark = !sp.getBoolean("dark_mode", false);
                sp.edit().putBoolean("dark_mode", nextDark).apply();
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        nextDark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                 : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            });
            chipRow.addView(chipTheme);
        }
    }

    /**
     * [#21] Lock-Screen Active Task Pin
     */
    private void updateLockScreenPinNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    LOCK_PIN_CHANNEL_ID, "Lock-Screen Task Pin", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Pins your #1 Up Next task on the lock screen");
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }

        if (!lockScreenPinned || TimerService.running || getSharedPreferences("ezpz_active_timer", MODE_PRIVATE).getBoolean("is_running", false)) { nm.cancel(LOCK_PIN_NOTIF_ID); return; } List<TimelineItem> today = getTodayItems();
        if (today.isEmpty()) {
            nm.cancel(LOCK_PIN_NOTIF_ID);
            return;
        }
        TimelineItem top = visibleItems.isEmpty() ? today.get(0) : visibleItems.get(0);

        Intent startIntent = new Intent(this, MainActivity.class);
        startIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startIntent.putExtra("start_task_title", top.getTitle());
        PendingIntent startPi = PendingIntent.getActivity(this, 701, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, LOCK_PIN_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("\uD83D\uDD12 Up Next: " + top.getTitle())
                .setContentText((top.getSubtitle().isEmpty() ? "25 min" : top.getSubtitle()) + " \u2022 Energy: " + top.getEnergyLevel())
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(startPi)
                .addAction(android.R.drawable.ic_media_play, "Start Timer", startPi);

        nm.notify(LOCK_PIN_NOTIF_ID, b.build());
    }

    private void cancelLockScreenPinNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(LOCK_PIN_NOTIF_ID);
    }

    private FrameLayout createPowerChip(String text, float d) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int)(32 * d));
        lp.setMarginEnd((int)(8 * d));
        frame.setLayoutParams(lp);
        frame.setBackgroundResource(R.drawable.bg_pill_inactive);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding((int)(14 * d), 0, (int)(14 * d), 0);
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        frame.addView(tv);
        frame.setScaleX(0.95f);
        frame.setScaleY(0.95f);
        frame.setAlpha(0.85f);
        return frame;
    }

    private void stylePowerChip(FrameLayout chip, boolean active) {
        TextView tv = (TextView) chip.getChildAt(0);
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_gradient_primary_rounded);
            tv.setTextColor(getResources().getColor(R.color.white));
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.animate().scaleX(1.05f).scaleY(1.05f).alpha(1.0f).setDuration(150).start();
        } else {
            chip.setBackgroundResource(R.drawable.bg_pill_inactive);
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            tv.setTypeface(null, android.graphics.Typeface.NORMAL);
            chip.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.85f).setDuration(150).start();
        }
    }

    /**
     * [#8] 'Pick For Me' Dice Roulette (2-min micro-sprint removed per user request)
     */
    private void spinTaskRoulette() {
        List<TimelineItem> candidates = getTodayItems();
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No tasks due today to pick from!", Toast.LENGTH_SHORT).show();
            return;
        }
        final TimelineItem chosen = candidates.get(new Random().nextInt(candidates.size()));
        new android.app.AlertDialog.Builder(this)
                .setTitle("\uD83C\uDFB2 Dice Roulette Picked:")
                .setMessage("\"" + chosen.getTitle() + "\"\n\n"
                        + (chosen.getSubtitle().isEmpty() ? "25 min" : chosen.getSubtitle())
                        + " \u2022 Energy: " + chosen.getEnergyLevel()
                        + "\n\nReady to jump in?")
                .setPositiveButton("\u25B6 Start Timer", (d, w) -> startTimer(chosen))
                .setNeutralButton("\uD83C\uDFB2 Spin Again", (d, w) -> spinTaskRoulette())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDoneTodayReceiptDialog() {
        List<TimelineItem> history = HistoryManager.getHistory(this);
        String today = TaskManager.todayDate();
        List<TimelineItem> doneToday = new ArrayList<>();
        int totalMins = 0;
        for (TimelineItem h : history) {
            if (today.equals(h.getDueDate())) {
                doneToday.add(h);
                int m = parseMinutes(h.getSubtitle());
                totalMins += (m > 0 ? m : 25);
            }
        }

        android.content.SharedPreferences pacePrefs = getSharedPreferences("ezpz_pace_stats", MODE_PRIVATE);
        String paceLog = pacePrefs.getString("pace_log_" + today, "");

        android.content.SharedPreferences parkedPrefs = getSharedPreferences("ezpz_parked", MODE_PRIVATE);
        String parkedThoughts = parkedPrefs.getString("thoughts", "");

        StringBuilder sb = new StringBuilder();
        sb.append("==============================\n");
        sb.append("   EZPZ DOPAMINE RECEIPT \uD83E\uDDFE\n");
        sb.append("   Date: ").append(today).append("\n");
        sb.append("==============================\n\n");
        if (doneToday.isEmpty()) {
            sb.append("No tasks checked off yet today.\nSwipe right on any card or complete a timer to print your wins!\n\n");
        } else {
            for (int i = 0; i < doneToday.size(); i++) {
                TimelineItem t = doneToday.get(i);
                int m = parseMinutes(t.getSubtitle());
                sb.append(String.format(Locale.US, "%d. \u2705 %s (%dm)\n", i + 1, t.getTitle(), m));
            }
            sb.append("\n------------------------------\n");
            sb.append("ITEMS COMPLETED:  ").append(doneToday.size()).append("\n");
            sb.append("EST. FOCUS TIME:  ").append(totalMins).append(" mins\n");
        }
        if (!paceLog.isEmpty()) {
            sb.append("\n\uD83D\uDCCA EST. vs ACTUAL PACE:\n").append(paceLog).append("\n");
        }
        if (!parkedThoughts.isEmpty()) {
            sb.append("\n\uD83E\uDDE0 PARKED THOUGHTS:\n").append(parkedThoughts).append("\n");
        }
        sb.append("------------------------------\n");
        sb.append("STREAK:           \uD83D\uDD25 ").append(streakManager.getStreak()).append(" days (").append(streakManager.getRankName()).append(")\n");
        sb.append("XP SCORE:         \uD83C\uDFB0 ").append(streakManager.getXp()).append(" XP\n");
        sb.append("STREAK SHIELDS:   \uD83D\uDEE1\uFE0F ").append(streakManager.getShields()).append(" / 3 active\n");
        sb.append("==============================");

        new android.app.AlertDialog.Builder(this)
                .setTitle("\uD83E\uDDFE Done Today & Pace Receipt")
                .setMessage(sb.toString())
                .setPositiveButton("Awesome!", null)
                .setNeutralButton("Clear Parked Thoughts", (d, w) -> {
                    parkedPrefs.edit().remove("thoughts").apply();
                    Toast.makeText(this, "Parked thoughts cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showReorderTodayDialog() {
        List<TimelineItem> todayList = new ArrayList<>();
        for (TimelineItem item : allItems) {
            if (isDueToday(item)) {
                String cat = item.getCategory();
                if (cat == null || cat.trim().isEmpty()) cat = "Personal";
                if (currentFilter.equals("All") || cat.equalsIgnoreCase(currentFilter)) todayList.add(item);
            }
        }
        if (todayList.isEmpty()) { Toast.makeText(this, "No tasks due today", Toast.LENGTH_SHORT).show(); return; }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Arrange Today\u0027s Queue (Top 3 on Home)");
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        float d = getResources().getDisplayMetrics().density; int pad = (int)(16 * d);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        Runnable[] ref = new Runnable[1];
        ref[0] = () -> {
            container.removeAllViews();
            for (int i = 0; i < todayList.size(); i++) {
                final int idx = i; TimelineItem item = todayList.get(i);
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int)(6 * d), 0, (int)(6 * d));
                TextView tv = new TextView(this);
                String tag = (i < 3) ? ("#" + (i + 1) + " [Home] ") : ("#" + (i + 1) + " [Other] ");
                tv.setText(tag + item.getTitle()); tv.setTextSize(14f);
                tv.setTextColor(getColor(i < 3 ? R.color.section_label : R.color.text_secondary));
                tv.setTypeface(null, i < 3 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                row.addView(tv, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
                if (i > 0) {
                    TextView bTop = new TextView(this); bTop.setText("Top #1"); bTop.setTextSize(11f); bTop.setTextColor(getColor(R.color.section_label));
                    bTop.setTypeface(null, android.graphics.Typeface.BOLD); bTop.setPadding((int)(10*d),(int)(4*d),(int)(10*d),(int)(4*d));
                    bTop.setBackgroundResource(R.drawable.bg_pill_inactive);
                    bTop.setOnClickListener(v -> {
                        TimelineItem p = todayList.remove(idx); todayList.add(0, p);
                        int rIdx = findTaskIndex(p); if (rIdx >= 0) { allItems.remove(rIdx); allItems.add(0, p); }
                        ref[0].run();
                    });
                    row.addView(bTop);
                }
                TextView bUp = new TextView(this); bUp.setText(" \u2191 "); bUp.setTextSize(16f); bUp.setEnabled(i > 0); bUp.setAlpha(i > 0 ? 1f : 0.25f);
                bUp.setPadding((int)(8*d),(int)(4*d),(int)(8*d),(int)(4*d));
                bUp.setOnClickListener(v -> {
                    if (idx <= 0) return;
                    TimelineItem a = todayList.get(idx), b = todayList.get(idx - 1);
                    int iA = findTaskIndex(a), iB = findTaskIndex(b);
                    java.util.Collections.swap(todayList, idx, idx - 1);
                    if (iA >= 0 && iB >= 0) java.util.Collections.swap(allItems, iA, iB);
                    ref[0].run();
                });
                row.addView(bUp);
                TextView bDn = new TextView(this); bDn.setText(" \u2193 "); bDn.setTextSize(16f); bDn.setEnabled(i < todayList.size() - 1); bDn.setAlpha(i < todayList.size() - 1 ? 1f : 0.25f);
                bDn.setPadding((int)(8*d),(int)(4*d),(int)(8*d),(int)(4*d));
                bDn.setOnClickListener(v -> {
                    if (idx >= todayList.size() - 1) return;
                    TimelineItem a = todayList.get(idx), b = todayList.get(idx + 1);
                    int iA = findTaskIndex(a), iB = findTaskIndex(b);
                    java.util.Collections.swap(todayList, idx, idx + 1);
                    if (iA >= 0 && iB >= 0) java.util.Collections.swap(allItems, iA, iB);
                    ref[0].run();
                });
                row.addView(bDn); container.addView(row);
            }
        };
        ref[0].run();
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(container); builder.setView(sv);
        builder.setPositiveButton("Done", (dialog, which) -> {
            TaskManager.saveTasks(this, allItems); ReminderHelper.scheduleAll(this, allItems);
            updateVisibleItems(); adapter.notifyDataSetChanged();
        });
        builder.show();
    }

    private void updateVisibleItems() {
        visibleItems.clear();
        List<TimelineItem> candidates = new ArrayList<>();
        for (TimelineItem item : allItems) {
            if (isDueToday(item)) {
                String cat = item.getCategory();
                if (cat == null || cat.trim().isEmpty()) cat = "Personal";
                if (currentFilter.equals("All") || cat.equalsIgnoreCase(currentFilter)) {
                    if (lowBatteryFilter) {
                        String e = item.getEnergyLevel();
                        if ("H".equalsIgnoreCase(e) || "High".equalsIgnoreCase(e)) {
                            continue;
                        }
                    }
                    candidates.add(item);
                }
            }
        }
        if (candidates.isEmpty() && lowBatteryFilter) {
            for (TimelineItem item : allItems) {
                if (isDueToday(item)) {
                    String cat = item.getCategory();
                    if (cat == null || cat.trim().isEmpty()) cat = "Personal";
                    if (currentFilter.equals("All") || cat.equalsIgnoreCase(currentFilter)) {
                        candidates.add(item);
                    }
                }
            }
        }
        if (snowballSortMode) {
            Collections.sort(candidates, (a, b) -> Integer.compare(parseMinutes(a.getSubtitle()), parseMinutes(b.getSubtitle())));
        }
        for (int i = 0; i < candidates.size() && visibleItems.size() < 3; i++) {
            visibleItems.add(candidates.get(i));
        }
        for (int i = 0; i < visibleItems.size(); i++) {
            TimelineItem cur = visibleItems.get(i);
            visibleItems.set(i, new TimelineItem(
                    cur.getTitle(), cur.getSubtitle(), i == 0,
                    cur.getDueDate(), cur.getPriority(), cur.getDescription(),
                    cur.getCategory(), cur.getEnergyLevel(), cur.getDeadline(),
                    cur.getReminderMinutes(), cur.getShiftedFromDate()
            ));
        }
        checkQuestValidity();
        if (lockScreenPinned) {
            updateLockScreenPinNotification();
        }
    }

    private void checkQuestValidity() {
        if (questTitle == null) return;
        String fullText = questTitle.getText().toString();

        List<TimelineItem> todayItems = getTodayItems();
        List<TimelineItem> eligibleTasks = new java.util.ArrayList<>();
        if (todayItems.size() > 3) {
            eligibleTasks.addAll(todayItems.subList(3, todayItems.size()));
        }
        for (TimelineItem item : allItems) {
            if (item.getDueDate().compareTo(TaskManager.todayDate()) > 0) {
                eligibleTasks.add(item);
            }
        }

        if (eligibleTasks.isEmpty()) {
            if (!fullText.equals("Only these tasks remain! Relax!")) {
                fetchNewQuest();
            }
            return;
        }

        if (fullText.equals("Only these tasks remain! Relax!") || fullText.equals("Try: Free time quest") || fullText.equals("Analyzing schedule...")) {
            if (fullText.equals("Only these tasks remain! Relax!")) {
                fetchNewQuest();
            }
            return;
        }

        if (fullText.startsWith("Start early: ")) {
            String taskTitle = fullText.substring("Start early: ".length());
            boolean found = false;
            for (TimelineItem item : eligibleTasks) {
                if (item.getTitle().equals(taskTitle)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                fetchNewQuest();
            }
        }
    }

    public void completeTask() {
        int index = currentTimerTask == null ? firstTodayTaskIndex() : findTaskIndex(currentTimerTask);
        if (index >= 0 && index < allItems.size()) {
            TimelineItem completedTask = allItems.get(index);
            allItems.remove(index);
            GoogleCalendarHelper.deleteTaskFromCalendar(MainActivity.this, completedTask);
            currentTimerTask = null;
            HistoryManager.saveCompletedTask(MainActivity.this, completedTask);
            boolean earnedShield = streakManager.taskCompleted();
            boolean crit = streakManager.wasLastCriticalHit();
            triggerCompletionHaptics(crit);
            showCompletionFlash(crit);
            String msg = crit ? "\uD83C\uDFB0 2x XP CRITICAL HIT!" : (earnedShield ? "Task completed! \uD83D\uDEE1\uFE0F +1 Streak Shield!" : "Task completed!");
            android.widget.Toast.makeText(MainActivity.this, msg, android.widget.Toast.LENGTH_SHORT).show();
            TaskManager.saveTasks(this, allItems);
            ReminderHelper.scheduleAll(this, allItems);
            updateVisibleItems();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            updateStats();
        }
    }

    private List<TimelineItem> getTodayItems() {
        List<TimelineItem> todayItems = new ArrayList<>();
        for (TimelineItem item : allItems) {
            if (isDueToday(item)) todayItems.add(item);
        }
        return todayItems;
    }

    private int countTasksDueToday() {
        int count = 0;
        for (TimelineItem item : allItems) {
            if (isDueToday(item)) count++;
        }
        return count;
    }

    private boolean isDueToday(TimelineItem item) {
        return item != null && TaskManager.todayDate().equals(item.getDueDate());
    }

    private int firstTodayTaskIndex() {
        for (int i = 0; i < allItems.size(); i++) {
            if (isDueToday(allItems.get(i))) return i;
        }
        return -1;
    }

    private int findTaskIndex(TimelineItem target) {
        if (target == null) return -1;
        for (int i = 0; i < allItems.size(); i++) {
            if (sameTask(allItems.get(i), target)) return i;
        }
        return -1;
    }

    private boolean sameTask(TimelineItem first, TimelineItem second) {
        if (first == null || second == null) return false;
        return first.getTitle().equals(second.getTitle())
                && first.getSubtitle().equals(second.getSubtitle())
                && first.getDueDate().equals(second.getDueDate());
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say one or more tasks");
        try {
            startActivityForResult(intent, REQUEST_VOICE_TASK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice input is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }
}
