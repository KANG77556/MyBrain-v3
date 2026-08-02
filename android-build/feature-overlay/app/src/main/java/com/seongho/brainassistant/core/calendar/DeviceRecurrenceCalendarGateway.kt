package com.seongho.brainassistant.core.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.seongho.brainassistant.core.model.RecurrenceMaster
import com.seongho.brainassistant.core.model.RecurrenceOccurrence
import java.time.Instant

class DeviceRecurrenceCalendarGateway(context: Context) : RecurrenceCalendarGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val targetResolver = CalendarTargetResolver(::loadCalendarTargets)

    override suspend fun upsertSeries(master: RecurrenceMaster, rrule: String, exdates: List<Instant>): RemoteSeries {
        val marker = seriesMarker(master.id)
        val existing = findEventId(marker)
        val values = seriesValues(master, rrule, exdates, marker)
        val id = if (existing != null) {
            resolver.update(CalendarContract.Events.CONTENT_URI, values, "${CalendarContract.Events._ID} = ?", arrayOf(existing))
            existing
        } else {
            requireNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment) { "캘린더 일정 저장에 실패했습니다." }
        }
        return RemoteSeries(id, Instant.now())
    }

    override suspend fun deleteSeries(calendarId: String, remoteSeriesId: String) {
        resolver.delete(CalendarContract.Events.CONTENT_URI, "${CalendarContract.Events._ID} = ?", arrayOf(remoteSeriesId))
    }

    override suspend fun upsertDetachedOccurrence(master: RecurrenceMaster, occurrence: RecurrenceOccurrence): RemoteOccurrence {
        val marker = detachedMarker(occurrence)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, targetResolver.resolve(master.googleCalendarId))
            put(CalendarContract.Events.TITLE, occurrence.title)
            put(CalendarContract.Events.DTSTART, occurrence.startAt.toEpochMilli())
            put(CalendarContract.Events.DTEND, occurrence.endAt.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, master.zoneId.id)
            put(CalendarContract.Events.DESCRIPTION, marker)
        }
        val existing = findEventId(marker)
        val id = if (existing != null) {
            resolver.update(CalendarContract.Events.CONTENT_URI, values, "${CalendarContract.Events._ID} = ?", arrayOf(existing))
            existing
        } else requireNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment)
        return RemoteOccurrence(id, Instant.now())
    }

    override suspend fun deleteDetachedOccurrence(calendarId: String, remoteEventId: String) {
        resolver.delete(CalendarContract.Events.CONTENT_URI, "${CalendarContract.Events._ID} = ?", arrayOf(remoteEventId))
    }

    private fun seriesValues(master: RecurrenceMaster, rrule: String, exdates: List<Instant>, marker: String) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, targetResolver.resolve(master.googleCalendarId))
        put(CalendarContract.Events.TITLE, master.title)
        put(CalendarContract.Events.DTSTART, master.startDate.atTime(master.startTime).atZone(master.zoneId).toInstant().toEpochMilli())
        put(CalendarContract.Events.DURATION, "PT${master.durationMinutes}M")
        put(CalendarContract.Events.EVENT_TIMEZONE, master.zoneId.id)
        put(CalendarContract.Events.RRULE, rrule)
        put(CalendarContract.Events.EXDATE, exdates.joinToString(",") { it.toString() })
        put(CalendarContract.Events.DESCRIPTION, marker)
    }

    private fun loadCalendarTargets(): List<CalendarTarget> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        return resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            buildList {
                val id = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val accountType = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val visible = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
                val access = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                while (cursor.moveToNext()) {
                    add(CalendarTarget(cursor.getLong(id), cursor.getString(accountType).orEmpty(), cursor.getInt(visible) == 1, cursor.getInt(access) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
                }
            }
        } ?: emptyList()
    }

    private fun findEventId(marker: String): String? = resolver.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events._ID),
        "${CalendarContract.Events.DESCRIPTION} = ?",
        arrayOf(marker),
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0).toString() else null }

    private fun seriesMarker(masterId: String) = "[brain-assistant-series:$masterId]"
    private fun detachedMarker(occurrence: RecurrenceOccurrence) = "[brain-assistant-occurrence:${occurrence.key.masterId}:${occurrence.key.originalStartAt.toEpochMilli()}]"
}
