package com.cce.attune.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cce.attune.R;
import com.cce.attune.context.UserProfileStore;
import com.cce.attune.databinding.FragmentProfileBinding;
import com.cce.attune.risk.StrictnessManager;
import com.cce.attune.telemetry.UsageStatsCollector;

public class ProfileFragment extends Fragment {

    private static final String PREF_NOTIFICATIONS = "pref_notifications_enabled";
    private static final String PREF_BLUETOOTH     = "pref_bluetooth_enabled";

    private static final int REQ_NOTIF = 201;
    private static final int REQ_BT    = 202;

    private boolean isUpdatingSwitch = false;

    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUserProfileCard();
        setupToggles();
        setupStrictness();
        refreshSummaryData();
        refreshStreakGrid();
        refreshXpCard();
        setupXpInfoButton();
    }

    private void setupUserProfileCard() {
        UserProfileStore store = new UserProfileStore(requireContext());
        refreshUserProfileUi(store);

        binding.btnEditProfile.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                        .navigate(R.id.nav_onboarding);
            } catch (Exception ignored) {
            }
        });
    }

    private void refreshUserProfileUi(UserProfileStore store) {
        String name = store.getName();
        int age = store.getAge();
        String gender = store.getGender();

        if (name == null || name.trim().isEmpty()) name = "Your profile";
        binding.tvUserName.setText(name);

        String initial = name.trim().isEmpty() ? "?" : String.valueOf(name.trim().charAt(0)).toUpperCase();
        binding.tvAvatar.setText(initial);

        String ageGender;
        if (age > 0 && gender != null && !gender.trim().isEmpty()) {
            ageGender = age + " • " + gender;
        } else if (age > 0) {
            ageGender = String.valueOf(age);
        } else if (gender != null && !gender.trim().isEmpty()) {
            ageGender = gender;
        } else {
            ageGender = "Tap Edit to add details";
        }
        binding.tvUserAgeGender.setText(ageGender);
    }

    private void setupToggles() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        com.cce.attune.context.SettingsManager settings = new com.cce.attune.context.SettingsManager(requireContext());

        isUpdatingSwitch = true;
        binding.switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS, true));
        binding.switchBluetooth.setChecked(prefs.getBoolean(PREF_BLUETOOTH, true));
        binding.switchSocialMode.setChecked(settings.isManualSocialActive());
        isUpdatingSwitch = false;

        binding.switchSocialMode.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingSwitch) return;
            settings.setManualSocialActive(checked);
            
            // Poke MonitoringService so it picks up the change immediately
            com.cce.attune.services.MonitoringService.startService(requireContext());
            
            // Send broadcast to update the widget UI
            android.content.Intent widgetIntent = new android.content.Intent(requireContext(), com.cce.attune.widget.SocialWidgetProvider.class);
            widgetIntent.setAction(com.cce.attune.widget.SocialWidgetProvider.ACTION_WIDGET_UPDATE);
            requireContext().sendBroadcast(widgetIntent);
        });

        binding.switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingSwitch) return;
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    
                    boolean hasRequested = prefs.getBoolean("has_requested_notif", false);
                    boolean rationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
                    
                    if (!rationale && hasRequested) { // Permanently denied
                        showGoToSettingsDialog("Notifications");
                        isUpdatingSwitch = true;
                        btn.setChecked(false);
                        isUpdatingSwitch = false;
                        return;
                    }
                    
                    prefs.edit().putBoolean("has_requested_notif", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    return; // Wait for result
                }
            }
            prefs.edit().putBoolean(PREF_NOTIFICATIONS, checked).apply();
        });

        binding.switchBluetooth.setOnCheckedChangeListener((btn, checked) -> {
            if (isUpdatingSwitch) return;
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean scanG = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                boolean connG = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
                if (!scanG || !connG) {
                    
                    boolean hasRequested = prefs.getBoolean("has_requested_bt", false);
                    boolean rationaleScan = shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN);
                    boolean rationaleConn = shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT);
                    
                    if ((!rationaleScan && !rationaleConn) && hasRequested) { // Permanently denied
                        showGoToSettingsDialog("Bluetooth");
                        isUpdatingSwitch = true;
                        btn.setChecked(false);
                        isUpdatingSwitch = false;
                        return;
                    }
                    
                    prefs.edit().putBoolean("has_requested_bt", true).apply();
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
                    return; // Wait for result
                }
            }
            prefs.edit().putBoolean(PREF_BLUETOOTH, checked).apply();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        if (requestCode == REQ_NOTIF) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean(PREF_NOTIFICATIONS, true).apply();
            } else {
                isUpdatingSwitch = true;
                binding.switchNotifications.setChecked(false);
                isUpdatingSwitch = false;
            }
        } else if (requestCode == REQ_BT) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (grantResults.length == 0) allGranted = false;

            if (allGranted) {
                prefs.edit().putBoolean(PREF_BLUETOOTH, true).apply();
            } else {
                isUpdatingSwitch = true;
                binding.switchBluetooth.setChecked(false);
                isUpdatingSwitch = false;
            }
        }
    }

    private void showUsageAccessDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Usage Access Required")
                .setMessage("Attune needs App Usage Access permission to strictly monitor your phone habits.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    try {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showGoToSettingsDialog(String permissionName) {
        
        String title;
        String message;
        
        if (permissionName.equals("Notifications")) {
            title = "Notification Access Required";
            message = "Attune needs Notification permission to send you Phubbing alerts.";
        } else {
            title = permissionName + " Required";
            message = "Attune needs " + permissionName + " permission to function properly.";
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Go to Settings", (d, w) -> {
                    android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", requireContext().getPackageName(), null));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupStrictness() {
        StrictnessManager sm = new StrictnessManager(requireContext());

        String[] options = {"Low", "Medium", "High"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStrictness.setAdapter(adapter);

        // Set initial selection
        int pos;
        switch (sm.getStrictness()) {
            case LOW:  pos = 0; break;
            case HIGH: pos = 2; break;
            default:   pos = 1; break;
        }
        binding.spinnerStrictness.setSelection(pos, false);

        binding.spinnerStrictness.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                switch (position) {
                    case 0: sm.setStrictness(StrictnessManager.Strictness.LOW);    break;
                    case 2: sm.setStrictness(StrictnessManager.Strictness.HIGH);   break;
                    default: sm.setStrictness(StrictnessManager.Strictness.MEDIUM); break;
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshUserProfileUi(new UserProfileStore(requireContext()));
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

            // Validate actual permission state to catch out-of-band changes
            boolean hasUsage = new UsageStatsCollector(requireContext()).hasUsageStatsPermission();

            boolean hasNotif = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotif = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
            if (!hasNotif) {
                prefs.edit().putBoolean(PREF_NOTIFICATIONS, false).apply();
            }

            boolean hasBt = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean scanG = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                boolean connG = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
                hasBt = scanG && connG;
            }
            if (!hasBt) {
                prefs.edit().putBoolean(PREF_BLUETOOTH, false).apply();
            }

            // Sync visual UI with confirmed preferences state
            isUpdatingSwitch = true;
            binding.switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS, true));
            binding.switchBluetooth.setChecked(prefs.getBoolean(PREF_BLUETOOTH, true));
            binding.switchSocialMode.setChecked(new com.cce.attune.context.SettingsManager(requireContext()).isManualSocialActive());
            isUpdatingSwitch = false;

            refreshSummaryData();
            refreshStreakGrid();
            refreshXpCard();
        }
    }

    private void refreshSummaryData() {
        if (binding == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("daily_summary_prefs", Context.MODE_PRIVATE);
        
        // Date check for UI as well
        String savedDate = prefs.getString("today_date", "");
        String currentDate = java.text.DateFormat.getDateInstance().format(new java.util.Date());
        
        if (!currentDate.equals(savedDate)) {
            binding.tvProfileSessions.setText("0");
            binding.tvProfileAvgRisk.setText("0%");
        } else {
            int sessions = prefs.getInt("sessions_count", 0);
            float riskSum = prefs.getFloat("risk_sum", 0f);
            int riskCount = prefs.getInt("risk_count", 0);
            
            binding.tvProfileSessions.setText(String.valueOf(sessions));
            
            if (riskCount > 0) {
                int avgRisk = Math.round((riskSum / riskCount) * 100);
                binding.tvProfileAvgRisk.setText(avgRisk + "%");
            } else {
                binding.tvProfileAvgRisk.setText("0%");
            }
        }

        // Unlocks - calculated from UsageStatsCollector
        UsageStatsCollector collector = new UsageStatsCollector(requireContext());
        long now = System.currentTimeMillis();
        // Today's start (roughly)
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        
        int unlocks = collector.getUnlockCount(cal.getTimeInMillis(), now);
        binding.tvProfileUnlocks.setText(String.valueOf(unlocks));
    }

    private void refreshStreakGrid() {
        if (binding == null) return;

        // Update streak counter in Activity card
        com.cce.attune.gamification.XpManager xp = new com.cce.attune.gamification.XpManager(requireContext());
        int streak = xp.getCleanStreak();
        String streakText = streak == 1 ? "1 day" : streak + " days";
        binding.tvActivityStreak.setText(streakText);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat monthFormat = new java.text.SimpleDateFormat("MMMM", java.util.Locale.US);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        
        // Month names
        binding.tvMonthCurr.setText(monthFormat.format(cal.getTime()));
        java.util.Calendar prevMonthCal = (java.util.Calendar) cal.clone();
        prevMonthCal.add(java.util.Calendar.MONTH, -1);
        binding.tvMonthPrev.setText(monthFormat.format(prevMonthCal.getTime()));

        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (14 * density); // Slightly smaller to fit two side-by-side
        int marginPx = (int) (2 * density);

        com.cce.attune.database.AppDatabase db = com.cce.attune.database.AppDatabase.getInstance(requireContext());

        // Render Previous Month
        renderMonthGrid(binding.gridStreakPrev, prevMonthCal, db, sdf, sizePx, marginPx);
        
        // Render Current Month
        renderMonthGrid(binding.gridStreakCurr, cal, db, sdf, sizePx, marginPx);
    }

    private void renderMonthGrid(android.widget.GridLayout grid, java.util.Calendar targetMonth, 
                                com.cce.attune.database.AppDatabase db, java.text.SimpleDateFormat sdf,
                                int sizePx, int marginPx) {
        grid.removeAllViews();
        
        java.util.Calendar cal = (java.util.Calendar) targetMonth.clone();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        int daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        
        String startDate = sdf.format(cal.getTime());
        cal.set(java.util.Calendar.DAY_OF_MONTH, daysInMonth);
        String endDate = sdf.format(cal.getTime());

        java.util.List<com.cce.attune.database.DailyStreak> streaks = db.dailyStreakDao().getStreaksInRange(startDate, endDate);
        java.util.Map<String, Integer> streakMap = new java.util.HashMap<>();
        for (com.cce.attune.database.DailyStreak s : streaks) {
            streakMap.put(s.date, s.status);
        }

        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        
        for (int i = 1; i < firstDayOfWeek; i++) {
            View space = new View(requireContext());
            grid.addView(space, new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
            ));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            cal.set(java.util.Calendar.DAY_OF_MONTH, day);
            String dateKey = sdf.format(cal.getTime());
            int status = streakMap.getOrDefault(dateKey, 0);

            View box = new View(requireContext());
            box.setBackgroundResource(R.drawable.streak_box_base);
            
            if (status == 1) {
                box.getBackground().setTint(requireContext().getColor(R.color.primary));
            } else {
                box.getBackground().setTint(requireContext().getColor(R.color.bg_dark));
            }

            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED)
            );
            params.width = sizePx;
            params.height = sizePx;
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            
            grid.addView(box, params);
        }
    }

    private void refreshXpCard() {
        if (binding == null) return;

        com.cce.attune.gamification.XpManager xp = new com.cce.attune.gamification.XpManager(requireContext());

        int level       = xp.getLevel() + 1;
        String name     = xp.getLevelName();
        int total       = xp.getTotalXp();
        int xpForNext   = xp.getXpForNextLevel();
        int progress    = Math.round(xp.getLevelProgress() * 100);

        binding.tvXpLevelBadge.setText(String.valueOf(level));
        binding.tvXpLevelName.setText(name);
        binding.tvXpTotal.setText(String.valueOf(total));
        binding.pbXpProgress.setProgress(progress);

        if (xpForNext > 0) {
            binding.tvXpNextLevel.setText(xpForNext + " XP to next level");
        } else {
            binding.tvXpNextLevel.setText("Max level reached!");
        }
    }

    private void setupXpInfoButton() {
        if (binding == null) return;
        binding.btnXpInfo.setOnClickListener(v -> {
            android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_xp_info, null);

            com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setView(dialogView);

            androidx.appcompat.app.AlertDialog dialog = builder.create();
            dialog.show();

            // Make the dialog window background transparent so the
            // MaterialAlertDialog's own rounded-corner shape shows through
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
