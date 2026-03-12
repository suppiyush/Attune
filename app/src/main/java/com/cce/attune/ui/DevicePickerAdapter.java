package com.cce.attune.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the Bluetooth device picker dialog.
 * Each row shows a device name + MAC and a checkbox.
 * Uses android.R.layout.simple_list_item_multiple_choice which provides a CheckedTextView.
 */
public class DevicePickerAdapter extends RecyclerView.Adapter<DevicePickerAdapter.ViewHolder> {

    /** Holds one discovered Bluetooth device. */
    public static class DeviceItem {
        public final String address;  // Bluetooth MAC
        public final String name;     // Display name
        public boolean checked;

        public DeviceItem(String address, String name) {
            this.address = address;
            this.name    = (name != null && !name.isEmpty()) ? name : address;
            this.checked = false;
        }
    }

    private final List<DeviceItem> items = new ArrayList<>();
    private final Set<String> seenAddresses = new LinkedHashSet<>();

    /** Add a newly discovered device during an active scan. Thread-safe via main-thread receiver. */
    public void addDevice(String address, String name) {
        // Skip devices with no readable name (name null, empty, or same as MAC)
        if (name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase(address)) return;
        if (seenAddresses.contains(address)) return;
        seenAddresses.add(address);
        items.add(new DeviceItem(address, name.trim()));
        notifyItemInserted(items.size() - 1);
    }

    /** Returns only the items the user checked. */
    public List<DeviceItem> getCheckedItems() {
        List<DeviceItem> result = new ArrayList<>();
        for (DeviceItem d : items) if (d.checked) result.add(d);
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeviceItem item = items.get(position);
        // Show name only; fall back to MAC only when the device has no name
        String label = item.name.equals(item.address) ? item.address : item.name;
        holder.ctv.setText(label);
        holder.ctv.setChecked(item.checked);
        holder.ctv.setOnClickListener(v -> {
            item.checked = !item.checked;
            holder.ctv.setChecked(item.checked);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckedTextView ctv;
        ViewHolder(View v) {
            super(v);
            ctv = (CheckedTextView) v;
        }
    }
}
