package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TelemetryDao {

    @Insert
    void insert(TelemetryEvent event);

    /** All events in a time range */
    @Query("SELECT * FROM telemetry_events WHERE timestampMs BETWEEN :fromMs AND :toMs ORDER BY timestampMs ASC")
    List<TelemetryEvent> getEventsInRange(long fromMs, long toMs);

    /** Count UNLOCK events in range */
    @Query("SELECT COUNT(*) FROM telemetry_events WHERE type = 'UNLOCK' AND timestampMs BETWEEN :fromMs AND :toMs")
    int getUnlockCount(long fromMs, long toMs);

    /** Count APP_SWITCH events in range */
    @Query("SELECT COUNT(*) FROM telemetry_events WHERE type = 'APP_SWITCH' AND timestampMs BETWEEN :fromMs AND :toMs")
    int getAppSwitchCount(long fromMs, long toMs);

    /** Average session duration (SCREEN_OFF events have durationMs set) */
    @Query("SELECT AVG(durationMs) FROM telemetry_events WHERE type = 'SCREEN_OFF' AND timestampMs BETWEEN :fromMs AND :toMs")
    float getAvgSessionDuration(long fromMs, long toMs);

    /** Get unlock counts grouped by day for the last N days — returns list of events */
    @Query("SELECT * FROM telemetry_events WHERE type = 'UNLOCK' AND timestampMs > :sinceMs ORDER BY timestampMs ASC")
    List<TelemetryEvent> getUnlocksSince(long sinceMs);

    @Query("DELETE FROM telemetry_events WHERE timestampMs < :beforeMs")
    void deleteOldEvents(long beforeMs);
}

