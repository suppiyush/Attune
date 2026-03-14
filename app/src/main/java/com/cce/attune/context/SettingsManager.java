package com.cce.attune.context;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SettingsManager {

    public static final String PREF_NOTIFICATIONS   = "pref_notifications_enabled";
    public static final String PREF_BLUETOOTH       = "pref_bluetooth_enabled";
    public static final String PREF_MANUAL_SOCIAL   = "pref_manual_social_active";
    public static final String PREF_LAST_RISK_SCORE = "pref_last_risk_score";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isPhubbingAlertsEnabled() {
        return prefs.getBoolean(PREF_NOTIFICATIONS, true);
    }

    public boolean isBluetoothSocialDetectionEnabled() {
        return prefs.getBoolean(PREF_BLUETOOTH, true);
    }

    /** Returns true if the user manually declared a social interaction via the widget. */
    public boolean isManualSocialActive() {
        return prefs.getBoolean(PREF_MANUAL_SOCIAL, false);
    }

    /** Toggles the manual social interaction state set from the home screen widget. */
    public void setManualSocialActive(boolean active) {
        prefs.edit().putBoolean(PREF_MANUAL_SOCIAL, active).apply();
    }

    /** Stores the most recent risk score so the widget can read it without waiting. */
    public void setLastRiskScore(float risk) {
        prefs.edit().putFloat(PREF_LAST_RISK_SCORE, risk).apply();
    }

    /** Returns the most recently stored risk score, or -1 if none recorded yet. */
    public float getLastRiskScore() {
        return prefs.getFloat(PREF_LAST_RISK_SCORE, -1f);
    }
}
