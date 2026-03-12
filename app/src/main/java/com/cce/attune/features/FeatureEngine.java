package com.cce.attune.features;

import android.content.Context;

import com.cce.attune.telemetry.UsageStatsCollector;

/**
 * Extracts behavioral features from telemetry data over the last hour.
 */
public class FeatureEngine {

    // Analysis window: last 1 hour
    private static final long WINDOW_MS = 60 * 60 * 1000L;

    private final UsageStatsCollector collector;

    public FeatureEngine(Context context) {
        this.collector = new UsageStatsCollector(context);
    }

    /**
     * Extracts features from the last hour of telemetry.
     * @return PhubbingFeatures with real values from UsageStatsManager + Room DB
     */
    public PhubbingFeatures extractFeatures() {
        long now = System.currentTimeMillis();
        long fromMs = now - WINDOW_MS;

        PhubbingFeatures features = new PhubbingFeatures();

        // Unlock rate: unlocks per hour (window is 1 hour so count = rate)
        features.unlockRate = collector.getUnlockCount(fromMs, now);

        // Average session duration in seconds
        features.avgSessionDurationSeconds = collector.getAvgSessionDurationSeconds(fromMs, now);

        // App switch rate: switches per hour
        features.switchRate = collector.getAppSwitchCount(fromMs, now);

        // Social app launches
        features.socialAppLaunches = collector.getSocialAppLaunchCount(fromMs, now);

        // Total screen time in seconds
        features.totalScreenTimeSeconds = collector.getTotalScreenTimeSeconds(fromMs, now);

        // Notification reaction time — currently derived from unlock rate as a proxy
        // Short time between notification and unlock signals phubbing
        // For MVP we use unlockRate as the proxy (high unlock = high reaction rate)
        features.notificationReactionSeconds = features.unlockRate > 0 ? (3600f / features.unlockRate) : 3600f;

        return features;
    }

    /** Features over the last N days, combined window. Used for baseline computation. */
    public PhubbingFeatures extractFeaturesForPeriod(long fromMs, long toMs) {
        PhubbingFeatures features = new PhubbingFeatures();
        float periodHours = (toMs - fromMs) / 3_600_000f;

        int unlocks = collector.getUnlockCount(fromMs, toMs);
        features.unlockRate = (periodHours > 0) ? (unlocks / periodHours) : 0;

        features.avgSessionDurationSeconds = collector.getAvgSessionDurationSeconds(fromMs, toMs);

        int switches = collector.getAppSwitchCount(fromMs, toMs);
        features.switchRate = (periodHours > 0) ? (switches / periodHours) : 0;

        features.socialAppLaunches = collector.getSocialAppLaunchCount(fromMs, toMs);
        features.totalScreenTimeSeconds = collector.getTotalScreenTimeSeconds(fromMs, toMs);
        features.notificationReactionSeconds = features.unlockRate > 0 ? (3600f / features.unlockRate) : 3600f;

        return features;
    }
}

