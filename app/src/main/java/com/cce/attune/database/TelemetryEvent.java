package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "telemetry_events")
public class TelemetryEvent {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;       // "UNLOCK", "SCREEN_OFF", "APP_SWITCH", "NOTIFICATION"
    public String packageName; // relevant app package (nullable)
    public long timestampMs;   // epoch ms
    public long durationMs;    // session duration if SCREEN_OFF, 0 otherwise

    public TelemetryEvent() {}

    public TelemetryEvent(String type, String packageName, long timestampMs, long durationMs) {
        this.type = type;
        this.packageName = packageName;
        this.timestampMs = timestampMs;
        this.durationMs = durationMs;
    }
}
