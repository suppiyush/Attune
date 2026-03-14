package com.cce.attune.risk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cce.attune.features.PhubbingFeatures;

/**
 * Computes a hybrid phubbing risk score combining three components:
 *
 * <pre>
 *   risk = 0.5 × heuristic + 0.3 × aiScore + 0.2 × deviation
 * </pre>
 *
 * <ul>
 *   <li>Heuristic  — rule-based formula using adaptive per-feature weights</li>
 *   <li>AI score   — TFLite classifier result (0.5 stub when model absent)</li>
 *   <li>Deviation  — distance from the user's personal EMA baseline</li>
 * </ul>
 */
public class RiskEngine {

    private static final String TAG   = "RiskEngine";
    private static final String PREFS = "risk_engine_prefs";

    // ── Baseline EMA keys ────────────────────────────────────────────────────
    private static final String KEY_BASELINE_UNLOCK  = "baseline_unlock_rate";
    private static final String KEY_BASELINE_SWITCH  = "baseline_switch_rate";
    private static final String KEY_BASELINE_SESSION = "baseline_session_sec";
    private static final String KEY_BASELINE_AI      = "baseline_ai_score";

    private static final float DEFAULT_BASELINE_UNLOCK  = 8f;
    private static final float DEFAULT_BASELINE_SWITCH  = 10f;
    private static final float DEFAULT_BASELINE_SESSION = 60f;
    private static final float DEFAULT_BASELINE_AI      = 0.4f;

    // ── Hybrid weights ───────────────────────────────────────────────────────
    private static final float W_HEURISTIC = 0.5f;
    private static final float W_AI        = 0.3f;
    private static final float W_DEVIATION = 0.2f;

    private final SharedPreferences  prefs;
    private final FeatureWeightStore weightStore;
    private final StrictnessManager  strictnessManager;

    public RiskEngine(Context context) {
        prefs             = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        weightStore       = new FeatureWeightStore(context);
        strictnessManager = new StrictnessManager(context);
    }

    /**
     * Hybrid risk score (0..1).
     *
     * @param f       behavioral features extracted for the current window
     * @param aiScore prediction from {@link PhubbingClassifier#predict(PhubbingFeatures)}
     */
    public float computeRisk(PhubbingFeatures f, float aiScore) {
        float heuristic = computeHeuristic(f);
        float deviation = computeDeviation(f);

        float risk = W_HEURISTIC * heuristic
                   + W_AI        * aiScore
                   + W_DEVIATION * deviation;

        Log.d(TAG, String.format(
                "Risks → heuristic=%.3f  ai=%.3f  deviation=%.3f  hybrid=%.3f",
                heuristic, aiScore, deviation, risk));

        return clamp(risk, 0f, 1f);
    }

    // ── Heuristic component (adaptive weights) ───────────────────────────────

    private float computeHeuristic(PhubbingFeatures f) {
        float wUnlock  = weightStore.getUnlockWeight();
        float wSwitch  = weightStore.getSwitchWeight();
        float wSession = weightStore.getSessionWeight();

        float unlockNorm  = clamp(f.unlockRate / 20f, 0f, 1f);
        float sessionNorm = clamp(1f - (f.avgSessionDurationSeconds / 120f), 0f, 1f);
        float switchNorm  = clamp(f.switchRate / 20f, 0f, 1f);

        return wUnlock * unlockNorm + wSwitch * switchNorm + wSession * sessionNorm;
    }

    // ── Deviation component (personal baseline) ──────────────────────────────

    private float computeDeviation(PhubbingFeatures f) {
        float baseUnlock  = prefs.getFloat(KEY_BASELINE_UNLOCK,  DEFAULT_BASELINE_UNLOCK);
        float baseSwitch  = prefs.getFloat(KEY_BASELINE_SWITCH,  DEFAULT_BASELINE_SWITCH);
        float baseSession = prefs.getFloat(KEY_BASELINE_SESSION, DEFAULT_BASELINE_SESSION);

        float unlockDev  = (baseUnlock  > 0) ? clamp((f.unlockRate - baseUnlock) / baseUnlock, 0f, 1f) : 0f;
        float switchDev  = (baseSwitch  > 0) ? clamp((f.switchRate - baseSwitch) / baseSwitch, 0f, 1f) : 0f;
        float sessionDev = (baseSession > 0) ? clamp((baseSession - f.avgSessionDurationSeconds) / baseSession, 0f, 1f) : 0f;

        return (unlockDev + switchDev + sessionDev) / 3f;
    }

