package com.seongho.brainassistant.core.sync

import com.seongho.brainassistant.core.calendar.ExclusionCalendarGateway
import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionSource
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionRefreshEngineTest {
    @Test
    fun refreshReplacesCompleteSourceYearThenRecalculatesEachSuccessfulYearOnce() = runTest {
        val holiday = source("holiday", ExclusionKind.KOREAN_PUBLIC_HOLIDAY, enabled = true)
        val school = source("school", ExclusionKind.SCHOOL_CALENDAR, enabled = false)
        val gateway = FakeGateway(listOf(holiday, school)) { source, year ->
            listOf(date(source.id, year, approved = source.kind == ExclusionKind.KOREAN_PUBLIC_HOLIDAY))
        }
        val store = FakeStore(previouslyEnabledIds = setOf("school"))
        val recalculator = FakeRecalculator()

        val summary = ExclusionRefreshEngine(gateway, store, recalculator).refresh(setOf(2026, 2027))

        assertEquals(4, summary.succeeded)
        assertEquals(0, summary.failed)
        assertEquals(
            setOf("holiday" to 2026, "holiday" to 2027, "school" to 2026, "school" to 2027),
            store.replaced.keys,
        )
        assertEquals(listOf(2026, 2027), recalculator.years.sorted())
        assertTrue(store.enabledSources().any { it.id == "school" && it.enabled })
    }

    @Test
    fun failedQueryRetainsExistingRowsAndDoesNotRecalculateThatYear() = runTest {
        val holiday = source("holiday", ExclusionKind.KOREAN_PUBLIC_HOLIDAY, enabled = true)
        val existing = listOf(date("holiday", 2026, approved = true))
        val store = FakeStore(
            previouslyEnabledIds = setOf("holiday"),
            initialRows = mutableMapOf(("holiday" to 2026) to existing),
        )
        val gateway = FakeGateway(listOf(holiday)) { _, _ -> error("provider unavailable") }
        val recalculator = FakeRecalculator()

        val summary = ExclusionRefreshEngine(gateway, store, recalculator).refresh(setOf(2026))

        assertEquals(0, summary.succeeded)
        assertEquals(1, summary.failed)
        assertEquals(existing, store.rows["holiday" to 2026])
        assertTrue(store.replaced.isEmpty())
        assertTrue(recalculator.years.isEmpty())
    }

    private fun source(id: String, kind: ExclusionKind, enabled: Boolean) = ExclusionSource(
        id = id,
        calendarId = id,
        displayName = id,
        kind = kind,
        enabled = enabled,
    )

    private fun date(sourceId: String, year: Int, approved: Boolean) = ExclusionDate(
        sourceId = sourceId,
        remoteEventId = "$sourceId-$year",
        date = LocalDate.of(year, 1, 1),
        title = "$sourceId-$year",
        approved = approved,
    )

    private class FakeGateway(
        private val sources: List<ExclusionSource>,
        private val loader: (ExclusionSource, Int) -> List<ExclusionDate>,
    ) : ExclusionCalendarGateway {
        override suspend fun discoverSources(): List<ExclusionSource> = sources
        override suspend fun loadDates(source: ExclusionSource, year: Int): List<ExclusionDate> = loader(source, year)
    }

    private class FakeStore(
        private val previouslyEnabledIds: Set<String>,
        initialRows: MutableMap<Pair<String, Int>, List<ExclusionDate>> = mutableMapOf(),
    ) : ExclusionCacheStore {
        private val sources = mutableListOf<ExclusionSource>()
        val rows = initialRows
        val replaced = mutableMapOf<Pair<String, Int>, List<ExclusionDate>>()

        override suspend fun mergeDiscoveredSources(discovered: List<ExclusionSource>) {
            sources.clear()
            sources += discovered.map { it.copy(enabled = it.enabled || it.id in previouslyEnabledIds) }
        }

        override suspend fun enabledSources(): List<ExclusionSource> = sources.filter(ExclusionSource::enabled)

        override suspend fun replaceSourceYear(sourceId: String, year: Int, dates: List<ExclusionDate>) {
            rows[sourceId to year] = dates
            replaced[sourceId to year] = dates
        }
    }

    private class FakeRecalculator : RecurrenceExclusionRecalculator {
        val years = mutableListOf<Int>()
        override suspend fun recalculate(year: Int) { years += year }
    }
}
