package kr.co.mybrain.ai;

import java.util.Date;
import java.util.List;

/** 기존 호출과 호환되는 한국어 일정 범위 분석 진입점입니다. */
public final class KoreanScheduleRangeParser {
    private KoreanScheduleRangeParser() { }

    public static List<AiAnalysisResult> parse(String rawText, Date referenceTime) {
        return KoreanWeekRangeParser.parse(rawText, referenceTime);
    }
}
