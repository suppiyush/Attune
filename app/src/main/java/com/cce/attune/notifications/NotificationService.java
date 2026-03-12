package com.cce.attune.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.cce.attune.R;
import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.FeedbackEvent;
import com.cce.attune.features.PhubbingFeatures;
import com.cce.attune.ui.MainActivity;

/**
 * Sends mindful phubbing alert notifications.
 *
 * <p>Each alert includes two action buttons — "Yes, I was" and "No, false alarm" —
 * which route to {@link FeedbackReceiver} to store the response and adapt feature weights.
 */
public class NotificationService {

    public static final String CHANNEL_ID    = "phubbing_alert_channel";
    public  static final int   NOTIFICATION_ID = 2001;

    private static final String[] MINDFUL_MESSAGES = {
            "You've been checking your phone a lot — take a moment to reconnect.",
            "Those around you will remember your presence more than your posts.",
            "Look up. The real world is worth your attention.",
            "Your phone can wait. The person beside you cannot.",
            "Mindful moment: put the phone down and engage with those around you.",
    };

    /**
     * Sends a phubbing alert notification with a Yes/No survey appended.
     *
     * @param context   app context
     * @param riskScore hybrid risk score that triggered the alert
     * @param features  feature snapshot used for feedback weight adaptation
     */
    public static void sendPhubbingAlert(Context context, float riskScore, PhubbingFeatures features) {
        createChannel(context);

        // Persist a FeedbackEvent row (wasCorrect = null until user taps)
        FeedbackEvent event = new FeedbackEvent(
                System.currentTimeMillis(), riskScore,
                features.unlockRate, features.switchRate,
                features.avgSessionDurationSeconds,
                features.socialAppLaunches,
                features.notificationReactionSeconds
        );
        int feedbackId = (int) AppDatabase.getInstance(context).feedbackDao().insertFeedback(event);

        // Build shared intent extras for feedback receiver
        Intent yesIntent = buildFeedbackIntent(context, FeedbackReceiver.ACTION_YES, feedbackId, features);
        Intent noIntent  = buildFeedbackIntent(context, FeedbackReceiver.ACTION_NO,  feedbackId, features);

        PendingIntent yesPi = PendingIntent.getBroadcast(context, feedbackId * 10 + 1,
                yesIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent noPi = PendingIntent.getBroadcast(context, feedbackId * 10 + 2,
                noIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tap-to-open action
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPi = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String message  = MINDFUL_MESSAGES[(int) (System.currentTimeMillis() % MINDFUL_MESSAGES.length)];
        String riskText = "Risk level: " + Math.round(riskScore * 100) + "%";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Stay Present 🧘")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message + "\n\n" + riskText + "\nWere you phubbing?"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .addAction(0, "✓  Yes, I was",   yesPi)
                .addAction(0, "✗  No, false alarm", noPi);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, builder.build());
    }

    private static Intent buildFeedbackIntent(Context context, String action,
                                              int feedbackId, PhubbingFeatures f) {
        Intent intent = new Intent(context, FeedbackReceiver.class);
        intent.setAction(action);
        intent.putExtra(FeedbackReceiver.EXTRA_FEEDBACK_ID, feedbackId);
        intent.putExtra(FeedbackReceiver.EXTRA_UNLOCK_RATE,    f.unlockRate);
        intent.putExtra(FeedbackReceiver.EXTRA_SWITCH_RATE,    f.switchRate);
        intent.putExtra(FeedbackReceiver.EXTRA_AVG_SESSION,    f.avgSessionDurationSeconds);
        intent.putExtra(FeedbackReceiver.EXTRA_SOCIAL_LAUNCHES,f.socialAppLaunches);
        intent.putExtra(FeedbackReceiver.EXTRA_NOTIF_REACTION, f.notificationReactionSeconds);
        return intent;
    }

    private static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Phubbing Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Gentle reminders to stay present during social interactions");
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
