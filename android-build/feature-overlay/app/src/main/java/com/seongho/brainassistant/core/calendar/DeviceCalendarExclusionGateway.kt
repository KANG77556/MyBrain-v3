package com.seongho.brainassistant.core.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.seongho.brainassistant.core.model.ExclusionDate
import com.seongho.brainassistant.core.model.ExclusionKind
import com.seongho.brainassistant.core.model.ExclusionSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DeviceCalendarSource(
    val calendarId: String,
    val displayName: String,
    val accountType: String,
    val visible: Boolean,
)

interface CalendarProviderReader {
    suspend fun listCalendars(): List<DeviceCalendarSource>
    suspend fun listEvents(calendarId: String, year: Int): List<CalendarExclusionEvent>
}

class DeviceCalendarExclusionGateway(
    private val reader: CalendarProviderReader,
    private val classifier: ExclusionCalendarClassifier = ExclusionCalendarClassifier(),
    private val normalizer: ExclusionDateNormalizer = ExclusionDateNormalizer(),
) : ExclusionCalendarGateway {
    constructor(context: Context) : this(AndroidCalendarProviderReader(context.applicationContext))

    override suspend fun discoverSources(): List<ExclusionSource> = reader.listCalendars()
        .asSequence()
        .filter(DeviceCalendarSource::visible)
        .filter { it.accountType.equals(GOOGLE_ACCOUNT_TYPE, ignoreCase = true) }
        .mapNotNull { calendar ->
            val isHoliday = classifier.isKoreanHoliday(calendar.displayName)
            if (!isHoliday && !classifier.isSchoolCalendarCandidate(calendar.displayName)) return@mapNotNull null
            ExclusionSource(
                id = "device:${calendar.calendarId}",
                calendarId = calendar.calendarId,
                displayName = calendar.displayName,
                kind = if (isHoliday) ExclusionKind.KOREAN_PUBLIC_HOLIDAY else ExclusionKind.SCHOOL_CALENDAR,
                enabled = isHoliday,
            )
        }
        .sortedWith(compareBy<ExclusionSource> { it.kind != ExclusionKind.KOREAN_PUBLIC_HOLIDAY }.thenBy { it.displayName })
        .toList()

    override suspend fun loadDates(source: ExclusionSource, year: Int): List<ExclusionDate> =
        reader.listEvents(source.calendarId, year)
            .asSequence()
            .filter { event ->
                source.kind == ExclusionKind.KOREAN_PUBLIC_HOLIDAY ||
                    classifier.classifySchoolEvent(event.title) != null
            }
            .flatMap { event -> normalizer.normalize(source.id, source.kind, event).asSequence() }
            .filter { it.date.year == year }
            .distinctBy { Triple(it.remoteEventId, it.date, it.sourceId) }
            .sortedWith(compareBy<ExclusionDate> { it.date }.thenBy { it.remoteEventId })
            .toList()

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

class AndroidCalendarProviderReader(
    private val context: Context,
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : CalendarProviderReader {
    override suspend fun listCalendars(): List<DeviceCalendarSource> {
        requireReadPermission()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DeviceCalendarSource(
                            calendarId = cursor.getLong(0).toString(),
                            displayName = cursor.getString(1).orEmpty(),
                            accountType = cursor.getString(2).orEmpty(),
                            visible = cursor.getInt(3) != 0,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    override suspend fun listEvents(calendarId: String, year: Int): List<CalendarExclusionEvent> {
        requireReadPermission()
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toEpochMilli().toString())
            .appendPath(end.toEpochMilli().toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        return context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.CALENDAR_ID} = ?",
            arrayOf(calendarId),
            CalendarContract.Instances.BEGIN,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val begin = Instant.ofEpochMilli(cursor.getLong(2))
                    val endExclusive = Instant.ofEpochMilli(cursor.getLong(3))
                    if (begin.isBefore(endExclusive)) {
                        add(
                            CalendarExclusionEvent(
                                remoteEventId = cursor.getLong(0).toString(),
                                title = cursor.getString(1).orEmpty(),
                                begin = begin,
                                endExclusive = endExclusive,
                            ),
                        )
                    }
                }
            }
        }.orEmpty()
    }

    private fun requireReadPermission() {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED,
        ) { "Calendar read permission is required" }
    }
}
