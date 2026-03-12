package com.cce.attune.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Simple adapter for the SSID whitelist in ProfileFragment */
public class SsidAdapter extends RecyclerView.Adapter<SsidAdapter.ViewHolder> {

    public interface OnRemoveListener {
        void onRemove(String ssid);
    }

    private List<String> ssids = new ArrayList<>();
    private final OnRemoveListener removeListener;

    public SsidAdapter(OnRemoveListener removeListener) {
        this.removeListener = removeListener;
    }

    public void submitList(List<String> list) {
        ssids = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Simple inline layout: chip-style row with SSID name and remove button
        View row = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String ssid = ssids.get(position);
        holder.tvSsid.setText("📶 " + ssid);
        holder.tvSsid.setOnLongClickListener(v -> {
            removeListener.onRemove(ssid);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return ssids.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSsid;

        ViewHolder(View v) {
            super(v);
            tvSsid = v.findViewById(android.R.id.text1);
            tvSsid.setTextColor(v.getContext().getColor(com.cce.attune.R.color.text_secondary));
        }
    }
}

