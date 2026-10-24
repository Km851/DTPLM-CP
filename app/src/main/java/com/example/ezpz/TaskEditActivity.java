package com.example.ezpz;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TaskEditActivity extends AppCompatActivity {

    private EditText editTitle, editDescription;
    private TextView valueStartTime, valueDuration, valueDeadline, valueReminder, valueDueDate;
    private TextView chipLow, chipMedium, chipHigh;
    private TextView catAcademics, catHealth, catPersonal;
    private TextView energyL, energyM, energyH;

    private String selectedPriority = "medium";
    private String selectedCategory = "Academics";
    private String selectedEnergy = "M";
    private String startTime = "";
    private String duration = "";
    private String deadline = "";
    private String dueDate = "";
    private int reminderMinutes = 5;
    private int taskIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editMain), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Bind views
        editTitle = findViewById(R.id.editTaskTitle);
        editDescription = findViewById(R.id.editDescription);
        valueStartTime = findViewById(R.id.valueStartTime);
        valueDuration = findViewById(R.id.valueDuration);
        valueDeadline = findViewById(R.id.valueDeadline);
        valueReminder = findViewById(R.id.valueReminder);
        valueDueDate = findViewById(R.id.valueDueDate);

        chipLow = findViewById(R.id.chipLow);
        chipMedium = findViewById(R.id.chipMedium);
        chipHigh = findViewById(R.id.chipHigh);

        catAcademics = findViewById(R.id.catAcademics);
        catHealth = findViewById(R.id.catHealth);
        catPersonal = findViewById(R.id.catPersonal);

        energyL = findViewById(R.id.energyL);
        energyM = findViewById(R.id.energyM);
        energyH = findViewById(R.id.energyH);

        // Load task data from intent
        taskIndex = getIntent().getIntExtra("task_index", -1);
        String title = getIntent().getStringExtra("task_title");
        String subtitle = getIntent().getStringExtra("task_subtitle");
        String desc = getIntent().getStringExtra("task_description");
        String prio = getIntent().getStringExtra("task_priority");
        String cat = getIntent().getStringExtra("task_category");
        String energy = getIntent().getStringExtra("task_energy");
        String dl = getIntent().getStringExtra("task_deadline");
        dueDate = getIntent().getStringExtra("task_due_date");
        reminderMinutes = getIntent().getIntExtra("task_reminder", 5);

        if (title != null) editTitle.setText(title);
        if (desc != null && !desc.isEmpty()) editDescription.setText(desc);
        if (subtitle != null) {
            // Parse start time and duration from subtitle
            parseSubtitle(subtitle);
        }
        if (prio != null) selectedPriority = prio;
        if (cat != null) selectedCategory = cat;
        if (energy != null) selectedEnergy = energy;
        if (dl != null && !dl.isEmpty()) {
            deadline = dl;
            valueDeadline.setText(dl.startsWith("Tonight") ? dl : "Tonight, " + dl);
            valueDeadline.setTextColor(getColor(R.color.text_primary));
        }
        if (dueDate != null && !dueDate.isEmpty()) {
            valueDueDate.setText(formatDueDate(dueDate));
            valueDueDate.setTextColor(getColor(R.color.text_primary));
        }
        valueReminder.setText(reminderMinutes == 0 ? "At time of event" : (reminderMinutes == 60 ? "1 hour before" : reminderMinutes + " min before"));
        valueReminder.setTextColor(getColor(R.color.text_primary));

        updatePriorityUI();
        updateCategoryUI();
        updateEnergyUI();

        // Priority chips
        chipLow.setOnClickListener(v -> { selectedPriority = "low"; updatePriorityUI(); });
        chipMedium.setOnClickListener(v -> { selectedPriority = "medium"; updatePriorityUI(); });
        chipHigh.setOnClickListener(v -> { selectedPriority = "high"; updatePriorityUI(); });

        // Category tags
        catAcademics.setOnClickListener(v -> { selectedCategory = "Academics"; updateCategoryUI(); });
        catHealth.setOnClickListener(v -> { selectedCategory = "Health"; updateCategoryUI(); });
        catPersonal.setOnClickListener(v -> { selectedCategory = "Personal"; updateCategoryUI(); });

        // Energy dots
        energyL.setOnClickListener(v -> { selectedEnergy = "L"; updateEnergyUI(); });
        energyM.setOnClickListener(v -> { selectedEnergy = "M"; updateEnergyUI(); });
        energyH.setOnClickListener(v -> { selectedEnergy = "H"; updateEnergyUI(); });

        // Start time picker
        findViewById(R.id.fieldStartTime).setOnClickListener(v -> showTimePicker(valueStartTime, true));

        // Duration picker
        findViewById(R.id.fieldDuration).setOnClickListener(v -> showDurationPicker());

        // Deadline picker
        findViewById(R.id.fieldDeadline).setOnClickListener(v -> showTimePicker(valueDeadline, false));

        // Reminder picker
        findViewById(R.id.fieldReminder).setOnClickListener(v -> showReminderPicker());

        // Due date picker
        findViewById(R.id.fieldDueDate).setOnClickListener(v -> showDatePicker());

        // Close
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        // Save (top)
        findViewById(R.id.btnSave).setOnClickListener(v -> saveTask());

        // Save (bottom)
        findViewById(R.id.btnSaveBottom).setOnClickListener(v -> saveTask());

        // Delete
        findViewById(R.id.btnDeleteTask).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete task?")
                    .setMessage("This can't be undone.")
                    .setPositiveButton("Delete", (d, w) -> {
                        if (taskIndex >= 0) {
                            List<TimelineItem> tasks = TaskManager.loadTasks(this);
                            if (taskIndex < tasks.size()) {
                                TimelineItem deletedTask = tasks.get(taskIndex);
                                tasks.remove(taskIndex);
                                GoogleCalendarHelper.deleteTaskFromCalendar(TaskEditActivity.this, deletedTask);
                                TaskManager.sortTasksSmartly(tasks);
        TaskManager.saveTasks(this, tasks);
                                ReminderHelper.scheduleAll(this, tasks);
                            }
                        }
                        Intent result = new Intent();
                        result.putExtra("tasks_changed", true);
                        setResult(RESULT_OK, result);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void parseSubtitle(String subtitle) {
        if (subtitle == null) return;
        // Try to extract time like 2:00 PM, 4:30 PM, or 2 PM
        java.util.regex.Matcher timeMatcher = java.util.regex.Pattern
                .compile("(\\d{1,2}(?::\\d{2})?\\s*(?:AM|PM|am|pm))").matcher(subtitle);
        if (timeMatcher.find()) {
            startTime = timeMatcher.group(1).trim().toUpperCase(Locale.US);
            if (!startTime.contains(":")) {
                startTime = startTime.replaceFirst("(\\d{1,2})\\s*(AM|PM)", "$1:00 $2");
            }
            valueStartTime.setText("Today, " + startTime);
            valueStartTime.setTextColor(getColor(R.color.text_primary));
        }

        // Try to extract duration like 45 min, 2 hours
        java.util.regex.Matcher durMatcher = java.util.regex.Pattern
                .compile("(\\d+\\.?\\d*)\\s*(hours|hour|hrs|hr|mins|min|h|m)\\b").matcher(subtitle.toLowerCase(Locale.US));
        if (durMatcher.find()) {
            duration = durMatcher.group(0);
            valueDuration.setText(duration);
            valueDuration.setTextColor(getColor(R.color.text_primary));
        }
    }

    private void showTimePicker(TextView target, boolean isStartTime) {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String ampm = hourOfDay >= 12 ? "PM" : "AM";
            int h = hourOfDay > 12 ? hourOfDay - 12 : (hourOfDay == 0 ? 12 : hourOfDay);
            String formatted = String.format(Locale.US, "%d:%02d %s", h, minute, ampm);
            if (isStartTime) {
                startTime = formatted;
                target.setText("Today, " + formatted);
            } else {
                deadline = formatted;
                target.setText("Tonight, " + formatted);
            }
            target.setTextColor(getColor(R.color.text_primary));
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    private void showDurationPicker() {
        String[] options = {"15 min", "25 min", "30 min", "45 min", "1 hour", "1.5 hours", "2 hours", "3 hours"};
        new AlertDialog.Builder(this)
                .setTitle("Time to complete")
                .setItems(options, (d, which) -> {
                    duration = options[which];
                    valueDuration.setText(duration);
                    valueDuration.setTextColor(getColor(R.color.text_primary));
                })
                .show();
    }

    private void showReminderPicker() {
        String[] options = {"At time of event", "5 min before", "10 min before", "15 min before", "30 min before", "1 hour before"};
        int[] values = {0, 5, 10, 15, 30, 60};
        new AlertDialog.Builder(this)
                .setTitle("Reminder")
                .setItems(options, (d, which) -> {
                    reminderMinutes = values[which];
                    valueReminder.setText(options[which]);
                    valueReminder.setTextColor(getColor(R.color.text_primary));
                })
                .show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            dueDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            valueDueDate.setText(formatDueDate(dueDate));
            valueDueDate.setTextColor(getColor(R.color.text_primary));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String formatDueDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "Today";
        String today = TaskManager.todayDate();
        if (dateStr.equals(today)) return "Today";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat display = new SimpleDateFormat("EEE, MMM d", Locale.US);
            return display.format(sdf.parse(dateStr));
        } catch (Exception e) {
            return dateStr;
        }
    }

    private void saveTask() {
        String title = editTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Title can't be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build subtitle from start time + duration
        StringBuilder sub = new StringBuilder();
        if (!startTime.isEmpty()) sub.append(startTime);
        if (!duration.isEmpty()) {
            if (sub.length() > 0) sub.append(" \u00b7 ");
            sub.append(duration);
        }

        String description = editDescription.getText().toString().trim();

        List<TimelineItem> tasks = TaskManager.loadTasks(this);
          if (taskIndex >= 0 && taskIndex < tasks.size()) {
              // Update existing task
              TimelineItem task = tasks.get(taskIndex);
              GoogleCalendarHelper.deleteTaskFromCalendar(this, task); // Delete old event first
            tasks.set(taskIndex, new TimelineItem(
                    title, sub.toString(), task.isActive(), 
                    dueDate != null && !dueDate.isEmpty() ? dueDate : task.getDueDate(),
                    selectedPriority, description, selectedCategory,
                    selectedEnergy, deadline, reminderMinutes, task.getShiftedFromDate()
            ));
        } else {
            // New task (shouldn't happen from edit, but safety)
            tasks.add(new TimelineItem(
                    title, sub.toString(), false,
                    dueDate != null && !dueDate.isEmpty() ? dueDate : TaskManager.todayDate(),
                    selectedPriority, description, selectedCategory,
                    selectedEnergy, deadline, reminderMinutes, ""
            ));
        }

        TaskManager.sortTasksSmartly(tasks);
        TaskManager.saveTasks(this, tasks);
        ReminderHelper.scheduleAll(this, tasks);

        Intent result = new Intent();
        result.putExtra("tasks_changed", true);
        setResult(RESULT_OK, result);
        finish();
    }

    private void updatePriorityUI() {
        chipLow.setBackground(getDrawable("low".equals(selectedPriority) ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive));
        chipLow.setTextColor("low".equals(selectedPriority) ? 0xFFFFFFFF : getColor(R.color.text_secondary));
        chipMedium.setBackground(getDrawable("medium".equals(selectedPriority) ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive));
        chipMedium.setTextColor("medium".equals(selectedPriority) ? 0xFFFFFFFF : getColor(R.color.text_secondary));
        chipHigh.setBackground(getDrawable("high".equals(selectedPriority) ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive));
        chipHigh.setTextColor("high".equals(selectedPriority) ? 0xFFFFFFFF : getColor(R.color.text_secondary));
    }

    private void updateCategoryUI() {
        catAcademics.setBackground(getDrawable("Academics".equals(selectedCategory) ? R.drawable.bg_tag_active : R.drawable.bg_tag_inactive));
        catAcademics.setTextColor("Academics".equals(selectedCategory) ? getColor(R.color.accent_blue) : getColor(R.color.text_secondary));
        catHealth.setBackground(getDrawable("Health".equals(selectedCategory) ? R.drawable.bg_tag_active : R.drawable.bg_tag_inactive));
        catHealth.setTextColor("Health".equals(selectedCategory) ? getColor(R.color.accent_blue) : getColor(R.color.text_secondary));
        catPersonal.setBackground(getDrawable("Personal".equals(selectedCategory) ? R.drawable.bg_tag_active : R.drawable.bg_tag_inactive));
        catPersonal.setTextColor("Personal".equals(selectedCategory) ? getColor(R.color.accent_blue) : getColor(R.color.text_secondary));
    }

    private void updateEnergyUI() {
        energyL.setBackground(getDrawable("L".equals(selectedEnergy) ? R.drawable.bg_energy_dot_active : R.drawable.bg_energy_dot));
        energyL.setTextColor("L".equals(selectedEnergy) ? 0xFFE65100 : getColor(R.color.text_muted));
        energyM.setBackground(getDrawable("M".equals(selectedEnergy) ? R.drawable.bg_energy_dot_active : R.drawable.bg_energy_dot));
        energyM.setTextColor("M".equals(selectedEnergy) ? 0xFFE65100 : getColor(R.color.text_muted));
        energyH.setBackground(getDrawable("H".equals(selectedEnergy) ? R.drawable.bg_energy_dot_active : R.drawable.bg_energy_dot));
        energyH.setTextColor("H".equals(selectedEnergy) ? 0xFFE65100 : getColor(R.color.text_muted));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}