package kr.co.mybrain.v2.assistant;

import kr.co.mybrain.v2.data.WorkItemEntity;

/** 자연어 분석 결과를 화면과 저장 계층에 전달하는 값 객체입니다. */
public final class ParsedWorkItem {
    public String type = WorkItemEntity.TYPE_MEMO;
    public String title = "";
    public String sourceText = "";
    public Long startAt;
    public Long endAt;
    public Long reminderAt;
    /** 사용자가 명시적으로 '알림 없음'을 선택했는지 구분합니다. */
    public boolean reminderExplicitlyDisabled;
    public boolean allDay;
    public String repeatRule = "NONE";
    public String priority = "NORMAL";
    public float confidence;

    public WorkItemEntity toEntity() {
        WorkItemEntity item = new WorkItemEntity();
        item.type = type;
        item.title = title;
        item.content = sourceText;
        item.sourceText = sourceText;
        item.startAt = startAt;
        item.endAt = endAt;
        item.reminderAt = reminderExplicitlyDisabled ? null
                : (reminderAt != null ? reminderAt
                : (startAt != null && startAt > System.currentTimeMillis() ? startAt : null));
        item.allDay = allDay;
        item.repeatRule = repeatRule;
        item.priority = priority;
        item.aiProvider = "LOCAL";
        item.aiConfidence = confidence;
        return item;
    }
}
