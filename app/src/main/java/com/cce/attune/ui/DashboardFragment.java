package com.cce.attune.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cce.attune.R;
import com.cce.attune.context.SocialContextManager;
import com.cce.attune.databinding.FragmentDashboardBinding;
import com.cce.attune.features.FeatureEngine;
import com.cce.attune.features.PhubbingFeatures;
import com.cce.attune.risk.PhubbingClassifier;
import com.cce.attune.risk.RiskEngine;
import com.cce.attune.risk.RiskEngine;
import com.cce.attune.risk.StrictnessManager;
import com.cce.attune.telemetry.UsageStatsCollector;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    private FeatureEngine featureEngine;
    private RiskEngine riskEngine;
    private SocialContextManager contextManager;
    private UsageStatsCollector statsCollector;

    // Chart period
    private int currentPeriod = 0;

    // Prevent baseline drift
    private long lastBaselineUpdate = 0;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshData();
            refreshHandler.postDelayed(this, 30_000); // 30 seconds
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        featureEngine = new FeatureEngine(requireContext());
        riskEngine = new RiskEngine(requireContext());
        contextManager = new SocialContextManager(requireContext());
        statsCollector = new UsageStatsCollector(requireContext());

        setupPeriodButtons();
        setupChart();
        setupRefreshLayout();
        refreshData();
    }

    private void setupRefreshLayout() {
        binding.refreshLayout.setOnRefreshListener(() -> {
            refreshData();
            binding.refreshLayout.setRefreshing(false);
        });

        // Optional: Customize refresh indicator colors
        binding.refreshLayout.setColorSchemeResources(
                R.color.primary,
                R.color.accent
        );
    }

    private void setupPeriodButtons() {

        binding.btnDaily.setOnClickListener(v -> selectPeriod(0));
        binding.btnWeekly.setOnClickListener(v -> selectPeriod(1));
        binding.btnMonthly.setOnClickListener(v -> selectPeriod(2));

    }

    private void selectPeriod(int period) {

        currentPeriod = period;

        int active = requireContext().getColor(R.color.primary);
        int inactive = requireContext().getColor(R.color.text_secondary);

        binding.btnDaily.setTextColor(period == 0 ? active : inactive);
        binding.btnWeekly.setTextColor(period == 1 ? active : inactive);
        binding.btnMonthly.setTextColor(period == 2 ? active : inactive);

        refreshChart();
    }

    private void refreshData() {
        Log.d("Look here", "Refreshing Data");

        long now = System.currentTimeMillis();

        // Extract behavioral features
        PhubbingFeatures features = featureEngine.extractFeatures();

        // AI score (stub 0.5 when model absent)
        PhubbingClassifier classifier = new PhubbingClassifier(requireContext());
        float aiScore = classifier.predict(features);
        classifier.close();

        // Compute hybrid risk
        float risk = riskEngine.computeRisk(features, aiScore);

        // Detect social context (schedule, BT, or manual widget toggle)
        boolean inSocial = contextManager.isSocialWindowActive(now)
                || new com.cce.attune.context.SettingsManager(requireContext()).isManualSocialActive();

        // Update baseline only once per hour
        if (!inSocial && now - lastBaselineUpdate > 3_600_000L) {
            riskEngine.updateBaseline(features);
            lastBaselineUpdate = now;
        }

        // Display risk score
        int riskPct = Math.round(risk * 100);

        binding.tvRiskScore.setText(riskPct + "%");
        binding.tvRiskLabel.setText(riskEngine.riskLabel(risk));

        int riskColor;
        RiskEngine.RiskLevel level = riskEngine.getRiskLevel(risk);

        switch (level) {
            case LOW:
                riskColor = requireContext().getColor(R.color.risk_low);
                break;
            case HIGH:
                riskColor = requireContext().getColor(R.color.risk_high);
                break;
            case MEDIUM:
            default:
                riskColor = requireContext().getColor(R.color.risk_medium);
                break;
        }

        binding.tvRiskScore.setTextColor(riskColor);
        binding.tvRiskLabel.setTextColor(riskColor);

        // Optional explanation text
        if (binding.tvRiskExplanation != null) {
            binding.tvRiskExplanation.setText(riskEngine.explainRisk(risk));
        }

        // Social context indicator
        binding.tvSocialContext.setText(
                inSocial ? "● Social session active"
                        : "○ No active social session"
        );

        binding.tvSocialContext.setTextColor(
                inSocial
                        ? requireContext().getColor(R.color.accent)
                        : requireContext().getColor(R.color.text_tertiary)
        );

        // Last hour screen time
        long fromMs = now - 3_600_000L;

        long screenTimeSec =
                statsCollector.getTotalScreenTimeSeconds(fromMs, now);

        binding.tvScreenTime.setText((screenTimeSec / 60) + " min");

        // Unlock rate
        float unlockRate = features.unlockRate;

        binding.tvUnlockRate.setText(
                String.format(Locale.getDefault(), "%.0f/hr", unlockRate)
        );

        float baselineUnlock = riskEngine.getBaselineUnlockRate();

        binding.tvUnlockBaseline.setText(
                String.format(Locale.getDefault(),
                        "baseline: %.0f/hr", baselineUnlock)
        );

        // Switch rate
        float switchRate = features.switchRate;

        binding.tvSwitchRate.setText(
                String.format(Locale.getDefault(), "%.0f/hr", switchRate)
        );

        float baselineSwitch = riskEngine.getBaselineSwitchRate();

        binding.tvSwitchBaseline.setText(
                String.format(Locale.getDefault(),
                        "baseline: %.0f/hr", baselineSwitch)
        );

        // Notification reaction time
        float notifReact = features.notificationReactionSeconds;

        binding.tvNotifRate.setText(
                notifReact > 600
                        ? "slow"
                        : String.format(Locale.getDefault(),
                        "%.0f sec", notifReact)
        );

        // Social screen time indicator
        binding.tvScreenTimeSocial.setText(
                inSocial ? "in social now" : "no active session"
        );

        refreshChart();
    }

    private void setupChart() {

        LineChart chart = binding.barChart;

        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(false);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);

        chart.getAxisRight().setEnabled(false);

        chart.getAxisLeft().setTextColor(Color.parseColor("#B0B3C6"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#1F2A4A"));
        chart.getAxisLeft().setAxisLineColor(Color.TRANSPARENT);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(100f);

        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.parseColor("#B0B3C6"));
        chart.getXAxis().setGridColor(Color.TRANSPARENT);
        chart.getXAxis().setDrawAxisLine(false);
        chart.getXAxis().setGranularity(1f);
    }

    private void refreshChart() {

        long now = System.currentTimeMillis();
        String[] labels;

        List<Entry> socialEntries   = new ArrayList<>();
        List<Entry> baselineEntries = new ArrayList<>();
        
        com.cce.attune.database.AppDatabase db = com.cce.attune.database.AppDatabase.getInstance(requireContext());

        switch (currentPeriod) {

            case 1: // Weekly — one point per day (7 points)
                labels = new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
                for (int i = 0; i < 7; i++) {
                    long to   = now - (6 - i) * 86_400_000L;
                    long from = to  - 86_400_000L;
                    
                    Float riskAvg = db.riskRecordDao().getAvgRiskInRange(from, to);
                    float risk = riskAvg != null ? riskAvg * 100f : 0f;
                    
                    socialEntries.add(new Entry(i, risk));
                    baselineEntries.add(new Entry(i, new StrictnessManager(requireContext()).getThreshold(new RiskEngine(requireContext())) * 100f));
                }
                break;

            case 2: // Monthly — one point per week (4 points)
                labels = new String[]{"Week 1","Week 2","Week 3","Week 4"};
                for (int i = 0; i < 4; i++) {
                    long weekMs = 7 * 86_400_000L;
                    long to     = now - (3 - i) * weekMs;
                    long from   = to  - weekMs;
                    
                    Float riskAvg = db.riskRecordDao().getAvgRiskInRange(from, to);
                    float risk = riskAvg != null ? riskAvg * 100f : 0f;
                    
                    socialEntries.add(new Entry(i, risk));
                    baselineEntries.add(new Entry(i, new StrictnessManager(requireContext()).getThreshold(new RiskEngine(requireContext())) * 100f));
                }
                break;

            default: // Daily — one point per hour for last 5 hours
                labels = new String[]{"4h ago","3h ago","2h ago","1h ago","Now"};
                for (int i = 0; i < 5; i++) {
                    long to   = now - (4 - i) * 3_600_000L;
                    long from = to  - 3_600_000L;
                    
                    Float riskAvg = db.riskRecordDao().getAvgRiskInRange(from, to);
                    float risk = riskAvg != null ? riskAvg * 100f : 0f;
                    
                    socialEntries.add(new Entry(i, risk));
                    baselineEntries.add(new Entry(i, new StrictnessManager(requireContext()).getThreshold(new RiskEngine(requireContext())) * 100f));
                }
                break;
        }

        // Risk Score line
        LineDataSet socialSet = new LineDataSet(socialEntries, "Risk Score");
        socialSet.setColor(requireContext().getColor(R.color.bar_social));
        socialSet.setLineWidth(2.5f);
        socialSet.setCircleRadius(3f);
        socialSet.setCircleColor(requireContext().getColor(R.color.bar_social));
        socialSet.setDrawCircleHole(false);
        socialSet.setDrawValues(false);
        socialSet.setMode(LineDataSet.Mode.LINEAR);
        socialSet.setDrawFilled(true);
        socialSet.setFillColor(requireContext().getColor(R.color.bar_social));
        socialSet.setFillAlpha(40);

        // Threshold line
        LineDataSet baselineSet = new LineDataSet(baselineEntries, "Threshold");
        baselineSet.setColor(requireContext().getColor(R.color.bar_baseline));
        baselineSet.setLineWidth(1.5f);
        baselineSet.setDrawCircles(false);
        baselineSet.setDrawValues(false);
        baselineSet.enableDashedLine(10f, 5f, 0f);
        baselineSet.setMode(LineDataSet.Mode.LINEAR);

        LineChart chart = binding.barChart;
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setLabelCount(labels.length);
        chart.setData(new LineData(baselineSet, socialSet));
        chart.setVisibleXRangeMaximum(labels.length);
        chart.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
        refreshHandler.post(refreshRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}