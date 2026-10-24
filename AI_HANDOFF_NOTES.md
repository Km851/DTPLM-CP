# EZPZ (FocusFlow) Android App - Complete AI Handoff & Architecture Notes

## 1. Project Location & Build Environment
- Root Path: C:\Users\Krish\AndroidStudioProjects\EZPZ
- App Module: C:\Users\Krish\AndroidStudioProjects\EZPZ\app
- Package: com.example.ezpz
- JAVA_HOME for Gradle CLI: C:\Program Files\Android\Android Studio\jbr
- Local Storage File: tasks.json in internal app storage via TaskManager.java

## 2. All 10 TaskEditActivity Fields (Now 100% Controlled by Gemini AI)
1. Task title -> title (String)
2. Description -> description (String, default empty)
3. Start time -> startTime (String in h:mm AM/PM format, e.g. 2:00 PM) + synced into subtitle
4. Time to complete -> duration (String, e.g. 30 min, 45 min, 2 hours) + synced into subtitle
5. Due date -> dueDate (String in yyyy-MM-dd format; relative dates resolved using current day-of-week + date)
6. Deadline -> deadline (String in h:mm AM/PM format, e.g. 5:00 PM, or MISSED)
7. Reminder -> reminderMinutes (int: 0 = At time of event, 5, 10, 15, 30, 60 = 1 hour before; default 5)
8. Priority -> priority (String: low, medium, high)
9. Category -> category (String: Academics, Health, Personal)
10. Energy needed -> energyLevel (String: L, M, H)
- Plus: Delete tasks (including clearing all tasks), Reorder tasks, and Split into subtasks.

## 3. Changes Applied & Verified (BUILD SUCCESSFUL)
1. GeminiHelper.java (editTaskList):
   - Added current day of week (EEEE) alongside yyyy-MM-dd date.
   - Expanded Gemini JSON output schema and instructions to include all 10 fields: title, description, startTime, duration, subtitle, dueDate, deadline, reminderMinutes, priority, category, energyLevel.
   - Instructed Gemini to infer category (Academics/Health/Personal) for new tasks and preserve all existing fields on unmodified tasks.
2. TaskManager.java (saveTasks & parseJsonResponse):
   - saveTasks now also writes explicit startTime and duration keys (extracted from subtitle) into tasks.json so Gemini sees them directly.
   - parseJsonResponse now merges startTime + duration into subtitle (via buildSubtitle), preserves active and shiftedFromDate, and normalizes priority (low/medium/high), category (Academics/Health/Personal), energyLevel (L/M/H), and reminderMinutes (int).
3. TaskEditActivity.java (onCreate & parseSubtitle):
   - parseSubtitle now supports both 2:00 PM and 2 PM formats and matches plural duration units (hours, mins) cleanly.
   - Restored active text color and formatting for valueDeadline, valueDueDate, and valueReminder (0 -> At time of event, 60 -> 1 hour before).
4. MainActivity.java (AI Breakdown Preview & Accept Handler):
   - Preview sheet now displays Category, Priority, Energy, Due Date, Deadline, and Description for every task in the AI preview.
   - Accept button now supports applying an empty task list when the user asks Gemini to delete/clear all tasks.

## 5. Front Page Top-3 Selection & 4th AI Task Recommendation Architecture
- Current behavior in MainActivity.java:
  - updateVisibleItems() loops through allItems in raw insertion order, picks the first 3 tasks due today (matching currentFilter), and stops at visibleItems.size() == 3.
  - getTodayItems() also returns today tasks in raw insertion order.
  - fetchNewQuest() takes todayItems.subList(3, todayItems.size()) (the 4th+ tasks of today) + future tasks (dueDate > today) and asks Gemini to pick 1 task for the bottom card (Start early: <title>).
  - All tasks (today + other dates) are accessible in SecondActivity.java (2nd nav tab - Calendar view).
- Problem identified:
  - Because allItems/updateVisibleItems() does not sort by priority or urgency, a newly added High-priority task (4th in the list) gets stuck at index 3 (in the bottom Start early bar) while 3 older Medium-priority tasks occupy the 3 front-page slots.
- Proposed Multi-Tier Sorting Logic for Front Page Top-3:
  1. Priority: high (3) > medium (2) > low (1)
  2. Deadline Urgency: Tasks with an earlier deadline today come before tasks with a later deadline or no deadline
  3. Scheduled Start Time: Tasks with an earlier startTime in subtitle come before later or unscheduled tasks
  4. Shifted/Rollover Status: Tasks rolled over from a past date (shiftedFromDate not empty) beat newer unscheduled tasks
  5. Duration / Energy Tie-Breaker: Shorter duration (Quick Wins, e.g. 30 min before 2 hr) or Energy match, followed by original list order.

## 6. Instant 5-Tier Smart Sort + Manual Drag-and-Drop Reordering (Implemented & Verified)
- Why Manual Reordering Was Missing Before:
  1. In MainActivity.java, ItemTouchHelper.SimpleCallback had dragDirs = 0 and onMove() returned false.
  2. adapter.setOnLongClickListener was starting the timer on long-press instead of letting ItemTouchHelper initiate a vertical drag.
- How Both Automatic 5-Tier Sorting and Manual Arrangement Work Together Now:
  1. Automatic 5-Tier Smart Sort (TaskManager.sortTasksSmartly):
     - Runs automatically and instantaneously (< 1 ms) when saving a task in TaskEditActivity.saveTask(), when Gemini updates tasks (unless the user explicitly asks Gemini to reorder/move to top), and once on initial upgrade (smart_sort_init_v1).
     - Sorts same-day tasks by: (1) Priority (high > medium > low), (2) Earlier Hard Deadline, (3) Earlier Start Time, (4) Rolled-over/Shifted tasks first, (5) Shorter Duration (Quick Wins, e.g. 30 min before 2 hr).
  2. Manual Drag-and-Drop on Front Page (MainActivity.java & TimelineAdapter.java):
     - Enabled UP and DOWN drag directions in ItemTouchHelper on recyclerTimeline.
     - Long-pressing any of the Top 3 cards on the Home screen elevates the card with haptic feedback and lets the user drag it up or down.
     - Dropping the card updates both visibleItems and allItems, dynamically transfers the purple active highlight (isActive = true) to whichever card is now at Slot #1, and persists the manual order to tasks.json.
  3. Promoting 4th+ Tasks from Calendar Page (SecondActivity.java):
     - Long-pressing any task row in the Calendar To-Do list pins/moves that task to #1 on the Home screen (Pinned to top of Home screen!).
  4. Asking Gemini in Chat:
     - Saying Put DTPLM CP at the top or Move Tournament first sets lastAiKeepOrder = true so Gemini custom order is preserved without being overridden by smart sort.
