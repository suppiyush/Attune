package com.cce.attune.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.FeedbackEvent;
import com.cce.attune.features.PhubbingFeatures;
import com.cce.attune.risk.FeatureWeightStore;
import com.cce.attune.risk.RiskEngine;

/**
 * Receives Yes/No survey responses from phubbing alert notification action buttons.
 *
 * <p>On receipt it:
 * <ol>
 *   <li>Updates the {@link FeedbackEvent} row created when the notification was sent</li>
 *   <li>Calls {@link FeatureWeightStore#applyFeedback} to nudge adaptive weights</li>
 * </ol>
 */
public class FeedbackReceiver extends BroadcastReceiver {

    public static final String ACTION_YES = "com.cce.attune.FEEDBACK_YES";
    public static final String ACTION_NO  = "com.cce.attune.FEEDBACK_NO";

    public static final String EXTRA_FEEDBACK_ID            = "feedback_id";
    public static final String EXTRA_UNLOCK_RATE            = "unlock_rate";
    public static final String EXTRA_SWITCH_RATE            = "switch_rate";
    public static final String EXTRA_AVG_SESSION            = "avg_session";
    public static final String EXTRA_SOCIAL_LAUNCHES        = "social_launches";
    public static final String EXTRA_NOTIF_REACTION         = "notif_reaction";

    private static final String TAG = "FeedbackReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null) return;

        boolean wasCorrect = ACTION_YES.equals(action);
        int feedbackId     = intent.getIntExtra(EXTRA_FEEDBACK_ID, -1);

        // Reconstruct feature snapshot from intent extras
        PhubbingFeatures f = new PhubbingFeatures();
        f.unlockRate                = intent.getFloatExtra(EXTRA_UNLOCK_RATE,     0f);
        f.switchRate                = intent.getFloatExtra(EXTRA_SWITCH_RATE,     0f);
        f.avgSessionDurationSeconds = intent.getFloatExtra(EXTRA_AVG_SESSION,     60f);
        f.socialAppLaunches         = (int) intent.getFloatExtra(EXTRA_SOCIAL_LAUNCHES, 0f);
        f.notificationReactionSeconds = intent.getFloatExtra(EXTRA_NOTIF_REACTION, 3600f);

        // Persist the response in DB
        if (feedbackId > 0) {
            try {
                AppDatabase.getInstance(context).feedbackDao().updateFeedback(feedbackId, wasCorrect);
                Log.d(TAG, "Feedback saved — id=" + feedbackId + " correct=" + wasCorrect);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save feedback", e);
            }
        }

        // Adapt feature weights using current user baseline
        RiskEngine riskEngine = new RiskEngine(context);
        new FeatureWeightStore(context).applyFeedback(wasCorrect, f, riskEngine);

        Log.d(TAG, "Feedback received: " + (wasCorrect ? "YES (correct)" : "NO (false alarm)"));

        // Dismiss the notification now that the user has responded
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NotificationService.NOTIFICATION_ID);
    }
}
