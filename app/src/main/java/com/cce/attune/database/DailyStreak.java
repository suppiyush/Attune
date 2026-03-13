package com.cce.attune.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_streaks")
public class DailyStreak {

    @PrimaryKey
    @NonNull
    public String date; // YYYY-MM-DD

    /**
     * 0: No Data
     * 1: Clean (No Phubbing)
     * 2: Phubbing Detected
     */
    public int status;

    public DailyStreak() {}

    public DailyStreak(@NonNull String date, int status) {
        this.date = date;
        this.status = status;
    }
}
