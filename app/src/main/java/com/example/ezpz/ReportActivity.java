package com.example.ezpz;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private int currentWeekOffset = 0;
    private List<TimelineItem> historyTasks;
    private List<TimelineItem> currentTasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report);
        // Animation Logic for Nav Indicator
        android.view.View navIndicator = findViewById(R.id.navIndicator);
        android.view.View bottomNavBar = findViewById(R.id.bottomNavBar);
        if (navIndicator != null && bottomNavBar != null) {
            int prevTab = getIntent().getIntExtra("prev_tab", 2);
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
                    float endPos = pad + (2 * tabW);
                    
                    navIndicator.setTranslationX(startPos);
                    if (prevTab != 2) {
                        navIndicator.animate().translationX(endPos).setDuration(250)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                }
            });
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            android.content.Intent home = new android.content.Intent(this, MainActivity.class);
            home.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            home.putExtra("prev_tab", 2); startActivity(home);
            overridePendingTransition(0, 0);
            finish();
        });

        // Nav bar
        findViewById(R.id.navHome).setOnClickListener(v -> {
            android.content.Intent home = new android.content.Intent(this, MainActivity.class);
            home.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            home.putExtra("prev_tab", 2); startActivity(home);
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navCalendar).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, SecondActivity.class); i.putExtra("prev_tab", 2); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });
        // navReport = current page
        findViewById(R.id.navSettings).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, SettingsActivity.class); i.putExtra("prev_tab", 2); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });

        // Week navigation buttons
        findViewById(R.id.btnPrevWeek).setOnClickListener(v -> {
            currentWeekOffset--;
            refreshCharts();
        });
        findViewById(R.id.btnNextWeek).setOnClickListener(v -> {
            if (currentWeekOffset < 0) {
                currentWeekOffset++;
                refreshCharts();
            }
        });

        currentTasks = TaskManager.loadTasks(this);
        historyTasks = HistoryManager.getHistory(this);
        StreakManager streak = new StreakManager(this);

        populateStats(currentTasks, streak);
        populateInsight(currentTasks, streak);
        refreshCharts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        currentTasks = TaskManager.loadTasks(this);
        historyTasks = HistoryManager.getHistory(this);
        StreakManager streak = new StreakManager(this);
        populateStats(currentTasks, streak);
        populateInsight(currentTasks, streak);
        refreshCharts();
    }

    private void refreshCharts() {
        TextView tvWeekLabel = findViewById(R.id.tvWeekLabel);
        if (tvWeekLabel != null) {
            if (currentWeekOffset == 0) {
                tvWeekLabel.setText("This week");
            } else if (currentWeekOffset == -1) {
                tvWeekLabel.setText("Last week");
            } else {
                tvWeekLabel.setText(Math.abs(currentWeekOffset) + " weeks ago");
            }
        }
        
        populateWeekChart(historyTasks);
        populateCategoryBars(historyTasks, -1);
        populateDailyTasks(historyTasks, -1);
    }

    private int[] weeklyCompletedByDay = new int[7];
    private int[] weeklyPendingByDay = new int[7];

    private void populateStats(List<TimelineItem> tasks, StreakManager streak) {
        int finalStreak = streak.getStreak();
        TextView tvStreak = findViewById(R.id.tvDayStreak);

        android.animation.ValueAnimator streakAnimator = android.animation.ValueAnimator.ofInt(0, finalStreak);
        streakAnimator.setDuration(800);
        streakAnimator.addUpdateListener(animation -> tvStreak.setText(String.valueOf(animation.getAnimatedValue())));
        streakAnimator.start();

        TextView tvStreakRank = findViewById(R.id.tvStreakRank);
        if (tvStreakRank != null) {
            tvStreakRank.setText(streak.getRankName().toUpperCase());
            tvStreakRank.setBackgroundTintList(android.content.res.ColorStateList.valueOf(streak.getRankColor()));
        }
    }
    
    private void showWeeklyOverallRate() {
        int comp = 0;
        int pend = 0;
        for (int i=0; i<7; i++) {
            comp += weeklyCompletedByDay[i];
            pend += weeklyPendingByDay[i];
        }
        updateRateUI(comp, comp + pend, "weekly overall");
        findViewById(R.id.btnResetOverall).setVisibility(View.GONE);
    }
    
    private void showDailyRate(int dayIndex) {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        int comp = weeklyCompletedByDay[dayIndex];
        int pend = weeklyPendingByDay[dayIndex];
        updateRateUI(comp, comp + pend, days[dayIndex] + " rate");
        findViewById(R.id.btnResetOverall).setVisibility(View.VISIBLE);
    }
    
    private void updateRateUI(int completed, int total, String label) {
        int rate = total > 0 ? (completed * 100 / total) : 0;
        TextView tvRate = findViewById(R.id.tvCompletionRate);
        TextView tvLabel = findViewById(R.id.tvRateLabel);
        if (tvLabel != null) tvLabel.setText(label);
        
        // Extract current integer from text
        int currentRate = 0;
        try {
            currentRate = Integer.parseInt(tvRate.getText().toString().replace("%", ""));
        } catch (Exception e) {}
        
        android.animation.ValueAnimator rateAnimator = android.animation.ValueAnimator.ofInt(currentRate, rate);
        rateAnimator.setDuration(500);
        rateAnimator.addUpdateListener(animation -> tvRate.setText(animation.getAnimatedValue() + "%"));
        rateAnimator.start();
    }

    private void populateWeekChart(List<TimelineItem> history) {
        LinearLayout chart = findViewById(R.id.weekChart);
        chart.removeAllViews();
        
        StackedCylinderChartView cylinderChart = new StackedCylinderChartView(this, null);
        chart.addView(cylinderChart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
                
        cylinderChart.setOnDaySelectedListener(dayIndex -> {
            cylinderChart.setSelectedDay(dayIndex);
            showDailyRate(dayIndex);
            populateCategoryBars(historyTasks, dayIndex);
            populateDailyTasks(historyTasks, dayIndex);
        });
        
        findViewById(R.id.btnResetOverall).setOnClickListener(v -> {
            cylinderChart.setSelectedDay(-1);
            showWeeklyOverallRate();
            populateCategoryBars(historyTasks, -1);
            populateDailyTasks(historyTasks, -1);
        });

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
        
        // Go to Monday of selected week
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DATE, -1);
        }

        int maxTasks = 1;
        StackedCylinderChartView.DayData[] dayDataArray = new StackedCylinderChartView.DayData[7];
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int d = 0; d < 7; d++) {
            weeklyCompletedByDay[d] = 0;
            weeklyPendingByDay[d] = 0;
            dayDataArray[d] = new StackedCylinderChartView.DayData();
            String dateKey = fmt.format(cal.getTime());
            
            for (TimelineItem t : history) {
                if (dateKey.equals(t.getDueDate())) {
                    weeklyCompletedByDay[d]++;
                    String cat = t.getCategory() != null ? t.getCategory().toLowerCase() : "";
                    if ("academics".equals(cat)) dayDataArray[d].academics++;
                    else if ("health".equals(cat)) dayDataArray[d].health++;
                    else if ("personal".equals(cat)) dayDataArray[d].personal++;
                    else dayDataArray[d].other++;
                }
            }
            
            for (TimelineItem t : currentTasks) {
                if (dateKey.equals(t.getDueDate())) {
                    weeklyPendingByDay[d]++;
                }
            }
            
            int total = dayDataArray[d].getTotal();
            if (total > maxTasks) maxTasks = total;
            cal.add(Calendar.DATE, 1);
        }

        Calendar today = Calendar.getInstance();
        int todayDow = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mon=0
        boolean isCurrentWeek = (currentWeekOffset == 0);

        cylinderChart.setData(dayDataArray, maxTasks, todayDow, isCurrentWeek);
        showWeeklyOverallRate(); // Initialize with overall rate
    }

    private void populateCategoryBars(List<TimelineItem> history, int dayIndex) {
        LinearLayout container = findViewById(R.id.categoryContainer);
        container.removeAllViews();
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DATE, -1);
        }
        
        List<String> weekDates = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i=0; i<7; i++) {
            if (dayIndex == -1 || dayIndex == i) {
                weekDates.add(fmt.format(cal.getTime()));
            }
            cal.add(Calendar.DATE, 1);
        }

        int academics = 0, health = 0, personal = 0, other = 0;
        for (TimelineItem t : history) {
            if (weekDates.contains(t.getDueDate())) {
                String cat = t.getCategory() != null ? t.getCategory().toLowerCase() : "";
                if ("academics".equals(cat)) academics++;
                else if ("health".equals(cat)) health++;
                else if ("personal".equals(cat)) personal++;
                else other++;
            }
        }
        
        int total = academics + health + personal + other;
        if (total == 0) {
            // No tasks — show empty state
            addCategoryRow(container, "Academics", 0, 1, 0xFFC26BFF, 0xFF8514F5); // Brand Purple
            addCategoryRow(container, "Health", 0, 1, 0xFF20E3B2, 0xFF0BA360);    // Vibrant Teal
            addCategoryRow(container, "Personal", 0, 1, 0xFF00C6FF, 0xFF0072FF);  // Vibrant Blue
            return;
        }

        addCategoryRow(container, "Academics", academics, total, 0xFFC26BFF, 0xFF8514F5);
        addCategoryRow(container, "Health", health, total, 0xFF20E3B2, 0xFF0BA360);
        addCategoryRow(container, "Personal", personal, total, 0xFF00C6FF, 0xFF0072FF);
        if (other > 0) {
            addCategoryRow(container, "Other", other, total, 0xFFFF7E5F, 0xFFFEB47B); // Sunset Pink/Orange
        }
    }

    private void addCategoryRow(LinearLayout container, String name, int count, int total, int startColor, int endColor) {
        int pct = total > 0 ? (count * 100 / total) : 0;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowP.setMargins(0, 0, 0, dpToPx(14));
        row.setLayoutParams(rowP);

        // Label row
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        label.setText(name);
        label.setTextSize(14);
        label.setTextColor(getColor(R.color.text_primary));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        labelRow.addView(label);

        TextView pctLabel = new TextView(this);
        pctLabel.setText(pct + "%");
        pctLabel.setTextSize(13);
        pctLabel.setTextColor(getColor(R.color.text_muted));
        labelRow.addView(pctLabel);

        row.addView(labelRow);

        // Progress bar
        LinearLayout barBg = new LinearLayout(this);
        LinearLayout.LayoutParams bgP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6));
        bgP.setMargins(0, dpToPx(6), 0, 0);
        barBg.setLayoutParams(bgP);
        GradientDrawable bgDraw = new GradientDrawable();
        bgDraw.setCornerRadius(dpToPx(3));
        bgDraw.setColor(0xFFEEEEEE);
        barBg.setBackground(bgDraw);

        View fill = new View(this);
        LinearLayout.LayoutParams fillP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
        float targetPct = Math.max(1, pct);
        fillP.weight = 0.01f; // start tiny
        fill.setLayoutParams(fillP);
        GradientDrawable fillDraw = new GradientDrawable();
        fillDraw.setCornerRadius(dpToPx(3));
        fillDraw.setColors(new int[]{startColor, endColor});
        fillDraw.setOrientation(GradientDrawable.Orientation.TL_BR);
        fill.setBackground(fillDraw);
        barBg.addView(fill);

        // Spacer for remaining
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
        spacerP.weight = 100f - 0.01f;
        spacer.setLayoutParams(spacerP);
        barBg.addView(spacer);

        if (targetPct > 0.01f) {
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0.01f, targetPct);
            anim.setDuration(600);
            anim.setStartDelay(200); // Small delay for visual effect
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                float val = (float) a.getAnimatedValue();
                fillP.weight = val;
                spacerP.weight = 100f - val;
                fill.setLayoutParams(fillP);
                spacer.setLayoutParams(spacerP);
            });
            anim.start();
        }

        row.addView(barBg);
        container.addView(row);
    }

    private void populateDailyTasks(List<TimelineItem> history, int dayIndex) {
        LinearLayout container = findViewById(R.id.dailyTaskListContainer);
        if (container == null) return;
        container.removeAllViews();
        
        if (dayIndex == -1) {
            container.setVisibility(View.GONE);
            return;
        }
        
        container.setVisibility(View.VISIBLE);
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset);
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DATE, -1);
        }
        cal.add(Calendar.DATE, dayIndex);
        String targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
        
        TextView header = new TextView(this);
        header.setText("Tasks completed");
        header.setTextSize(15);
        header.setTextColor(getColor(R.color.text_primary));
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.setPadding(0, 0, 0, dpToPx(8));
        container.addView(header);
        
        boolean foundAny = false;
        for (TimelineItem t : history) {
            if (targetDate.equals(t.getDueDate())) {
                foundAny = true;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dpToPx(6), 0, dpToPx(6));
                
                TextView dot = new TextView(this);
                dot.setText("•");
                dot.setTextColor(getColor(R.color.accent_blue));
                dot.setPadding(0, 0, dpToPx(8), 0);
                row.addView(dot);
                
                TextView name = new TextView(this);
                name.setText(t.getTitle());
                name.setTextColor(getColor(R.color.text_secondary));
                name.setTextSize(14);
                name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(name);
                
                TextView time = new TextView(this);
                String sub = t.getSubtitle();
                time.setText(sub != null && !sub.isEmpty() ? sub : t.getCategory());
                time.setTextColor(getColor(R.color.text_muted));
                time.setTextSize(12);
                row.addView(time);
                
                container.addView(row);
            }
        }
        
        if (!foundAny) {
            TextView empty = new TextView(this);
            empty.setText("No tasks completed on this day.");
            empty.setTextColor(getColor(R.color.text_muted));
            empty.setTextSize(14);
            empty.setPadding(0, dpToPx(8), 0, dpToPx(8));
            container.addView(empty);
        }
    }

    private void populateInsight(List<TimelineItem> tasks, StreakManager streak) {
        TextView tv = findViewById(R.id.tvInsight);
        int todayCount = countTodayTasks(tasks);
        int streakDays = streak.getStreak();
        int completed = streak.getCompletedToday();

        if (streakDays >= 7) {
            tv.setText("\uD83D\uDD25 " + streakDays + "-day streak! You're on fire. Keep the momentum going.");
        } else if (completed > 0 && todayCount == 0) {
            tv.setText("All tasks done today! Consider planning ahead for tomorrow.");
        } else if (todayCount > 5) {
            tv.setText("Heavy day ahead with " + todayCount + " tasks. Focus on high-priority ones first.");
        } else if (todayCount > 0) {
            tv.setText(todayCount + " tasks remaining today. You've got this!");
        } else {
            tv.setText("No tasks scheduled for today. Perfect time to plan your week.");
        }
    }

    private int countTodayTasks(List<TimelineItem> tasks) {
        String today = TaskManager.todayDate();
        int count = 0;
        for (TimelineItem t : tasks) {
            if (today.equals(t.getDueDate())) count++;
        }
        return count;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}