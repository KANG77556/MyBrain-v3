# Theme, Session, and Calendar Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users select the app theme, keep an approved Google connection across launches, and write saved schedules to the device Google Calendar immediately and reliably.

**Architecture:** Extend the existing DataStore-backed settings with a `ThemeMode` and a persisted session preference. Keep authentication state separate from OAuth secrets: it stores only the selected account display information and the `keepSignedIn` preference. Add a small WorkManager scheduler around the existing CalendarSyncWorker so the Room outbox is consumed immediately and periodically.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, DataStore Preferences, WorkManager, Room, Android Calendar Provider, JUnit, Compose UI test.

## Global Constraints

- Use the existing `feature/brain-assistant-batch` isolated worktree.
- Keep all app copy in Korean.
- Do not store OAuth access tokens, refresh tokens, client secrets, or account passwords.
- `ThemeMode.SYSTEM` is the default.
- `keepSignedIn` defaults to `true`; disabling it clears locally persisted session metadata and shows the connection screen on the next app launch.
- Saving a schedule must succeed locally even when Calendar Provider synchronization fails.
- Schedule CalendarSyncWorker immediately after a successful save and every 15 minutes while the app is installed.
- Verify each task with a RED test before production code, then run the listed verification command.

---

## File Structure

- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/settings/UserSettingsRepository.kt` — preference model and DataStore persistence.
- Create: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/auth/AuthSessionRepository.kt` — persisted non-secret sign-in state.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/auth/AuthViewModel.kt` — restore and clear persisted session state.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/settings/SettingsViewModel.kt` — expose and mutate theme and login-retention state.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/settings/SettingsScreen.kt` — Korean theme selector, login-retention switch, sync status, and manual-sync action.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/ui/theme/Theme.kt` — resolve system/light/dark choice.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/MainActivity.kt` — collect persisted settings and apply theme.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/CalendarSyncWorker.kt` — immediate and periodic scheduling API.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/BrainAssistantApp.kt` — initialize periodic Calendar sync.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt` — provide session repository and sync scheduler.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/capture/CaptureViewModel.kt` — enqueue immediate sync after persisted calendar changes.
- Modify: `android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/navigation/AppNavHost.kt` — inject the revised view models and route Settings actions.
- Create or modify tests under `android-build/feature-overlay/app/src/test/java/...` and `android-build/feature-overlay/app/src/androidTest/java/...` matching each component.

### Task 1: Persist theme and login-retention preferences

**Files:**
- Modify: `core/settings/UserSettingsRepository.kt`
- Test: `core/settings/DataStoreUserSettingsRepositoryTest.kt`

**Interfaces:**
- Produces `enum class ThemeMode { SYSTEM, LIGHT, DARK }`.
- Produces `UserSettings(themeMode: ThemeMode, keepSignedIn: Boolean)`.
- Produces `suspend fun setThemeMode(mode: ThemeMode)` and `suspend fun setKeepSignedIn(enabled: Boolean)`.

- [ ] **Step 1: Write failing persistence tests**

```kotlin
@Test fun defaultsUseSystemThemeAndKeepSignedIn() = runTest {
    assertEquals(ThemeMode.SYSTEM, repository.settings.first().themeMode)
    assertTrue(repository.settings.first().keepSignedIn)
}

@Test fun persistsThemeAndLoginRetention() = runTest {
    repository.setThemeMode(ThemeMode.DARK)
    repository.setKeepSignedIn(false)
    assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)
    assertFalse(repository.settings.first().keepSignedIn)
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `gradle :app:testDebugUnitTest --tests '*DataStoreUserSettingsRepositoryTest'`

Expected: compilation failure because `ThemeMode`, `themeMode`, and `setKeepSignedIn` do not exist.

- [ ] **Step 3: Add DataStore keys and mapping**

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val KEEP_SIGNED_IN = booleanPreferencesKey("keep_signed_in")

override suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[THEME_MODE] = mode.name }
override suspend fun setKeepSignedIn(enabled: Boolean) = dataStore.edit { it[KEEP_SIGNED_IN] = enabled }
```

Map a missing or invalid string to `ThemeMode.SYSTEM`; map a missing retention preference to `true`.

- [ ] **Step 4: Run focused test and confirm GREEN**

Run: `gradle :app:testDebugUnitTest --tests '*DataStoreUserSettingsRepositoryTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/settings/UserSettingsRepository.kt android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/settings/DataStoreUserSettingsRepositoryTest.kt
git commit -m "feat: persist theme and login retention settings"
```

### Task 2: Restore a non-secret Google session

**Files:**
- Create: `core/auth/AuthSessionRepository.kt`
- Modify: `feature/auth/AuthViewModel.kt`
- Test: `feature/auth/AuthViewModelTest.kt`

**Interfaces:**
- Produces `data class PersistedAuthSession(val email: String, val displayName: String)`.
- Produces `AuthSessionRepository.observe(): Flow<PersistedAuthSession?>`, `save(session)`, and `clear()`.
- Consumes `UserSettings.keepSignedIn`.

- [ ] **Step 1: Write failing restoration tests**

```kotlin
@Test fun restoresPersistedUserWhenRetentionIsEnabled() = runTest {
    sessionRepository.save(PersistedAuthSession("teacher@example.com", "교사"))
    val viewModel = AuthViewModel(gateway, sessionRepository, keepSignedIn = flowOf(true))
    assertTrue(viewModel.state.first { it.isSignedIn }.isSignedIn)
}

@Test fun disablingRetentionClearsSession() = runTest {
    viewModel.setKeepSignedIn(false)
    assertNull(sessionRepository.observe().first())
}
```

- [ ] **Step 2: Run focused test and confirm RED**

Run: `gradle :app:testDebugUnitTest --tests '*AuthViewModelTest'`

Expected: compilation failure because the session repository and retention action do not exist.

- [ ] **Step 3: Implement DataStore session persistence and ViewModel restore**

```kotlin
data class PersistedAuthSession(val email: String, val displayName: String)

fun setKeepSignedIn(enabled: Boolean) = viewModelScope.launch {
    settings.setKeepSignedIn(enabled)
    if (!enabled) sessionRepository.clear()
}
```

On successful `signIn`, save only `email` and `displayName` when retention is enabled. On startup restore only when retention is enabled; check Calendar Provider permission before treating the calendar as connected. `signOut` always clears the session repository.

- [ ] **Step 4: Run focused test and confirm GREEN**

Run: `gradle :app:testDebugUnitTest --tests '*AuthViewModelTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/auth/AuthSessionRepository.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/auth/AuthViewModel.kt android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/feature/auth/AuthViewModelTest.kt
git commit -m "feat: retain approved Google session locally"
```

### Task 3: Apply the selected theme and expose settings controls

**Files:**
- Modify: `ui/theme/Theme.kt`
- Modify: `MainActivity.kt`
- Modify: `feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/SettingsScreen.kt`
- Test: `ui/theme/ThemeModeTest.kt`
- Test: `feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces `fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean`.
- Produces `SettingsAction.SetThemeMode(mode: ThemeMode)` and `SettingsAction.SetKeepSignedIn(enabled: Boolean)`.

- [ ] **Step 1: Write failing theme and settings-action tests**

```kotlin
@Test fun lightModeOverridesDarkSystem() = assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemDark = true))
@Test fun darkModeOverridesLightSystem() = assertTrue(ThemeMode.DARK.resolveDarkTheme(systemDark = false))
@Test fun systemModeUsesSystemSetting() = assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemDark = true))
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `gradle :app:testDebugUnitTest --tests '*ThemeModeTest' --tests '*SettingsViewModelTest'`

Expected: compilation failure because the resolver and actions do not exist.

- [ ] **Step 3: Implement app-wide theme collection and settings controls**

```kotlin
@Composable
fun BrainAssistantTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
```

In `MainActivity`, collect `container.settings.settings` with lifecycle-aware Compose state and pass `themeMode`. Add a three-choice Korean selector and `로그인 유지` switch to `SettingsScreen`; wire both through `SettingsViewModel`.

- [ ] **Step 4: Run focused tests and a Compose screen test**

Run: `gradle :app:testDebugUnitTest --tests '*ThemeModeTest' --tests '*SettingsViewModelTest' :app:connectedDebugAndroidTest --tests '*SettingsScreenTest'`

Expected: PASS; selector and retention switch are visible and emit actions.

- [ ] **Step 5: Commit**

```bash
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/ui/theme/Theme.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/MainActivity.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/settings android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/ui/theme android-build/feature-overlay/app/src/androidTest/java/com/seongho/brainassistant/feature/settings
git commit -m "feat: add app theme and login retention controls"
```

### Task 4: Schedule reliable Google Calendar outbox synchronization

**Files:**
- Modify: `core/sync/CalendarSyncWorker.kt`
- Modify: `BrainAssistantApp.kt`
- Modify: `app/AppContainer.kt`
- Modify: `feature/capture/CaptureViewModel.kt`
- Modify: `navigation/AppNavHost.kt`
- Test: `core/sync/CalendarSyncSchedulerTest.kt`
- Test: `feature/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Produces `object CalendarSyncScheduler` with `enqueueNow(context: Context)` and `schedulePeriodic(context: Context)`.
- Consumes a successful calendar-bearing capture transaction.
- Produces a unique immediate WorkManager request and a unique 15-minute periodic request.

