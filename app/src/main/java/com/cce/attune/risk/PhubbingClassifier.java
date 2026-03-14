package com.cce.attune.risk;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.cce.attune.features.PhubbingFeatures;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * On-device TFLite classifier that estimates phubbing probability from behavioral features.
 *
 * <p>Input tensor:  float[1][5] — { unlockRate, switchRate, avgSessionSec, socialAppLaunches, notificationReactionSec }
 * <p>Output tensor: float[1][1] — phubbing probability in [0, 1]
 *
 * <p>Before inference, raw input features are standardised using pre-saved
 * {@code scaler_params.json} (mean + scale per feature), matching the
 * {@code StandardScaler} applied in the Python training notebook.
 *
 * <p>If the model asset or scaler params are absent / inference fails, returns
 * a neutral stub value of 0.5f so the hybrid score formula still operates correctly.
 */
public class PhubbingClassifier {

    private static final String TAG          = "PhubbingClassifier";
    private static final String MODEL_FILE   = "phubbing_model.tflite";
    private static final String SCALER_FILE  = "scaler_params.json";
    private static final float  STUB_SCORE   = 0.5f;

    // Feature order must match the order used during Python training.
    private static final String[] FEATURE_NAMES = {
        "unlockRate",
        "switchRate",
        "avgSessionDurationSeconds",
        "socialAppLaunches",
        "notificationReactionSeconds"
    };
    private static final int NUM_FEATURES = FEATURE_NAMES.length;

    private final Interpreter interpreter;

    // Scaler parameters loaded from scaler_params.json
    private final double[] featureMeans;
    private final double[] featureScales;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PhubbingClassifier(Context context) {
        // --- Load scaler parameters first ---
        double[] means  = null;
        double[] scales = null;
        try {
            means  = new double[NUM_FEATURES];
            scales = new double[NUM_FEATURES];
            loadScalerParams(context, means, scales);
            Log.d(TAG, "Scaler params loaded successfully.");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load scaler params — scaling disabled. (" + e.getMessage() + ")");
            means  = null;
            scales = null;
        }
        this.featureMeans  = means;
        this.featureScales = scales;

        // --- Load TFLite model ---
        Interpreter temp = null;
        try {
            MappedByteBuffer model = loadModelFile(context);
            temp = new Interpreter(model);
            Log.d(TAG, "TFLite model loaded successfully.");
        } catch (Exception e) {
            Log.w(TAG, "TFLite model not available — using stub score 0.5. (" + e.getMessage() + ")");
        }
        this.interpreter = temp;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns predicted phubbing probability [0..1].
     * Raw features are standardised before inference when scaler params are available.
     * Falls back to STUB_SCORE if model is unavailable.
     */
    public float predict(PhubbingFeatures f) {
        if (interpreter == null) return STUB_SCORE;

        try {
            // 1. Collect raw feature values (same order as FEATURE_NAMES).
            float[] raw = {
                f.unlockRate,
                f.switchRate,
                f.avgSessionDurationSeconds,
                f.socialAppLaunches,
                f.notificationReactionSeconds
            };

            Log.d(TAG, "Raw input  → unlockRate=" + raw[0]
                    + " switchRate=" + raw[1]
                    + " avgSessionSec=" + raw[2]
                    + " socialAppLaunches=" + raw[3]
                    + " notifReactionSec=" + raw[4]);

            // 2. Apply StandardScaler: z = (x - mean) / scale
            float[] scaled = applyScaling(raw);

            Log.d(TAG, "Scaled input → " + scaled[0] + ", " + scaled[1]
                    + ", " + scaled[2] + ", " + scaled[3] + ", " + scaled[4]);

            // 3. Run inference.
            float[][] input  = { scaled };
            float[][] output = new float[1][1];
            interpreter.run(input, output);

            float score = output[0][0];
            Log.d(TAG, "Model output score: " + score);
            return Math.max(0f, Math.min(1f, score));

        } catch (Exception e) {
            Log.w(TAG, "Inference failed — returning stub score.", e);
            return STUB_SCORE;
        }
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }

    // -------------------------------------------------------------------------
    // Scaling helper
    // -------------------------------------------------------------------------

    /**
     * Applies z-score standardisation to {@code raw} using the loaded scaler params.
     * If params were not loaded, returns the raw values unchanged (graceful degradation).
     *
     * <p>Formula: {@code scaled[i] = (raw[i] - mean[i]) / scale[i]}
     */
    private float[] applyScaling(float[] raw) {
        float[] scaled = new float[NUM_FEATURES];

        if (featureMeans == null || featureScales == null) {
            // Scaler unavailable — pass through unmodified (will likely degrade accuracy).
            Log.w(TAG, "Scaler params not available; using raw (unscaled) features.");
            System.arraycopy(raw, 0, scaled, 0, NUM_FEATURES);
            return scaled;
        }

        for (int i = 0; i < NUM_FEATURES; i++) {
            double scale = featureScales[i];
            if (scale == 0.0) {
                // Guard against division-by-zero for constant features.
                scaled[i] = 0f;
                Log.w(TAG, "Scale is zero for feature '" + FEATURE_NAMES[i] + "' — setting scaled value to 0.");
            } else {
                scaled[i] = (float) ((raw[i] - featureMeans[i]) / scale);
            }
        }
        return scaled;
    }

    // -------------------------------------------------------------------------
    // Asset loading
    // -------------------------------------------------------------------------

    /**
     * Parses {@code scaler_params.json} from the assets folder.
     *
     * <p>Expected JSON structure (produced by the Python notebook):
     * <pre>
     * {
     *   "mean":  [m0, m1, m2, m3, m4],
     *   "scale": [s0, s1, s2, s3, s4]
     * }
     * </pre>
     * The array order must match {@link #FEATURE_NAMES}.
     */
    private void loadScalerParams(Context context, double[] means, double[] scales)
            throws Exception {

        InputStream is = context.getAssets().open(SCALER_FILE);
        byte[] buffer = new byte[is.available()];
        //noinspection ResultOfMethodCallIgnored
        is.read(buffer);
        is.close();

        String json = new String(buffer, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);

        org.json.JSONArray meanArray  = root.getJSONArray("mean");
        org.json.JSONArray scaleArray = root.getJSONArray("scale");

        if (meanArray.length() != NUM_FEATURES || scaleArray.length() != NUM_FEATURES) {
            throw new IllegalArgumentException(
                "scaler_params.json has " + meanArray.length() + " features; expected " + NUM_FEATURES);
        }

        for (int i = 0; i < NUM_FEATURES; i++) {
            means[i]  = meanArray.getDouble(i);
            scales[i] = scaleArray.getDouble(i);
        }
    }

    /**
     * Memory-maps the TFLite model file from assets for zero-copy loading.
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }
}