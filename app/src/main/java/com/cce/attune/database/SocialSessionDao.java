package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SocialSessionDao {

    @Insert
    void insert(SocialSession session);

    @Delete
    void delete(SocialSession session);

    /** All sessions ordered by start time */
    @Query("SELECT * FROM social_sessions ORDER BY startMs ASC")
    List<SocialSession> getAllSessions();

    /** Sessions that are currently active at time nowMs */
    @Query("SELECT * FROM social_sessions WHERE startMs <= :nowMs AND endMs >= :nowMs LIMIT 1")
    SocialSession getActiveSession(long nowMs);

    /** Future sessions (not yet started) */
    @Query("SELECT * FROM social_sessions WHERE startMs > :nowMs ORDER BY startMs ASC")
    List<SocialSession> getUpcomingSessions(long nowMs);

    @Query("DELETE FROM social_sessions WHERE id = :id")
    void deleteById(int id);
}

