package com.cce.attune.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.cce.attune.R;
import com.cce.attune.context.SettingsManager;
import com.cce.attune.context.SocialContextManager;
import com.cce.attune.features.FeatureEngine;
import com.cce.attune.features.PhubbingFeatures;
import com.cce.attune.notifications.NotificationService;
import com.cce.attune.risk.PhubbingClassifier;
import com.cce.attune.risk.RiskEngine;
import com.cce.attune.risk.StrictnessManager;
import com.cce.attune.telemetry.UnlockReceiver;
import com.cce.attune.ui.MainActivity;

import java.util.HashSet;
import java.util.Set;

public class MonitoringService extends Service {

    public static final String CHANNEL_ID = "monitoring_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "MonitoringService";
    private static final String SUMMARY_PREFS = "daily_summary_prefs";
    private static final String KEY_TODAY_DATE = "today_date";
    private static final String KEY_RISK_SUM = "risk_sum";
    private static final String KEY_RISK_COUNT = "risk_count";
    private static final String KEY_SESSIONS_COUNT = "sessions_count";

    private static final long BT_SCAN_INTERVAL_MS = 60 * 1000L;
    private static final long CHECK_INTERVAL_MS = 1 * 60 * 1000L;

    private UnlockReceiver unlockReceiver;
    private BroadcastReceiver btDiscoveryReceiver;

    private HandlerThread monitoringThread;
    private Handler handler;

    private final Set<String> currentScanMacs = new HashSet<>();

    private boolean monitoringStarted = false;
    private boolean isScheduleActive = false;

    private PhubbingClassifier classifier;

    private final Runnable btScanRunnable = new Runnable() {
        @Override
        public void run() {


            if (new SettingsManager(MonitoringService.this).isBluetoothSocialDetectionEnabled()) {
                startBluetoothDiscovery();
            } else {
                Log.d(TAG, "BT scanning disabled in settings");
            }
            handler.postDelayed(this, BT_SCAN_INTERVAL_MS);
        }
    };

    private final Runnable phubbingCheckRunnable = new Runnable() {
        @Override
        public void run() {

            runPhubbingCheck();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private void runPhubbingCheck() {
        if (!new SettingsManager(this).isPhubbingAlertsEnabled()) {
            Log.d(TAG, "Phubbing alerts disabled in settings; skipping check");
            return;
        }

        try {

            SocialContextManager cm = new SocialContextManager(this);

            boolean inSocialContext =
                    isScheduleActive
                            || cm.isBluetoothGroupActive(this);

            Log.d(TAG, "Phubbing check — social context: " + inSocialContext);
            Log.d(TAG, "Schedule Active: " + isScheduleActive);


            FeatureEngine featureEngine = new FeatureEngine(this);
            PhubbingFeatures features = featureEngine.extractFeatures();

            float aiScore = classifier != null
                    ? classifier.predict(features)
                    : 0f;

            Log.d(TAG,"ai score"+aiScore);
            Log.d(TAG,"features"+features);

            RiskEngine riskEngine = new RiskEngine(this);

            float risk = riskEngine.computeRisk(features, aiScore);
            
            // Save this calculated risk score to the database
            com.cce.attune.database.AppDatabase.getInstance(this)
                    .riskRecordDao()
                    .insert(new com.cce.attune.database.RiskRecord(System.currentTimeMillis(), risk));

            riskEngine.updateBaseline(features);

            float threshold =
                    new StrictnessManager(this).getThreshold(riskEngine);

            updateDailyMetrics(risk, inSocialContext);
            updateStreakStatus(risk, threshold);
            awardDailyXp(inSocialContext);

            if (inSocialContext && risk >= threshold) {

                NotificationService.sendPhubbingAlert(
                        this,
                        risk,
                        features
                );

                Log.d(TAG, "Alert sent");

            }

        } catch (Exception e) {

            Log.e(TAG, "Phubbing check failed", e);

        }
    }

    @Override
    public void onCreate() {

        super.onCreate();

        monitoringThread = new HandlerThread("MonitoringThread");
        monitoringThread.start();

        handler = new Handler(monitoringThread.getLooper());

        classifier = new PhubbingClassifier(this);

        createNotificationChannel();

        registerBtDiscoveryReceiver();
        registerUnlockReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {



        int type = 0;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            type |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        }

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                type |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            type |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildForegroundNotification(), type);
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground", e);
        }

        if (!monitoringStarted) {

            monitoringStarted = true;

            handler.post(btScanRunnable);
            handler.post(phubbingCheckRunnable);

            Log.d(TAG, "Monitoring loops started");

        }

        // Re-evaluate schedule state synchronously against the database
        // This ensures deleting an active schedule instantly updates the service state
        SocialContextManager cm = new SocialContextManager(this);
        isScheduleActive = cm.isSocialWindowActive(System.currentTimeMillis());
        
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, "Action received: " + intent.getAction() + " | isScheduleActive=" + isScheduleActive);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        handler.removeCallbacks(btScanRunnable);
        handler.removeCallbacks(phubbingCheckRunnable);

        if (btDiscoveryReceiver != null)
            unregisterReceiver(btDiscoveryReceiver);

        if (unlockReceiver != null)
            unregisterReceiver(unlockReceiver);

        stopBluetoothDiscovery();

        if (classifier != null)
            classifier.close();

        if (monitoringThread != null)
            monitoringThread.quitSafely();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void registerBtDiscoveryReceiver() {

        btDiscoveryReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context ctx, Intent intent) {

                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                    BluetoothDevice device =
                            intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE);

