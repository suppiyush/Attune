package com.cce.attune.risk;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.cce.attune.features.PhubbingFeatures;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * On-device TFLite classifier that estimates phubbing probability from behavioral features.
 *
 * <p>Input tensor:  float[1][5] — { unlockRate, switchRate, avgSessionSec, socialAppLaunches, notificationReactionSec }
 * <p>Output tensor: float[1][1] — phubbing probability in [0, 1]
 *
 * <p>If the model asset is absent or inference fails, returns a neutral stub value of 0.5f
 * so the hybrid score formula still operates correctly.
 */
public class PhubbingClassifier {

    private static final String TAG        = "PhubbingClassifier";
    private static final String MODEL_FILE = "phubbing_model.tflite";
    private static final float  STUB_SCORE = 0.5f;

    private final Interpreter interpreter;

    public PhubbingClassifier(Context context) {
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

    /**
     * Returns predicted phubbing probability [0..1].
     * Falls back to STUB_SCORE if model is unavailable.
     */
    public float predict(PhubbingFeatures f) {
        if (interpreter == null) return STUB_SCORE;
        try {
            float[][] input = {{
                f.unlockRate,
                f.switchRate,
                f.avgSessionDurationSeconds,
                f.socialAppLaunches,
                f.notificationReactionSeconds
            }};
            float[][] output = new float[1][1];
            interpreter.run(input, output);
            float score = output[0][0];
            return Math.max(0f, Math.min(1f, score));
        } catch (Exception e) {
            Log.w(TAG, "Inference failed — returning stub score.", e);
            return STUB_SCORE;
        }
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }
}
