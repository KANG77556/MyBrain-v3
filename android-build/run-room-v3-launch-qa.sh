#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${1:-qa-input/app-debug.apk}"
PACKAGE_NAME="com.seongho.brainassistant.debug"
OUTPUT_DIR="qa-output"

mkdir -p "$OUTPUT_DIR"

test -f "$APK_PATH"
sha256sum "$APK_PATH" | tee "$OUTPUT_DIR/app-debug.apk.sha256"
adb install -r "$APK_PATH" | tee "$OUTPUT_DIR/install-result.txt"

adb shell am force-stop "$PACKAGE_NAME"
adb logcat -c

ACTIVITY="$(adb shell cmd package resolve-activity --brief "$PACKAGE_NAME" | tr -d '\r' | grep '/' | tail -n 1)"
test -n "$ACTIVITY"
printf '%s\n' "$ACTIVITY" | tee "$OUTPUT_DIR/resolved-activity.txt"

adb shell am start -W -n "$ACTIVITY" | tee "$OUTPUT_DIR/launch-result.txt"
sleep 8

adb shell pidof -s "$PACKAGE_NAME" | tee "$OUTPUT_DIR/app-pid.txt"
test -s "$OUTPUT_DIR/app-pid.txt"

adb exec-out screencap -p > "$OUTPUT_DIR/device-home.png"
adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/device-ui.xml" || {
  sleep 3
  adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/device-ui.xml"
}
adb shell dumpsys window windows > "$OUTPUT_DIR/window-state.txt"
adb logcat -d > "$OUTPUT_DIR/device-logcat.txt"
adb logcat -b crash -d > "$OUTPUT_DIR/device-crash.txt"

if grep -Eq 'FATAL EXCEPTION|Process: com\.seongho\.brainassistant\.debug' "$OUTPUT_DIR/device-crash.txt"; then
  echo '앱 크래시가 감지되었습니다.'
  cat "$OUTPUT_DIR/device-crash.txt"
  exit 1
fi

printf 'INSTALL=PASS\nLAUNCH=PASS\nPROCESS_ALIVE=PASS\nCRASH_BUFFER=CLEAR\n' | tee "$OUTPUT_DIR/device-qa-summary.txt"
