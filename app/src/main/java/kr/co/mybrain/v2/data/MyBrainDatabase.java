package kr.co.mybrain.v2.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {WorkItemEntity.class},
        version = 1,
        exportSchema = true
)
public abstract class MyBrainDatabase extends RoomDatabase {

    private static volatile MyBrainDatabase instance;

    public abstract WorkItemDao workItemDao();

    public static MyBrainDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (MyBrainDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    MyBrainDatabase.class,
                                    "mybrain-v2.db"
                            )
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return instance;
    }
}
