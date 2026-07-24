from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"교체할 원문을 찾지 못했습니다: {path}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/kr/co/mybrain/ai/CloudAiAnalyzer.java",
    '''            String key = apiKey == null ? "" : apiKey.trim();
            String input = userText == null ? "" : userText.trim();
            if (input.isEmpty()) throw new IllegalArgumentException("분석할 내용이 없습니다.");
            if (settings.isCloudProvider() && key.isEmpty()) {''',
    '''            String key = apiKey == null ? "" : apiKey.trim();
            String input = userText == null ? "" : userText.trim();
            if (input.isEmpty()) throw new IllegalArgumentException("분석할 내용이 없습니다.");

            // 날짜·요일·시간 범위는 작은 AI 모델보다 기기 내부 규칙으로 먼저 정확하게 처리합니다.
            // 예: 다음 주 월요일부터 금요일까지 9시부터 12시까지 방과후수업
            List<AiAnalysisResult> rangeResults = KoreanScheduleRangeParser.parse(input, new Date());
            if (!rangeResults.isEmpty()) return rangeResults;

            if (settings.isCloudProvider() && key.isEmpty()) {'''
)

replace_once(
    "app/build.gradle",
    "        versionCode 21\n        versionName '1.7.0'",
    "        versionCode 22\n        versionName '1.7.2'"
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/IntegratedMainActivity.java",
    'versionText.setText("v1.7.0 · 여러 일정·할 일 자동 분리");',
    'versionText.setText("v1.7.2 · 날짜·시간 범위 자동 분리");'
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java",
    '''        AiSettings settings = AiSettings.load(this);
        if (!settings.isModelProvider()) {''',
    '''        // 날짜·요일·시간 범위는 Ollama나 클라우드 호출 전에 기기 안에서 즉시 분석합니다.
        List<AiAnalysisResult> rangeResults = KoreanScheduleRangeParser.parse(value, new java.util.Date());
        if (!rangeResults.isEmpty()) {
            showResultEditor(rangeResults);
            return;
        }

        AiSettings settings = AiSettings.load(this);
        if (!settings.isModelProvider()) {'''
)

print("날짜·시간 범위 분석 수정 완료")
