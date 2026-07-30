from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
activity = ROOT / "app/src/main/java/kr/co/mybrain/ai/RedesignedMainActivity.java"
manifest = ROOT / "app/src/main/AndroidManifest.xml"
launcher = ROOT / "app/src/main/java/kr/co/mybrain/ai/NotificationPermissionActivity.java"

assert activity.exists(), "RedesignedMainActivity.java가 아직 없습니다."
text = activity.read_text(encoding="utf-8")

required = [
    "오늘 요약",
    "빠른 작업",
    "전체 기록",
    "D-Day",
    "제출",
    "홈",
    "캘린더",
    "설정",
    "화면 테마",
    "시스템 설정 따르기",
    "밝은 모드",
    "어두운 모드",
]
for marker in required:
    assert marker in text, f"필수 UI 문구 누락: {marker}"

manifest_text = manifest.read_text(encoding="utf-8")
assert '.RedesignedMainActivity' in manifest_text, "Manifest에 새 메인 화면이 등록되지 않았습니다."
launcher_text = launcher.read_text(encoding="utf-8")
assert 'RedesignedMainActivity.class' in launcher_text, "런처가 새 메인 화면을 열지 않습니다."
assert 'MainWorkspaceActivityV3.class' not in launcher_text, "기존 패치 화면이 런처에 남아 있습니다."

print("Redesigned home validation passed")
