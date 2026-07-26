package kr.co.mybrain.v2.transfer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** DB에 쓰지 않고 백업 복원 결과를 미리 계산합니다. */
public final class BackupRestorePlanner {
    private BackupRestorePlanner() {}

    public static Plan compare(List<WorkItemEntity> current, List<WorkItemEntity> incoming) {
        Map<String, WorkItemEntity> currentById = new HashMap<>();
        if (current != null) {
            for (WorkItemEntity item : current) {
                if (item != null && item.externalId != null) currentById.put(item.externalId, item);
            }
        }

        int total = 0;
        int insert = 0;
        int update = 0;
        int unchanged = 0;
        int deleted = 0;
        int reminders = 0;
        int duplicateIds = 0;
        Set<String> seen = new HashSet<>();

        if (incoming != null) {
            for (WorkItemEntity item : incoming) {
                if (item == null) continue;
                total++;
                String externalId = item.externalId == null ? "" : item.externalId;
                if (!seen.add(externalId)) duplicateIds++;
                if (item.deletedAt != null) deleted++;
                if (item.deletedAt == null && item.reminderAt != null) reminders++;
                WorkItemEntity existing = currentById.get(externalId);
                if (existing == null) insert++;
                else if (same(existing, item)) unchanged++;
                else update++;
            }
        }

        int currentOnly = Math.max(0, currentById.size() - countMatching(currentById, seen));
        return new Plan(total, insert, update, unchanged, currentOnly,
                deleted, reminders, duplicateIds, duplicateIds == 0);
    }

    private static int countMatching(Map<String, WorkItemEntity> current, Set<String> incomingIds) {
        int count = 0;
        for (String id : incomingIds) if (current.containsKey(id)) count++;
        return count;
    }

    private static boolean same(WorkItemEntity a, WorkItemEntity b) {
        return Objects.equals(a.externalId, b.externalId)
                && Objects.equals(a.type, b.type)
                && Objects.equals(a.title, b.title)
                && Objects.equals(a.content, b.content)
                && Objects.equals(a.sourceText, b.sourceText)
                && Objects.equals(a.startAt, b.startAt)
                && Objects.equals(a.endAt, b.endAt)
                && a.allDay == b.allDay
                && a.completed == b.completed
                && Objects.equals(a.priority, b.priority)
                && Objects.equals(a.repeatRule, b.repeatRule)
                && Objects.equals(a.reminderAt, b.reminderAt)
                && Objects.equals(a.color, b.color)
                && Objects.equals(a.aiProvider, b.aiProvider)
                && Float.compare(a.aiConfidence, b.aiConfidence) == 0
                && a.createdAt == b.createdAt
                && a.updatedAt == b.updatedAt
                && Objects.equals(a.deletedAt, b.deletedAt);
    }

    public static final class Plan {
        public final int backupItems;
        public final int insertCount;
        public final int updateCount;
        public final int unchangedCount;
        public final int currentOnlyCount;
        public final int deletedCount;
        public final int reminderCount;
        public final int duplicateExternalIds;
        public final boolean safeToRestore;

        Plan(int backupItems, int insertCount, int updateCount, int unchangedCount,
             int currentOnlyCount, int deletedCount, int reminderCount,
             int duplicateExternalIds, boolean safeToRestore) {
            this.backupItems = backupItems;
            this.insertCount = insertCount;
            this.updateCount = updateCount;
            this.unchangedCount = unchangedCount;
            this.currentOnlyCount = currentOnlyCount;
            this.deletedCount = deletedCount;
            this.reminderCount = reminderCount;
            this.duplicateExternalIds = duplicateExternalIds;
            this.safeToRestore = safeToRestore;
        }

        public String summary() {
            return "백업 항목 " + backupItems + "개"
                    + "\n새로 추가 " + insertCount + "개"
                    + "\n갱신 예정 " + updateCount + "개"
                    + "\n변경 없음 " + unchangedCount + "개"
                    + "\n현재 기기에만 존재 " + currentOnlyCount + "개"
                    + "\n휴지통 항목 " + deletedCount + "개"
                    + "\n알림 재등록 대상 " + reminderCount + "개"
                    + "\n중복 외부 ID " + duplicateExternalIds + "개";
        }
    }
}
