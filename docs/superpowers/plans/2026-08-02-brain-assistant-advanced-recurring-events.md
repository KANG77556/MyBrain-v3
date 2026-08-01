# Brain Assistant Advanced Recurring Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse advanced Korean recurring-event sentences, preview every occurrence with holiday and school-calendar exclusions, save the series atomically in Room, synchronize it through Android Calendar Provider, and support scoped edits plus integrated Undo.

**Architecture:** Room remains the local source of truth. A recurrence master stores the rule, exception rows store only changed/cancelled/moved occurrences, and a pure `RecurrenceEngine` generates occurrences for requested ranges. Android Calendar Provider is isolated behind recurrence and exclusion gateways; all local mutations write recurrence Outbox and Undo records in the same Room transaction.

**Tech Stack:** Kotlin, Java Time, Kotlin coroutines/Flow, Room, Jetpack Compose Material 3, WorkManager, Android `CalendarContract`, JUnit, AndroidX Test, Compose UI Test, Gradle/JDK 17.

## Global Constraints

- Work only in the isolated `feature/brain-assistant-batch` worktree; never commit generated `brain-assistant/`, `brain-assistant-source.tar.xz`, APKs, or unrelated untracked artifacts.
- The tracked implementation source is `android-build/feature-overlay/`; copy it over the reconstructed `brain-assistant/` tree only for local builds.
- Use `Asia/Seoul` for parsing, recurrence calculation, day boundaries, and previews.
- Every recurring input requires batch review even when confidence is at least `0.85`.
- Supported rules: daily, weekdays, weekends, selected weekdays, every n weeks, monthly date, monthly nth/last weekday, yearly date, end date, and final occurrence count.
- An occurrence count means the number remaining after exclusions and moves.
- Korean public holidays are discovered automatically from device calendars and cached by year; school exclusions use only user-selected Google calendars and user-approved events.
- Each series stores one overlap policy: `SKIP` or `MOVE_TO_NEXT_WEEKDAY`.
- Edit and delete always require `THIS_OCCURRENCE`, `THIS_AND_FOLLOWING`, or `ALL_OCCURRENCES`.
- Room master, exceptions, Undo snapshot, and recurrence Outbox writes must share one transaction.
- Google synchronization uses Android Calendar Provider; no new OAuth screen or secret API key is introduced.
- Keep current one-off event, task, note, D-Day, dashboard, and calendar behavior compatible.
- Start every task with a failing test, run the narrow test to observe RED, add the minimum production code, rerun GREEN, then commit.

## Source Map

Create focused files instead of extending `Models.kt`, `Daos.kt`, or `ReviewScreen.kt` indefinitely:

- `core/model/RecurrenceModels.kt`: domain types, commands, occurrence keys, preview/result contracts.
- `core/recurrence/RecurrenceEngine.kt`: pure recurrence generation and exception/exclusion application.
- `core/parser/KoreanRecurrenceParser.kt`: Korean phrase extraction into `RecurrenceDraft`.
- `core/database/RecurrenceEntities.kt`: Room v3 recurrence, exclusion, normalized Undo snapshot, and Outbox rows.
- `core/database/RecurrenceDaos.kt`: recurrence-specific DAO interfaces.
- `core/database/RecurrenceMappers.kt`: domain/entity conversion only.
- `core/calendar/ExclusionCalendarGateway.kt`: exclusion source discovery and date loading contract.
- `core/calendar/DeviceCalendarExclusionGateway.kt`: `CalendarContract` implementation.
- `core/calendar/RecurrenceCalendarGateway.kt`: remote series/instance synchronization contract.
- `core/calendar/DeviceRecurrenceCalendarGateway.kt`: RRULE/EXDATE/provider implementation.
- `core/sync/RecurrenceSyncWorker.kt`: recurrence Outbox processor and WorkManager adapter.
- `feature/review/RecurrenceReviewContent.kt`: recurring preview and editable rule UI.
- `feature/calendar/RecurrenceScopeSheet.kt`: mandatory edit/delete scope selector.
- `feature/settings/ExclusionCalendarScreen.kt`: school calendar and exclusion candidate selection.

Existing files modified by the feature:

- `core/model/Models.kt`, `core/parser/HybridInputAnalyzer.kt`, `core/parser/RuleBasedInputAnalyzer.kt`
- `core/database/AppDatabase.kt`, `core/database/Migrations.kt`
- `data/BrainRepository.kt`, `data/RoomBrainRepository.kt`, `data/CaptureUseCase.kt`
- `core/calendar/CalendarGateway.kt`, `core/calendar/GoogleCalendarGateway.kt`
- `core/sync/CalendarSyncWorker.kt`, `core/sync/CalendarPullSyncWorker.kt`
- `app/AppContainer.kt`
- `feature/review/ReviewViewModel.kt`, `feature/review/ReviewScreen.kt`
- `feature/calendar/CalendarViewModel.kt`, `feature/calendar/CalendarScreen.kt`
- `feature/settings/SettingsScreen.kt`, `navigation/AppNavHost.kt`
- `.github/workflows/brain-assistant-extended.yml`

---

