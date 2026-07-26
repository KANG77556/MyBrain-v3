package kr.co.mybrain.v2.data;

import android.content.Context;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.co.mybrain.v2.reminder.RecurrenceCalculator;
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
            if (item.repeatRule != null && item.repeatRule.startsWith("RANGE_DAILY|")) {
                long firstId = insertBoundedRange(item);
                if (callback != null) callback.onResult(firstId);
                return;
            }
            item.updatedAt = System.currentTimeMillis();
            long id = dao.insert(item);
            item.id = id;
            ReminderScheduler.schedule(appContext, item);
            if (callback != null) callback.onResult(id);
        });
    }

    /** 기간 제한 반복을 무기한 반복으로 저장하지 않고 실제 개별 일정으로 확장합니다. */
    private long insertBoundedRange(WorkItemEntity source) {
        String[] parts = source.repeatRule.split("\\|");
        if (parts.length < 3 || source.startAt == null) {
            source.repeatRule = "NONE";
            source.updatedAt = System.currentTimeMillis();
            long id = dao.insert(source);
            source.id = id;
            ReminderScheduler.schedule(appContext, source);
            return id;
        }

        long endEpochDay;
        try {
            endEpochDay = Long.parseLong(parts[1]);
        } catch (NumberFormatException error) {
            source.repeatRule = "NONE";
            source.updatedAt = System.currentTimeMillis();
            long id = dao.insert(source);
            source.id = id;
            ReminderScheduler.schedule(appContext, source);
            return id;
        }

        boolean skipWeekends = "1".equals(parts[2]);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate startDate = Instant.ofEpochMilli(source.startAt).atZone(zoneId).toLocalDate();
        LocalDate endDate = LocalDate.ofEpochDay(endEpochDay);
        long duration = source.endAt == null ? 3_600_000L : Math.max(60_000L, source.endAt - source.startAt);
        Long reminderOffset = source.reminderAt == null ? null : source.startAt - source.reminderAt;
        long firstId = -1L;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek day = date.getDayOfWeek();
            if (skipWeekends && (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)) continue;

            WorkItemEntity item = copyOf(source);
            long start = date.atTime(Instant.ofEpochMilli(source.startAt).atZone(zoneId).toLocalTime())
                    .atZone(zoneId).toInstant().toEpochMilli();
            item.startAt = start;
            item.endAt = start + duration;
            item.reminderAt = reminderOffset == null ? null : start - reminderOffset;
            item.repeatRule = "NONE";
            item.updatedAt = System.currentTimeMillis();
            item.createdAt = System.currentTimeMillis();
            long id = dao.insert(item);
            item.id = id;
            if (firstId < 0) firstId = id;
            ReminderScheduler.schedule(appContext, item);
        }
        return firstId;
    }

    private WorkItemEntity copyOf(WorkItemEntity source) {
        WorkItemEntity item = new WorkItemEntity();
        item.externalId = UUID.randomUUID().toString();
        item.type = source.type;
        item.title = source.title;
        item.content = source.content;
        item.sourceText = source.sourceText;
        item.startAt = source.startAt;
        item.endAt = source.endAt;
        item.allDay = source.allDay;
        item.completed = source.completed;
        item.priority = source.priority;
        item.repeatRule = source.repeatRule;
        item.reminderAt = source.reminderAt;
        item.color = source.color;
        item.aiProvider = source.aiProvider;
        item.aiConfidence = source.aiConfidence;
        return item;
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

    public void getBetween(long from, long to, ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getBetween(from, to)));
    }

    public void getOpenTasks(ResultCallback<List<WorkItemEntity>> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.getOpenTasks()));
    }

    public void findDuplicate(String sourceText, ResultCallback<WorkItemEntity> callback) {
        databaseExecutor.execute(() -> callback.onResult(dao.findActiveBySourceText(sourceText)));
    }

    public void setCompleted(long id, boolean completed, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.setCompleted(id, completed, System.currentTimeMillis());
            if (completed) ReminderScheduler.cancel(appContext, id);
            else ReminderScheduler.schedule(appContext, dao.getById(id));
            if (callback != null) callback.onResult(count);
        });
    }

    public void advanceRecurrence(long id, ResultCallback<Boolean> callback) {
        databaseExecutor.execute(() -> {
            WorkItemEntity item = dao.getById(id);
            boolean advanced = RecurrenceCalculator.moveToNext(item, ZoneId.systemDefault());
            if (advanced) {
                item.updatedAt = System.currentTimeMillis();
                dao.update(item);
                ReminderScheduler.cancel(appContext, id);
                ReminderScheduler.schedule(appContext, item);
            }
            if (callback != null) callback.onResult(advanced);
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
