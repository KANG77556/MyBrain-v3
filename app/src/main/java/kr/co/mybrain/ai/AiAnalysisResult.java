package kr.co.mybrain.ai;

/**
 * GPT·Gemini·기기 내부 규칙 분석기가 공통으로 사용하는 구조화 결과입니다.
 * 날짜 범위 입력도 첫 날짜 하나로 축약하지 않도록 종료 날짜와 건수를 함께 보존합니다.
 */
public final class AiAnalysisResult {
    public String type = "메모";
    public String title = "";
    public String content = "";
    public String date = "";
    public String time = "";
    public String endTime = "";
    public String repeatType = "NONE";
    public String rangeEndDate = "";
    public int rangeCount = 1;
}
