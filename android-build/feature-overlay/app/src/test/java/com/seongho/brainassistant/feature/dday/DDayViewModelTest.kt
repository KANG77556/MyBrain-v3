package com.seongho.brainassistant.feature.dday

import com.seongho.brainassistant.core.model.DDayCategory
import com.seongho.brainassistant.core.model.DDayItem
import com.seongho.brainassistant.testing.FakeBrainRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DDayViewModelTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-08-02T01:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @Test
    fun createsAndEditsDDayWithDefaultReminders() = runTest {
        val repository = FakeBrainRepository()
        val viewModel = DDayViewModel(repository, clock, zone, StandardTestDispatcher(testScheduler))

        viewModel.onAction(DDayAction.Add)
        viewModel.onAction(DDayAction.ChangeTitle("개학"))
        viewModel.onAction(DDayAction.ChangeTargetDate("2026-08-20"))
        viewModel.onAction(DDayAction.ChangeCategory(DDayCategory.EVENT))
        viewModel.onAction(DDayAction.ChangeImportance(3))
        viewModel.onAction(DDayAction.TogglePinned)
        viewModel.onAction(DDayAction.Save)
        advanceUntilIdle()

        val saved = repository.dDays.single()
        assertEquals("개학", saved.title)
        assertEquals(LocalDate.of(2026, 8, 20), saved.targetDate)
        assertEquals(DDayCategory.EVENT, saved.category)
        assertEquals(3, saved.importance)
        assertTrue(saved.isPinned)
        assertEquals(setOf(7, 3, 1, 0), saved.reminderOffsets)
        assertFalse(viewModel.state.value.editorVisible)
    }

    @Test
    fun invalidDateKeepsEditorOpenAndDoesNotSave() = runTest {
        val repository = FakeBrainRepository()
        val viewModel = DDayViewModel(repository, clock, zone, StandardTestDispatcher(testScheduler))

        viewModel.onAction(DDayAction.Add)
        viewModel.onAction(DDayAction.ChangeTitle("건강검진"))
        viewModel.onAction(DDayAction.ChangeTargetDate("8월 20일"))
        viewModel.onAction(DDayAction.Save)
        advanceUntilIdle()

        assertTrue(repository.dDays.isEmpty())
        assertTrue(viewModel.state.value.editorVisible)
        assertEquals("날짜를 YYYY-MM-DD 형식으로 입력해 주세요.", viewModel.state.value.message)
    }

    @Test
    fun individualDeleteDoesNotDeleteOtherItemFromSameTransaction() = runTest {
        val repository = FakeBrainRepository()
        val first = sample("first", "같은거래", LocalDate.of(2026, 8, 20))
        val second = sample("second", "같은거래", LocalDate.of(2026, 8, 21))
        repository.saveDDay(first)
        repository.saveDDay(second)
        val viewModel = DDayViewModel(repository, clock, zone, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onAction(DDayAction.Delete("first"))
        advanceUntilIdle()

        assertEquals(
            listOf("second"),
            repository.observeDDays(LocalDate.of(2026, 8, 2)).first().map { it.id },
        )
    }

    private fun sample(id: String, transactionId: String, date: LocalDate) = DDayItem(
        id = id,
        inputId = "input-$id",
        transactionId = transactionId,
        title = id,
        targetDate = date,
        category = DDayCategory.CUSTOM,
        updatedAt = now,
    )
}