### Task 1: Recurrence domain contract

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/model/RecurrenceModels.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/model/RecurrenceModelsTest.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/model/Models.kt`

**Interfaces:**
- Consumes: `SyncState`, `ParsedItem`, `AnalysisResult`, `ParsedBatch`.
- Produces: `RecurrenceRule`, `RecurrenceDraft`, `RecurrenceMaster`, `RecurrenceException`, `RecurrenceOccurrence`, `RecurrenceMutation`, and optional `recurrences` fields on parser results.

- [ ] **Step 1: Write the failing model tests**

```kotlin
@Test fun countMeansAcceptedOccurrences() {
    val end = RecurrenceEnd.Count(10)
    assertEquals(10, end.occurrences)
}

@Test fun occurrenceIdentityUsesOriginalStart() {
    val key = OccurrenceKey("series-1", Instant.parse("2026-08-03T00:00:00Z"))
    val moved = key.copy()
    assertEquals(key, moved)
}
```

- [ ] **Step 2: Run the model test and verify RED**

Run from reconstructed source after copying the overlay:

```powershell
Copy-Item -Recurse -Force ..\android-build\feature-overlay\* .
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceModelsTest'
```

Expected: compilation fails because the recurrence domain types do not exist.

- [ ] **Step 3: Add the exact domain types**

```kotlin
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class ExclusionKind { KOREAN_PUBLIC_HOLIDAY, SCHOOL_CALENDAR }
enum class ExclusionPolicy { SKIP, MOVE_TO_NEXT_WEEKDAY }
enum class RecurrenceExceptionKind { MODIFIED, CANCELLED, MOVED }
enum class RecurrenceScope { THIS_OCCURRENCE, THIS_AND_FOLLOWING, ALL_OCCURRENCES }

sealed interface RecurrenceEnd {
    data class Until(val date: LocalDate) : RecurrenceEnd
    data class Count(val occurrences: Int) : RecurrenceEnd
    data object Never : RecurrenceEnd
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val ordinal: Int? = null,
    val ordinalWeekday: DayOfWeek? = null,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
)

data class RecurrenceDraft(
    val localId: String = UUID.randomUUID().toString(),
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
    val rule: RecurrenceRule,
    val exclusionKinds: Set<ExclusionKind> = emptySet(),
    val exclusionPolicy: ExclusionPolicy = ExclusionPolicy.SKIP,
    val confidence: Double,
)

data class RecurrenceMaster(
    val id: String,
    val inputId: String,
    val transactionId: String,
    val title: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val zoneId: ZoneId,
    val rule: RecurrenceRule,
    val exclusionKinds: Set<ExclusionKind>,
    val exclusionPolicy: ExclusionPolicy,
    val googleCalendarId: String = "primary",
    val remoteSeriesId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val deletedAt: Instant? = null,
    val updatedAt: Instant,
)

data class OccurrenceKey(val masterId: String, val originalStartAt: Instant)

data class RecurrenceException(
    val id: String,
    val key: OccurrenceKey,
    val kind: RecurrenceExceptionKind,
    val effectiveStartAt: Instant? = null,
    val effectiveEndAt: Instant? = null,
    val titleOverride: String? = null,
    val remoteEventId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
)

data class RecurrenceOccurrence(
    val key: OccurrenceKey,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val kind: RecurrenceExceptionKind? = null,
    val exclusionReason: String? = null,
    val conflictReason: String? = null,
)

data class ExclusionSource(
    val id: String,
    val calendarId: String,
    val displayName: String,
    val kind: ExclusionKind,
    val enabled: Boolean,
)

data class ExclusionDate(
    val sourceId: String,
    val remoteEventId: String,
    val date: LocalDate,
    val title: String,
    val approved: Boolean,
)

enum class RecurrenceMutationKind { UPDATE, DELETE }

data class RecurrenceMutation(
    val key: OccurrenceKey,
    val scope: RecurrenceScope,
    val kind: RecurrenceMutationKind,
    val replacement: RecurrenceDraft? = null,
)

data class RecurrenceCommit(
    val operationId: String,
    val affectedMasterIds: List<String>,
    val remoteSyncPending: Boolean,
)

data class PersistedBatch(
    val transactionId: String,
    val ordinaryItems: PersistedItems,
    val recurrenceCommit: RecurrenceCommit?,
)
```

Add `recurrences: List<RecurrenceDraft> = emptyList()` to `AnalysisResult` and `ParsedBatch` so current analyzers remain source-compatible.

- [ ] **Step 4: Add validation tests and implementation**

Test that interval, duration, count, day-of-month, and ordinal are positive and that monthly ordinal requires a weekday. Add `require(...)` checks in `init` blocks with stable Korean error messages.

- [ ] **Step 5: Run the narrow model suite and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceModelsTest'
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/model android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/model
git commit -m "feat: define recurring schedule domain"
```

Expected: all `RecurrenceModelsTest` tests pass.

---

