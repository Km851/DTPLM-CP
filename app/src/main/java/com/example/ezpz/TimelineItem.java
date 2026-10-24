package com.example.ezpz;

public class TimelineItem {
    private String title;
    private String subtitle;
    private boolean isActive;
    private String dueDate;
    private String priority;    // "low", "medium", "high"
    private String description;
    private String category;    // "Academics", "Health", "Personal", etc.
    private String energyLevel; // "L", "M", "H"
    private String deadline;
    private int reminderMinutes; // minutes before to remind
    private String shiftedFromDate;

    public TimelineItem(String title, String subtitle, boolean isActive) {
        this(title, subtitle, isActive, "", "medium", "", "Academics", "M", "", 5);
    }

    public TimelineItem(String title, String subtitle, boolean isActive, String dueDate) {
        this(title, subtitle, isActive, dueDate, "medium", "", "Academics", "M", "", 5);
    }

    public TimelineItem(String title, String subtitle, boolean isActive, String dueDate,
                         String priority, String description, String category,
                         String energyLevel, String deadline, int reminderMinutes) {
        this(title, subtitle, isActive, dueDate, priority, description, category, energyLevel, deadline, reminderMinutes, "");
    }

    public TimelineItem(String title, String subtitle, boolean isActive, String dueDate,
                         String priority, String description, String category,
                         String energyLevel, String deadline, int reminderMinutes, String shiftedFromDate) {
        this.title = title;
        this.subtitle = subtitle;
        this.isActive = isActive;
        this.dueDate = dueDate == null ? "" : dueDate;
        this.priority = priority == null ? "medium" : priority;
        this.description = description == null ? "" : description;
        this.category = category == null ? "Academics" : category;
        this.energyLevel = energyLevel == null ? "M" : energyLevel;
        this.deadline = deadline == null ? "" : deadline;
        this.reminderMinutes = reminderMinutes;
        this.shiftedFromDate = shiftedFromDate == null ? "" : shiftedFromDate;
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public boolean isActive() { return isActive; }
    public String getDueDate() { return dueDate; }
    public String getPriority() { return priority; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getEnergyLevel() { return energyLevel; }
    public String getDeadline() { return deadline; }
    public int getReminderMinutes() { return reminderMinutes; }
    public String getShiftedFromDate() { return shiftedFromDate; }

    public void setTitle(String title) { this.title = title; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setEnergyLevel(String energyLevel) { this.energyLevel = energyLevel; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public void setReminderMinutes(int minutes) { this.reminderMinutes = minutes; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public void setShiftedFromDate(String shiftedFromDate) { this.shiftedFromDate = shiftedFromDate; }
}