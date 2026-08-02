# Advanced recurring schedules verification

Date: 2026-08-02 (Asia/Seoul)

## Local verification

Reconstructed source was refreshed from `android-build/feature-overlay` and verified with Gradle 9.5 / JDK 17:

```powershell
gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

- JVM: 121 tests, 0 failures, 0 errors
- Android Lint: passed
- Debug APK: built
- Android test APK: built

## Connected device

- Device: Samsung Galaxy S24 Ultra (`SM-S928N`), Android API 36
- APK installed and launcher activity started through ADB.
- UI hierarchy dump was collected.
- Filtered recent logcat did not contain `FATAL EXCEPTION` or `ANR` for `com.seongho.brainassistant`.

## APK evidence

- APK: `brain-assistant/app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `61AF6B4208CF212D0CC483770F8B4736997FD808B0BEEEC306E88C7BE2AA1036`

## Limitations

- The locally connected handset is API 36. The API 28 database suite is delegated to GitHub Actions.
- Google Calendar Provider synchronization code is compiled and locally retry-tested with fakes. No writable personal Google calendar was altered during verification.
