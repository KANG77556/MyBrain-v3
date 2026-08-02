package com.seongho.brainassistant.core.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTargetResolverTest {
    @Test
    fun blankTargetUsesWritableGoogleCalendarInsteadOfLocalIdOne() {
        val resolver = CalendarTargetResolver {
            listOf(
                CalendarTarget(1L, "LOCAL", visible = true, writable = true),
                CalendarTarget(42L, "com.google", visible = true, writable = true),
            )
        }

        assertEquals(42L, resolver.resolve(""))
    }

    @Test
    fun explicitNumericTargetIsPreserved() {
        val resolver = CalendarTargetResolver { emptyList() }

        assertEquals(99L, resolver.resolve("99"))
    }
}
