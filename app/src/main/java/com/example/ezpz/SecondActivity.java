package com.example.ezpz;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SecondActivity extends AppCompatActivity {
    private static final int REQUEST_VOICE_TASK = 73;
    private final Calendar windowStart = Calendar.getInstance();
    private final Calendar selectedDate = Calendar.getInstance();
    private final List<TimelineItem> tasks = new ArrayList<>();
    private TextView calendarTitle;
    private TextView selectedDateHeading;
    private LinearLayout weekHeader;
    private GridLayout twoWeekGrid;
    private LinearLayout dayTaskList;
    private EditText page2TaskInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_second);
        // Animation Logic for Nav Indicator
        android.view.View navIndicator = findViewById(R.id.navIndicator);
        android.view.View bottomNavBar = findViewById(R.id.bottomNavBar);
        if (navIndicator != null && bottomNavBar != null) {
            int prevTab = getIntent().getIntExtra("prev_tab", 1);
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
                    float endPos = pad + (1 * tabW);
                    
                    navIndicator.setTranslationX(startPos);
                    if (prevTab != 1) {
                        navIndicator.animate().translationX(endPos).setDuration(250)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                }
            });
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.second_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        calendarTitle = findViewById(R.id.calendarTitle);
        selectedDateHeading = findViewById(R.id.selectedDateHeading);
        weekHeader = findViewById(R.id.weekHeader);
        twoWeekGrid = findViewById(R.id.twoWeekGrid);
        dayTaskList = findViewById(R.id.dayTaskList);
        page2TaskInput = findViewById(R.id.page2TaskInput);

        loadTasks();

        moveToWeekStart(windowStart);
        selectedDate.setTime(Calendar.getInstance().getTime());

        View homeButton = findViewById(R.id.navHome);
        View prevWindow = findViewById(R.id.btnPrevWindow);
        View nextWindow = findViewById(R.id.btnNextWindow);
        android.view.View voiceButton = findViewById(R.id.page2VoiceButton);
        android.view.View sendButton = findViewById(R.id.page2SendButton);

        homeButton.setOnClickListener(v -> { finish(); overridePendingTransition(0, 0); });
        prevWindow.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                windowStart.add(Calendar.DATE, -14);
                selectedDate.setTime(windowStart.getTime());
                renderCalendar();
            }).start();
        });
        nextWindow.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                windowStart.add(Calendar.DATE, 14);
                selectedDate.setTime(windowStart.getTime());
                renderCalendar();
            }).start();
        });
        voiceButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                startVoiceInput();
            }).start();
        });
        sendButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                sendSelectedDayEdit();
            }).start();
        });

        renderCalendar();

        // Nav bar
        findViewById(R.id.navSettings).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, SettingsActivity.class); i.putExtra("prev_tab", 1); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navReport).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, ReportActivity.class); i.putExtra("prev_tab", 1); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void renderCalendar() {
        android.transition.TransitionManager.beginDelayedTransition(findViewById(R.id.second_main), new android.transition.AutoTransition().setDuration(250));
        renderTitle();
        renderWeekHeader();
        renderTwoWeekGrid();
        renderTasksForSelectedDay();
    }

    private void renderTitle() {
        Calendar end = (Calendar) windowStart.clone();
        end.add(Calendar.DATE, 13);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        if (windowStart.get(Calendar.MONTH) == end.get(Calendar.MONTH)) {
            calendarTitle.setText(monthFormat.format(windowStart.getTime()));
        } else {
            calendarTitle.setText(new SimpleDateFormat("MMM", Locale.getDefault()).format(windowStart.getTime())
                    + " - " + monthFormat.format(end.getTime()));
        }
    }

    private void renderWeekHeader() {
        weekHeader.removeAllViews();
        String[] days = {"S", "M", "T", "W", "T", "F", "S"};
        for (String day : days) {
            TextView label = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            label.setLayoutParams(params);
            label.setGravity(Gravity.CENTER);
            label.setText(day);
            label.setTextColor(getColor(R.color.text_muted));
            label.setTextSize(12);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            weekHeader.addView(label);
        }
    }

    private void renderTwoWeekGrid() {
        twoWeekGrid.removeAllViews();
        for (int i = 0; i < 14; i++) {
            Calendar date = (Calendar) windowStart.clone();
            date.add(Calendar.DATE, i);
            twoWeekGrid.addView(buildDayCell(date));
        }
    }

    private View buildDayCell(Calendar date) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dpToPx(5), dpToPx(4), dpToPx(5), dpToPx(4));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dpToPx(43);
        params.height = dpToPx(70);
        params.setMargins(0, 0, dpToPx(2), dpToPx(4));
        cell.setLayoutParams(params);

        boolean isSelected = sameDate(date, selectedDate);
        boolean isToday = sameDate(date, Calendar.getInstance());
        cell.setBackgroundResource(isSelected ? R.drawable.bg_calendar_day_selected :
                isToday ? R.drawable.bg_calendar_day_today : R.drawable.bg_calendar_day);

        TextView dateLabel = new TextView(this);
        dateLabel.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));
        dateLabel.setTextColor(getColor(isSelected ? R.color.purple_primary_end : R.color.text_primary));
        dateLabel.setTextSize(11);
        dateLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cell.addView(dateLabel);

        List<TimelineItem> dayTasks = getTasksForDate(date);
        for (int i = 0; i < Math.min(dayTasks.size(), 2); i++) {
            TextView preview = new TextView(this);
            preview.setText(shortTitle(dayTasks.get(i).getTitle()));
            preview.setSingleLine(true);
            preview.setTextSize(9);
            preview.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            preview.setTextColor(getColor(android.R.color.white));
            preview.setGravity(Gravity.CENTER_VERTICAL);
            preview.setBackgroundResource(R.drawable.bg_gradient_primary_rounded);
            preview.setPadding(dpToPx(4), 0, dpToPx(4), 0);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(15)
            );
            previewParams.setMargins(0, dpToPx(2), 0, 0);
            preview.setLayoutParams(previewParams);
            cell.addView(preview);
        }

        if (dayTasks.size() > 2) {
            TextView more = new TextView(this);
            more.setText("+" + (dayTasks.size() - 2) + " more");
            more.setTextColor(getColor(R.color.purple_primary_end));
            more.setTextSize(9);
            more.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            cell.addView(more);
        }

        cell.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(50)
                .withEndAction(() -> {
                    selectedDate.setTime(date.getTime());
                    renderCalendar();
                }).start();
        });
        return cell;
    }

    private void renderTasksForSelectedDay() {
        dayTaskList.removeAllViews();
        selectedDateHeading.setText(new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(selectedDate.getTime()));

        String selectedDateKey = formatDateKey(selectedDate);
        String todayKey = formatDateKey(Calendar.getInstance());
        
        List<TimelineItem> allHistory = HistoryManager.getHistory(this);
        List<TimelineItem> completedList = new ArrayList<>();
        for (TimelineItem t : allHistory) {
            if (selectedDateKey.equals(t.getDueDate())) {
                completedList.add(t);
            }
        }
        
        if (selectedDateKey.compareTo(todayKey) >= 0) {
            // Current or Future Day: Completed List, To-Do List
            List<TimelineItem> todoList = getTasksForDate(selectedDate);
            
            if (completedList.isEmpty() && todoList.isEmpty()) {
                dayTaskList.addView(buildEmptyState());
                return;
            }
            
            if (!todoList.isEmpty()) {
                dayTaskList.addView(buildSectionHeader("To-Do List"));
                for (int i = 0; i < todoList.size(); i++) {
                    dayTaskList.addView(buildTaskRow(todoList.get(i), i == 0)); 
                }
            }
            if (!completedList.isEmpty()) {
                dayTaskList.addView(buildSectionHeader("Completed List"));
                for (TimelineItem t : completedList) {
                    dayTaskList.addView(buildTaskRow(t, false));
                }
            }
        } else {
            // Older Day: Completed List, Missed List, Missed-Shifted List
            List<TimelineItem> missedList = new ArrayList<>();
            List<TimelineItem> missedShiftedList = new ArrayList<>();
            
            for (TimelineItem t : tasks) {
                if (selectedDateKey.equals(t.getDueDate()) && "MISSED".equals(t.getDeadline())) {
                    missedList.add(t);
                } else {
                    String shifted = t.getShiftedFromDate();
                    if (shifted != null && shifted.contains(selectedDateKey)) {
                        missedShiftedList.add(t);
                    }
                }
            }
            
            if (completedList.isEmpty() && missedList.isEmpty() && missedShiftedList.isEmpty()) {
                dayTaskList.addView(buildEmptyState());
                return;
            }
            
            if (!completedList.isEmpty()) {
                dayTaskList.addView(buildSectionHeader("Completed List"));
                for (TimelineItem t : completedList) {
                    dayTaskList.addView(buildTaskRow(t, false));
                }
            }
            if (!missedList.isEmpty()) {
                dayTaskList.addView(buildSectionHeader("Missed List"));
                for (TimelineItem t : missedList) {
                    dayTaskList.addView(buildTaskRow(t, false));
                }
            }
            if (!missedShiftedList.isEmpty()) {
                dayTaskList.addView(buildSectionHeader("Missed-Shifted List"));
                for (TimelineItem t : missedShiftedList) {
                    dayTaskList.addView(buildTaskRow(t, false));
                }
            }
        }
    }

    private TextView buildSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(getColor(R.color.text_primary));
        header.setTextSize(16);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        header.setPadding(dpToPx(14), dpToPx(16), dpToPx(14), dpToPx(8));
        return header;
    }

    private TextView buildEmptyState() {
        TextView empty = new TextView(this);
        empty.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)
        ));
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        empty.setBackgroundResource(R.drawable.bg_task_row);
        empty.setText("No tasks for this day");
        empty.setTextColor(getColor(R.color.text_muted));
        empty.setTextSize(14);
        empty.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return empty;
    }

    private View buildTaskRow(TimelineItem item, boolean highlighted) {
        View view = getLayoutInflater().inflate(R.layout.item_cal_task, null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dpToPx(10));
        view.setLayoutParams(params);

        TextView time = view.findViewById(R.id.taskTime);
        TextView title = view.findViewById(R.id.taskTitle);
        View bar = view.findViewById(R.id.taskBar);
        android.widget.ImageView btnExpand = view.findViewById(R.id.btnExpand);
        LinearLayout actionMenu = view.findViewById(R.id.actionMenu);
        
        TextView btnDelete = view.findViewById(R.id.btnMenuDelete);
        TextView btnDone = view.findViewById(R.id.btnMenuDone);
        TextView btnEdit = view.findViewById(R.id.btnMenuEdit);
        TextView btnStart = view.findViewById(R.id.btnMenuStart);

        time.setText(extractTime(item.getSubtitle()));
        title.setText(item.getTitle());

        String priority = item.getPriority();
        if ("high".equals(priority)) {
            bar.setBackgroundColor(0xFFD85A30);
        } else if ("low".equals(priority)) {
            bar.setBackgroundColor(0xFF7BCBAD);
        } else {
            bar.setBackgroundResource(R.drawable.bg_gradient_primary_rounded);
        }
        
        if (highlighted) {
            view.setBackgroundResource(R.drawable.bg_task_row_selected);
        }

        View.OnClickListener toggleMenu = v -> {
            if (actionMenu.getVisibility() == View.VISIBLE) {
                actionMenu.setVisibility(View.GONE);
                btnExpand.setRotation(0);
            } else {
                actionMenu.setVisibility(View.VISIBLE);
                btnExpand.setRotation(180);
            }
        };

        btnExpand.setOnClickListener(toggleMenu);
        title.setOnClickListener(toggleMenu);

        btnDelete.setOnClickListener(v -> deleteTask(item));
        btnDone.setOnClickListener(v -> {
            HistoryManager.saveCompletedTask(SecondActivity.this, item);
            new StreakManager(SecondActivity.this).taskCompleted();
            deleteTask(item); // UI refresh & save
            android.widget.Toast.makeText(SecondActivity.this, "Task completed!", android.widget.Toast.LENGTH_SHORT).show();
        });
        btnEdit.setOnClickListener(v -> openTaskEditor(item));
        btnStart.setOnClickListener(v -> startTaskOnHome(item));

        return view;
    }

    private void openTaskEditor(TimelineItem item) {
        int realIndex = -1;
        List<TimelineItem> allTasks = TaskManager.loadTasks(this);
        for (int i = 0; i < allTasks.size(); i++) {
            if (allTasks.get(i).getTitle().equals(item.getTitle()) && allTasks.get(i).getDueDate().equals(item.getDueDate())) {
                realIndex = i; break;
            }
        }
        Intent intent = new Intent(SecondActivity.this, TaskEditActivity.class);
        intent.putExtra("task_title", item.getTitle());
        intent.putExtra("task_subtitle", item.getSubtitle());
        intent.putExtra("task_index", realIndex);
        intent.putExtra("task_description", item.getDescription());
        intent.putExtra("task_priority", item.getPriority());
        intent.putExtra("task_category", item.getCategory());
        intent.putExtra("task_energy", item.getEnergyLevel());
        intent.putExtra("task_deadline", item.getDeadline());
        intent.putExtra("task_due_date", item.getDueDate());
        intent.putExtra("task_reminder", item.getReminderMinutes());
        startActivityForResult(intent, 200);
        overridePendingTransition(0, 0);
    }

    private void startTaskOnHome(TimelineItem item) {
        Intent result = new Intent();
        result.putExtra("start_task_title", item.getTitle());
        setResult(RESULT_OK, result);
        finish();
    }

    private void deleteTask(TimelineItem item) {
        for (int i = 0; i < tasks.size(); i++) {
            if (sameTask(tasks.get(i), item)) {
                tasks.remove(i);
                GoogleCalendarHelper.deleteTaskFromCalendar(this, item);
                TaskManager.saveTasks(this, tasks);
                ReminderHelper.scheduleAll(this, tasks);
                markTasksChanged();
                renderCalendar();
                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }

    private void markTasksChanged() {
        Intent result = new Intent();
        result.putExtra("tasks_changed", true);
        setResult(RESULT_OK, result);
    }

    private boolean sameTask(TimelineItem first, TimelineItem second) {
        if (first == null || second == null) return false;
        return first.getTitle().equals(second.getTitle())
                && first.getSubtitle().equals(second.getSubtitle())
                && first.getDueDate().equals(second.getDueDate());
    }

    private List<TimelineItem> getTasksForDate(Calendar date) {
        List<TimelineItem> result = new ArrayList<>();
        String dateKey = formatDateKey(date);
        for (TimelineItem task : tasks) {
            if (dateKey.equals(task.getDueDate())) {
                result.add(task);
            }
        }
        return result;
    }

    private void sendSelectedDayEdit() {
        String userMessage = page2TaskInput.getText().toString().trim();
        if (userMessage.isEmpty()) return;

        String selectedKey = formatDateKey(selectedDate);
        String selectedLabel = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(selectedDate.getTime());
        page2TaskInput.setText("");

        // Quick-add: prefix with + or ! to skip AI
        if (userMessage.startsWith("+") || userMessage.startsWith("!")) {
            String quickTitle = userMessage.substring(1).trim();
            if (!quickTitle.isEmpty()) {
                TimelineItem quickTask = new TimelineItem(quickTitle, "", false, selectedKey);
                tasks.add(quickTask);
                TaskManager.saveTasks(this, tasks);
                ReminderHelper.scheduleAll(this, tasks);
                markTasksChanged();
                renderCalendar();
                Toast.makeText(this, "Task added to " + selectedLabel, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Toast.makeText(this, "Updating " + selectedLabel + "...", Toast.LENGTH_SHORT).show();

        String datedMessage = "For " + selectedLabel + " (" + selectedKey + "), " + userMessage
                + ". Apply changes to that day unless I clearly mention another date.";

        GeminiHelper.editTaskList(datedMessage, TaskManager.getTasksAsJson(this), new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                List<TimelineItem> updatedTasks = TaskManager.parseJsonResponse(response);
                if (updatedTasks.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(SecondActivity.this, "No valid task update returned", Toast.LENGTH_SHORT).show());
                    return;
                }
                runOnUiThread(() -> {
                    tasks.clear();
                    tasks.addAll(updatedTasks);
                    TaskManager.saveTasks(SecondActivity.this, tasks);
                    ReminderHelper.scheduleAll(SecondActivity.this, tasks);
                    markTasksChanged();
                    renderCalendar();
                    Toast.makeText(SecondActivity.this, "Calendar updated", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(SecondActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadTasks() {
        tasks.clear();
        tasks.addAll(TaskManager.loadTasks(this));
    }

    private String extractTime(String subtitle) {
        if (subtitle == null || subtitle.trim().isEmpty()) return "";
        String[] parts = subtitle.split("[-Ãƒâ€šÃ‚Â·ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢]");
        String first = parts[0].trim();
        if (hasTime(first)) return first;
        return first.length() > 8 ? "" : first;
    }

    private boolean hasTime(String text) {
        if (text == null) return false;
        String value = text.toLowerCase(Locale.ROOT);
        return value.matches(".*\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)\\b.*")
                || value.matches(".*\\b\\d{1,2}:\\d{2}\\b.*");
    }

    private String shortTitle(String title) {
        if (title == null) return "";
        return title.length() <= 6 ? title : title.substring(0, 6) + "...";
    }

    private String formatDateKey(Calendar date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime());
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task change");
        try {
            startActivityForResult(intent, REQUEST_VOICE_TASK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice input is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("tasks_changed", false)) {
                loadTasks();
                renderCalendar();
            }
        }
        if (requestCode == REQUEST_VOICE_TASK && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                page2TaskInput.setText(matches.get(0));
                page2TaskInput.setSelection(page2TaskInput.getText().length());
            }
        }
    }

    private void moveToWeekStart(Calendar date) {
        while (date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            date.add(Calendar.DATE, -1);
        }
    }

    private boolean sameDate(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
                && first.get(Calendar.DAY_OF_MONTH) == second.get(Calendar.DAY_OF_MONTH);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
