package com.cce.attune.features;

public class PhubbingFeatures {

    public float unlockRate;
    public float avgSessionDurationSeconds;
    public float switchRate;
    public float notificationReactionSeconds;
    public int socialAppLaunches;
    public long totalScreenTimeSeconds;

    @Override
    public String toString() {
        return "PhubbingFeatures{" +
                "unlockRate=" + unlockRate +
                ", avgSessionDurationSec=" + avgSessionDurationSeconds +
                ", switchRate=" + switchRate +
                ", notificationReactionSeconds=" + notificationReactionSeconds +
                ", socialAppLaunches=" + socialAppLaunches +
                ", totalScreenTimeSec=" + totalScreenTimeSeconds +
                '}';
    }
}