### Task 2: Pure recurrence generation engine

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/recurrence/RecurrenceEngine.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/recurrence/RecurrenceEngineTest.kt`

**Interfaces:**
- Consumes: `RecurrenceMaster`, `RecurrenceException`, `ExclusionDate`, `ClosedRange<LocalDate>`.
- Produces: `fun generate(master, exceptions, exclusions, range): List<RecurrenceOccurrence>`.

Private helpers have these exact signatures:

```kotlin
private fun candidateDates(master: RecurrenceMaster): Sequence<LocalDate>
private fun moveToAvailableWeekday(date: LocalDate, exclusions: Set<LocalDate>): LocalDate
private fun isWithinEnd(end: RecurrenceEnd, acceptedCount: Int, date: LocalDate): Boolean
private fun applyException(occurrence: RecurrenceOccurrence, exceptions: Map<OccurrenceKey, RecurrenceException>): RecurrenceOccurrence?
```

- [ ] **Step 1: Write RED tests for core frequencies**

Cover daily, weekdays, selected weekdays, biweekly, monthly day, monthly last Friday, yearly date, leap year, and missing monthly day. Use a fixed `Asia/Seoul` master and assert exact `LocalDate` lists.

```kotlin
@Test fun weekdaysWithinNextWeekProduceFiveOccurrences() {
    val dates = engine.generate(master(weekdays = DayOfWeek.entries.take(5).toSet()), emptyList(), emptySet(), LocalDate.parse("2026-08-03")..LocalDate.parse("2026-08-07"))
        .map { it.startAt.atZone(SEOUL).toLocalDate() }
    assertEquals((3L..7L).map { LocalDate.of(2026, 8, it.toInt()) }, dates)
}
```

- [ ] **Step 2: Run the engine tests and observe RED**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceEngineTest'
```

Expected: missing `RecurrenceEngine` compilation failure.

- [ ] **Step 3: Add candidate generation**

Create `RecurrenceEngine.generate` with one internal candidate sequence per frequency. Weekly rules anchor interval calculation to the Monday of `master.startDate`; monthly ordinal uses `TemporalAdjusters.dayOfWeekInMonth` or `lastInMonth`; nonexistent day-of-month candidates are skipped. A private `isWithinEnd(end, acceptedCount, date)` function handles `Until`, `Count`, and `Never`; do not add an undefined method to `RecurrenceEnd`.

```kotlin
class RecurrenceEngine {
    fun generate(
        master: RecurrenceMaster,
        exceptions: List<RecurrenceException>,
        exclusions: Set<LocalDate>,
        range: ClosedRange<LocalDate>,
    ): List<RecurrenceOccurrence> {
        val exceptionMap = exceptions.associateBy(RecurrenceException::key)
        val accepted = mutableListOf<RecurrenceOccurrence>()
        for (originalDate in candidateDates(master)) {
            if (master.rule.end is RecurrenceEnd.Until && originalDate > master.rule.end.date) break
            val effectiveDate = when {
                originalDate !in exclusions -> originalDate
                master.exclusionPolicy == ExclusionPolicy.SKIP -> continue
                else -> moveToAvailableWeekday(originalDate.plusDays(1), exclusions)
            }
            if (master.rule.end is RecurrenceEnd.Until && effectiveDate > master.rule.end.date) continue
            if (master.rule.end is RecurrenceEnd.Count && accepted.size >= master.rule.end.occurrences) break
            val originalStart = originalDate.atTime(master.startTime).atZone(master.zoneId).toInstant()
            val effectiveStart = effectiveDate.atTime(master.startTime).atZone(master.zoneId).toInstant()
            val occurrence = RecurrenceOccurrence(
                key = OccurrenceKey(master.id, originalStart),
                title = master.title,
                startAt = effectiveStart,
                endAt = effectiveStart.plus(Duration.ofMinutes(master.durationMinutes.toLong())),
                kind = if (effectiveDate == originalDate) null else RecurrenceExceptionKind.MOVED,
            )
            applyException(occurrence, exceptionMap)?.let(accepted::add)
        }
        return accepted.filter { it.startAt.atZone(master.zoneId).toLocalDate() in range }
            .sortedBy(RecurrenceOccurrence::startAt)
    }
}
```

- [ ] **Step 4: Write RED tests for exclusions, moves, exceptions, and count semantics**

Assert that holidays are skipped, consecutive exclusions move to the next available weekday, moves beyond `Until` are dropped, modified/cancelled exceptions use `originalStartAt`, and `Count(10)` returns ten accepted occurrences after exclusions.

- [ ] **Step 5: Implement policy and exception application**

Keep original and effective starts separately. Detect a collision when two occurrences resolve to the same time; retain both and set `conflictReason = "반복 일정 이동 결과가 겹칩니다."`.

- [ ] **Step 6: Run the full engine suite and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceEngineTest'
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/recurrence android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/recurrence
git commit -m "feat: generate recurring schedule occurrences"
```

---

### Task 3: Korean recurring-language parser and mandatory review

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/parser/KoreanRecurrenceParser.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/parser/KoreanRecurrenceParserTest.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/parser/RuleBasedInputAnalyzer.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/parser/HybridInputAnalyzer.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data/CaptureUseCase.kt`
- Modify: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/parser/RuleBasedInputAnalyzerTest.kt`
- Modify: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/data/CaptureUseCaseTest.kt`

**Interfaces:**
- Consumes: `AnalysisRequest` and recurrence domain types.
- Produces: `fun parse(text: String, reference: ZonedDateTime): RecurrenceDraft?`; `CaptureResult.NeedsReview.recurrences`.

- [ ] **Step 1: Write exact RED parser cases**

Use fixed reference `2026-08-02T12:00+09:00` and assert:

