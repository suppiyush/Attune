package com.cce.attune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "risk_records")
public class RiskRecord {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public long timestampMs;
    public float riskScore; // Original computed score [0..1] range

    public RiskRecord() {
    }

    public RiskRecord(long timestampMs, float riskScore) {
        this.timestampMs = timestampMs;
        this.riskScore = riskScore;
    }
}
