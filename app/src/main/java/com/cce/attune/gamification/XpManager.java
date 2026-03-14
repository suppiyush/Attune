package com.cce.attune.gamification;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * XpManager — tracks XP points and levels for the gamification system.
 *
 * XP sources (no penalties):
 * • +50 XP — clean day (no phubbing, status == 1)
 * • +20 XP — low avg-risk day (avg risk < 20 %)
 * • +15 XP — low unlock count (< 30 unlocks)
 * • +30 XP — social session survived clean
 *
 * Streak multipliers applied to the clean-day base award:
 * • 7–29 clean days → 1.5×
 * • 30+ clean days → 2.0×
 *
 * Bonuses (awarded once the threshold is crossed):
 * • 7-day clean streak → +100 XP
 * • 30-day clean streak → +500 XP
 */
public class XpManager {

    // ── SharedPreferences keys ────────────────────────────────────────────────
    private static final String PREFS_NAME = "xp_prefs";
    private static final String KEY_TOTAL_XP = "total_xp";
    private static final String KEY_CLEAN_STREAK = "clean_streak"; // current consecutive clean days
    private static final String KEY_LAST_DATE = "last_award_date"; // YYYY-MM-DD of last award
    private static final String KEY_BONUS_7_GIVEN = "bonus_7_given"; // whether 7-day bonus was awarded for current
                                                                     // streak run
    private static final String KEY_BONUS_30_GIVEN = "bonus_30_given"; // whether 30-day bonus was awarded for current
                                                                       // streak run

    // ── XP values ─────────────────────────────────────────────────────────────
    public static final int XP_CLEAN_DAY = 50;
    public static final int XP_LOW_RISK = 20; // avg risk < 20 %
    public static final int XP_LOW_UNLOCKS = 15; // < 30 unlocks
    public static final int XP_SOCIAL_CLEAN = 30; // social session, no phubbing
    public static final int XP_BONUS_7_DAY = 100;
    public static final int XP_BONUS_30_DAY = 500;

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final float LOW_RISK_THRESHOLD = 0.20f; // 20 %
    private static final int LOW_UNLOCK_THRESHOLD = 30;

    // ── XP per level (roughly) — simple linear tiers ─────────────────────────
    private static final int[] LEVEL_THRESHOLDS = {
            0, 100, 300, 600, 1000, 1500, 2200, 3000, 4000, 5500, Integer.MAX_VALUE
    };
    private static final String[] LEVEL_NAMES = {
            "Newbie", "Aware", "Mindful", "Focused", "Disciplined",
            "Balanced", "Intentional", "Attuned", "Master", "Legend"
    };

    private final SharedPreferences prefs;

    public XpManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called once per day (from MonitoringService) after the day's streak
     * status is settled.
     *
     * @param today            today's date string "yyyy-MM-dd"
     * @param cleanDay         true if today was a clean (no-phubbing) day
     * @param avgRisk          average risk score for today [0.0 – 1.0]
     * @param unlockCount      number of phone unlocks today
     * @param hadSocialSession true if at least one social session was detected
     *                         today
     */
    public void awardDailyXp(String today,
            boolean cleanDay,
            float avgRisk,
            int unlockCount,
            boolean hadSocialSession) {

        String lastDate = prefs.getString(KEY_LAST_DATE, "");
        if (today.equals(lastDate))
            return; // already awarded today — skip

        SharedPreferences.Editor editor = prefs.edit();
        int gained = 0;

        // ── Streak bookkeeping ───────────────────────────────────────────────
        int streak = prefs.getInt(KEY_CLEAN_STREAK, 0);

        if (cleanDay) {
            streak += 1;

            // Base clean-day XP with multiplier
            float multiplier = streakMultiplier(streak);
            int cleanXp = Math.round(XP_CLEAN_DAY * multiplier);
            gained += cleanXp;

            // Milestone bonuses (award once per streak run)
            if (streak == 7 && !prefs.getBoolean(KEY_BONUS_7_GIVEN, false)) {
                gained += XP_BONUS_7_DAY;
                editor.putBoolean(KEY_BONUS_7_GIVEN, true);
            }
            if (streak == 30 && !prefs.getBoolean(KEY_BONUS_30_GIVEN, false)) {
                gained += XP_BONUS_30_DAY;
                editor.putBoolean(KEY_BONUS_30_GIVEN, true);
            }

        } else {
            // Streak broken — reset milestone flags so they can be re-earned
            streak = 0;
            editor.putBoolean(KEY_BONUS_7_GIVEN, false);
            editor.putBoolean(KEY_BONUS_30_GIVEN, false);
        }

        editor.putInt(KEY_CLEAN_STREAK, streak);

        // ── Additional XP sources ────────────────────────────────────────────
        if (avgRisk < LOW_RISK_THRESHOLD) {
            gained += XP_LOW_RISK;
        }
        if (unlockCount < LOW_UNLOCK_THRESHOLD) {
            gained += XP_LOW_UNLOCKS;
        }
        if (hadSocialSession && cleanDay) {
            gained += XP_SOCIAL_CLEAN;
        }

        // ── Persist ──────────────────────────────────────────────────────────
        int total = prefs.getInt(KEY_TOTAL_XP, 0) + gained;
        editor.putInt(KEY_TOTAL_XP, total);
        editor.putString(KEY_LAST_DATE, today);
        editor.apply();
    }

    /** Total XP accumulated so far. */
    public int getTotalXp() {
        return prefs.getInt(KEY_TOTAL_XP, 0);
    }

    /** Current consecutive clean-day streak. */
    public int getCleanStreak() {
        return prefs.getInt(KEY_CLEAN_STREAK, 0);
    }

    /** Level index (0-based). */
    public int getLevel() {
        int xp = getTotalXp();
        for (int i = LEVEL_THRESHOLDS.length - 2; i >= 0; i--) {
            if (xp >= LEVEL_THRESHOLDS[i])
                return i;
        }
        return 0;
    }

    /** Human-readable level name. */
    public String getLevelName() {
        return LEVEL_NAMES[Math.min(getLevel(), LEVEL_NAMES.length - 1)];
    }

    /** XP needed for the next level (returns 0 if max level reached). */
    public int getXpForNextLevel() {
        int level = getLevel();
        if (level >= LEVEL_THRESHOLDS.length - 2)
            return 0;
        return LEVEL_THRESHOLDS[level + 1] - getTotalXp();
    }

    /** Progress within current level as a fraction [0.0 – 1.0]. */
    public float getLevelProgress() {
        int level = getLevel();
        if (level >= LEVEL_THRESHOLDS.length - 2)
            return 1f;
        int levelStart = LEVEL_THRESHOLDS[level];
        int levelEnd = LEVEL_THRESHOLDS[level + 1];
        int xp = getTotalXp();
        return (float) (xp - levelStart) / (levelEnd - levelStart);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private float streakMultiplier(int streak) {
        if (streak >= 30)
            return 2.0f;
        if (streak >= 7)
            return 1.5f;
        return 1.0f;
    }
}
