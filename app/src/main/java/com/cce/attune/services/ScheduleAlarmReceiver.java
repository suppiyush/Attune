package com.cce.attune.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Triggered by AlarmManager when a social schedule starts or ends.
 */
public class ScheduleAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ScheduleAlarmReceiver";

    public static final String ACTION_SCHEDULE_START = "com.cce.attune.ACTION_SCHEDULE_START";
    public static final String ACTION_SCHEDULE_END   = "com.cce.attune.ACTION_SCHEDULE_END";
    public static final String EXTRA_SESSION_ID      = "extra_session_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);

        Log.d(TAG, "Alarm received: " + action + " for session " + sessionId);

        if (ACTION_SCHEDULE_START.equals(action) || ACTION_SCHEDULE_END.equals(action)) {
            // Forward to MonitoringService
            Intent serviceIntent = new Intent(context, MonitoringService.class);
            serviceIntent.setAction(action);
            serviceIntent.putExtra(EXTRA_SESSION_ID, sessionId);
            context.startService(serviceIntent);
        }
    }
}
