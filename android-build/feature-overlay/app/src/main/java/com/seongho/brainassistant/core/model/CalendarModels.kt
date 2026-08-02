package com.seongho.brainassistant.core.model

import java.time.Instant
import java.time.LocalDate

data class CalendarRange(
    val start: Instant,
    val endExclusive: Instant,
)

enum class AgendaKind {
    EVENT,
    TASK,
    NOTE,
    DDAY,
}

data class AgendaEntry(
    val id: String,
    val kind: AgendaKind,
    val title: String,
    val date: LocalDate,
    val startAt: Instant? = null,
    val statusLabel: String? = null,
)
