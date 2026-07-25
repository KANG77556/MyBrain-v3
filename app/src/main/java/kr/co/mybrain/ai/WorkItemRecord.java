package kr.co.mybrain.ai;

/**
 * 기존 SharedPreferences 업무 자료 한 건을 표현합니다.
 * 기존 저장 필드 순서를 유지하고 종료 시간은 마지막 선택 필드로 확장합니다.
 */
public final class WorkItemRecord {
    public String type = "메모";
    public String title = "";
    public String date = "";
    public String time = "";
    public String original = "";
    public boolean completed;
    public int reminderMinutes;
    public String repeatType = "NONE";
    public String repeatEndDate = "";
    public String colorValue = "DEFAULT";
    /** 일정 종료 시각(HH:mm). 기존 자료는 빈 문자열로 읽습니다. */
    public String endTime = "";

    public WorkItemRecord copy() {
        WorkItemRecord value = new WorkItemRecord();
        value.type = type;
        value.title = title;
        value.date = date;
        value.time = time;
        value.original = original;
        value.completed = completed;
        value.reminderMinutes = reminderMinutes;
        value.repeatType = repeatType;
        value.repeatEndDate = repeatEndDate;
        value.colorValue = colorValue;
        value.endTime = endTime;
        return value;
    }
}