```kotlin
@Test fun parsesNextMondayThroughFridayTimeRange() {
    val draft = requireNotNull(parser.parse("다음주 월요일부터 금요일까지 9시부터 12시까지 방과후수업", reference))
    assertEquals(LocalDate.of(2026, 8, 3), draft.startDate)
    assertEquals(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), draft.rule.weekdays)
    assertEquals(LocalTime.of(9, 0), draft.startTime)
    assertEquals(180, draft.durationMinutes)
    assertEquals(RecurrenceEnd.Until(LocalDate.of(2026, 8, 7)), draft.rule.end)
}
```

Add cases for `매주 월수금`, `격주 화요일`, `매월 15일`, `매월 마지막 금요일`, `매년 8월 15일`, `10회`, `다음 달 말까지`, `공휴일과 방학 제외`, and `다음 평일로 이동`.

- [ ] **Step 2: Run the parser tests and observe RED**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*KoreanRecurrenceParserTest'
```

- [ ] **Step 3: Implement deterministic phrase extraction**

Keep patterns small and ordered: time range, termination, exclusions/policy, frequency, weekday selection, and clean title. Return `null` when no recurrence marker exists so the current one-off parser continues unchanged.

```kotlin
class KoreanRecurrenceParser {
    fun parse(text: String, reference: ZonedDateTime): RecurrenceDraft? {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val schedule = parseSchedule(normalized, reference.toLocalDate()) ?: return null
        val times = parseTimeRange(normalized) ?: return null
        return RecurrenceDraft(
            title = cleanRecurringTitle(normalized),
            startDate = schedule.start,
            startTime = times.first,
            durationMinutes = Duration.between(times.first, times.second).toMinutes().toInt(),
            rule = schedule.rule,
            exclusionKinds = parseExclusions(normalized),
            exclusionPolicy = if (normalized.contains("다음 평일")) ExclusionPolicy.MOVE_TO_NEXT_WEEKDAY else ExclusionPolicy.SKIP,
            confidence = 0.90,
        )
    }
}
```

- [ ] **Step 4: Integrate without duplicating one-off events**

Call `KoreanRecurrenceParser` before `parseWeekdayRangeEvents`. When it returns a draft, put it in `AnalysisResult.recurrences` and do not emit expanded `ParsedItem` events. Preserve the existing result when no recurrence is found. `HybridInputAnalyzer` must retain local recurrence drafts even if remote analysis wins ordinary items.

- [ ] **Step 5: Force recurring input to review**

Add `recurrences: List<RecurrenceDraft> = emptyList()` to `CaptureResult.NeedsReview`. In `CaptureUseCase`, any non-empty recurrence list bypasses auto-save and returns the review result with message `반복 일정의 전체 날짜를 확인해 주세요.`.

- [ ] **Step 6: Run parser, capture, and current regression suites; commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*KoreanRecurrenceParserTest' --tests '*RuleBasedInputAnalyzerTest' --tests '*CaptureUseCaseTest'
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/parser android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data/CaptureUseCase.kt android-build/feature-overlay/app/src/test
git commit -m "feat: parse Korean recurring schedules"
```

---

### Task 4: Room v3 recurrence schema and migration

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database/RecurrenceEntities.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database/RecurrenceDaos.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database/RecurrenceMappers.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database/AppDatabase.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database/Migrations.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt`
- Create: `android-build/feature-overlay/app/schemas/com.seongho.brainassistant.core.database.AppDatabase/2.json`
- Create: `android-build/feature-overlay/app/schemas/com.seongho.brainassistant.core.database.AppDatabase/3.json`
- Create: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/core/database/Migration2To3Test.kt`
- Create: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/core/database/RecurrenceDaoTest.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/database/RecurrenceMapperTest.kt`

**Interfaces:**
- Consumes: Task 1 domain types.
- Produces: eight v3 tables and DAOs for masters, exceptions, exclusion sources/dates, normalized Undo operation/master/exception snapshots, and recurrence Outbox.

- [ ] **Step 1: Write RED mapper and DAO tests**

Assert round-trip preservation of weekdays, `RecurrenceEnd`, original occurrence start, school calendar IDs, Undo payload, and Outbox operation. DAO tests must query masters overlapping a date range and replace cache rows per source/year.

- [ ] **Step 2: Write the RED 2→3 migration test**

Create a v2 database with existing input, calendar, D-Day, and widget rows. Run migration and assert those rows remain plus these tables:

```text
recurrence_masters
recurrence_exceptions
exclusion_sources
exclusion_dates
recurrence_undo_operations
recurrence_undo_master_snapshots
recurrence_undo_exception_snapshots
recurrence_outbox
```

- [ ] **Step 3: Add entities and focused DAOs**

Use normalized scalar columns rather than serialized Java objects. Store weekday sets as stable ISO numbers (`1,3,5`), dates as epoch days, and instants as epoch milliseconds. Store Undo data in an operation header plus child master/exception snapshot rows keyed by `operationId`; do not serialize domain snapshots into one opaque text column.

Use this stable Outbox operation enum in both the entity mapper and sync engine:

```kotlin
enum class RecurrenceOutboxOperation {
    UPSERT_SERIES,
    DELETE_SERIES,
    UPSERT_DETACHED_OCCURRENCE,
    DELETE_DETACHED_OCCURRENCE,
}
```

```kotlin
@Entity(tableName = "recurrence_exceptions", indices = [Index(value = ["masterId", "originalStartEpochMs"], unique = true)])
data class RecurrenceExceptionEntity(
    @PrimaryKey val id: String,
    val masterId: String,
    val originalStartEpochMs: Long,
    val kind: String,
    val effectiveStartEpochMs: Long?,
    val effectiveEndEpochMs: Long?,
    val remoteEventId: String?,
    val syncState: String,
)
```

- [ ] **Step 4: Add `MIGRATION_2_3` and register version 3**

Create every table and index explicitly, update `AppDatabase(version = 3)`, expose the new DAOs, and register `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` in `AppContainer`.

Generate schema 3 with the Room schema export task, copy the reconstructed source's verified schema 2 plus generated schema 3 into the overlay schema directory, and run `git diff --check` on both JSON files before staging.

- [ ] **Step 5: Run mapper tests and build the Android test APK**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceMapperTest' :app:assembleDebugAndroidTest
```

