package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "social_sessions")
public class SocialSession {

    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;       // e.g. "Dinner with friends"
    public long startMs;      // epoch ms
    public long endMs;        // epoch ms

    public SocialSession() {}

    public SocialSession(String name, long startMs, long endMs) {
        this.name = name;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    /** Returns true if the given timestamp falls within this session. */
    public boolean isActive(long nowMs) {
        return nowMs >= startMs && nowMs <= endMs;
    }

    public String getDurationString() {
        long diffMs = endMs - startMs;
        long hours = diffMs / 3_600_000;
        long mins  = (diffMs % 3_600_000) / 60_000;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }
}
