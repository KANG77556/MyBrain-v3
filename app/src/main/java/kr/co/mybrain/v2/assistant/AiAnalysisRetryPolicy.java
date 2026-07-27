package kr.co.mybrain.v2.assistant;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import kr.co.mybrain.v2.settings.AiSettings;

/** AI 오류를 재시도 가능 여부와 사용자용 안내 문구로 변환합니다. */
public final class AiAnalysisRetryPolicy {
    private AiAnalysisRetryPolicy() {}

    /** Gemini 응답 형식 오류에만 최대 한 번 재시도합니다. */
    public static boolean shouldRetry(String provider, int retryCount, Throwable error) {
        if (retryCount >= 1 || !AiSettings.PROVIDER_GEMINI.equals(AiSettings.normalizeProvider(provider))) {
            return false;
        }
        String text = normalized(error);
        if (text.contains("http 401") || text.contains("http 403") || text.contains("http 404")
                || text.contains("http 429") || text.contains("권한") || text.contains("한도")
                || text.contains("인증") || text.contains("네트워크") || text.contains("시간이 초과")) {
            return false;
        }
        return text.contains("응답 형식") || text.contains("복구할 수")
                || text.contains("분석 결과를 읽지") || text.contains("unterminated")
                || text.contains("expected") || text.contains("json");
    }

    /** 예외 원문 대신 사용자가 바로 조치할 수 있는 짧은 안내를 반환합니다. */
    public static String friendlyMessage(Throwable error) {
        String text = normalized(error);
        if (error instanceof SocketTimeoutException || text.contains("timeout") || text.contains("시간이 초과")) {
            return "AI 응답 시간이 초과됐습니다. 잠시 후 다시 시도하세요.";
        }
        if (error instanceof UnknownHostException || text.contains("unable to resolve")
                || text.contains("network") || text.contains("네트워크")) {
            return "인터넷 연결을 확인하고 다시 시도하세요.";
        }
        if (text.contains("http 401") || text.contains("인증") || text.contains("연결 정보")) {
            return "AI 연결 정보가 올바른지 확인하세요.";
        }
        if (text.contains("http 403") || text.contains("권한")) {
            return "선택한 AI 모델을 사용할 권한이 없습니다.";
        }
        if (text.contains("http 404") || text.contains("모델을 찾") || text.contains("model not found")) {
            return "선택한 AI 모델을 찾을 수 없습니다. 모델 설정을 확인하세요.";
        }
        if (text.contains("http 429") || text.contains("quota") || text.contains("한도") || text.contains("결제")) {
            return "AI 사용량 또는 결제 한도를 확인하세요.";
        }
        if (text.contains("응답 형식") || text.contains("unterminated")
                || text.contains("expected") || text.contains("json")) {
            return "AI 응답 형식이 불완전했습니다. 기기 분석 결과를 사용합니다.";
        }
        return "AI 분석을 완료하지 못했습니다. 기기 분석 결과를 사용합니다.";
    }

    private static String normalized(Throwable error) {
        if (error == null) return "";
        StringBuilder value = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 4) {
            if (current.getMessage() != null) value.append(' ').append(current.getMessage());
            value.append(' ').append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return value.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
