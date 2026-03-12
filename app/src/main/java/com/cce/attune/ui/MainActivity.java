package com.cce.attune.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cce.attune.R;
import com.cce.attune.databinding.ActivityMainBinding;
import com.cce.attune.services.MonitoringService;
import com.cce.attune.services.MonitoringWorker;
import com.cce.attune.telemetry.UsageStatsCollector;

import com.cce.attune.context.UserProfileStore;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final int REQUEST_NOTIFICATIONS  = 101;
    private static final int REQUEST_BLUETOOTH      = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force light mode regardless of device dark mode setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
        checkPermissionsAndStart();
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) return;

        NavController navController = navHostFragment.getNavController();

        // Route to onboarding if profile isn't set up yet
        if (!new UserProfileStore(this).isComplete()) {
            navController.navigate(R.id.nav_onboarding);
        }

        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean showBottom =
                    id == R.id.nav_dashboard ||
                            id == R.id.nav_schedule ||
                            id == R.id.nav_info ||
                            id == R.id.nav_profile;
            binding.bottomNavigation.setVisibility(showBottom ? android.view.View.VISIBLE : android.view.View.GONE);
        });
    }

    private void checkPermissionsAndStart() {
        UsageStatsCollector collector = new UsageStatsCollector(this);
        if (!collector.hasUsageStatsPermission()) {
            showUsageAccessDialog();
            return;
        }

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
                // Don't return — continue to BT check below
            }
        }

        // Request Bluetooth permissions on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean scanGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean connectGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            if (!scanGranted || !connectGranted) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        REQUEST_BLUETOOTH);
                // startMonitoring() is called once permissions are answered (see callback below)
                return;
            }
        }

        startMonitoring();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // After permissions are answered (granted or denied), start monitoring regardless
        if (requestCode == REQUEST_BLUETOOTH || requestCode == REQUEST_NOTIFICATIONS) {
            startMonitoring();
        }
    }

    private void startMonitoring() {
        MonitoringService.startService(this);
        MonitoringWorker.startMonitoring(this);
    }

    private void showUsageAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage("Attune needs 'Usage Access' permission to monitor your phone habits.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UsageStatsCollector collector = new UsageStatsCollector(this);
        if (collector.hasUsageStatsPermission()) {
            startMonitoring();
        }
    }
}