- [ ] **Step 6: Run migration/DAO tests on an API 28 target and commit**

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.seongho.brainassistant.core.database
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/database android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt android-build/feature-overlay/app/src/test android-build/feature-overlay/app/src/androidTest
git commit -m "feat: persist recurring schedules in Room"
```

Expected: v1→v2 and v2→v3 migration paths pass, including preservation assertions.

---

### Task 5: Atomic repository mutations and integrated Undo

**Files:**
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data/BrainRepository.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data/RoomBrainRepository.kt`
- Modify: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/testing/FakeBrainRepository.kt`
- Create: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/core/database/RecurrenceRepositoryTransactionTest.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/data/RecurrenceMutationPolicyTest.kt`

**Interfaces:**
- Consumes: recurrence DAOs, `RecurrenceMutation`, existing parsed-item transaction.
- Produces: `confirmReviewedBatch`, `mutateRecurrence`, `undoRecurrence`, and range observation APIs.

- [ ] **Step 1: Write RED transaction tests**

Inject a failing DAO after the master insert and assert zero master, exception, Undo, and Outbox rows remain. Add success assertions for mixed ordinary items plus one recurrence in a single transaction.

- [ ] **Step 2: Define repository signatures**

```kotlin
suspend fun confirmReviewedBatch(
    inputId: String,
    items: List<ParsedItem>,
    recurrences: List<RecurrenceDraft>,
): PersistedBatch

suspend fun mutateRecurrence(command: RecurrenceMutation): RecurrenceCommit
suspend fun undoRecurrence(operationId: String): RecurrenceCommit
fun observeRecurringOccurrences(start: Instant, end: Instant): Flow<List<RecurrenceOccurrence>>
```

- [ ] **Step 3: Add atomic creation and deterministic occurrence queries**

Move the body of existing `saveParsedItems` into a private transaction-aware helper so `confirmReviewedBatch` inserts normal items and recurrence data under one outer `database.withTransaction`. Enqueue one `UPSERT_SERIES` row per new master and save a versioned before/after Undo snapshot.

Generate the approved preview again inside the transaction and persist every policy-derived moved occurrence as a `MOVED` exception keyed by its original start. Skipped occurrences remain derivable from the exclusion cache and do not need exception rows.

- [ ] **Step 4: Write RED scope mutation tests**

Assert:

- `THIS_OCCURRENCE` creates one modified or cancelled exception.
- `THIS_AND_FOLLOWING` ends the old master immediately before the selected original occurrence and creates a new master starting there.
- `ALL_OCCURRENCES` updates one master and reports orphaned exceptions for review.
- delete uses the same three scopes.

- [ ] **Step 5: Implement mutations and Undo**

Use one `when(command.scope)` transaction. For series split, move exceptions whose `originalStartAt` is at or after the split to the new master. Undo restores all affected rows from the snapshot and enqueues inverse remote operations without waiting for network access.

- [ ] **Step 6: Run repository tests and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceMutationPolicyTest' :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.seongho.brainassistant.core.database.RecurrenceRepositoryTransactionTest
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data android-build/feature-overlay/app/src/test android-build/feature-overlay/app/src/androidTest
git commit -m "feat: mutate recurring schedules atomically"
```

---

### Task 6: Public-holiday and school-calendar exclusion cache

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/ExclusionCalendarGateway.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/DeviceCalendarExclusionGateway.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/ExclusionRefreshWorker.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/settings/ExclusionCalendarScreen.kt`
- Modify: `android-build/dashboard-overlay/app/src/main/java/com/seongho/brainassistant/feature/settings/SettingsScreen.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/navigation/AppNavHost.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/calendar/ExclusionCalendarClassifierTest.kt`
- Create: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/feature/settings/ExclusionCalendarScreenTest.kt`

**Interfaces:**
- Consumes: exclusion DAOs and `CalendarContract` read permission.
- Produces: discoverable sources, yearly cached exclusion dates, and settings selection UI.

- [ ] **Step 1: Write RED classification tests**

```kotlin
@Test fun detectsKoreanHolidayCalendarNames() {
    assertTrue(classifier.isKoreanHoliday("대한민국의 공휴일"))
    assertTrue(classifier.isKoreanHoliday("Holidays in South Korea"))
    assertFalse(classifier.isKoreanHoliday("교직원 회의"))
}

