package com.seongho.brainassistant.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class DDayCategory {
    DEADLINE,
    EXAM,
    EVENT,
    HEALTH,
    TRAVEL,
    ANNIVERSARY,
    CUSTOM,
}

sealed interface DDayDisplay {
    data class Before(val days: Long) : DDayDisplay
    data object Today : DDayDisplay
    data class After(val days: Long) : DDayDisplay

    companion object {
        fun between(today: LocalDate, target: LocalDate): DDayDisplay {
            val days = ChronoUnit.DAYS.between(today, target)
            return when {
                days > 0 -> Before(days)
                days == 0L -> Today
                else -> After(-days)
            }
        }
    }
}

data class DDayItem(
    val id: String = UUID.randomUUID().toString(),
    val inputId: String,
    val transactionId: String,
    val title: String,
    val targetDate: LocalDate,
    val category: DDayCategory,
    val importance: Int = 2,
    val isPinned: Boolean = false,
    val showElapsedDays: Boolean = true,
    val archiveAfterDays: Int = 7,
    val recurrenceRule: String? = null,
    val linkedTaskId: String? = null,
    val linkedCalendarId: String? = null,
    val reminderOffsets: Set<Int> = setOf(7, 3, 1, 0),
    val status: ItemStatus = ItemStatus.ACTIVE,
    val deletedAt: Instant? = null,
    val updatedAt: Instant = Instant.now(),
)