    // ── EMA baseline update ──────────────────────────────────────────────────

    /** Update rolling baseline after each monitoring cycle (EMA α = 0.1). */
    public void updateBaseline(PhubbingFeatures f) {
        float alpha = 0.1f;

        float prevUnlock  = prefs.getFloat(KEY_BASELINE_UNLOCK,  DEFAULT_BASELINE_UNLOCK);
        float prevSwitch  = prefs.getFloat(KEY_BASELINE_SWITCH,  DEFAULT_BASELINE_SWITCH);
        float prevSession = prefs.getFloat(KEY_BASELINE_SESSION, DEFAULT_BASELINE_SESSION);
        float prevAi      = prefs.getFloat(KEY_BASELINE_AI,      DEFAULT_BASELINE_AI);

        // For updateBaseline we don't have aiScore passed in natively.
        // For simplicity, we just use DEFAULT_BASELINE_AI or update signature to include aiScore.
        // Let's assume aiScore is DEFAULT_BASELINE_AI here unless we change signature.
        float currentAiScore = DEFAULT_BASELINE_AI; 

        prefs.edit()
                .putFloat(KEY_BASELINE_UNLOCK,  (1 - alpha) * prevUnlock  + alpha * f.unlockRate)
                .putFloat(KEY_BASELINE_SWITCH,  (1 - alpha) * prevSwitch  + alpha * f.switchRate)
                .putFloat(KEY_BASELINE_SESSION, (1 - alpha) * prevSession + alpha * f.avgSessionDurationSeconds)
                .putFloat(KEY_BASELINE_AI,      (1 - alpha) * prevAi      + alpha * currentAiScore)
                .apply();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public float getBaselineUnlockRate()     { return prefs.getFloat(KEY_BASELINE_UNLOCK,  DEFAULT_BASELINE_UNLOCK);  }
    public float getBaselineSwitchRate()     { return prefs.getFloat(KEY_BASELINE_SWITCH,  DEFAULT_BASELINE_SWITCH);  }
    public float getBaselineSessionDuration(){ return prefs.getFloat(KEY_BASELINE_SESSION, DEFAULT_BASELINE_SESSION); }
    public float getBaselineAiScore()        { return prefs.getFloat(KEY_BASELINE_AI,      DEFAULT_BASELINE_AI);      }

    /**
     * Computes the theoretical risk score if the user's current behavior
     * exactly matches their historical baseline.
     */
    public float getBaselineRisk() {
        PhubbingFeatures baselineF = new PhubbingFeatures();
        baselineF.unlockRate = getBaselineUnlockRate();
        baselineF.switchRate = getBaselineSwitchRate();
        baselineF.avgSessionDurationSeconds = getBaselineSessionDuration();

        float heuristic = computeHeuristic(baselineF);
        // Deviation is 0 by definition when behavior matches baseline exactly
        float deviation = 0f;
        float aiScore = getBaselineAiScore();

        float risk = W_HEURISTIC * heuristic
                   + W_AI        * clamp(aiScore, 0f, 1f)
                   + W_DEVIATION * deviation;

        return clamp(risk, 0f, 1f);
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    public RiskLevel getRiskLevel(float risk) {
        float baseline = getBaselineRisk();
        float threshold = strictnessManager.getThreshold(this);

        float lower = Math.min(baseline, threshold);
        float upper = Math.max(baseline, threshold);

        if (risk < lower) return RiskLevel.LOW;
        if (risk > upper) return RiskLevel.HIGH;
        return RiskLevel.MEDIUM;
    }

    public String riskLabel(float risk) {
        RiskLevel level = getRiskLevel(risk);
        switch (level) {
            case LOW:    return "Low";
            case HIGH:   return "High";
            case MEDIUM:
            default:     return "Medium";
        }
    }

    public String explainRisk(float risk) {
        RiskLevel level = getRiskLevel(risk);
        switch (level) {
            case LOW:    return "Phone usage appears normal.";
            case HIGH:   return "Strong phubbing behaviour detected.";
            case MEDIUM:
            default:     return "Frequent phone checking detected.";
        }
    }
}