# Recurrence Google Calendar Target Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save recurring schedules to a writable Google calendar rather than the device-local calendar.

**Architecture:** Extract a small calendar-target resolver used by the recurrence gateway. It preserves an explicit numeric calendar ID, otherwise queries visible writable Google calendars and throws a connection error instead of using local ID `1`.

**Tech Stack:** Kotlin, Android Calendar Provider, JUnit, Gradle, adb.

## Global Constraints

- Never use a local calendar as a fallback for a Google sync request.
- Use the existing `feature/brain-assistant-batch` worktree.
- Build and install the Debug APK on `R3CX10CP6YR`.

---

### Task 1: Resolve a writable Google calendar for recurring schedules

**Files:**
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar/DeviceRecurrenceCalendarGateway.kt`
- Create: `android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/calendar/CalendarTargetResolverTest.kt`

**Interfaces:**
- Produces: `CalendarTargetResolver.resolve(requestedCalendarId: String): Long`.
- Consumes: a requested numeric ID or a visible writable Google calendar.

- [ ] **Step 1: Write the failing target-selection test**

```kotlin
@Test fun blankTargetUsesWritableGoogleCalendarInsteadOfLocalIdOne() {
    val resolver = CalendarTargetResolver { listOf(CalendarTarget(1, "LOCAL"), CalendarTarget(42, "com.google")) }
    assertEquals(42L, resolver.resolve(""))
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `gradle :app:testDebugUnitTest --tests '*CalendarTargetResolverTest'`

Expected: unresolved `CalendarTargetResolver`.

- [ ] **Step 3: Add resolver and use it for series and detached occurrences**

```kotlin
put(CalendarContract.Events.CALENDAR_ID, targetResolver.resolve(master.googleCalendarId))
```

The provider query accepts only visible `com.google` calendars with contributor access or better.

- [ ] **Step 4: Run focused test and build**

Run: `gradle :app:testDebugUnitTest --tests '*CalendarTargetResolverTest' :app:assembleDebug --no-daemon --max-workers=1`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install and verify device permissions**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: installation succeeds and Calendar read/write permissions remain granted.

- [ ] **Step 6: Commit**

```powershell
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/calendar android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/calendar
git commit -m "fix: target Google calendar for recurring schedules"
```
