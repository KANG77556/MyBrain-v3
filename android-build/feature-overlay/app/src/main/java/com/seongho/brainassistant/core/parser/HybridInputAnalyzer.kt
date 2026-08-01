package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.AnalysisResult

class HybridInputAnalyzer(
    private val local: InputAnalyzer,
    private val remote: InputAnalyzer,
) : InputAnalyzer {
    override suspend fun analyze(request: AnalysisRequest): AnalysisResult {
        val localResult = local.analyze(request)
        if (localResult.isDecisive()) return localResult

        val remoteResult = runCatching { remote.analyze(request) }
            .getOrElse { return localResult }

        return chooseBetter(localResult, remoteResult)
    }

    private fun AnalysisResult.isDecisive(): Boolean =
        items.isNotEmpty() &&
            confidence >= AUTO_SAVE_CONFIDENCE &&
            clarificationFields.isEmpty()

    private fun chooseBetter(
        localResult: AnalysisResult,
        remoteResult: AnalysisResult,
    ): AnalysisResult {
        val localQuality = localResult.quality()
        val remoteQuality = remoteResult.quality()
        return if (remoteQuality > localQuality) remoteResult else localResult
    }

    private fun AnalysisResult.quality(): AnalysisQuality = AnalysisQuality(
        hasItems = items.isNotEmpty(),
        clarificationScore = -clarificationFields.size,
        confidence = confidence,
    )

    private data class AnalysisQuality(
        val hasItems: Boolean,
        val clarificationScore: Int,
        val confidence: Double,
    ) : Comparable<AnalysisQuality> {
        override fun compareTo(other: AnalysisQuality): Int =
            compareValuesBy(
                this,
                other,
                AnalysisQuality::hasItems,
                AnalysisQuality::clarificationScore,
                AnalysisQuality::confidence,
            )
    }

    private companion object {
        const val AUTO_SAVE_CONFIDENCE = 0.85
    }
}
