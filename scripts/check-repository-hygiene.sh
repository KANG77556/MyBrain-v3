#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

failures=0
secret_scan_file="$(mktemp)"
trap 'rm -f "$secret_scan_file"' EXIT

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  failures=$((failures + 1))
}

sensitive_files="$(git ls-files | grep -E '(^|/)[^/]+\.(jks|keystore|p12|pfx)(\.b64)?$' || true)"
if [[ -n "$sensitive_files" ]]; then
  printf '%s\n' "$sensitive_files" >&2
  fail '서명키 또는 인증서 파일이 Git 추적 대상에 남아 있습니다.'
fi

legacy_debug_password="$(printf '%s%s' 'mybrain-debug-' 'only')"
if git grep -n -F "$legacy_debug_password" -- . >"$secret_scan_file" 2>/dev/null; then
  cat "$secret_scan_file" >&2
  fail '폐기된 개발 서명 비밀번호가 Git 추적 텍스트에 남아 있습니다.'
fi

private_key_headers="$(git grep -n -I -E -- '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----' -- . || true)"
if [[ -n "$private_key_headers" ]]; then
  printf '%s\n' "$private_key_headers" >&2
  fail 'PEM 개인키 헤더가 Git 추적 텍스트에 남아 있습니다.'
fi

if ! grep -Fq 'applicationIdSuffix ".debug"' app/build.gradle; then
  fail 'Debug applicationIdSuffix가 .debug으로 설정되지 않았습니다.'
fi

if ! grep -Fq 'resValue "string", "app_name", "MyBrain AI Debug"' app/build.gradle; then
  fail 'Debug 앱 표시명이 MyBrain AI Debug로 분리되지 않았습니다.'
fi

if ! grep -Fq 'android:label="@string/app_name"' app/src/main/AndroidManifest.xml; then
  fail 'AndroidManifest 애플리케이션 라벨이 @string/app_name을 사용하지 않습니다.'
fi

if ! grep -Fq '"cleanup/**"' .github/workflows/build-v2.yml; then
  fail '대표 V2 워크플로가 cleanup/** push를 감시하지 않습니다.'
fi

v2_trigger_count="$(grep -Fc '      - "v2"' .github/workflows/build-v2.yml || true)"
if ((v2_trigger_count < 2)); then
  fail '대표 V2 워크플로가 v2 push와 pull request 대상을 모두 감시하지 않습니다.'
fi

if ! grep -Fq 'bash scripts/check-repository-hygiene.sh' .github/workflows/build-v2.yml; then
  fail '대표 V2 워크플로가 저장소 위생 검사를 실행하지 않습니다.'
fi

if grep -Fq 'EXPECTED_DEBUG_CERT_SHA256' .github/workflows/build-v2.yml; then
  fail '폐기 대상 고정 Debug 인증서 지문 검사가 CI에 남아 있습니다.'
fi

if ((failures > 0)); then
  printf 'Repository hygiene checks failed: %d issue(s).\n' "$failures" >&2
  exit 1
fi

printf 'Repository hygiene checks passed.\n'