@Test fun schoolCandidatesRequireKnownCategory() {
    assertEquals(SCHOOL_EVENT, classifier.classify("여름방학"))
    assertNull(classifier.classify("수학과 협의회"))
}
```

- [ ] **Step 2: Define the gateway and fake**

```kotlin
interface ExclusionCalendarGateway {
    suspend fun discoverSources(): List<ExclusionSource>
    suspend fun loadDates(source: ExclusionSource, year: Int): List<ExclusionDate>
}
```

Use a fake gateway for JVM/worker tests. The device implementation queries visible Google calendars, automatically enables names classified as Korean public-holiday calendars, and queries `CalendarContract.Instances` for the requested year.

- [ ] **Step 3: Normalize all-day and multi-day entries**

Expand `[begin, end)` into one `LocalDate` row per day in `Asia/Seoul`. Public-holiday rows become approved immediately. School rows remain candidates until the user approves them.

- [ ] **Step 4: Add refresh scheduling and offline behavior**

`ExclusionRefreshWorker` refreshes the current and next year, replaces one source/year cache only after a complete successful query, and retains prior rows on errors. Schedule unique daily work with network not required because Calendar Provider may already have local data; also refresh immediately after calendar permission or source selection.

After a successful cache replacement, recalculate affected masters in the refreshed year. Add, update, or remove only policy-derived `MOVED` exceptions, preserve user-created `MODIFIED`/`CANCELLED` exceptions, and enqueue one `UPSERT_SERIES` operation for each changed master.

- [ ] **Step 5: Add the selection/review screen**

Show school Google calendars with checkboxes and new candidate events grouped under `방학`, `재량휴업`, and `학교행사`. Saving writes enabled source IDs and approved event IDs; ordinary events never become exclusions automatically.

- [ ] **Step 6: Run classifier/UI tests and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*ExclusionCalendarClassifierTest' :app:assembleDebugAndroidTest
git add android-build/feature-overlay android-build/dashboard-overlay
git commit -m "feat: cache holiday and school exclusions"
```

---

### Task 7: Recurrence synchronization through Calendar Provider

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/RecurrenceCalendarGateway.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/DeviceRecurrenceCalendarGateway.kt`
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/RecurrenceSyncWorker.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/CalendarGateway.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/GoogleCalendarGateway.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/CalendarPullSyncWorker.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/calendar/RecurrenceRRuleMapperTest.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/sync/RecurrenceSyncEngineTest.kt`

**Interfaces:**
- Consumes: recurrence Outbox and master/exception snapshots.
- Produces: RRULE/EXDATE mapping, idempotent series operations, retryable sync summary.

- [ ] **Step 1: Write RED RRULE tests**

Assert exact values such as:

```text
FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR;UNTIL=20260831T145959Z
FREQ=MONTHLY;BYDAY=-1FR
FREQ=YEARLY;BYMONTH=8;BYMONTHDAY=15
```

Verify finite count plus exclusions maps to computed `UNTIL` and `EXDATE`, never both `COUNT` and `UNTIL`.

- [ ] **Step 2: Define provider-safe gateway operations**

```kotlin
interface RecurrenceCalendarGateway {
    suspend fun upsertSeries(master: RecurrenceMaster, rrule: String, exdates: List<Instant>): RemoteSeries
    suspend fun deleteSeries(calendarId: String, remoteSeriesId: String)
    suspend fun upsertDetachedOccurrence(master: RecurrenceMaster, occurrence: RecurrenceOccurrence): RemoteOccurrence
    suspend fun deleteDetachedOccurrence(calendarId: String, remoteEventId: String)
}

data class RemoteSeries(val id: String, val updatedAt: Instant)
data class RemoteOccurrence(val id: String, val updatedAt: Instant)
```

Calendar Provider realizes a one-occurrence modification by adding the original start to master EXDATE and inserting/updating one standalone event marked with the local occurrence key in its description. A one-occurrence cancellation adds EXDATE only. A following-scope mutation updates old UNTIL and creates a new series.

- [ ] **Step 3: Implement `ContentValues` and idempotency**

Write `RRULE`, `EXDATE`, `DTSTART`, `DURATION`, `EVENT_TIMEZONE`, and a stable local marker. Before insert, query for that marker to avoid duplicate series after worker retries. Keep one-off gateway behavior unchanged.

- [ ] **Step 4: Write RED sync engine tests**

With fake repository/gateway, assert create, update, scoped exception, split, delete, authorization failure, generic retry, duplicate retry, and Undo inverse operations.

- [ ] **Step 5: Implement recurrence Outbox processing and pull mapping**

Process rows in creation order. Delete a row only after the gateway result and local remote-ID update are committed. Pull recurring masters and detached events without importing generated provider instances as duplicate one-off events.

