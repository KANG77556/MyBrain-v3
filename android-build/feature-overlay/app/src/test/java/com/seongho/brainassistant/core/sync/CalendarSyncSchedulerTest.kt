package com.seongho.brainassistant.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncSchedulerTest {
    @Test
    fun immediateRequestUsesStableUniqueWorkName() {
        assertEquals("calendar-sync-now", CalendarSyncScheduler.immediateWorkName)
    }

    @Test
    fun periodicRequestUsesStableUniqueWorkName() {
        assertEquals("calendar-sync-periodic", CalendarSyncScheduler.periodicWorkName)
    }
}
