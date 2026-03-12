package com.cce.attune.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cce.attune.R;
import com.cce.attune.database.SocialSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(int sessionId);
    }

    private List<SocialSession> sessions = new ArrayList<>();
    private final OnDeleteListener deleteListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d  HH:mm", Locale.getDefault());

    public ScheduleAdapter(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void submitList(List<SocialSession> list) {
        sessions = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SocialSession session = sessions.get(position);
        holder.tvName.setText(session.name);
        holder.tvTime.setText(dateFormat.format(new Date(session.startMs)) +
                " – " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(session.endMs)) +
                "  (" + session.getDurationString() + ")");

        long now = System.currentTimeMillis();
        if (session.isActive(now)) {
            holder.tvStatus.setText("● Active");
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.risk_low));
        } else if (session.startMs > now) {
            holder.tvStatus.setText("◦ Upcoming");
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.text_secondary));
        } else {
            holder.tvStatus.setText("✓ Completed");
            holder.tvStatus.setTextColor(holder.itemView.getContext()
                    .getColor(R.color.text_tertiary));
        }

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(session.id));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime, tvStatus;
        ImageButton btnDelete;

        ViewHolder(View v) {
            super(v);
            tvName   = v.findViewById(R.id.tv_session_name);
            tvTime   = v.findViewById(R.id.tv_session_time);
            tvStatus = v.findViewById(R.id.tv_session_status);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}

