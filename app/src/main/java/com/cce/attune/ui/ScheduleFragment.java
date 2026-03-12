package com.cce.attune.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cce.attune.R;
import com.cce.attune.context.SocialContextManager;
import com.cce.attune.database.AppDatabase;
import com.cce.attune.database.SocialSession;
import com.cce.attune.database.SsidGroup;
import com.cce.attune.database.SsidGroupDao;
import com.cce.attune.database.SsidGroupMember;
import com.cce.attune.databinding.FragmentScheduleBinding;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScheduleFragment extends Fragment {

    private FragmentScheduleBinding binding;
    private SocialContextManager contextManager;
    private ScheduleAdapter adapter;

    private enum Mode { SCHEDULE, BLUETOOTH }
    private Mode mode = Mode.SCHEDULE;

    // Bluetooth groups state
    private SsidGroupDao groupDao;
    private SsidGroupAdapter groupAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver scanReceiver;
    private DevicePickerAdapter pickerAdapter;

    // Request codes / launchers
    private static final int REQUEST_ENABLE_BT = 1001;
    private ActivityResultLauncher<Intent> enableBtLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    // What to do after BT/permission is granted
    private Runnable pendingAction;

    private int selectedYear, selectedMonth, selectedDay;
    private int startHour = -1, startMin = -1;
    private int endHour = -1, endMin = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Launcher: system "Enable Bluetooth" dialog
        enableBtLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                        // BT now on — continue to permission check
                        if (pendingAction != null) checkBtPermissionsThen(pendingAction);
                    } else {
                        Toast.makeText(requireContext(),
                                "Bluetooth must be on to use this feature", Toast.LENGTH_SHORT).show();
                    }
                });

        // Launcher: runtime permission request (Android 12+)
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                granted -> {
                    boolean allGranted = !granted.containsValue(false);
                    if (allGranted) {
                        if (pendingAction != null) pendingAction.run();
                    } else {
                        showGoToSettingsDialog();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScheduleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        contextManager = new SocialContextManager(requireContext());
        groupDao = AppDatabase.getInstance(requireContext()).ssidGroupDao();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        setupRecyclerView();
        loadSessions();

        binding.fabAddSchedule.setOnClickListener(v -> showAddScheduleDialog());

        setupBluetoothGroups();
        setupModeSwitch();
        applyMode(Mode.SCHEDULE);
    }

    private void setupRecyclerView() {
        adapter = new ScheduleAdapter(sessionId -> {
            contextManager.deleteSession(sessionId);
            loadSessions();
        });
        binding.rvSchedules.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSchedules.setAdapter(adapter);
    }

    private void loadSessions() {
        List<SocialSession> sessions = contextManager.getAllSessions();
        adapter.submitList(sessions);
        binding.tvEmpty.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Mode switch ──────────────────────────────────────────────────────────

    private void setupModeSwitch() {
        binding.btnModeSchedule.setOnClickListener(v -> applyMode(Mode.SCHEDULE));
        // Bluetooth tab: gate BT enable + permission before revealing BT UI
        binding.btnModeBluetooth.setOnClickListener(v -> {
            pendingAction = () -> applyMode(Mode.BLUETOOTH);
            checkBtEnabledThen(() -> checkBtPermissionsThen(() -> applyMode(Mode.BLUETOOTH)));
        });
    }

    private void applyMode(Mode newMode) {
        mode = newMode;

        boolean isSchedule = mode == Mode.SCHEDULE;
        binding.containerSchedule.setVisibility(isSchedule ? View.VISIBLE : View.GONE);
        binding.containerBluetooth.setVisibility(isSchedule ? View.GONE : View.VISIBLE);
        binding.fabAddSchedule.setVisibility(isSchedule ? View.VISIBLE : View.GONE);

        int active = requireContext().getColor(R.color.primary);
        int inactive = requireContext().getColor(R.color.text_secondary);
        binding.btnModeSchedule.setTextColor(isSchedule ? active : inactive);
        binding.btnModeBluetooth.setTextColor(isSchedule ? inactive : active);

        if (!isSchedule) refreshGroupList();
    }

    // ── BT enable + permission gate ──────────────────────────────────────────

    /** Step 1: make sure Bluetooth is switched on. */
    private void checkBtEnabledThen(Runnable next) {
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "This device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            next.run();
            return;
        }
        pendingAction = next;
        // Ask system to enable BT — shows standard Android dialog
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBtLauncher.launch(enableBtIntent);
    }

    /** Step 2: make sure BLUETOOTH_SCAN + BLUETOOTH_CONNECT are granted (Android 12+). */
    private void checkBtPermissionsThen(Runnable next) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Android < 12 uses BLUETOOTH + BLUETOOTH_ADMIN (already in manifest), no runtime grant needed
            next.run();
            return;
        }
        boolean scanOk    = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)    == PackageManager.PERMISSION_GRANTED;
        boolean connectOk = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        if (scanOk && connectOk) {
            next.run();
            return;
        }
        pendingAction = next;
        // Show rationale then request
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Bluetooth Permission Needed")
                .setMessage("Attune needs Bluetooth permission to scan for nearby devices and detect your social group.")
                .setPositiveButton("Grant", (d, w) -> permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shown when user permanently denied permission — guide them to app settings. */
    private void showGoToSettingsDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage("Bluetooth permission was denied. Please enable it in Settings → Apps → Attune → Permissions.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", requireContext().getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // ── Bluetooth groups UI (moved from Profile) ─────────────────────────────

    private void setupBluetoothGroups() {
        groupAdapter = new SsidGroupAdapter(new SsidGroupAdapter.Listener() {
            @Override public void onDeleteGroup(SsidGroup group) { deleteGroup(group); }
            @Override public void onDeleteMember(SsidGroupMember member) { deleteMember(member); }
            @Override public void onAddDevices(SsidGroup group) { openDevicePicker(group); }
        });
        binding.rvSsidGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSsidGroups.setAdapter(groupAdapter);
        binding.btnAddGroup.setOnClickListener(v -> showNewGroupDialog());
        refreshGroupList();
    }

    private void refreshGroupList() {
        List<SsidGroup> groups = groupDao.getAllGroups();
        Map<Integer, List<SsidGroupMember>> map = new HashMap<>();
        for (SsidGroup g : groups) {
            map.put(g.id, groupDao.getMembersOf(g.id));
        }
        groupAdapter.submitData(groups, map);
    }

    private void showNewGroupDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_new_group, null);

        com.google.android.material.textfield.TextInputEditText etName =
                dialogView.findViewById(R.id.et_group_name);

        androidx.appcompat.app.AlertDialog alert =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Create", (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter a group name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long id = groupDao.insertGroup(new SsidGroup(name));
                    refreshGroupList();
                    // Open picker immediately so user can add devices
                    SsidGroup newGroup = new SsidGroup(name);
                    newGroup.id = (int) id;
                    openDevicePicker(newGroup);
                })
                .setNegativeButton("Cancel", null)
                .create();

        // Transparent window so bg_dialog_rounded corners clip correctly
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        alert.show();
    }

    private void deleteGroup(SsidGroup group) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Group")
                .setMessage("Delete \"" + group.name + "\" and all its devices?")
                .setPositiveButton("Delete", (d, w) -> {
                    groupDao.deleteMembersOf(group.id);
                    groupDao.deleteGroup(group.id);
                    refreshGroupList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMember(SsidGroupMember member) {
        groupDao.deleteMember(member.id);
        refreshGroupList();
    }

    private void openDevicePicker(SsidGroup group) {
        pickerAdapter = new DevicePickerAdapter();

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_device_picker, null);

        androidx.recyclerview.widget.RecyclerView rv =
                dialogView.findViewById(R.id.rv_scanned_devices);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(pickerAdapter);

        com.google.android.material.textfield.TextInputEditText etManual =
                dialogView.findViewById(R.id.et_manual_device);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Devices to \"" + group.name + "\"")
                .setView(dialogView)
                .setPositiveButton("Add Selected", (d, w) -> {
                    List<DevicePickerAdapter.DeviceItem> checked = pickerAdapter.getCheckedItems();
                    String manual = etManual.getText() != null ? etManual.getText().toString().trim() : "";

                    boolean anyAdded = false;
                    for (DevicePickerAdapter.DeviceItem item : checked) {
                        groupDao.insertMember(new SsidGroupMember(group.id, item.address, item.name));
                        anyAdded = true;
                    }
                    if (!manual.isEmpty()) {
                        groupDao.insertMember(new SsidGroupMember(group.id, manual, manual));
                        anyAdded = true;
                    }
                    if (anyAdded) refreshGroupList();
                    stopBluetoothScan();
                })
                .setNegativeButton("Cancel", (d, w) -> stopBluetoothScan())
                .setOnCancelListener(d -> stopBluetoothScan())
                .create();

        // Transparent window so bg_dialog_rounded corners clip correctly
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        startBluetoothScan();
        dialog.show();
    }

    private void startBluetoothScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Shouldn't normally happen (gate runs before openDevicePicker), but guard anyway
            checkBtEnabledThen(() -> checkBtPermissionsThen(this::doStartScan));
            return;
        }
        checkBtPermissionsThen(this::doStartScan);
    }

    private void doStartScan() {
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && pickerAdapter != null) {
                        try {
                            String name = device.getName();
                            String addr = device.getAddress();
                            pickerAdapter.addDevice(addr, name);
                        } catch (SecurityException ignored) {
                        }
                    }
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireContext().registerReceiver(scanReceiver, filter);
        try {
            if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
            bluetoothAdapter.startDiscovery();
        } catch (SecurityException e) {
            Toast.makeText(requireContext(), "Bluetooth scan permission required", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopBluetoothScan() {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering())
                bluetoothAdapter.cancelDiscovery();
        } catch (SecurityException ignored) {}
        if (scanReceiver != null) {
            try { requireContext().unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
            scanReceiver = null;
        }
    }

    private void showAddScheduleDialog() {
        // Reset state
        Calendar cal = Calendar.getInstance();
        selectedYear = cal.get(Calendar.YEAR);
        selectedMonth = cal.get(Calendar.MONTH);
        selectedDay = cal.get(Calendar.DAY_OF_MONTH);
        startHour = startMin = endHour = endMin = -1;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_schedule, null);

        TextInputEditText etName = dialogView.findViewById(R.id.et_session_name);
        android.widget.TextView tvSelectedTime = dialogView.findViewById(R.id.tv_selected_time);
        android.widget.Button btnDate = dialogView.findViewById(R.id.btn_pick_date);
        android.widget.Button btnStart = dialogView.findViewById(R.id.btn_pick_start);
        android.widget.Button btnEnd = dialogView.findViewById(R.id.btn_pick_end);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        btnDate.setOnClickListener(v -> {
            new DatePickerDialog(requireContext(), (dp, y, m, d) -> {
                selectedYear = y; selectedMonth = m; selectedDay = d;
                Calendar c = Calendar.getInstance();
                c.set(y, m, d);
                btnDate.setText(sdf.format(c.getTime()));
            }, selectedYear, selectedMonth, selectedDay).show();
        });

        btnStart.setOnClickListener(v -> {
            new TimePickerDialog(requireContext(), (tp, h, m) -> {
                startHour = h; startMin = m;
                btnStart.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
                updateTimeSummary(tvSelectedTime);
            }, 18, 0, true).show();
        });

        btnEnd.setOnClickListener(v -> {
            new TimePickerDialog(requireContext(), (tp, h, m) -> {
                endHour = h; endMin = m;
                btnEnd.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
                updateTimeSummary(tvSelectedTime);
            }, 20, 0, true).show();
        });

        android.app.AlertDialog alert = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (startHour < 0 || endHour < 0) {
                        Toast.makeText(requireContext(), "Please pick start and end times", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Calendar start = Calendar.getInstance();
                    start.set(selectedYear, selectedMonth, selectedDay, startHour, startMin, 0);
                    Calendar end = Calendar.getInstance();
                    end.set(selectedYear, selectedMonth, selectedDay, endHour, endMin, 0);

                    if (end.before(start)) {
                        Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SocialSession session = new SocialSession(name, start.getTimeInMillis(), end.getTimeInMillis());
                    contextManager.addSession(session);
                    loadSessions();
                })
                .setNegativeButton("Cancel", null)
                .create();

        // Transparent window so bg_dialog_rounded corners clip correctly
        if (alert.getWindow() != null) {
            alert.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        alert.show();
    }

    private void updateTimeSummary(android.widget.TextView tv) {
        if (startHour >= 0 && endHour >= 0) {
            tv.setText(String.format(Locale.getDefault(),
                    "%02d:%02d → %02d:%02d", startHour, startMin, endHour, endMin));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopBluetoothScan();
        binding = null;
    }
}

