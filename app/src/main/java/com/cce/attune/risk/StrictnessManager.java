package com.cce.attune.risk;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the user-configurable sensitivity level that controls when phubbing alerts fire.
 *
 * <ul>
 *   <li>LOW    — only alert on very strong phubbing signals (threshold 0.75)</li>
 *   <li>MEDIUM — balanced sensitivity (threshold 0.55)</li>
 *   <li>HIGH   — alert early / be strict (threshold 0.35)</li>
 * </ul>
 */
public class StrictnessManager {

    public enum Strictness {
        LOW(0.15f),    // Lenient: Baseline + 0.15
        MEDIUM(0f),    // Balanced: Baseline
        HIGH(-0.15f);  // Strict: Baseline - 0.15

        public final float offset;
        Strictness(float offset) { this.offset = offset; }
    }

    private static final String PREFS = "strictness_prefs";
    private static final String KEY   = "strictness";

    private final SharedPreferences prefs;

    public StrictnessManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Strictness getStrictness() {
        String name = prefs.getString(KEY, Strictness.MEDIUM.name());
        try {
            return Strictness.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Strictness.MEDIUM;
        }
    }

    public void setStrictness(Strictness s) {
        prefs.edit().putString(KEY, s.name()).apply();
    }

    /** Convenience — returns the risk threshold float for the current setting relative to baseline. */
    public float getThreshold(RiskEngine engine) {
        float baseline = engine.getBaselineRisk();
        float offset = getStrictness().offset;
        return Math.max(0f, Math.min(1f, baseline + offset));
    }
}
