package kr.co.mybrain.v2.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * 일정·할 일·메모를 하나의 저장 구조로 관리하는 핵심 데이터 모델입니다.
 */
@Entity(
        tableName = "work_items",
        indices = {
                @Index("type"),
                @Index("startAt"),
                @Index("completed"),
                @Index(value = "externalId", unique = true)
        }
)
public class WorkItemEntity {

    public static final String TYPE_SCHEDULE = "SCHEDULE";
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_MEMO = "MEMO";

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 백업·동기화 시에도 유지되는 고유 식별자입니다. */
    @NonNull
    public String externalId = UUID.randomUUID().toString();

    @NonNull
    public String type = TYPE_MEMO;

    @NonNull
    public String title = "";

    @NonNull
    public String content = "";

    /** 음성 또는 공유 메시지의 원문입니다. */
    @NonNull
    public String sourceText = "";

    /** UTC epoch millis. 날짜가 없는 메모는 null입니다. */
    public Long startAt;
    public Long endAt;

    /** 종일 일정 여부입니다. */
    public boolean allDay;

    /** 할 일 완료 여부입니다. */
    public boolean completed;

    /** LOW, NORMAL, HIGH */
    @NonNull
    public String priority = "NORMAL";

    /** NONE, DAILY, WEEKLY, MONTHLY, WEEKDAYS */
    @NonNull
    public String repeatRule = "NONE";

    /** 알림 기준 시각. 알림이 없으면 null입니다. */
    public Long reminderAt;

    @NonNull
    public String color = "DEFAULT";

    @NonNull
    public String aiProvider = "LOCAL";

    /** AI 분석 결과에 대한 0~1 신뢰도입니다. */
    public float aiConfidence;

    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();
    public Long deletedAt;
}