                    if (device != null) {

                        try {

                            currentScanMacs.add(
                                    device.getAddress());

                            Log.d(TAG,
                                    "BT found: "
                                            + device.getAddress());

                        } catch (SecurityException e) {

                            Log.w(TAG,
                                    "BT permission denied");

                        }

                    }

                } else if (
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                                .equals(action)) {

                    persistSeenMacs(currentScanMacs);

                    Log.d(TAG,
                            "BT discovery finished — "
                                    + currentScanMacs.size());

                }
            }
        };

        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(btDiscoveryReceiver, filter);
    }

    private void startBluetoothDiscovery() {

        try {

            BluetoothAdapter adapter =
                    BluetoothAdapter.getDefaultAdapter();

            if (adapter == null || !adapter.isEnabled()) {

                Log.d(TAG,
                        "BT off — skipping discovery");

                return;
            }

            currentScanMacs.clear();

            if (adapter.isDiscovering())
                adapter.cancelDiscovery();

            boolean started = adapter.startDiscovery();

            Log.d(TAG,
                    "BT discovery started: " + started);

        } catch (SecurityException e) {

            Log.w(TAG,
                    "BT scan permission denied");

        }
    }

    private void stopBluetoothDiscovery() {

        try {

            BluetoothAdapter adapter =
                    BluetoothAdapter.getDefaultAdapter();

            if (adapter != null && adapter.isDiscovering())
                adapter.cancelDiscovery();

        } catch (SecurityException ignored) {}
    }

    private void persistSeenMacs(Set<String> macs) {

        SharedPreferences prefs =
                getSharedPreferences(
                        SocialContextManager.BT_PREFS,
                        MODE_PRIVATE);

        prefs.edit()
                .putStringSet(
                        SocialContextManager.KEY_BT_SEEN,
                        new HashSet<>(macs))
                .apply();
    }

    private void registerUnlockReceiver() {
        unlockReceiver = new UnlockReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(unlockReceiver, filter);
    }

    private Notification buildForegroundNotification() {

        Intent openApp = new Intent(this, MainActivity.class);

        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle("Attune")
                .setContentText(
                        "Monitoring your phone habits")
                .setSmallIcon(
                        R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(
                        NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Monitoring Service",
                        NotificationManager.IMPORTANCE_LOW
                );

        channel.setDescription(
                "Attune background monitoring");

        NotificationManager nm =
                getSystemService(NotificationManager.class);

        if (nm != null)
            nm.createNotificationChannel(channel);
    }

    private void updateDailyMetrics(float currentRisk, boolean inSocial) {

        SharedPreferences prefs = getSharedPreferences(SUMMARY_PREFS, MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_TODAY_DATE, "");
        String currentDate = java.text.DateFormat.getDateInstance().format(new java.util.Date());

        SharedPreferences.Editor editor = prefs.edit();

        if (!currentDate.equals(savedDate)) {
            // New day, reset everything
            editor.putString(KEY_TODAY_DATE, currentDate);
            editor.putFloat(KEY_RISK_SUM, currentRisk);
            editor.putInt(KEY_RISK_COUNT, 1);
            editor.putInt(KEY_SESSIONS_COUNT, inSocial ? 1 : 0);
        } else {
            // Same day, accumulate
            float sum = prefs.getFloat(KEY_RISK_SUM, 0f) + currentRisk;
            int count = prefs.getInt(KEY_RISK_COUNT, 0) + 1;
            editor.putFloat(KEY_RISK_SUM, sum);
            editor.putInt(KEY_RISK_COUNT, count);
            
            if (inSocial) {
                // Simplified: Increment sessions count if we detect social context during this check
                // In a more robust system, we would track session start/end
                int sessions = prefs.getInt(KEY_SESSIONS_COUNT, 0) + 1;
                editor.putInt(KEY_SESSIONS_COUNT, sessions);
            }
        }
        editor.apply();
    }

    private void updateStreakStatus(float risk, float threshold) {

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        com.cce.attune.database.AppDatabase db = com.cce.attune.database.AppDatabase.getInstance(this);
        com.cce.attune.database.DailyStreak existing = db.dailyStreakDao().getStreakForDate(today);

        if (existing == null) {
            // First check of the day
            int initialStatus = (risk >= threshold) ? 2 : 1;
            db.dailyStreakDao().insertOrUpdate(new com.cce.attune.database.DailyStreak(today, initialStatus));
        } else if (existing.status == 1 && risk >= threshold) {
            // Found phubbing, break the "clean" streak for today
            db.dailyStreakDao().updateStatus(today, 2);
        }
    }

    private void awardDailyXp(boolean inSocialContext) {
        try {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());

            com.cce.attune.database.AppDatabase db = com.cce.attune.database.AppDatabase.getInstance(this);
            com.cce.attune.database.DailyStreak streak = db.dailyStreakDao().getStreakForDate(today);
            boolean cleanDay = streak != null && streak.status == 1;

            // Average risk for today
            SharedPreferences summaryPrefs = getSharedPreferences(SUMMARY_PREFS, MODE_PRIVATE);
            float riskSum   = summaryPrefs.getFloat(KEY_RISK_SUM, 0f);
            int   riskCount = summaryPrefs.getInt(KEY_RISK_COUNT, 0);
            float avgRisk   = riskCount > 0 ? (riskSum / riskCount) : 1f;

            // Unlock count for today
            com.cce.attune.telemetry.UsageStatsCollector collector = new com.cce.attune.telemetry.UsageStatsCollector(this);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            int unlocks = collector.getUnlockCount(cal.getTimeInMillis(), System.currentTimeMillis());

            new com.cce.attune.gamification.XpManager(this)
                    .awardDailyXp(today, cleanDay, avgRisk, unlocks, inSocialContext);
        } catch (Exception e) {
            Log.e(TAG, "XP award failed", e);
        }
    }

    public static void startService(Context context) {

        context.startForegroundService(
                new Intent(context, MonitoringService.class)
        );
    }
}