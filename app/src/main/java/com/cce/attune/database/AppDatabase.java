package com.cce.attune.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        TelemetryEvent.class,
        SocialSession.class,
        SsidGroup.class,
        SsidGroupMember.class,
        FeedbackEvent.class
    },
    version = 3,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract TelemetryDao telemetryDao();
    public abstract SocialSessionDao socialSessionDao();
    public abstract SsidGroupDao ssidGroupDao();
    public abstract FeedbackDao feedbackDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "attune_db"
                            )
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
