package com.seongho.brainassistant.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceSyncDecisionTest {
    @Test
    fun confirmedRecurringSaveEnqueuesImmediateSync() {
        assertTrue(RecurrenceSyncDecision.shouldEnqueue(recurrenceCount = 1))
    }

    @Test
    fun ordinarySaveDoesNotEnqueueRecurrenceSync() {
        assertFalse(RecurrenceSyncDecision.shouldEnqueue(recurrenceCount = 0))
    }
}
