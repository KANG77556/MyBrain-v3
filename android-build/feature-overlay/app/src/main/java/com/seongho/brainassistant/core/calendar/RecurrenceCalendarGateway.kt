package com.seongho.brainassistant.core.calendar

import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import java.time.Instant

interface RecurrenceCalendarGateway {
    suspend fun upsertSeries(master: RecurrenceMaster, rrule: String, exdates: List<Instant>): RemoteSeries
    suspend fun deleteSeries(calendarId: String, remoteSeriesId: String)
    suspend fun upsertDetachedOccurrence(master: RecurrenceMaster, occurrence: RecurrenceOccurrence): RemoteOccurrence
    suspend fun deleteDetachedOccurrence(calendarId: String, remoteEventId: String)
}

data class RemoteSeries(val id: String, val updatedAt: Instant)
data class RemoteOccurrence(val id: String, val updatedAt: Instant)
