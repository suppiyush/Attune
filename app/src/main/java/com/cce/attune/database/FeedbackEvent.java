package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores a user's feedback response to a phubbing alert notification.
 * Each row records the features that triggered the alert and whether the
 * user confirmed the detection was correct.
 *
 * This data is used to:
 * 1. Adapt per-feature weights in {@link com.cce.attune.risk.FeatureWeightStore}
 * 2. Provide labeled training data for future AI model retraining
 */
@Entity(tableName = "feedback_events")
public class FeedbackEvent {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestamp;

    /** The hybrid risk score that triggered the alert (0..1). */
    public float riskScore;

    /**
     * Whether the user confirmed phubbing was happening.
     * null = no feedback given yet (notification dismissed without tapping).
     */
    public Boolean wasCorrect;

    // Feature snapshot at the time of the alert ─────────────────────────────
    public float unlockRate;
    public float switchRate;
    public float avgSessionDurationSeconds;
    public float socialAppLaunches;
    public float notificationReactionSeconds;

    public FeedbackEvent() {}

    public FeedbackEvent(long timestamp, float riskScore,
                         float unlockRate, float switchRate,
                         float avgSessionDurationSeconds,
                         float socialAppLaunches,
                         float notificationReactionSeconds) {
        this.timestamp                  = timestamp;
        this.riskScore                  = riskScore;
        this.unlockRate                 = unlockRate;
        this.switchRate                 = switchRate;
        this.avgSessionDurationSeconds  = avgSessionDurationSeconds;
        this.socialAppLaunches          = socialAppLaunches;
        this.notificationReactionSeconds= notificationReactionSeconds;
        this.wasCorrect                 = null; // populated after user taps Yes/No
    }
}
