package kr.co.mybrain.v2.data;

import android.content.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final MyBrainDatabase database;
    private final WorkItemDao dao;
    private final Context appContext;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private WorkItemRepository(Context context) {
        appContext = context.getApplicationContext();
        database = MyBrainDatabase.getInstance(appContext);
        dao = database.workItemDao();
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
            if (item.repeatRule != null && (item.repeatRule.startsWith("RANGE_DAILY|") || item.repeatRule.startsWith("RANGE_DAYS|"))) {
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

    /** 기간 제한 반복을 실제 개별 일정으로 확장합니다. */
    private long insertBoundedRange(WorkItemEntity source) {
        String[] parts = source.repeatRule.split("\\|");
        if (parts.length < 3 || source.startAt == null) return insertSingleFallback(source);

        long endEpochDay;
        try { endEpochDay = Long.parseLong(parts[1]); }
        catch (NumberFormatException error) { return insertSingleFallback(source); }

        int dayMask;
        if ("RANGE_DAILY".equals(parts[0])) dayMask = "1".equals(parts[2]) ? 31 : 127;
        else {
            try { dayMask = Integer.parseInt(parts[2]); }
            catch (NumberFormatException error) { dayMask = 127; }
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate startDate = Instant.ofEpochMilli(source.startAt).atZone(zoneId).toLocalDate();
        LocalDate endDate = LocalDate.ofEpochDay(endEpochDay);
        long duration = source.endAt == null ? 3_600_000L : Math.max(60_000L, source.endAt - source.startAt);
        Long reminderOffset = source.reminderAt == null ? null : source.startAt - source.reminderAt;
        long firstId = -1L;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int bit = 1 << (date.getDayOfWeek().getValue() - 1);
            if ((dayMask & bit) == 0) continue;
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

    private long insertSingleFallback(WorkItemEntity source) {
        source.repeatRule = "NONE";
        source.updatedAt = System.currentTimeMillis();
        long id = dao.insert(source);
        source.id = id;
        ReminderScheduler.schedule(appContext, source);
        return id;
    }

    private WorkItemEntity copyOf(WorkItemEntity source) {
        WorkItemEntity item = new WorkItemEntity();
        item.externalId = UUID.randomUUID().toString(); item.type = source.type; item.title = source.title;
        item.content = source.content; item.sourceText = source.sourceText; item.startAt = source.startAt; item.endAt = source.endAt;
        item.allDay = source.allDay; item.completed = source.completed; item.priority = source.priority; item.repeatRule = source.repeatRule;
        item.reminderAt = source.reminderAt; item.color = source.color; item.aiProvider = source.aiProvider; item.aiConfidence = source.aiConfidence;
        return item;
    }

    public void update(WorkItemEntity item, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            item.updatedAt = System.currentTimeMillis(); int count = dao.update(item);
            ReminderScheduler.cancel(appContext, item.id); ReminderScheduler.schedule(appContext, item);
            if (callback != null) callback.onResult(count);
        });
    }
    public void getAll(ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getAllActive())); }
    public void getAllForBackup(ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getAllIncludingDeleted())); }
    public void getDeleted(ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getDeleted())); }
    public void getByType(String type, ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getByType(type))); }
    public void getById(long id, ResultCallback<WorkItemEntity> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getById(id))); }
    public void getBetween(long from, long to, ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getBetween(from, to))); }
    public void getOpenTasks(ResultCallback<List<WorkItemEntity>> callback) { databaseExecutor.execute(() -> callback.onResult(dao.getOpenTasks())); }
    public void findDuplicate(String sourceText, ResultCallback<WorkItemEntity> callback) { databaseExecutor.execute(() -> callback.onResult(dao.findActiveBySourceText(sourceText))); }

    /**
     * 외부 식별자를 기준으로 백업을 병합하거나 기존 데이터를 완전히 교체합니다.
     * 동일 항목은 덮어쓰고 새 항목만 추가하므로 반복 복원 시 중복이 생기지 않습니다.
     */
    public void restoreBackup(List<WorkItemEntity> incoming, boolean replaceAll, ResultCallback<RestoreResult> callback) {
        databaseExecutor.execute(() -> {
            List<WorkItemEntity> current = dao.getAllIncludingDeleted();
            if (replaceAll) {
                for (WorkItemEntity item : current) ReminderScheduler.cancel(appContext, item.id);
            }

            int[] inserted = {0};
            int[] updated = {0};
            List<WorkItemEntity> activeAfterRestore = new ArrayList<>();
            try {
                database.runInTransaction(() -> {
                    if (replaceAll) dao.deleteAllForRestore();
                    if (incoming == null) return;
                    for (WorkItemEntity source : incoming) {
                        WorkItemEntity item = normalizeBackupItem(source);
                        WorkItemEntity existing = dao.getByExternalId(item.externalId);
                        if (existing != null) {
                            item.id = existing.id;
                            dao.update(item);
                            updated[0]++;
                        } else {
                            item.id = 0L;
                            item.id = dao.insert(item);
                            inserted[0]++;
                        }
                        if (item.deletedAt == null) activeAfterRestore.add(item);
                    }
                });
                if (!replaceAll) {
                    for (WorkItemEntity item : activeAfterRestore) ReminderScheduler.cancel(appContext, item.id);
                }
                for (WorkItemEntity item : activeAfterRestore) ReminderScheduler.schedule(appContext, item);
                if (callback != null) callback.onResult(new RestoreResult(true, inserted[0], updated[0], null));
            } catch (Exception error) {
                if (callback != null) callback.onResult(new RestoreResult(false, inserted[0], updated[0], error));
            }
        });
    }

    private WorkItemEntity normalizeBackupItem(WorkItemEntity source) {
        WorkItemEntity item = source == null ? new WorkItemEntity() : source;
        if (item.externalId == null || item.externalId.trim().isEmpty()) item.externalId = UUID.randomUUID().toString();
        if (!WorkItemEntity.TYPE_SCHEDULE.equals(item.type) && !WorkItemEntity.TYPE_TASK.equals(item.type)) item.type = WorkItemEntity.TYPE_MEMO;
        item.title = safe(item.title, 240);
        item.content = safe(item.content, 20_000);
        item.sourceText = safe(item.sourceText, 20_000);
        if (item.priority == null || !("LOW".equals(item.priority) || "NORMAL".equals(item.priority) || "HIGH".equals(item.priority))) item.priority = "NORMAL";
        if (item.repeatRule == null || item.repeatRule.trim().isEmpty()) item.repeatRule = "NONE";
        if (item.color == null || item.color.trim().isEmpty()) item.color = "DEFAULT";
        if (item.aiProvider == null || item.aiProvider.trim().isEmpty()) item.aiProvider = "LOCAL";
        item.aiConfidence = Math.max(0f, Math.min(1f, item.aiConfidence));
        long now = System.currentTimeMillis();
        if (item.createdAt <= 0L) item.createdAt = now;
        if (item.updatedAt <= 0L) item.updatedAt = item.createdAt;
        return item;
    }

    private String safe(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }

    public void setCompleted(long id, boolean completed, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> {
            int count = dao.setCompleted(id, completed, System.currentTimeMillis());
            if (completed) ReminderScheduler.cancel(appContext, id); else ReminderScheduler.schedule(appContext, dao.getById(id));
            if (callback != null) callback.onResult(count);
        });
    }

    public void advanceRecurrence(long id, ResultCallback<Boolean> callback) {
        databaseExecutor.execute(() -> {
            WorkItemEntity item = dao.getById(id); boolean advanced = RecurrenceCalculator.moveToNext(item, ZoneId.systemDefault());
            if (advanced) { item.updatedAt = System.currentTimeMillis(); dao.update(item); ReminderScheduler.cancel(appContext, id); ReminderScheduler.schedule(appContext, item); }
            if (callback != null) callback.onResult(advanced);
        });
    }

    public void softDelete(long id, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> { int count = dao.softDelete(id, System.currentTimeMillis()); ReminderScheduler.cancel(appContext, id); if (callback != null) callback.onResult(count); });
    }
    public void restore(long id, ResultCallback<Integer> callback) {
        databaseExecutor.execute(() -> { int count = dao.restore(id, System.currentTimeMillis()); ReminderScheduler.schedule(appContext, dao.getById(id)); if (callback != null) callback.onResult(count); });
    }

    public static final class RestoreResult {
        public final boolean success;
        public final int inserted;
        public final int updated;
        public final Exception error;

        RestoreResult(boolean success, int inserted, int updated, Exception error) {
            this.success = success;
            this.inserted = inserted;
            this.updated = updated;
            this.error = error;
        }
    }
}