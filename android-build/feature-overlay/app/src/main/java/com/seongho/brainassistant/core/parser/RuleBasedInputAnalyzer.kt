package com.seongho.brainassistant.core.parser

import com.seongho.brainassistant.core.model.AnalysisRequest
import com.seongho.brainassistant.core.model.AnalysisResult
import com.seongho.brainassistant.core.model.ClarificationField
import com.seongho.brainassistant.core.model.ItemType
import com.seongho.brainassistant.core.model.ParsedItem
import com.seongho.brainassistant.core.model.ParsedBatch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class RuleBasedInputAnalyzer : InputAnalyzer {
    private val recurrenceParser = KoreanRecurrenceParser()

    suspend fun analyzeBatch(request: AnalysisRequest): ParsedBatch {
        val result = analyze(request)
        val batchId = result.items.firstOrNull()?.batchId ?: java.util.UUID.randomUUID().toString()
        return ParsedBatch(
            id = batchId,
            originalText = request.rawText,
            items = result.items,
            requiresReview = result.items.isEmpty() ||
                result.recurrences.isNotEmpty() ||
                result.confidence < AUTO_SAVE_CONFIDENCE ||
                result.clarificationFields.isNotEmpty(),
            recurrences = result.recurrences,
        )
    }

    override suspend fun analyze(request: AnalysisRequest): AnalysisResult {
        val normalized = request.rawText.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) {
            return AnalysisResult(
                items = emptyList(),
                confidence = 0.0,
                clarificationFields = setOf(ClarificationField.TITLE),
                analyzer = ANALYZER_NAME,
            )
        }

        recurrenceParser.parse(normalized, request.referenceTime)?.let { recurrence ->
            return AnalysisResult(
                items = emptyList(),
                confidence = recurrence.confidence,
                clarificationFields = emptySet(),
                analyzer = ANALYZER_NAME,
                recurrences = listOf(recurrence),
            )
        }

        val vagueDate = VAGUE_DATE_WORDS.any(normalized::contains)
        val batchId = java.util.UUID.randomUUID().toString()
        val sourceSegments = splitBatchSegments(request.rawText)
        val parsedSegments = sourceSegments.flatMap { segment ->
            parseSegments(segment.text.trim().replace(Regex("\\s+"), " "), request.referenceTime, request.zoneId)
                .map { parsed ->
                    parsed.copy(
                        item = parsed.item.copy(
                            batchId = batchId,
                            sourceStart = segment.start,
                            sourceEnd = segment.end,
                        ),
                    )
                }
        }.mapIndexed { index, parsed ->
            parsed.copy(item = parsed.item.copy(batchIndex = index))
        }
        val items = parsedSegments.map(ParsedSegment::item)
        val missing = buildSet {
            if (vagueDate) add(ClarificationField.DATE)
            if (items.isEmpty()) add(ClarificationField.TITLE)
            parsedSegments.forEach { addAll(it.clarificationFields) }
        }
        val confidence = when {
            items.isEmpty() -> 0.20
            missing.isNotEmpty() -> 0.60
            else -> 0.88
        }
        return AnalysisResult(
            items = items,
            confidence = confidence,
            clarificationFields = missing,
            analyzer = ANALYZER_NAME,
        )
    }

    private fun parseSegment(
        segment: String,
        reference: ZonedDateTime,
        zone: ZoneId,
    ): ParsedSegment? {
        val date = parseDate(segment, reference.toLocalDate())
        val time = parseTime(segment)
        val looksLikeDDay = DDAY_PATTERN.containsMatchIn(segment)
        val looksLikeNote = NOTE_WORDS.any(segment::contains)
        val looksLikeEvent = EVENT_WORDS.any(segment::contains) && !segment.contains("전에")
        val looksLikeTask = TASK_WORDS.any(segment::contains) || segment.contains("전에") || segment.contains("까지")
        val title = cleanTitle(segment)
        if (title.isBlank()) return null

        return when {
            looksLikeDDay -> {
                val dueAt = date
                    ?.atTime(23, 59, 59)
                    ?.atZone(zone)
                    ?.toInstant()
                ParsedSegment(
                    item = ParsedItem(
                        type = ItemType.D_DAY,
                        title = title,
                        targetDate = date,
                        dueAt = dueAt,
                        priority = if (segment.contains("긴급")) 3 else 2,
                        estimatedMinutes = estimateMinutes(segment),
                    ),
                    clarificationFields = if (date == null) {
                        setOf(ClarificationField.DATE)
                    } else {
                        emptySet()
                    },
                )
            }

            looksLikeNote -> ParsedSegment(
                item = ParsedItem(
                    type = ItemType.NOTE,
                    title = title,
                    body = segment,
                    priority = 1,
                ),
            )

            looksLikeEvent -> {
                val start = when {
                    date != null && time != null -> date.atTime(time).atZone(zone).toInstant()
                    date != null -> date.atTime(DEFAULT_EVENT_TIME).atZone(zone).toInstant()
                    time != null -> nextOccurrence(reference, time).toInstant()
                    else -> null
                }
                ParsedSegment(
                    item = ParsedItem(
                        type = ItemType.EVENT,
                        title = title,
                        startAt = start,
                        endAt = start?.plus(Duration.ofHours(1)),
                        priority = 2,
                    ),
                    clarificationFields = buildSet {
                        if (date == null) add(ClarificationField.DATE)
                        if (time == null) add(ClarificationField.TIME)
                    },
                )
            }

            looksLikeTask -> ParsedSegment(
                item = ParsedItem(
                    type = ItemType.TASK,
                    title = title,
                    dueAt = date
                        ?.atTime(23, 59, 59)
                        ?.atZone(zone)
                        ?.toInstant(),
                    priority = if (segment.contains("긴급") || segment.contains("내일까지")) 3 else 2,
                    estimatedMinutes = estimateMinutes(segment),
                ),
            )

            else -> ParsedSegment(
                item = ParsedItem(
                    type = ItemType.NOTE,
                    title = title,
                    body = segment,
                    priority = 1,
                ),
            )
        }
    }

    private fun parseSegments(
        segment: String,
        reference: ZonedDateTime,
        zone: ZoneId,
    ): List<ParsedSegment> = parseWeekdayRangeEvents(segment, reference, zone)
        ?: listOfNotNull(parseSegment(segment, reference, zone))

    private fun parseWeekdayRangeEvents(
        segment: String,
        reference: ZonedDateTime,
        zone: ZoneId,
    ): List<ParsedSegment>? {
        if (!EVENT_WORDS.any(segment::contains)) return null
        val dateRange = WEEKDAY_RANGE_PATTERN.find(segment) ?: return null
        val timeRange = TIME_RANGE_PATTERN.find(segment) ?: return null
        val startDay = WEEKDAYS.getValue("${dateRange.groupValues[1]}요일")
        val endDay = WEEKDAYS.getValue("${dateRange.groupValues[2]}요일")
        if (endDay.value < startDay.value) return null

        val startMarker = timeRange.groupValues[1]
        val startTime = rangeTime(startMarker, timeRange.groupValues[2], timeRange.groupValues[3]) ?: return null
        val endTime = rangeTime(
            timeRange.groupValues[4].ifBlank { startMarker },
            timeRange.groupValues[5],
            timeRange.groupValues[6],
        ) ?: return null
        if (!endTime.isAfter(startTime)) return null

        val nextMonday = reference.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val title = cleanTitle(segment)
        return (startDay.value..endDay.value).map { dayValue ->
            val date = nextMonday.plusDays((dayValue - DayOfWeek.MONDAY.value).toLong())
            ParsedSegment(
                item = ParsedItem(
                    type = ItemType.EVENT,
                    title = title,
                    startAt = date.atTime(startTime).atZone(zone).toInstant(),
                    endAt = date.atTime(endTime).atZone(zone).toInstant(),
                    priority = 2,
                ),
            )
        }
    }

    private fun rangeTime(marker: String, hour: String, minute: String): LocalTime? {
        var parsedHour = hour.toIntOrNull() ?: return null
        if (parsedHour !in 0..23) return null
        val parsedMinute = minute.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        if (marker == "오후" && parsedHour in 1..11) parsedHour += 12
        if (marker == "오전" && parsedHour == 12) parsedHour = 0
        return LocalTime.of(parsedHour, parsedMinute)
    }

    private fun parseDate(text: String, reference: LocalDate): LocalDate? {
        if (text.contains("오늘")) return reference
        if (text.contains("모레")) return reference.plusDays(2)
        if (text.contains("내일")) return reference.plusDays(1)

        MONTH_DAY_PATTERN.find(text)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            return runCatching {
                var date = LocalDate.of(reference.year, month, day)
                if (date.isBefore(reference)) date = date.plusYears(1)
                date
            }.getOrNull()
        }

        val dayOfWeek = WEEKDAYS.entries.firstOrNull { text.contains(it.key) }?.value
        if (dayOfWeek != null) {
            return if (text.contains("다음 주")) {
                reference
                    .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(dayOfWeek))
            } else {
                reference.with(TemporalAdjusters.nextOrSame(dayOfWeek)).let { candidate ->
                    if (candidate == reference) reference.plusWeeks(1) else candidate
                }
            }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val numeric = NUMERIC_TIME_PATTERN.find(text)
        if (numeric != null) {
            val marker = numeric.groupValues[1]
            var hour = numeric.groupValues[2].toInt().coerceIn(0, 23)
            val minute = numeric.groupValues[3].ifBlank { "0" }.toInt().coerceIn(0, 59)
            if (marker == "오후" && hour in 1..11) hour += 12
            if (marker == "오전" && hour == 12) hour = 0
            return LocalTime.of(hour, minute)
        }

        val wordHour = KOREAN_HOURS.entries.firstOrNull { entry ->
            text.contains("${entry.key} 시") || text.contains("${entry.key}시")
        }?.value
        if (wordHour != null) {
            val hour = if (text.contains("오후") && wordHour < 12) wordHour + 12 else wordHour
            return LocalTime.of(hour, 0)
        }
        return null
    }

    private fun cleanTitle(text: String): String {
        var value = text
            .replace(WEEKDAY_RANGE_PATTERN, "")
            .replace(TIME_RANGE_PATTERN, "")
            .replace(Regex("다음 주\\s*[월화수목금토일]요일(?:까지|에)?"), "")
            .replace(Regex("(오늘|내일|모레)(?:까지|에)?"), "")
            .replace(Regex("\\d{1,2}월\\s*\\d{1,2}일(?:까지|에)?"), "")
            .replace(Regex("[월화수목금토일]요일(?:까지|에)?"), "")
            .replace(Regex("(오전|오후)?\\s*\\d{1,2}시(?:에|까지)?(?:\\s*\\d{1,2}분)?"), "")
            .replace(Regex("(오전|오후)?\\s*[한두세네다섯여섯일곱여덟아홉열]\\s*시(?:에|까지)?"), "")
            .replace(DDAY_PATTERN, "")
            .replace("다음 주쯤", "")
            .replace("쯤", "")
            .replace("상담 전에", "")
            .replace(Regex("\\s*(하고|넣고|추가하고)$"), "")
            .replace(Regex("\\s*(해야 해|해야함|해줘|잡아줘|등록해줘|기억해줘)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.')
        value = value.replace(Regex("^(까지|에|에서)\\s*"), "")
        return value.ifBlank { text.trim() }
    }

    private fun nextOccurrence(reference: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = reference.toLocalDate().atTime(time).atZone(reference.zone)
        return if (today.isAfter(reference)) today else today.plusDays(1)
    }

    private fun estimateMinutes(text: String): Int? =
        Regex("(\\d+)분").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun splitBatchSegments(rawText: String): List<SourceSegment> {
        val segments = mutableListOf<SourceSegment>()
        var start = 0
        BATCH_SEPARATOR.findAll(rawText).forEach { match ->
            addSourceSegment(rawText, start, match.range.first, segments)
            start = match.range.last + 1
        }
        addSourceSegment(rawText, start, rawText.length, segments)
        return segments.ifEmpty {
            listOf(SourceSegment(rawText.trim(), rawText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0), rawText.trimEnd().length))
        }
    }

    private fun addSourceSegment(
        rawText: String,
        rawStart: Int,
        rawEnd: Int,
        destination: MutableList<SourceSegment>,
    ) {
        var start = rawStart
        var end = rawEnd
        while (start < end && rawText[start].isWhitespace()) start++
        while (end > start && (rawText[end - 1].isWhitespace() || rawText[end - 1] == '.')) end--
        if (start < end) destination += SourceSegment(rawText.substring(start, end), start, end)
    }

    private data class ParsedSegment(
        val item: ParsedItem,
        val clarificationFields: Set<ClarificationField> = emptySet(),
    )

    private data class SourceSegment(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private companion object {
        const val ANALYZER_NAME = "RULE_BASED_V1"
        const val AUTO_SAVE_CONFIDENCE = 0.85
        val BATCH_SEPARATOR = Regex("[,，;]+|\\.(?=\\s|$)|\\s+그리고\\s+|(?<=하고)\\s+")
        val DEFAULT_EVENT_TIME: LocalTime = LocalTime.of(9, 0)
        val VAGUE_DATE_WORDS = listOf("쯤", "언젠가", "시간 될 때", "나중에")
        val EVENT_WORDS = listOf("상담", "회의", "예약", "병원", "수업", "약속", "검사", "면담")
        val TASK_WORDS = listOf("할 일", "채점", "제출", "확인", "작성", "준비", "처리", "검토", "해야")
        val NOTE_WORDS = listOf("메모해줘", "메모로", "메모해")
        val MONTH_DAY_PATTERN = Regex("(\\d{1,2})월\\s*(\\d{1,2})일")
        val NUMERIC_TIME_PATTERN = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?")
        val WEEKDAY_RANGE_PATTERN = Regex("다음\\s*주\\s*([월화수목금토일])요일\\s*부터\\s*([월화수목금토일])요일\\s*까지")
        val TIME_RANGE_PATTERN = Regex("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?\\s*부터\\s*(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?\\s*까지")
        val DDAY_PATTERN = Regex("(?i)D\\s*-?\\s*(?:DAY|데이)|디데이")
        val WEEKDAYS = mapOf(
            "월요일" to DayOfWeek.MONDAY,
            "화요일" to DayOfWeek.TUESDAY,
            "수요일" to DayOfWeek.WEDNESDAY,
            "목요일" to DayOfWeek.THURSDAY,
            "금요일" to DayOfWeek.FRIDAY,
            "토요일" to DayOfWeek.SATURDAY,
            "일요일" to DayOfWeek.SUNDAY,
        )
        val KOREAN_HOURS = mapOf(
            "한" to 1,
            "두" to 2,
            "세" to 3,
            "네" to 4,
            "다섯" to 5,
            "여섯" to 6,
            "일곱" to 7,
            "여덟" to 8,
            "아홉" to 9,
            "열" to 10,
        )
    }
}
