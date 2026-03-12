package com.cce.attune.risk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cce.attune.features.PhubbingFeatures;

/**
 * Stores and adapts per-feature weights used in the heuristic component of the risk score.
 *
 * <p>Weights are nudged based on user feedback:
 * <ul>
 *   <li>Correct alert → increase weights of features that were elevated</li>
 *   <li>False alarm   → decrease those same weights</li>
 * </ul>
 * Weights are renormalized after every update so they always sum to 1.0.
 */
public class FeatureWeightStore {

    private static final String TAG   = "FeatureWeightStore";
    private static final String PREFS = "feature_weight_prefs";

    private static final String KEY_W_UNLOCK  = "w_unlock";
    private static final String KEY_W_SWITCH  = "w_switch";
    private static final String KEY_W_SESSION = "w_session";

    // Default weights (must sum to 1.0)
    private static final float DEFAULT_W_UNLOCK  = 0.4f;
    private static final float DEFAULT_W_SWITCH  = 0.3f;
    private static final float DEFAULT_W_SESSION = 0.3f;

    private static final float LEARNING_RATE = 0.02f;
    private static final float MIN_WEIGHT    = 0.05f;

    private final SharedPreferences prefs;

    public FeatureWeightStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public float getUnlockWeight()  { return prefs.getFloat(KEY_W_UNLOCK,  DEFAULT_W_UNLOCK);  }
    public float getSwitchWeight()  { return prefs.getFloat(KEY_W_SWITCH,  DEFAULT_W_SWITCH);  }
    public float getSessionWeight() { return prefs.getFloat(KEY_W_SESSION, DEFAULT_W_SESSION); }

    /**
     * Adjusts weights based on feedback for one alert event.
     *
     * @param wasCorrect true if user confirmed phubbing was happening; false if false alarm
     * @param f          the features at the time of the alert
     */
    public void applyFeedback(boolean wasCorrect, PhubbingFeatures f) {
        float sign = wasCorrect ? +LEARNING_RATE : -LEARNING_RATE;

        // Determine which features were "elevated" (above simple thresholds)
        boolean unlockElevated  = f.unlockRate > 6f;
        boolean switchElevated  = f.switchRate > 5f;
        boolean sessionElevated = f.avgSessionDurationSeconds < 45f; // short sessions → elevated

        float wUnlock  = getUnlockWeight()  + (unlockElevated  ? sign : 0f);
        float wSwitch  = getSwitchWeight()  + (switchElevated  ? sign : 0f);
        float wSession = getSessionWeight() + (sessionElevated ? sign : 0f);

        // Clamp to minimum
        wUnlock  = Math.max(MIN_WEIGHT, wUnlock);
        wSwitch  = Math.max(MIN_WEIGHT, wSwitch);
        wSession = Math.max(MIN_WEIGHT, wSession);

        // Renormalize
        float total = wUnlock + wSwitch + wSession;
        wUnlock  /= total;
        wSwitch  /= total;
        wSession /= total;

        prefs.edit()
                .putFloat(KEY_W_UNLOCK,  wUnlock)
                .putFloat(KEY_W_SWITCH,  wSwitch)
                .putFloat(KEY_W_SESSION, wSession)
                .apply();

        Log.d(TAG, String.format("Weights updated (%s) → unlock=%.3f switch=%.3f session=%.3f",
                wasCorrect ? "correct" : "false alarm", wUnlock, wSwitch, wSession));
    }

    public void reset() {
        prefs.edit()
                .putFloat(KEY_W_UNLOCK,  DEFAULT_W_UNLOCK)
                .putFloat(KEY_W_SWITCH,  DEFAULT_W_SWITCH)
                .putFloat(KEY_W_SESSION, DEFAULT_W_SESSION)
                .apply();
    }
}
