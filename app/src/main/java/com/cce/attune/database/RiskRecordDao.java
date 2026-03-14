package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface RiskRecordDao {

    @Insert
    void insert(RiskRecord riskRecord);

    /**
     * Get the average risk score within a given time range.
     * Returns null if no records are found in the range (so wrap it in Float or catch it).
     */
    @Query("SELECT AVG(riskScore) FROM risk_records WHERE timestampMs BETWEEN :fromMs AND :toMs")
    Float getAvgRiskInRange(long fromMs, long toMs);

    /** Clean up old data */
    @Query("DELETE FROM risk_records WHERE timestampMs < :beforeMs")
    void deleteOldRecords(long beforeMs);
}
