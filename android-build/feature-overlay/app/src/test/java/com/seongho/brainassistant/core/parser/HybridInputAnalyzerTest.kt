package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.AnalysisResult
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridInputAnalyzerTest {
    private val request = AnalysisRequest(
        "내일까지 채점",
        ZonedDateTime.of(2026, 7, 31, 22, 0, 0, 0, ZoneId.of("Asia/Seoul")),
    )

    @Test
    fun highConfidenceLocalResultSkipsRemoteForOfflineFirstBehavior() = runTest {
        var remoteCalls = 0
        val local = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                confidence = 0.91,
                clarificationFields = emptySet(),
                analyzer = "RULE_BASED_V1",
            )
        }
        val remote = InputAnalyzer {
            remoteCalls += 1
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "원격 채점")),
                confidence = 0.99,
                clarificationFields = emptySet(),
                analyzer = "REMOTE_AI",
            )
        }

        val result = HybridInputAnalyzer(local, remote).analyze(request)

        assertEquals("RULE_BASED_V1", result.analyzer)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun ambiguousLocalResultUsesBetterRemoteResult() = runTest {
        val local = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                confidence = 0.60,
                clarificationFields = setOf(ClarificationField.DATE),
                analyzer = "RULE_BASED_V1",
            )
        }
        val remote = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                confidence = 0.94,
                clarificationFields = emptySet(),
                analyzer = "REMOTE_AI",
            )
        }

        assertEquals("REMOTE_AI", HybridInputAnalyzer(local, remote).analyze(request).analyzer)
    }

    @Test
    fun ambiguousLocalResultIsKeptWhenRemoteResultIsWorse() = runTest {
        val local = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                confidence = 0.72,
                clarificationFields = setOf(ClarificationField.DATE),
                analyzer = "RULE_BASED_V1",
            )
        }
        val remote = InputAnalyzer {
            AnalysisResult(
                items = listOf(ParsedItem(type = ItemType.NOTE, title = "채점")),
                confidence = 0.40,
                clarificationFields = setOf(ClarificationField.DATE, ClarificationField.TITLE),
                analyzer = "REMOTE_AI",
            )
        }

        assertEquals("RULE_BASED_V1", HybridInputAnalyzer(local, remote).analyze(request).analyzer)
    }

    @Test
    fun fallsBackToLocalWhenRemoteThrows() = runTest {
        val local = InputAnalyzer {
            AnalysisResult(
                listOf(ParsedItem(type = ItemType.TASK, title = "채점")),
                0.72,
                emptySet(),
                "RULE_BASED_V1",
            )
        }
        val remote = InputAnalyzer { error("network") }

        assertEquals("RULE_BASED_V1", HybridInputAnalyzer(local, remote).analyze(request).analyzer)
    }
}
