package com.cce.attune.telemetry;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.TelemetryEvent;

public class UnlockReceiver extends BroadcastReceiver {

    private static final String TAG = "UnlockReceiver";
    private long screenOnTimeMs = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        if (!new com.cce.attune.context.SettingsManager(context).isMonitoringEnabled()) {
            return;
        }

        String action = intent.getAction();
        long now = System.currentTimeMillis();

        switch (action) {

            // Phone unlocked
            case Intent.ACTION_USER_PRESENT:
                screenOnTimeMs = now;
                Log.d("User Present", "Phone Unlocked");
                TelemetryEvent unlockEvent = new TelemetryEvent("UNLOCK", null, now, 0);
                insertEvent(context, unlockEvent);

                Log.d(TAG, "Unlock event recorded at " + now);
                break;

            // Screen turned off — compute session duration
            case Intent.ACTION_SCREEN_OFF:
                Log.d("Scree Off", "Screen Off");
                long duration = (screenOnTimeMs > 0) ? (now - screenOnTimeMs) : 0;
                TelemetryEvent screenOffEvent = new TelemetryEvent("SCREEN_OFF", null, now, duration);
                insertEvent(context, screenOffEvent);
                screenOnTimeMs = 0;

                Log.d(TAG, "Screen-off event recorded. Session duration: " + duration + "ms");
                break;
        }
    }
    private void insertEvent(Context context, TelemetryEvent event) {
        AppDatabase.getInstance(context).telemetryDao().insert(event);
    }
}

