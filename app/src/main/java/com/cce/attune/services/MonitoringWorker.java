package com.cce.attune.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cce.attune.context.SettingsManager;

import java.util.concurrent.TimeUnit;


public class MonitoringWorker extends Worker {

    private static final String TAG = "MonitoringWorker";
    private static final String WORK_NAME = "service_watchdog";

    private static final long MONITORING_INTERVAL = 15; // WorkManager minimum

    public MonitoringWorker(@NonNull Context context,
                            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        if (!new SettingsManager(getApplicationContext()).isMonitoringEnabled()) {
            Log.d(TAG, "Monitoring disabled in settings, skipping service start");
            return Result.success();
        }

        Log.d(TAG, "Worker checking MonitoringService");

        MonitoringService.startService(getApplicationContext());

        return Result.success();
    }

    public static void startMonitoring(Context context) {

        if (!new SettingsManager(context).isMonitoringEnabled()) {
            Log.d(TAG, "Monitoring disabled in settings, will not schedule watchdog");
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            return;
        }

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        MonitoringWorker.class,
                        MONITORING_INTERVAL,
                        TimeUnit.MINUTES
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );

        Log.d(TAG, "Service watchdog scheduled");
    }
}
