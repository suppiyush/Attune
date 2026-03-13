package com.cce.attune.context;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.SocialSession;
import com.cce.attune.services.ScheduleAlarmReceiver;

import java.util.List;

/**
 * Manages AlarmManager schedules for SocialSessions.
 */
public class ScheduleAlarmManager {

    private static final String TAG = "ScheduleAlarmManager";

    public static void rescheduleAllAlarms(Context context) {
        Log.d(TAG, "Rescheduling all future social alarms");
        
        AppDatabase db = AppDatabase.getInstance(context);
        List<SocialSession> futureSessions = db.socialSessionDao().getSessionsEndingAfter(System.currentTimeMillis());

        for (SocialSession session : futureSessions) {
            scheduleSessionAlarms(context, session);
        }
    }

    public static void scheduleSessionAlarms(Context context, SocialSession session) {
        long now = System.currentTimeMillis();

        // 1. Start Alarm (if in future)
        if (session.startMs > now) {
            setAlarm(context, session.id, session.startMs, true);
        }

        // 2. End Alarm (if in future)
        if (session.endMs > now) {
            setAlarm(context, session.id, session.endMs, false);
        }
    }

    public static void cancelSessionAlarms(Context context, int sessionId) {
        cancelAlarm(context, sessionId, true);
        cancelAlarm(context, sessionId, false);
    }

    private static void setAlarm(Context context, int sessionId, long timeMs, boolean isStart) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.setAction(isStart ? ScheduleAlarmReceiver.ACTION_SCHEDULE_START : ScheduleAlarmReceiver.ACTION_SCHEDULE_END);
        intent.putExtra(ScheduleAlarmReceiver.EXTRA_SESSION_ID, sessionId);

        // Unique RequestCode per session+type
        int requestCode = sessionId * 2 + (isStart ? 0 : 1);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
            }
            Log.d(TAG, "Scheduled alarm: " + (isStart ? "START" : "END") + " for session " + sessionId + " at " + timeMs);
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException: Cannot schedule exact alarm. Falling back to inexact alarm.");
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
        }
    }

    private static void cancelAlarm(Context context, int sessionId, boolean isStart) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.setAction(isStart ? ScheduleAlarmReceiver.ACTION_SCHEDULE_START : ScheduleAlarmReceiver.ACTION_SCHEDULE_END);
        intent.putExtra(ScheduleAlarmReceiver.EXTRA_SESSION_ID, sessionId);
        int requestCode = sessionId * 2 + (isStart ? 0 : 1);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.cancel(pi);
        Log.d(TAG, "Canceled alarm: " + (isStart ? "START" : "END") + " for session " + sessionId);
    }
}
