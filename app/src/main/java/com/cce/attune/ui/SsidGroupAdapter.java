package com.cce.attune.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cce.attune.R;
import com.cce.attune.database.SsidGroup;
import com.cce.attune.database.SsidGroupMember;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outer RecyclerView adapter — one card per Bluetooth device group.
 * Each card embeds an inner RecyclerView listing the group's device members.
 */
public class SsidGroupAdapter extends RecyclerView.Adapter<SsidGroupAdapter.ViewHolder> {

    public interface Listener {
        void onDeleteGroup(SsidGroup group);
        void onDeleteMember(SsidGroupMember member);
        void onAddDevices(SsidGroup group);
    }

    private List<SsidGroup> groups = new ArrayList<>();
    /** groupId → list of members, pre-loaded before submitData */
    private Map<Integer, List<SsidGroupMember>> membersMap = new HashMap<>();
    private final Listener listener;

    public SsidGroupAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Pass both groups and their members in one call to avoid partial updates. */
    public void submitData(List<SsidGroup> groups, Map<Integer, List<SsidGroupMember>> membersMap) {
        this.groups = new ArrayList<>(groups);
        this.membersMap = new HashMap<>(membersMap);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ssid_group, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SsidGroup group = groups.get(position);
        holder.tvName.setText(group.name);

        List<SsidGroupMember> members = membersMap.getOrDefault(group.id, new ArrayList<>());

        boolean isExpanded = expandedGroups.contains(group.id);
        holder.membersContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.ivExpand.setRotation(isExpanded ? 180f : 0f);

        View.OnClickListener toggleListener = v -> {
            if (isExpanded) {
                expandedGroups.remove(group.id);
            } else {
                expandedGroups.add(group.id);
            }
            notifyItemChanged(position);
        };
        holder.tvName.setOnClickListener(toggleListener);
        holder.ivExpand.setOnClickListener(toggleListener);

        // Set up inner adapter for member devices
        MemberAdapter memberAdapter = new MemberAdapter(members, listener::onDeleteMember);
        holder.rvMembers.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvMembers.setAdapter(memberAdapter);

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteGroup(group));
        holder.btnAddDevices.setOnClickListener(v -> listener.onAddDevices(group));
    }

    @Override
    public int getItemCount() { return groups.size(); }

    private final java.util.Set<Integer> expandedGroups = new java.util.HashSet<>();

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivExpand;
        View membersContainer;
        RecyclerView rvMembers;
        ImageButton btnDelete;
        Button btnAddDevices;

        ViewHolder(View v) {
            super(v);
            tvName       = v.findViewById(R.id.tv_group_name);
            ivExpand     = v.findViewById(R.id.iv_expand);
            membersContainer = v.findViewById(R.id.ll_members_container);
            rvMembers    = v.findViewById(R.id.rv_group_members);
            btnDelete    = v.findViewById(R.id.btn_delete_group);
            btnAddDevices = v.findViewById(R.id.btn_add_devices);
        }
    }

    // ── Inner adapter for device members ─────────────────────────────────────

    static class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MVH> {

        interface OnRemoveMember { void onRemove(SsidGroupMember m); }

        private final List<SsidGroupMember> members;
        private final OnRemoveMember removeListener;

        MemberAdapter(List<SsidGroupMember> members, OnRemoveMember removeListener) {
            this.members = members;
            this.removeListener = removeListener;
        }

        @NonNull
        @Override
        public MVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new MVH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull MVH holder, int position) {
            SsidGroupMember m = members.get(position);
            // Prefer the stored name; fall back to MAC only if name is blank
            String label = (m.deviceName != null && !m.deviceName.isEmpty())
                    ? m.deviceName
                    : m.deviceAddress;
            holder.tv.setText("• " + label);
            holder.tv.setOnLongClickListener(v -> {
                removeListener.onRemove(m);
                return true;
            });
        }

        @Override
        public int getItemCount() { return members.size(); }

        static class MVH extends RecyclerView.ViewHolder {
            TextView tv;
            MVH(View v) {
                super(v);
                tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(v.getContext().getColor(R.color.text_secondary));
                tv.setTextSize(12f);
            }
        }
    }
}
