# Brain Assistant App Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a clear adaptive launcher icon and reinstall the verified Debug APK on the connected Galaxy phone.

**Architecture:** Use Android vector drawables for the blue circular background and the white brain-and-check foreground mark. Reference both through the Android adaptive icon XML so every launcher density receives the same mark without raster assets.

**Tech Stack:** Android resource XML, VectorDrawable, adaptive icon API, Gradle, adb.

## Global Constraints

- Work only in `C:\tmp\mybrain-batch-20260801` on `feature/brain-assistant-batch`.
- Keep the icon text-free and inside the adaptive-icon safe zone.
- Do not modify the Google OAuth configuration or store secrets in source control.
- Build and install the Debug APK on device `R3CX10CP6YR`.

---

### Task 1: Add and validate the adaptive icon

**Files:**
- Create: `android-build/feature-overlay/app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `android-build/feature-overlay/app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `android-build/feature-overlay/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `android-build/feature-overlay/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `android-build/feature-overlay/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` launcher resources.
- Consumes: Android launcher `icon` and `roundIcon` manifest attributes.

- [ ] **Step 1: Write the failing resource check**

```powershell
Test-Path 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml'
```

Expected: `False` before the adaptive resource is added.

- [ ] **Step 2: Add minimal adaptive icon resources**

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Use a blue circular vector background and a white brain/check vector foreground with no text.

- [ ] **Step 3: Make the launcher use the new resources**

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round" />
```

- [ ] **Step 4: Build the Debug APK**

Run:

```powershell
gradle :app:assembleDebug --no-daemon --max-workers=1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install and inspect on device**

Run:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package resolve-activity --brief com.seongho.brainassistant.debug
```

Expected: installation succeeds and resolves `MainActivity`.

- [ ] **Step 6: Commit**

```powershell
git add android-build/feature-overlay/app/src/main/res android-build/feature-overlay/app/src/main/AndroidManifest.xml
git commit -m "feat: add Brain Assistant adaptive app icon"
```
