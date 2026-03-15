package com.cce.attune.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cce.attune.databinding.FragmentInfoBinding;

public class InfoFragment extends Fragment {

    private FragmentInfoBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.cardArticle1.setOnClickListener(v -> openUrl(
                "https://www.resiliencelab.us/thought-lab/phubbing"));
        binding.cardArticle2.setOnClickListener(v -> openUrl(
                "https://www.ebsco.com/research-starters/communication-and-mass-media/phubbing"));
        binding.cardArticle3.setOnClickListener(v -> openUrl(
                "https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2025.1561159/full"));
        binding.cardArticle4.setOnClickListener(v -> openUrl(
                "https://pmc.ncbi.nlm.nih.gov/articles/PMC9853171/"));
        binding.cardArticle5.setOnClickListener(v -> openUrl(
                "https://pmc.ncbi.nlm.nih.gov/articles/PMC5774041/"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

