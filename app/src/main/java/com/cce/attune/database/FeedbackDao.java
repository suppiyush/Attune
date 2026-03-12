package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FeedbackDao {

    @Insert
    long insertFeedback(FeedbackEvent event);

    @Query("UPDATE feedback_events SET wasCorrect = :wasCorrect WHERE id = :id")
    void updateFeedback(int id, boolean wasCorrect);

    @Query("SELECT * FROM feedback_events ORDER BY timestamp DESC LIMIT 100")
    List<FeedbackEvent> getRecentFeedback();

    @Query("SELECT COUNT(*) FROM feedback_events WHERE wasCorrect = 1")
    int getCorrectCount();

    @Query("SELECT COUNT(*) FROM feedback_events WHERE wasCorrect = 0")
    int getFalseAlarmCount();
}
