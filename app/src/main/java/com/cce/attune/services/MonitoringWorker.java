package com.cce.attune.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cce.attune.context.SocialContextManager;
import com.cce.attune.features.FeatureEngine;
import com.cce.attune.features.PhubbingFeatures;
import com.cce.attune.notifications.NotificationService;
import com.cce.attune.risk.PhubbingClassifier;
import com.cce.attune.risk.RiskEngine;
import com.cce.attune.risk.StrictnessManager;

import java.util.concurrent.TimeUnit;


public class MonitoringWorker extends Worker {

    private static final String TAG       = "MonitoringWorker";
    private static final String WORK_NAME = "phubbing_check";

    public MonitoringWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Log.d(TAG, "MonitoringWorker running periodic analysis");

        try {
            SocialContextManager contextManager = new SocialContextManager(ctx);
            boolean inSocialContext = contextManager.isSocialWindowActive(System.currentTimeMillis())
                    || contextManager.isBluetoothGroupActive(ctx);

            if (!inSocialContext) {
                Log.d(TAG, "Not in social context — skipping phubbing check");
                return Result.success();
            }

            FeatureEngine    featureEngine = new FeatureEngine(ctx);
            PhubbingFeatures features      = featureEngine.extractFeatures();

            // AI score (stub 0.5 when model absent)
            PhubbingClassifier classifier = new PhubbingClassifier(ctx);
            float aiScore = classifier.predict(features);
            classifier.close();

            RiskEngine riskEngine = new RiskEngine(ctx);
            float risk = riskEngine.computeRisk(features, aiScore);
            riskEngine.updateBaseline(features);

            float threshold = new StrictnessManager(ctx).getThreshold();
            Log.d(TAG, "Risk=" + risk + " threshold=" + threshold + " ai=" + aiScore);

            if (risk >= threshold) {
                NotificationService.sendPhubbingAlert(ctx, risk, features);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "MonitoringWorker failed", e);
            return Result.retry();
        }
    }

    /** Schedule periodic checks using WorkManager */
    public static void startMonitoring(Context context) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                MonitoringWorker.class,
                2, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
        );

        Log.d(TAG, "Monitoring scheduled");
    }
}
