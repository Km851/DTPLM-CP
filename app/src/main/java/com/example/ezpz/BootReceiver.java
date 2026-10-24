package com.example.ezpz;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            List<TimelineItem> tasks = TaskManager.loadTasks(context);
            ReminderHelper.scheduleAll(context, tasks);
        }
    }
}