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
        LOW(0.75f),
        MEDIUM(0.55f),
        HIGH(0.35f);

        public final float threshold;
        Strictness(float threshold) { this.threshold = threshold; }
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

    /** Convenience — returns the risk threshold float for the current setting. */
    public float getThreshold() {
        return getStrictness().threshold;
    }
}
