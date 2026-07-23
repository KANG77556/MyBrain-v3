package kr.co.mybrain.ai;

/**
 * GPT 또는 Gemini가 반환한 분석 결과를 앱 내부 공통 형식으로 전달합니다.
 */
public final class AiAnalysisResult {
    public String type = "메모";
    public String title = "";
    public String content = "";
    public String date = "";
    public String time = "";
    public String repeatType = "NONE";
}
