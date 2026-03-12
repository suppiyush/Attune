package com.cce.attune.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.cce.attune.R;
import com.cce.attune.context.UserProfileStore;
import com.cce.attune.databinding.FragmentOnboardingBinding;

public class OnboardingFragment extends Fragment {

    private FragmentOnboardingBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Gender dropdown — use custom item layout so colors follow the app theme,
        // not the system dark/light mode
        String[] genderOptions = getResources().getStringArray(R.array.gender_options);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_dropdown,
                genderOptions
        );
        binding.acvGender.setAdapter(genderAdapter);
        // Force popup background to app surface color regardless of system theme
        binding.acvGender.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);

        // Prefill if already saved (editing)
        UserProfileStore store = new UserProfileStore(requireContext());
        if (store.getName() != null) binding.etName.setText(store.getName());
        if (store.getAge() > 0)      binding.etAge.setText(String.valueOf(store.getAge()));
        if (store.getGender() != null) {
            binding.acvGender.setText(store.getGender(), false);
        }

        binding.btnContinue.setOnClickListener(v -> saveAndContinue());
    }

    private void saveAndContinue() {
        String name   = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String ageStr = binding.etAge.getText()  != null ? binding.etAge.getText().toString().trim()  : "";
        String gender = binding.acvGender.getText() != null ? binding.acvGender.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }

        int age;
        try {
            age = Integer.parseInt(ageStr);
        } catch (Exception e) {
            age = 0;
        }
        if (age <= 0 || age > 120) {
            Toast.makeText(requireContext(), "Please enter a valid age", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(gender)) {
            Toast.makeText(requireContext(), "Please select a gender", Toast.LENGTH_SHORT).show();
            return;
        }

        new UserProfileStore(requireContext()).save(name, age, gender);

        NavController nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.action_onboarding_to_dashboard);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
