package com.example.ezpz;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleCalendarHelper {

    public static void syncTasksToCalendar(Context context, List<TimelineItem> tasks, boolean silent) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            if (!silent) Toast.makeText(context, "Please sign in to Google first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!silent) Toast.makeText(context, "Syncing tasks to Google Calendar...", Toast.LENGTH_SHORT).show();

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(CalendarScopes.CALENDAR));
        credential.setSelectedAccount(account.getAccount());

        Calendar service = new Calendar.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("EZPZ")
                .build();

        new Thread(() -> {
            try {
                int syncedCount = 0;
                for (TimelineItem task : tasks) {
                    if ("MISSED".equals(task.getDeadline())) {
                        continue;
                    }
                    String due = task.getDueDate();
                    if (due == null || due.isEmpty()) due = TaskManager.todayDate();

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    Date startDate = sdf.parse(due);
                    
                    DateTime startMin = new DateTime(startDate, TimeZone.getDefault());
                    Date endDateDay = new Date(startDate.getTime() + 86400000L);
                    DateTime startMax = new DateTime(endDateDay, TimeZone.getDefault());

                    Events events = service.events().list("primary")
                            .setTimeMin(startMin)
                            .setTimeMax(startMax)
                            .setQ(task.getTitle())
                            .execute();

                    // If event already exists, skip creating a duplicate
                    boolean exists = false;
                    for (Event e : events.getItems()) {
                        if (e.getSummary() != null && e.getSummary().equals(task.getTitle())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        Event event = new Event()
                                .setSummary(task.getTitle())
                                .setDescription(task.getSubtitle() + "\n\nSynced from EZPZ");
                                
                                                boolean hasTime = false;
                        int startHour = 9;
                        int startMinTime = 0;
                        int durationMins = 45; // Default

                        try {
                            String sub = task.getSubtitle().toLowerCase();
                            
                            // Extract explicit duration first if present (e.g., "2 hours", "45 min")
                            Matcher durMatcher = Pattern.compile("(\\d+)\\s*(hour|hr|min)").matcher(sub);
                            if (durMatcher.find()) {
                                int val = Integer.parseInt(durMatcher.group(1));
                                String unit = durMatcher.group(2);
                                if (unit.startsWith("hour") || unit.startsWith("hr")) {
                                    durationMins = val * 60;
                                } else {
                                    durationMins = val;
                                }
                            }

                            // Extract start time securely. Require AM/PM or a colon to avoid matching random numbers like "2 hours"
                            Matcher timeMatcher = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").matcher(sub);
                            if (timeMatcher.find()) {
                                hasTime = true;
                                startHour = Integer.parseInt(timeMatcher.group(1));
                                if (timeMatcher.group(2) != null) startMinTime = Integer.parseInt(timeMatcher.group(2));
                                String ampm = timeMatcher.group(3);
                                if (ampm.equals("pm") && startHour != 12) startHour += 12;
                                if (ampm.equals("am") && startHour == 12) startHour = 0;
                            } else {
                                // Maybe it's just "14:30" (military time)
                                Matcher militaryMatcher = Pattern.compile("\\b([0-1]?[0-9]|2[0-3]):([0-5][0-9])\\b").matcher(sub);
                                if (militaryMatcher.find()) {
                                    hasTime = true;
                                    startHour = Integer.parseInt(militaryMatcher.group(1));
                                    startMinTime = Integer.parseInt(militaryMatcher.group(2));
                                }
                            }
                        } catch(Exception e) {
                            Log.e("CalendarSync", "Regex parsing error", e);
                        }

                        if (hasTime) {
                            java.util.Calendar cal = java.util.Calendar.getInstance();
                            cal.setTime(startDate);
                            cal.set(java.util.Calendar.HOUR_OF_DAY, startHour);
                            cal.set(java.util.Calendar.MINUTE, startMinTime);
                            DateTime startDateTime = new DateTime(cal.getTime(), TimeZone.getDefault());
                            event.setStart(new EventDateTime().setDateTime(startDateTime));
                            
                            java.util.Calendar calEnd = (java.util.Calendar) cal.clone();
                            calEnd.add(java.util.Calendar.MINUTE, durationMins);

                            DateTime endDateTime = new DateTime(calEnd.getTime(), TimeZone.getDefault());
                            event.setEnd(new EventDateTime().setDateTime(endDateTime));
                        } else {
                            // All day event
                            DateTime dateOnly = new DateTime(sdf.format(startDate));
                            event.setStart(new EventDateTime().setDate(dateOnly));
                            // End date is exclusive for all-day events, so it must be startDate + 1 day
                            DateTime dateOnlyEnd = new DateTime(sdf.format(endDateDay));
                            event.setEnd(new EventDateTime().setDate(dateOnlyEnd));
                        }

                        service.events().insert("primary", event).execute();
                        syncedCount++;
                    }
                }
                
                final int finalCount = syncedCount;
                if (!silent && finalCount > 0) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(context, "Sync complete! Added " + finalCount + " new tasks to Calendar.", Toast.LENGTH_LONG).show();
                    });
                }

            } catch (Exception e) {
                Log.e("CalendarSync", "Error syncing tasks", e);
            }
        }).start();
    }

    public static void deleteTaskFromCalendar(Context context, TimelineItem task) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) return;

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(CalendarScopes.CALENDAR));
        credential.setSelectedAccount(account.getAccount());

        Calendar service = new Calendar.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("EZPZ")
                .build();

        new Thread(() -> {
            try {
                String due = task.getDueDate();
                if (due == null || due.isEmpty()) due = TaskManager.todayDate();
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date startDate = sdf.parse(due);
                // Widen search window just in case timezone math shifted the event slightly
                Date searchStart = new Date(startDate.getTime() - 86400000L);
                Date searchEnd = new Date(startDate.getTime() + 86400000L * 2);
                DateTime startMin = new DateTime(searchStart, TimeZone.getDefault());
                DateTime startMax = new DateTime(searchEnd, TimeZone.getDefault());

                Events events = service.events().list("primary")
                        .setTimeMin(startMin)
                        .setTimeMax(startMax)
                        .setQ(task.getTitle())
                        .execute();

                for (Event event : events.getItems()) {
                    if (event.getSummary() != null && event.getSummary().equals(task.getTitle())) {
                        service.events().delete("primary", event.getId()).execute();
                    }
                }
            } catch (Exception e) {
                Log.e("CalendarSync", "Error deleting task from Calendar", e);
            }
        }).start();
    }

    public static void importTasksFromCalendar(Context context, Runnable onComplete) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(CalendarScopes.CALENDAR));
        credential.setSelectedAccount(account.getAccount());

        Calendar service = new Calendar.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("EZPZ")
                .build();

        new Thread(() -> {
            try {
                String due = TaskManager.todayDate();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date startDate = sdf.parse(due);
                DateTime startMin = new DateTime(startDate, TimeZone.getDefault());
                Date endDateDay = new Date(startDate.getTime() + 86400000L);
                DateTime startMax = new DateTime(endDateDay, TimeZone.getDefault());

                Events events = service.events().list("primary")
                        .setTimeMin(startMin)
                        .setTimeMax(startMax)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .execute();

                List<TimelineItem> existingTasks = TaskManager.loadTasks(context);
                boolean changed = false;

                SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.US);

                for (Event event : events.getItems()) {
                    String title = event.getSummary();
                    if (title == null || title.isEmpty()) continue;
                    
                    // Don't import if it already exists in EZPZ
                    boolean exists = false;
                    for (TimelineItem t : existingTasks) {
                        if (t.getTitle().equals(title)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) { for (TimelineItem h : HistoryManager.getHistory(context)) { if (h.getTitle() != null && h.getTitle().trim().equalsIgnoreCase(title.trim()) && due.equals(h.getDueDate())) { exists = true; break; } } } if (exists) continue;

                    // Build subtitle for time
                    String subtitle = "";
                    if (event.getStart() != null && event.getStart().getDateTime() != null) {
                        Date evStart = new Date(event.getStart().getDateTime().getValue());
                        subtitle = timeFmt.format(evStart);
                        
                        if (event.getEnd() != null && event.getEnd().getDateTime() != null) {
                            Date evEnd = new Date(event.getEnd().getDateTime().getValue());
                            subtitle += " to " + timeFmt.format(evEnd);
                        }
                    } else {
                        subtitle = "All-day event";
                    }

                    // Create task
                    TimelineItem imported = new TimelineItem(
                            title, subtitle, true, due,
                            "medium", "Imported from Google Calendar", "Personal",
                            "medium", "", 0
                    );
                    existingTasks.add(imported);
                    changed = true;
                }

                if (changed) {
                    TaskManager.saveTasks(context, existingTasks);
                    ReminderHelper.scheduleAll(context, existingTasks);
                }

            } catch (Exception e) {
                Log.e("CalendarSync", "Error importing tasks", e);
            } finally {
                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
        }).start();
    }
}