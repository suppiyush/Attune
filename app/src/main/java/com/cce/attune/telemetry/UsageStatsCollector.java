package com.cce.attune.telemetry;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import com.cce.attune.database.AppDatabase;

import java.util.ArrayList;
import java.util.List;

public class UsageStatsCollector {

    private final Context context;
    private final AppDatabase db;

    // Social apps to identify context
    private static final List<String> SOCIAL_PACKAGES = new ArrayList<String>() {{
        add("com.whatsapp");
        add("com.instagram.android");
        add("com.facebook.katana");
        add("com.facebook.orca"); // Messenger
        add("com.snapchat.android");
        add("com.twitter.android");
        add("com.linkedin.android");
        add("com.discord");
        add("com.telegram.messenger");
    }};

    public UsageStatsCollector(Context context) {
        this.context = context;
        this.db = AppDatabase.getInstance(context);
    }

    public int getUnlockCount(long fromMs, long toMs) {
        try {
            return db.telemetryDao().getUnlockCount(fromMs, toMs);
        } catch (Exception e) {
            return 0;
        }
    }

    public int getAppSwitchCount(long fromMs, long toMs) {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            UsageEvents events = usm.queryEvents(fromMs, toMs);
            int switchCount = 0;
            String lastPackage = null;

            while (events.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (lastPackage != null && !lastPackage.equals(event.getPackageName())) {
                        switchCount++;
                    }
                    lastPackage = event.getPackageName();
                }
            }
            return switchCount;
        } catch (Exception e) {
            Log.d("App Switch Exception", e.toString());
            return 0;
        }
    }

    public float getAvgSessionDurationSeconds(long fromMs, long toMs) {
        try {
            float avgMs = db.telemetryDao().getAvgSessionDuration(fromMs, toMs);
            return avgMs / 1000f;
        } catch (Exception e) {
            return 0f;
        }
    }

    public long getTotalScreenTimeSeconds(long fromMs, long toMs) {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            UsageEvents events = usm.queryEvents(fromMs, toMs);
            long screenOnTime = 0;
            long lastForeground = 0;

            while (events.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && lastForeground > 0) {
                    screenOnTime += (event.getTimeStamp() - lastForeground);
                    lastForeground = 0;
                }
            }
            return screenOnTime / 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    public int getSocialAppLaunchCount(long fromMs, long toMs) {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            UsageEvents events = usm.queryEvents(fromMs, toMs);
            int count = 0;

            while (events.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                        && SOCIAL_PACKAGES.contains(event.getPackageName())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean hasUsageStatsPermission() {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return false;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 1000, now);
            return events != null;
        } catch (Exception e) {
            return false;
        }
    }
}

