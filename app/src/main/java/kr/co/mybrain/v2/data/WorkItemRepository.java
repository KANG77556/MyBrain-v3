package kr.co.mybrain.v2.data;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 화면과 Room 사이를 연결합니다. 데이터베이스 작업은 메인 스레드에서 실행하지 않습니다.
 */
public final class WorkItemRepository {

    public interface ResultCallback<T> {
        void onResult(T value);
    }

    private static volatile WorkItemRepository instance;

    private final WorkItemDao dao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private WorkItemRepository(Context context) {
        dao = MyBrainDatabase.getInstance(context).workItemDao();
    }

    public static WorkItemRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (WorkItemRepository.class) {
                if (instance == null) {
                    instance = new WorkItemRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void insert(WorkItemEntity item, ResultCallback<Long> callback) {
        databaseExecutor.execute(() -> {
            item.updatedAt = System.currentTimeMillis();
            long id = dao.insert(item);
            if (callback != null) callback.onResult(id);
        });
    }

    public void update(WorkItemEntity item, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            item.updatedAt = System.currentTimeMillis();
            int count = dao.update(item);
            if (callback != null) callback.onResult(count);
        });
    }

    public void getAll(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getAllActive()));
    }

    public void getByType(String type, ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getByType(type)));
    }

    public void getOpenTasks(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getOpenTasks()));
    }

    public void softDelete(long id, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.softDelete(id, System.currentTimeMillis());
            if (callback != null) callback.onResult(count);
        });
    }
}
