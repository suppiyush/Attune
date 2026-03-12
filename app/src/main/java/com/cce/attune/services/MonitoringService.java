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
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.cce.attune.R;
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

/**
 * Foreground service that keeps the app alive in the background.
 *
 * <p>Two periodic loops run inside a single Handler:</p>
 * <ul>
 *   <li>BT discovery  — refreshes the nearby-device cache every BT_SCAN_INTERVAL_MS.</li>
 *   <li>Phubbing check — evaluates social context and phubbing risk every CHECK_INTERVAL_MS.</li>
 * </ul>
 * Both loops start immediately when the service starts, so the first run happens at launch.
 */
public class MonitoringService extends Service {

    public static final String CHANNEL_ID   = "monitoring_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG          = "MonitoringService";

    // ── Intervals ──────────────────────────────────────────────────────────
    private static final long BT_SCAN_INTERVAL_MS = 1 * 60 * 1000L;  // 1 minute
    private static final long CHECK_INTERVAL_MS   = 2 * 60 * 1000L;  // 2 minutes

    private UnlockReceiver      unlockReceiver;
    private BroadcastReceiver   btDiscoveryReceiver;
    private final Handler       handler         = new Handler(Looper.getMainLooper());
    private final Set<String>   currentScanMacs = new HashSet<>();

    /** Loaded once in onCreate() and reused across every check cycle. */
    private PhubbingClassifier  classifier;

    // ── BT scan loop ───────────────────────────────────────────────────────

    private final Runnable btScanRunnable = new Runnable() {
        @Override public void run() {
            startBluetoothDiscovery();
            handler.postDelayed(this, BT_SCAN_INTERVAL_MS);
        }
    };

    // ── Phubbing evaluation loop ───────────────────────────────────────────

    private final Runnable phubbingCheckRunnable = new Runnable() {
        @Override public void run() {
            runPhubbingCheck();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private void runPhubbingCheck() {
        try {
            SocialContextManager cm = new SocialContextManager(MonitoringService.this);
            boolean inSocialContext =
                    cm.isSocialWindowActive(System.currentTimeMillis())
                    || cm.isBluetoothGroupActive(MonitoringService.this);

            Log.d(TAG, "Phubbing check — social context: " + inSocialContext);

            // NOTE: social context gate is bypassed during testing so the full
            // pipeline always runs. Re-enable the early-return after testing:
            // if (!inSocialContext) return;

            FeatureEngine    featureEngine = new FeatureEngine(MonitoringService.this);
            PhubbingFeatures features      = featureEngine.extractFeatures();

            // AI score from TFLite (0.5 stub when model absent)
            float aiScore = classifier != null ? classifier.predict(features) : 0.5f;

            RiskEngine riskEngine = new RiskEngine(MonitoringService.this);
            float risk            = riskEngine.computeRisk(features, aiScore);

            // Update personal baseline after each observation
            riskEngine.updateBaseline(features);

            // Strictness-aware threshold
            float threshold = new StrictnessManager(MonitoringService.this).getThreshold();

            Log.d(TAG, "Risk=" + risk + " | threshold=" + threshold
                    + " | ai=" + aiScore + " | social=" + inSocialContext);

            if (risk >= threshold) {
                NotificationService.sendPhubbingAlert(MonitoringService.this, risk, features);
                Log.d(TAG, "Alert sent!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Phubbing check failed", e);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        classifier = new PhubbingClassifier(this);
        createNotificationChannel();
        registerUnlockReceiver();
        registerBtDiscoveryReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildForegroundNotification());
        // Both loops start immediately — no initial delay
        handler.post(btScanRunnable);
        handler.post(phubbingCheckRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(btScanRunnable);
        handler.removeCallbacks(phubbingCheckRunnable);
        if (unlockReceiver != null)      unregisterReceiver(unlockReceiver);
        if (btDiscoveryReceiver != null) unregisterReceiver(btDiscoveryReceiver);
        stopBluetoothDiscovery();
        if (classifier != null)          classifier.close();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Bluetooth discovery ───────────────────────────────────────────────────

    private void registerBtDiscoveryReceiver() {
        btDiscoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        try {
                            currentScanMacs.add(device.getAddress());
                            Log.d(TAG, "BT found: " + device.getAddress()
                                    + " (" + device.getName() + ")");
                        } catch (SecurityException e) {
                            Log.w(TAG, "BT permission denied reading device");
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    persistSeenMacs(currentScanMacs);
                    Log.d(TAG, "BT discovery finished — " + currentScanMacs.size() + " devices cached");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(btDiscoveryReceiver, filter);
    }

    private void startBluetoothDiscovery() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                Log.d(TAG, "BT off or unavailable — skipping discovery");
                return;
            }
            currentScanMacs.clear();
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            boolean started = adapter.startDiscovery();
            Log.d(TAG, "BT discovery started: " + started);
        } catch (SecurityException e) {
            Log.w(TAG, "BT scan permission denied — skipping");
        }
    }

    private void stopBluetoothDiscovery() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException ignored) {}
    }

    private void persistSeenMacs(Set<String> macs) {
        SharedPreferences prefs = getSharedPreferences(SocialContextManager.BT_PREFS, MODE_PRIVATE);
        prefs.edit()
             .putStringSet(SocialContextManager.KEY_BT_SEEN, new HashSet<>(macs))
             .apply();
    }

    // ── Notification / channel ────────────────────────────────────────────────

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
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Attune")
                .setContentText("Monitoring your phone habits in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Monitoring Service", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Persistent notification while Attune monitors your habits");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    /** Static helper to start the service from MainActivity */
    public static void startService(Context context) {
        context.startForegroundService(new Intent(context, MonitoringService.class));
    }
}
