package com.cce.attune.services;

import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Starts the MonitoringService when the device boots.

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            
            if (!new com.cce.attune.context.SettingsManager(context).isMonitoringEnabled()) {
                Log.i(TAG, "Boot completed — Monitoring is disabled, skipping service start");
                return;
            }

            Log.i(TAG, "Boot completed — starting MonitoringService & Worker");
            MonitoringService.startService(context);
            MonitoringWorker.startMonitoring(context);
            
            // Reschedule social alarms
            com.cce.attune.context.ScheduleAlarmManager.rescheduleAllAlarms(context);

            Log.d(TAG, "Services and alarms started successfully");
        }
    }
}

