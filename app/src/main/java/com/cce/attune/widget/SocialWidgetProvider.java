package com.cce.attune.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.cce.attune.R;
import com.cce.attune.context.SettingsManager;

/**
 * Home screen widget that provides a one-tap "I am in a social interaction" toggle
 * and displays the most recently computed phubbing risk score.
 *
 * <p>The toggle state is stored in SharedPreferences (PREF_MANUAL_SOCIAL) and is
 * picked up as a third social-context source by MonitoringService alongside schedules
 * and Bluetooth group detection.</p>
 */
public class SocialWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_SOCIAL = "com.cce.attune.ACTION_TOGGLE_SOCIAL";
    public static final String ACTION_WIDGET_UPDATE = "com.cce.attune.WIDGET_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_TOGGLE_SOCIAL.equals(action)) {
            // Flip the manual social toggle
            SettingsManager settings = new SettingsManager(context);
            boolean current = settings.isManualSocialActive();
            settings.setManualSocialActive(!current);

            // Poke MonitoringService so it picks up the change immediately
            com.cce.attune.services.MonitoringService.startService(context);

            // Refresh all widget instances
            refreshAllWidgets(context);

        } else if (ACTION_WIDGET_UPDATE.equals(action)) {
            // MonitoringService broadcast with updated risk score
            float risk = intent.getFloatExtra("risk_score", -1f);
            if (risk >= 0f) {
                new SettingsManager(context).setLastRiskScore(risk);
            }
            refreshAllWidgets(context);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Pushes fresh RemoteViews state to every placed instance of this widget. */
    private static void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, SocialWidgetProvider.class));
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    /** Builds and applies RemoteViews for a single widget instance. */
    public static void updateWidget(Context context,
                                    AppWidgetManager manager,
                                    int widgetId) {
        SettingsManager settings = new SettingsManager(context);
        boolean socialActive = settings.isManualSocialActive();
        float   risk         = settings.getLastRiskScore();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_social);

        // ── Risk score display ────────────────────────────────────────────────
        if (risk < 0f) {
            views.setTextViewText(R.id.widget_risk_score, "--");
            views.setTextColor(R.id.widget_risk_score, 0xFF1C1C1E); // text_primary
        } else {
            int percentage = Math.round(risk * 100f);
            views.setTextViewText(R.id.widget_risk_score, percentage + "%");
            views.setTextColor(R.id.widget_risk_score, riskColor(risk));
        }

        // ── Toggle slider appearance ──────────────────────────────────────────
        if (socialActive) {
            views.setImageViewResource(R.id.widget_toggle_track, R.drawable.widget_toggle_on);
            views.setTextViewText(R.id.widget_toggle_label, "In Social");
            views.setTextColor(R.id.widget_toggle_label, 0xFFFF6A2E);
        } else {
            views.setImageViewResource(R.id.widget_toggle_track, R.drawable.widget_toggle_off);
            views.setTextViewText(R.id.widget_toggle_label, "Not Social");
            views.setTextColor(R.id.widget_toggle_label, 0xFF6E6E73);
        }

        // ── Toggle click intent (on the whole right area) ─────────────────────
        Intent toggleIntent = new Intent(context, SocialWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_SOCIAL);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | android.app.PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_toggle_btn, pi);

        // ── App launch intent (on the remaining left area) ────────────────────
        Intent appIntent = new Intent(context, com.cce.attune.ui.MainActivity.class);
        android.app.PendingIntent appPi = android.app.PendingIntent.getActivity(
                context, 0, appIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        | android.app.PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, appPi);

        manager.updateAppWidget(widgetId, views);
    }

    /** Returns ARGB color for the risk score text based on severity. */
    private static int riskColor(float risk) {
        if (risk < 0.35f) return 0xFF34C759;      // risk_low  (green)
        if (risk < 0.65f) return 0xFFFF9500;      // risk_medium (amber)
        return              0xFFFF3B30;            // risk_high  (red)
    }
}
