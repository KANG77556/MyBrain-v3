package com.seongho.brainassistant.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModelTest {
    @Test
    fun ddayDisplayUsesDateNotClockTime() {
        val target = LocalDate.of(2026, 8, 20)

        assertEquals(
            DDayDisplay.Before(3),
            DDayDisplay.between(LocalDate.of(2026, 8, 17), target),
        )
        assertEquals(DDayDisplay.Today, DDayDisplay.between(target, target))
        assertEquals(
            DDayDisplay.After(1),
            DDayDisplay.between(LocalDate.of(2026, 8, 21), target),
        )
    }
}
