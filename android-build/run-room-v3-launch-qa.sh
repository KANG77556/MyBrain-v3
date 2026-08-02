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

adb exec-out screencap -p > "$OUTPUT_DIR/device-onboarding.png"
adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/onboarding-ui.xml" || {
  sleep 3
  adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/onboarding-ui.xml"
}

read -r TAP_X TAP_Y < <(
  python3 - "$OUTPUT_DIR/onboarding-ui.xml" <<'PY'
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1])
text = path.read_text(errors="replace")
end = text.index("</hierarchy>") + len("</hierarchy>")
root = ET.fromstring(text[:end])
target = next(
    node for node in root.iter("node")
    if node.attrib.get("text") == "캘린더 없이 로컬 모드로 시작"
)
x1, y1, x2, y2 = map(int, re.findall(r"\d+", target.attrib["bounds"]))
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)

printf '%s,%s\n' "$TAP_X" "$TAP_Y" | tee "$OUTPUT_DIR/local-mode-tap.txt"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 6

adb shell pidof -s "$PACKAGE_NAME" | tee "$OUTPUT_DIR/dashboard-pid.txt"
test -s "$OUTPUT_DIR/dashboard-pid.txt"
adb exec-out screencap -p > "$OUTPUT_DIR/device-home.png"
adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/device-ui.xml" || {
  sleep 3
  adb exec-out uiautomator dump /dev/tty > "$OUTPUT_DIR/device-ui.xml"
}

if grep -q '캘린더 없이 로컬 모드로 시작' "$OUTPUT_DIR/device-ui.xml"; then
  echo '로컬 모드 진입 후에도 시작 화면이 남아 있습니다.'
  exit 1
fi

adb shell dumpsys window windows > "$OUTPUT_DIR/window-state.txt"
adb logcat -d > "$OUTPUT_DIR/device-logcat.txt"
adb logcat -b crash -d > "$OUTPUT_DIR/device-crash.txt"

if grep -Eq 'FATAL EXCEPTION|Process: com\.seongho\.brainassistant\.debug' "$OUTPUT_DIR/device-crash.txt"; then
  echo '앱 크래시가 감지되었습니다.'
  cat "$OUTPUT_DIR/device-crash.txt"
  exit 1
fi

printf 'INSTALL=PASS\nLAUNCH=PASS\nPROCESS_ALIVE=PASS\nLOCAL_MODE_DASHBOARD=PASS\nCRASH_BUFFER=CLEAR\n' | tee "$OUTPUT_DIR/device-qa-summary.txt"
