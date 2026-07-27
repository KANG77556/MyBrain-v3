package kr.co.mybrain.v2.assistant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import kr.co.mybrain.v2.settings.AiSettings;

public class AiAnalysisRetryPolicyTest {
    @Test public void geminiMalformedJsonRetriesOnce() {
        Exception error = new CloudAiWorkItemAnalyzer.AnalysisException(
                "AI 응답 형식을 확인하지 못했습니다. 기기 분석 결과를 사용합니다.");
        assertTrue(AiAnalysisRetryPolicy.shouldRetry(AiSettings.PROVIDER_GEMINI, 0, error));
        assertFalse(AiAnalysisRetryPolicy.shouldRetry(AiSettings.PROVIDER_GEMINI, 1, error));
    }

    @Test public void openAiMalformedJsonDoesNotUseGeminiRetry() {
        Exception error = new CloudAiWorkItemAnalyzer.AnalysisException("Unterminated object at character 104");
        assertFalse(AiAnalysisRetryPolicy.shouldRetry(AiSettings.PROVIDER_OPENAI, 0, error));
    }

    @Test public void authenticationAndQuotaErrorsDoNotRetry() {
        assertFalse(AiAnalysisRetryPolicy.shouldRetry(AiSettings.PROVIDER_GEMINI, 0,
                new Exception("Gemini 분석 실패 (HTTP 401)")));
        assertFalse(AiAnalysisRetryPolicy.shouldRetry(AiSettings.PROVIDER_GEMINI, 0,
                new Exception("Gemini 분석 실패 (HTTP 429) 사용량 한도")));
    }

    @Test public void rawJsonErrorBecomesFriendlyMessage() {
        assertEquals("AI 응답 형식이 불완전했습니다. 기기 분석 결과를 사용합니다.",
                AiAnalysisRetryPolicy.friendlyMessage(
                        new Exception("Unterminated object at character 104 of { category...")));
    }

    @Test public void modelAndCredentialErrorsHaveActions() {
        assertEquals("AI 연결 정보가 올바른지 확인하세요.",
                AiAnalysisRetryPolicy.friendlyMessage(new Exception("HTTP 401 invalid key")));
        assertEquals("선택한 AI 모델을 찾을 수 없습니다. 모델 설정을 확인하세요.",
                AiAnalysisRetryPolicy.friendlyMessage(new Exception("HTTP 404 model not found")));
    }
}