- [ ] **Step 6: Run gateway/sync tests and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceRRuleMapperTest' --tests '*RecurrenceSyncEngineTest'
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app android-build/feature-overlay/app/src/test
git commit -m "feat: sync recurring schedules to device calendar"
```

---

### Task 8: Batch recurrence review UI

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/review/RecurrenceReviewContent.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/review/ReviewViewModel.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/review/ReviewScreen.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/navigation/AppNavHost.kt`
- Modify: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/feature/review/ReviewScreenTest.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/feature/review/RecurrenceReviewViewModelTest.kt`

**Interfaces:**
- Consumes: `CaptureResult.NeedsReview.recurrences`, recurrence engine, exclusion cache.
- Produces: editable recurrence review state, exact occurrence preview, atomic save action.

- [ ] **Step 1: Write RED ViewModel tests**

Assert the sample input shows five dates, duration `3시간`, expected count, exclusion labels, conflict rows, and disabled save when title/time/rule is invalid. Assert changing `SKIP` to `MOVE_TO_NEXT_WEEKDAY` recalculates immediately.

- [ ] **Step 2: Add recurrence review state/actions**

```kotlin
data class RecurrenceReviewUi(
    val draft: RecurrenceDraft,
    val occurrences: List<OccurrencePreviewUi>,
    val expectedCount: Int,
    val warnings: List<String>,
)

data class OccurrencePreviewUi(
    val key: OccurrenceKey,
    val dateLabel: String,
    val timeLabel: String,
    val statusLabel: String?,
    val warning: String?,
)

sealed interface ReviewAction {
    data class ChangeRecurrence(val localId: String, val draft: RecurrenceDraft) : ReviewAction
    data class ChangeExclusionPolicy(val localId: String, val policy: ExclusionPolicy) : ReviewAction
    data object Save : ReviewAction
}
```

- [ ] **Step 3: Use `ReviewViewModel` as the single owner**

Remove duplicated review mutation logic from `AppNavHost`; instantiate `ReviewViewModel` with the pending result, collect state/events, and call `repository.confirmReviewedBatch` once. Preserve ordinary item cards beside recurrence cards.

- [ ] **Step 4: Add responsive Compose content**

Mobile uses one column. At tablet width, display source/rule on the left and occurrence list on the right. Show original text, rule summary, date range, time, exclusion sources, policy toggle, expected count, every date, moved/excluded markers, and conflicts. Keep `취소 / 전체 저장` in the scaffold bottom bar.

- [ ] **Step 5: Run ViewModel and Compose tests; commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*RecurrenceReviewViewModelTest' :app:assembleDebugAndroidTest
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/review android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/navigation android-build/feature-overlay/app/src/test android-build/feature-overlay/app/src/androidTest
git commit -m "feat: review recurring schedule batches"
```

---

### Task 9: Calendar occurrence display and mandatory scope selection

**Files:**
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/calendar/RecurrenceScopeSheet.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/calendar/CalendarViewModel.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/calendar/CalendarScreen.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data/RoomBrainRepository.kt`
- Modify: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/feature/calendar/CalendarViewPolicyTest.kt`
- Modify: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/feature/calendar/CalendarScreenTest.kt`

**Interfaces:**
- Consumes: generated `RecurrenceOccurrence` flow and `mutateRecurrence`.
- Produces: merged calendar agenda and scope-gated edit/delete actions.

- [ ] **Step 1: Write RED merge and scope tests**

Assert one-off events and generated occurrences are sorted together without duplicates. Tapping edit/delete on a recurring occurrence must expose exactly `이번 일정만`, `이 일정부터 이후`, and `전체 반복 일정`; no repository mutation occurs before a choice.

- [ ] **Step 2: Merge recurrence occurrences into repository range results**

Map occurrences to calendar UI rows with a stable `OccurrenceKey`, recurrence badge, effective time, original time, movement reason, sync state, and conflict reason. Keep excluded occurrences hidden from the normal agenda and expose them from series detail.

- [ ] **Step 3: Add ViewModel pending command state**

```kotlin
data class PendingRecurrenceAction(
    val key: OccurrenceKey,
    val action: RecurrenceActionKind,
)

enum class RecurrenceActionKind { EDIT, DELETE }

sealed interface CalendarAction {
    data class RequestRecurrenceEdit(val key: OccurrenceKey) : CalendarAction
    data class RequestRecurrenceDelete(val key: OccurrenceKey) : CalendarAction
    data class ConfirmScope(val scope: RecurrenceScope) : CalendarAction
    data object DismissScope : CalendarAction
}
```

- [ ] **Step 4: Render the scope sheet and Undo feedback**

After a mutation succeeds, show one snackbar action `실행 취소`. Trigger `undoRecurrence(operationId)` and show `Google 복원 대기` when inverse remote work remains pending.

- [ ] **Step 5: Run calendar tests and commit**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests '*CalendarViewPolicyTest' :app:assembleDebugAndroidTest
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/calendar android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/data android-build/feature-overlay/app/src/test android-build/feature-overlay/app/src/androidTest
git commit -m "feat: edit recurring occurrences by scope"
```

---

### Task 10: Offline recovery, conflicts, and end-to-end regression

