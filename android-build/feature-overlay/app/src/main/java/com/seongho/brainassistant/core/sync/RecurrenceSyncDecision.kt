package com.seongho.brainassistant.core.sync

fun interface RecurrenceSyncTrigger {
    fun enqueue()
}

object RecurrenceSyncDecision {
    fun shouldEnqueue(recurrenceCount: Int): Boolean = recurrenceCount > 0
}
