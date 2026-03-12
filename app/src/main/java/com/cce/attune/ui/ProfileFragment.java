package com.cce.attune.ui;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cce.attune.R;
import com.cce.attune.context.UserProfileStore;
import com.cce.attune.databinding.FragmentProfileBinding;
import com.cce.attune.risk.StrictnessManager;

public class ProfileFragment extends Fragment {

    private static final String PREF_MONITORING    = "pref_monitoring_enabled";
    private static final String PREF_NOTIFICATIONS = "pref_notifications_enabled";
    private static final String PREF_BLUETOOTH     = "pref_bluetooth_enabled";

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

        binding.switchMonitoring.setChecked(prefs.getBoolean(PREF_MONITORING, true));
        binding.switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS, true));
        binding.switchBluetooth.setChecked(prefs.getBoolean(PREF_BLUETOOTH, true));

        binding.switchMonitoring.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_MONITORING, checked).apply());
        binding.switchNotifications.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_NOTIFICATIONS, checked).apply());
        binding.switchBluetooth.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(PREF_BLUETOOTH, checked).apply());
    }

    private void setupStrictness() {
        StrictnessManager sm = new StrictnessManager(requireContext());

        // Select the currently saved button
        int selectedId;
        switch (sm.getStrictness()) {
            case LOW:  selectedId = R.id.btn_strictness_low;  break;
            case HIGH: selectedId = R.id.btn_strictness_high; break;
            default:   selectedId = R.id.btn_strictness_medium; break;
        }
        binding.toggleStrictness.check(selectedId);

        binding.toggleStrictness.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_strictness_low) {
                sm.setStrictness(StrictnessManager.Strictness.LOW);
            } else if (checkedId == R.id.btn_strictness_high) {
                sm.setStrictness(StrictnessManager.Strictness.HIGH);
            } else {
                sm.setStrictness(StrictnessManager.Strictness.MEDIUM);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshUserProfileUi(new UserProfileStore(requireContext()));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