**Files:**
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt`
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/CalendarSyncWorker.kt`
- Create: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/e2e/RecurringScheduleE2ETest.kt`
- Modify: `android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/e2e/OfflineRecoveryE2ETest.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/sync/RecurrenceConflictTest.kt`

**Interfaces:**
- Consumes: all feature components.
- Produces: automatic worker scheduling and fixed regression evidence.

- [ ] **Step 1: Write RED offline/conflict tests**

Assert local save remains visible when the gateway fails, recurrence Outbox survives restart, retry does not duplicate a series, a remote deletion does not automatically delete the local master, and simultaneous local/remote edits set `CONFLICT` with both snapshots retained.

- [ ] **Step 2: Add unique WorkManager scheduling**

Schedule immediate recurrence sync after transaction commit, periodic sync every 15 minutes, exclusion refresh daily, and startup recovery for pending recurrence Outbox. Use unique work names so repeated app launches do not multiply workers.

- [ ] **Step 3: Add the exact E2E happy path**

Automate:

1. Enter `다음주 월요일부터 금요일까지 9시부터 12시까지 방과후수업`.
2. Verify review shows five dates and `09:00~12:00`.
3. Save once.
4. Verify calendar shows five occurrences.
5. Delete Wednesday with `이번 일정만`.
6. Verify four visible occurrences.
7. Undo and verify all five return.

- [ ] **Step 4: Add exclusion E2E with fake cached dates**

Seed Wednesday as a holiday. Verify `SKIP` shows four final dates and `MOVE_TO_NEXT_WEEKDAY` shows five accepted occurrences with the moved date and conflict warning when appropriate.

- [ ] **Step 5: Run all JVM and instrumented suites; commit**

```powershell
.\gradlew.bat --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleDebug
.\gradlew.bat --no-daemon --stacktrace :app:connectedDebugAndroidTest
git add android-build/feature-overlay
git commit -m "test: cover recurring schedule recovery"
```

Expected: zero failed tests; debug and Android test APKs exist.

---

### Task 11: CI, connected Galaxy verification, and deliverable evidence

**Files:**
- Modify: `.github/workflows/brain-assistant-extended.yml`
- Create: `docs/superpowers/verification/2026-08-02-advanced-recurring-events.md`

**Interfaces:**
- Consumes: complete feature branch.
- Produces: reproducible CI, APK artifact, SHA-256, test counts, and real-device evidence.

- [ ] **Step 1: Write the CI workflow change and validate trigger scope**

Add `feature/brain-assistant-batch` to push branches. Keep source reconstruction order unchanged. Run JVM tests, Android test APK compilation, Debug APK, Lint, and the full API 28 connected suite. Add artifact hashing and upload:

```yaml
- name: Verify APK and record SHA-256
  working-directory: brain-assistant
  run: sha256sum app/build/outputs/apk/debug/app-debug.apk | tee app-debug.sha256

- name: Upload recurring schedule debug APK
  uses: actions/upload-artifact@v4
  with:
    name: Brain-Assistant-Recurring-debug
    path: |
      brain-assistant/app/build/outputs/apk/debug/app-debug.apk
      brain-assistant/app-debug.sha256
```

- [ ] **Step 2: Run local final verification from a clean reconstructed tree**

Reconstruct the carrier source, apply baseline patch, dashboard overlay, then feature overlay exactly as CI does. Run:

```powershell
.\gradlew.bat --no-daemon --stacktrace :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:assembleDebug
```

Record executed test counts and verify failures are zero.

- [ ] **Step 3: Install and validate on the connected Galaxy**

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.seongho.brainassistant.debug
adb shell monkey -p com.seongho.brainassistant.debug -c android.intent.category.LAUNCHER 1
```

Run the exact Korean sentence flow, capture `uiautomator dump`, screenshots, and filtered logcat. Verify the five dates, 09:00–12:00 range, save, calendar display, scoped delete, Undo, and absence of `FATAL EXCEPTION`/`ANR`.

- [ ] **Step 4: Commit workflow and verification record**

The verification document must contain exact commands, JVM/instrumented test counts, device model/API, APK path, SHA-256, and any limitation. Do not claim Google remote synchronization unless a writable device Google calendar is actually observed.

```powershell
git add .github/workflows/brain-assistant-extended.yml docs/superpowers/verification/2026-08-02-advanced-recurring-events.md
git commit -m "ci: verify advanced recurring schedules"
```

- [ ] **Step 5: Push and verify GitHub Actions**

```powershell
git push -u origin feature/brain-assistant-batch
gh run list --workflow "Brain Assistant Extended Features" --branch feature/brain-assistant-batch --limit 3
gh run watch <run-id> --exit-status
```

Expected: successful workflow with Debug APK artifact. Update the verification record with the real run ID and artifact hash in a final evidence-only commit if they were not known at Step 4.

## Final Acceptance Checklist

- [ ] The exact failing user sentence produces one reviewed recurrence with five occurrences, not five unrelated parsed events.
- [ ] Daily, weekday/weekend, weekly selected days, biweekly, monthly, nth/last weekday, yearly, end date, and accepted-count rules pass.
- [ ] Korean holiday cache and approved school exclusions work offline.
- [ ] `SKIP` and `MOVE_TO_NEXT_WEEKDAY` are stored per series and produce deterministic previews.
- [ ] New series, exceptions, split-series mutations, deletion, and Undo are atomic.
- [ ] `THIS_OCCURRENCE`, `THIS_AND_FOLLOWING`, and `ALL_OCCURRENCES` are mandatory and consistent locally and in Calendar Provider.
- [ ] Retry is idempotent; remote conflicts never overwrite local data silently.
- [ ] Current note/task/D-Day/one-off event tests remain green.
- [ ] JVM, Lint, Debug APK, Android test APK, API 28 connected tests, and Galaxy flow are verified with exact evidence.
