package com.cce.attune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DailyStreakDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DailyStreak streak);

    @Query("SELECT * FROM daily_streaks WHERE date = :date LIMIT 1")
    DailyStreak getStreakForDate(String date);

    @Query("SELECT * FROM daily_streaks WHERE date BETWEEN :startAnd AND :endDate ORDER BY date ASC")
    List<DailyStreak> getStreaksInRange(String startAnd, String endDate);

    @Query("UPDATE daily_streaks SET status = :status WHERE date = :date")
    void updateStatus(String date, int status);
}
