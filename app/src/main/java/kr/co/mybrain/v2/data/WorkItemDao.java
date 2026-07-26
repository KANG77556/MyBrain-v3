package kr.co.mybrain.v2.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface WorkItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) long insert(WorkItemEntity item);
    @Update int update(WorkItemEntity item);
    @Delete int delete(WorkItemEntity item);

    @Query("SELECT * FROM work_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    List<WorkItemEntity> getAllActive();

    /** 백업에는 휴지통 항목도 포함해 사용자가 완전한 상태를 복원할 수 있게 합니다. */
    @Query("SELECT * FROM work_items ORDER BY createdAt ASC, id ASC")
    List<WorkItemEntity> getAllIncludingDeleted();

    @Query("SELECT * FROM work_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    List<WorkItemEntity> getDeleted();

    @Query("SELECT * FROM work_items WHERE type = :type AND deletedAt IS NULL ORDER BY CASE WHEN startAt IS NULL THEN 1 ELSE 0 END, startAt ASC, createdAt DESC")
    List<WorkItemEntity> getByType(String type);

    @Query("SELECT * FROM work_items WHERE id = :id LIMIT 1")
    WorkItemEntity getById(long id);

    @Query("SELECT * FROM work_items WHERE externalId = :externalId LIMIT 1")
    WorkItemEntity getByExternalId(String externalId);

    @Query("SELECT * FROM work_items WHERE sourceText = :sourceText AND deletedAt IS NULL LIMIT 1")
    WorkItemEntity findActiveBySourceText(String sourceText);

    @Query("SELECT * FROM work_items WHERE deletedAt IS NULL AND startAt >= :from AND startAt < :to ORDER BY startAt ASC")
    List<WorkItemEntity> getBetween(long from, long to);

    @Query("SELECT * FROM work_items WHERE type = 'TASK' AND completed = 0 AND deletedAt IS NULL ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END, CASE WHEN startAt IS NULL THEN 1 ELSE 0 END, startAt ASC")
    List<WorkItemEntity> getOpenTasks();

    @Query("UPDATE work_items SET completed = :completed, updatedAt = :updatedAt WHERE id = :id")
    int setCompleted(long id, boolean completed, long updatedAt);

    @Query("UPDATE work_items SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    int softDelete(long id, long deletedAt);

    @Query("UPDATE work_items SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    int restore(long id, long updatedAt);

    @Query("DELETE FROM work_items")
    int deleteAllForRestore();

    @Query("SELECT COUNT(*) FROM work_items WHERE deletedAt IS NULL")
    int countActive();
}