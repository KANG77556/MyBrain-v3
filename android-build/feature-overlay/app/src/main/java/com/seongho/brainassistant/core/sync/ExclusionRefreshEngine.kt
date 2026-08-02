package com.seongho.brainassistant.core.sync

import com.seongho.brainassistant.core.calendar.ExclusionCalendarGateway
import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionSource

interface ExclusionCacheStore {
    suspend fun mergeDiscoveredSources(discovered: List<ExclusionSource>)
    suspend fun enabledSources(): List<ExclusionSource>
    suspend fun replaceSourceYear(sourceId: String, year: Int, dates: List<ExclusionDate>)
}

fun interface RecurrenceExclusionRecalculator {
    suspend fun recalculate(year: Int)
}

data class ExclusionRefreshSummary(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
)

class ExclusionRefreshEngine(
    private val gateway: ExclusionCalendarGateway,
    private val store: ExclusionCacheStore,
    private val recalculator: RecurrenceExclusionRecalculator,
) {
    suspend fun refresh(years: Set<Int>): ExclusionRefreshSummary {
        require(years.all { it > 0 })
        store.mergeDiscoveredSources(gateway.discoverSources())
        val enabled = store.enabledSources()
        var succeeded = 0
        var failed = 0
        val successfulYears = mutableSetOf<Int>()
        for (source in enabled) {
            for (year in years.sorted()) {
                try {
                    val completeRows = gateway.loadDates(source, year)
                    store.replaceSourceYear(source.id, year, completeRows)
                    succeeded++
                    successfulYears += year
                } catch (_: Exception) {
                    failed++
                }
            }
        }
        successfulYears.sorted().forEach { recalculator.recalculate(it) }
        return ExclusionRefreshSummary(enabled.size * years.size, succeeded, failed)
    }
}
