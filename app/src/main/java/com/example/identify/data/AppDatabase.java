package com.example.identify.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CorrectionEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract CorrectionDao correctionDao();

    /** Main-thread queries stay disallowed (Room default). */
    public static AppDatabase get(Context ctx) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "identify.db")
                            .addMigrations(MigrationHelper.ALL)
                            .build();
                }
            }
        }
        return instance;
    }
}
