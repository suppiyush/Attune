package com.cce.attune.context;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SettingsManager {

    public static final String PREF_MONITORING    = "pref_monitoring_enabled";
    public static final String PREF_NOTIFICATIONS = "pref_notifications_enabled";
    public static final String PREF_BLUETOOTH     = "pref_bluetooth_enabled";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isMonitoringEnabled() {
        return prefs.getBoolean(PREF_MONITORING, true);
    }

    public boolean isPhubbingAlertsEnabled() {
        return prefs.getBoolean(PREF_NOTIFICATIONS, true);
    }

    public boolean isBluetoothSocialDetectionEnabled() {
        return prefs.getBoolean(PREF_BLUETOOTH, true);
    }
}