- [ ] **Step 1: Write failing scheduling tests**

```kotlin
@Test fun immediateRequestUsesUniqueCalendarSyncWork() {
    assertEquals("calendar-sync-now", CalendarSyncScheduler.immediateWorkName)
}

@Test fun autoSavedCalendarEnqueuesSync() = runTest {
    viewModel.onInputChanged("내일 오후 3시 상담")
    viewModel.submit()
    advanceUntilIdle()
    assertEquals(1, scheduler.enqueueCalls)
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `gradle :app:testDebugUnitTest --tests '*CalendarSyncSchedulerTest' --tests '*CaptureViewModelTest'`

Expected: compilation failure because the scheduler and capture dependency do not exist.

- [ ] **Step 3: Implement scheduler and inject it into capture**

```kotlin
fun enqueueNow(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork(
    immediateWorkName,
    ExistingWorkPolicy.KEEP,
    OneTimeWorkRequestBuilder<CalendarSyncWorker>().build(),
)

fun schedulePeriodic(context: Context) = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    periodicWorkName,
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES).build(),
)
```

Call `schedulePeriodic` from `BrainAssistantApp.onCreate`. Inject a `CalendarSyncTrigger` abstraction into `CaptureViewModel`; trigger it after `AutoSaved` and successful review confirmation only if the persisted items include a calendar item. Expose `SettingsAction.SyncNow` and invoke `enqueueNow` from the settings route.

- [ ] **Step 4: Run focused tests and inspect WorkManager**

Run: `gradle :app:testDebugUnitTest --tests '*CalendarSyncSchedulerTest' --tests '*CaptureViewModelTest'`

Expected: PASS.

On device run: `adb shell dumpsys jobscheduler | rg calendar-sync`

Expected: one immediate or periodic CalendarSyncWorker job is listed after saving a schedule.

- [ ] **Step 5: Commit**

```bash
git add android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/core/sync/CalendarSyncWorker.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/BrainAssistantApp.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/app/AppContainer.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/feature/capture/CaptureViewModel.kt android-build/feature-overlay/app/src/main/java/com/seongho/brainassistant/navigation/AppNavHost.kt android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/core/sync android-build/feature-overlay/app/src/test/java/com/seongho/brainassistant/feature/capture
git commit -m "feat: sync saved schedules to Google Calendar"
```

### Task 5: End-to-end verification and handoff

**Files:**
- Modify: `docs/CODEX_HANDOFF.md`

- [ ] **Step 1: Add the end-to-end acceptance checklist**

```text
1. Choose dark mode, restart, and confirm the app remains dark.
2. Turn off login retention, restart, and confirm the connection screen appears.
3. Turn on login retention, sign in, restart, and confirm dashboard opens without account selection.
4. Save a dated event and confirm it appears in the device Google Calendar.
5. Disable Calendar permission, save a dated event, restore permission, run sync now, and confirm it appears.
```

- [ ] **Step 2: Run all automated verification**

Run: `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest`

Expected: BUILD SUCCESSFUL with all connected tests passing.

- [ ] **Step 3: Install and perform the device checks**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: install succeeds; complete the acceptance checklist on the connected device.

- [ ] **Step 4: Commit the verified handoff**

```bash
git add docs/CODEX_HANDOFF.md
git commit -m "docs: record theme and calendar sync verification"
```
