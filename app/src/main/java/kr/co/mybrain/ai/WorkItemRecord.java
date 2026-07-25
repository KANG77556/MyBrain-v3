package kr.co.mybrain.ai;

/**
 * 기존 SharedPreferences 업무 자료 한 건을 표현합니다.
 * 1.8.2~1.8.4 저장 형식과 호환하기 위해 필드 순서를 유지합니다.
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
        return value;
    }
}
