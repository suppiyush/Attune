package com.cce.attune.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cce.attune.R;
import com.cce.attune.context.UserProfileStore;
import com.cce.attune.databinding.ActivityMainBinding;
import com.cce.attune.services.MonitoringService;
import com.cce.attune.services.MonitoringWorker;
import com.cce.attune.telemetry.UsageStatsCollector;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final int REQUEST_NOTIFICATIONS = 101;
    private static final int REQUEST_BLUETOOTH = 102;

    private boolean monitoringStarted = false;
    private boolean isRequestingPermissions = false;
    private boolean askedForNotifications = false;
    private boolean askedForBluetooth = false;
    private boolean askedForUsageStats = false;
    private AlertDialog usageAccessDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();

        checkPermissionsAndStart();
    }

    // ----------------------------------------------------
    // Navigation
    // ----------------------------------------------------

    private void setupNavigation() {

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) return;

        NavController navController = navHostFragment.getNavController();

        if (!new UserProfileStore(this).isComplete()) {
            navController.navigate(R.id.nav_onboarding);
        }

        NavigationUI.setupWithNavController(
                binding.bottomNavigation,
                navController
        );

        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {

                    int id = destination.getId();

                    boolean showBottom =
                            id == R.id.nav_dashboard ||
                                    id == R.id.nav_schedule ||
                                    id == R.id.nav_info ||
                                    id == R.id.nav_profile;

                    binding.bottomNavigation.setVisibility(
                            showBottom ? View.VISIBLE : View.GONE
                    );
                });
    }

    // ----------------------------------------------------
    // Permission Handling
    // ----------------------------------------------------

    private void checkPermissionsAndStart() {
        if (isRequestingPermissions) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 1. Android 13+ Notification permission
        if (!askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                isRequestingPermissions = true;
                askedForNotifications = true;
                prefs.edit().putBoolean("pref_notifications_enabled", false).apply();
                
                boolean hasRequested = prefs.getBoolean("has_requested_notif", false);
                boolean rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS);
                
                if (!rationale && hasRequested) { // Permanently denied
                    showGoToSettingsDialog("Notifications", () -> {
                        isRequestingPermissions = false;
                        checkPermissionsAndStart();
                    });
                    return;
                }
                
                prefs.edit().putBoolean("has_requested_notif", true).apply();
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS
                );
                return; // Wait for result
            } else {
                prefs.edit().putBoolean("pref_notifications_enabled", true).apply();
            }
        }
        // 2. Usage Stats
        UsageStatsCollector collector = new UsageStatsCollector(this);
        if (!collector.hasUsageStatsPermission()) {
            prefs.edit().putBoolean("pref_monitoring_enabled", false).apply();
            if (!askedForUsageStats) {
                askedForUsageStats = true;
                showUsageAccessDialog();
            }
            return;
        } else {
            prefs.edit().putBoolean("pref_monitoring_enabled", true).apply();
        }

        startMonitoring();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        isRequestingPermissions = false;
        
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);

        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
            prefs.edit().putBoolean("pref_notifications_enabled", granted).apply();
            checkPermissionsAndStart();
        } else if (requestCode == REQUEST_BLUETOOTH) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (grantResults.length == 0) allGranted = false;
            prefs.edit().putBoolean("pref_bluetooth_enabled", allGranted).apply();
            checkPermissionsAndStart();
        }
    }

    // ----------------------------------------------------
    // Monitoring Startup
    // ----------------------------------------------------

    private void startMonitoring() {

        if (monitoringStarted) return;

        monitoringStarted = true;

        MonitoringService.startService(this);

        MonitoringWorker.startMonitoring(this);
    }

    // ----------------------------------------------------
    // Usage Access Dialog
    // ----------------------------------------------------

    private void showUsageAccessDialog() {

        if (usageAccessDialog != null && usageAccessDialog.isShowing()) {
            return;
        }

        usageAccessDialog = new AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage(
                        "Attune needs Usage Access permission " +
                                "to monitor your phone habits."
                )
                .setPositiveButton("Go to Settings",
                        (dialog, which) -> {

                            Intent intent = new Intent(
                                    Settings.ACTION_USAGE_ACCESS_SETTINGS
                            );

                            startActivity(intent);
                        })
                .setNegativeButton("Later", null)
                .show();
    }

    private void showGoToSettingsDialog(String permissionName, Runnable onWait) {
        
        String title;
        String message;
        
        if (permissionName.equals("Notifications")) {
            title = "Notification Access Required";
            message = "Attune needs Notification permission to send you Phubbing alerts.";
        } else {
            title = permissionName + " Required";
            message = "Attune needs " + permissionName + " permission to function properly.";
        }
        
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    isRequestingPermissions = false; // reset flag, onResume will smoothly catch and continue next launch sequence
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Later", (dialog, which) -> onWait.run())
                .setOnCancelListener(dialog -> onWait.run())
                .show();
    }

    // ----------------------------------------------------
    // Activity Lifecycle
    // ----------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        
        UsageStatsCollector collector = new UsageStatsCollector(this);
        if (collector.hasUsageStatsPermission()) {
            if (usageAccessDialog != null && usageAccessDialog.isShowing()) {
                usageAccessDialog.dismiss();
            }
        }

        if (!monitoringStarted) {
            checkPermissionsAndStart();
        }
    }
}
