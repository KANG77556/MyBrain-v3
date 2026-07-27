package kr.co.mybrain.v2.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnalysisUiPolicyTest {
    @Test public void providerIsDetectedFromCompletionText() {
        assertEquals("GEMINI", AnalysisUiPolicy.providerFromStatus("Gemini 정밀 분석 완료 · 2.7초"));
        assertEquals("OPENAI", AnalysisUiPolicy.providerFromStatus("GPT 정밀 분석 완료 · 1.4초"));
        assertEquals("LOCAL", AnalysisUiPolicy.providerFromStatus("기기 분석 완료"));
    }

    @Test public void runningAndTerminalStatesAreSeparated() {
        assertTrue(AnalysisUiPolicy.isCloudRunning("Gemini로 정밀 분석 중입니다…"));
        assertFalse(AnalysisUiPolicy.isTerminal("Gemini로 정밀 분석 중입니다…"));
        assertTrue(AnalysisUiPolicy.isTerminal("Gemini 정밀 분석 완료"));
        assertTrue(AnalysisUiPolicy.isTerminal("인터넷 연결이 없어 기기 분석으로 전환했습니다."));
        assertTrue(AnalysisUiPolicy.isTerminal("AI 분석 취소 · 기기 분석 결과를 표시합니다."));
    }

    @Test public void elapsedTextIsReadable() {
        assertEquals("420ms", AnalysisUiPolicy.elapsed(420));
        assertEquals("1.5초", AnalysisUiPolicy.elapsed(1500));
        assertEquals("0ms", AnalysisUiPolicy.elapsed(-1));
    }
}