package kr.co.mybrain.v2.data;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.co.mybrain.v2.reminder.ReminderScheduler;

/** 화면과 Room 사이를 연결하며 모든 DB 작업을 백그라운드에서 실행합니다. */
public final class WorkItemRepository {

    public interface ResultCallback<T> { void onResult(T value); }

    private static volatile WorkItemRepository instance;
    private final WorkItemDao dao;
    private final Context appContext;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private WorkItemRepository(Context context) {
        appContext = context.getApplicationContext();
        dao = MyBrainDatabase.getInstance(appContext).workItemDao();
    }

    public static WorkItemRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (WorkItemRepository.class) {
                if (instance == null) instance = new WorkItemRepository(context);
            }
        }
        return instance;
    }

    public void insert(WorkItemEntity item, ResultCallback<Long> callback) {
        databaseExecutor.execute(() -> {
            item.updatedAt = System.currentTimeMillis();
            long id = dao.insert(item);
            item.id = id;
            ReminderScheduler.schedule(appContext, item);
            if (callback != null) callback.onResult(id);
        });
    }

    public void update(WorkItemEntity item, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            item.updatedAt = System.currentTimeMillis();
            int count = dao.update(item);
            ReminderScheduler.cancel(appContext, item.id);
            ReminderScheduler.schedule(appContext, item);
            if (callback != null) callback.onResult(count);
        });
    }

    public void getAll(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getAllActive()));
    }

    public void getDeleted(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getDeleted()));
    }

    public void getByType(String type, ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getByType(type)));
    }

    public void getById(long id, ResultCallback<WorkItemEntity> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getById(id)));
    }

    public void getOpenTasks(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getOpenTasks()));
    }

    public void setCompleted(long id, boolean completed, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.setCompleted(id, completed, System.currentTimeMillis());
            if (completed) ReminderScheduler.cancel(appContext, id);
            else {
                WorkItemEntity item = dao.getById(id);
                ReminderScheduler.schedule(appContext, item);
            }
            if (callback != null) callback.onResult(count);
        });
    }

    public void softDelete(long id, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.softDelete(id, System.currentTimeMillis());
            ReminderScheduler.cancel(appContext, id);
            if (callback != null) callback.onResult(count);
        });
    }

    public void restore(long id, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.restore(id, System.currentTimeMillis());
            ReminderScheduler.schedule(appContext, dao.getById(id));
            if (callback != null) callback.onResult(count);
        });
    }
}