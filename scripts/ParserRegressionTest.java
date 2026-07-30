import java.util.Calendar;
import java.util.Date;
import java.util.List;

import kr.co.mybrain.ai.AiAnalysisResult;
import kr.co.mybrain.ai.KoreanScheduleRangeParser;
import kr.co.mybrain.ai.QuickInputParser;

/** Galaxy S24 Ultra에서 확인된 한국어 일정 범위 분석 오류의 회귀 테스트입니다. */
public final class ParserRegressionTest {
    public static void main(String[] args) {
        Calendar base = Calendar.getInstance();
        base.set(2026, Calendar.JULY, 31, 7, 46, 0);
        base.set(Calendar.MILLISECOND, 0);
        Date now = base.getTime();

        String raw = "방과후수업\n이번주 월요일~금요일\n매일 오전 9:00~오후 12:00\n알림 30분 전";
        List<AiAnalysisResult> results = KoreanScheduleRangeParser.parse(raw, now);

        require(results.size() == 5, "이번주 월~금은 5건이어야 합니다: " + results.size());
        String[] dates = {"2026-07-27", "2026-07-28", "2026-07-29", "2026-07-30", "2026-07-31"};
        for (int i = 0; i < dates.length; i++) {
            AiAnalysisResult item = results.get(i);
            require("일정".equals(item.type), "유형은 일정이어야 합니다");
            require("방과후수업".equals(item.title), "제목은 방과후수업이어야 합니다: " + item.title);
            require(dates[i].equals(item.date), "날짜 오류: " + item.date);
            require("09:00".equals(item.time), "시작 시간 오류: " + item.time);
            require("12:00".equals(item.endTime), "종료 시간 오류: " + item.endTime);
            require("NONE".equals(item.repeatType), "날짜별 5건이므로 무기한 반복이면 안 됩니다");
        }

        AiAnalysisResult first = QuickInputParser.parseSingle(raw, now);
        require("2026-07-27".equals(first.date), "첫 날짜 오류: " + first.date);
        require("2026-07-31".equals(first.rangeEndDate), "종료 날짜 오류: " + first.rangeEndDate);
        require("09:00".equals(first.time), "단일 분석 시작 시간 오류: " + first.time);
        require("12:00".equals(first.endTime), "단일 분석 종료 시간 오류: " + first.endTime);
        require(first.rangeCount == 5, "범위 건수 오류: " + first.rangeCount);

        System.out.println("ParserRegressionTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
