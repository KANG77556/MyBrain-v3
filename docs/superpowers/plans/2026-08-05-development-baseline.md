# MyBrain V2 지속 개발 기준선 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `v2`를 단일 개발 기준선으로 확정하고, 이후 기능 개발을 반복 가능하게 만드는 기여 문서·아키텍처 규칙·로드맵·Release 체크리스트·이슈/PR 템플릿·CI 검증을 추가한다.

**Architecture:** 제품 코드는 수정하지 않고 저장소 운영 계층만 강화한다. 먼저 PR #91을 `v2`에 병합하고 저장소 소유자가 기본 브랜치를 `v2`로 전환한 뒤, `chore/development-baseline`에서 문서 기준선 검사를 실패하는 상태로 추가한다. 그다음 문서와 GitHub 템플릿을 채워 검사를 통과시키고, 기존 Android 단위 테스트와 APK 검증을 그대로 재사용한다.

**Tech Stack:** GitHub branches, pull requests, issue forms, GitHub Actions, Bash, Markdown, Android Gradle Plugin, Java 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9, JUnit 4.

## Global Constraints

- 저장소는 `KANG77556/MyBrain-v3`다.
- 선행 PR은 `#91 chore: prepare v2 as the default branch`다.
- 장기 통합 브랜치는 `v2` 하나만 사용하며 `develop` 브랜치를 만들지 않는다.
- 개발 기준선 구현 브랜치는 `chore/development-baseline`이고 PR base는 `v2`다.
- `main`은 `55066aab295f7434a7ed413166fa5cfb1a377ede`에서 동결한다.
- `backup/main-pre-v2-20260805`도 `55066aab295f7434a7ed413166fa5cfb1a377ede`를 유지한다.
- `feature/personal-1.1-widgets`, 과거 워크플로, QA PR #89는 기본 브랜치 전환 후 최소 7일 동안 삭제하지 않는다.
- Java 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9, `compileSdk 35`, `targetSdk 35`, `minSdk 26`을 유지한다.
- Release 패키지는 `kr.co.mybrain.v2`, Debug 패키지는 `kr.co.mybrain.v2.debug`를 유지한다.
- 앱 기능, UI 동작, Android 권한, Manifest 구성, Room 스키마, 버전 코드, 버전 이름, Release 키와 Secrets는 변경하지 않는다.
- 새 DI 프레임워크, 네트워크 라이브러리, 테스트 프레임워크, Kotlin, Jetpack Compose를 도입하지 않는다.
- 기준선 PR은 문서, GitHub 템플릿, Bash 검사, 대표 CI 트리거만 변경한다.
- 기능·버그·정리·문서·테스트 작업 브랜치 패턴은 각각 `feature/**`, `fix/**`, `chore/**`, `docs/**`, `test/**`다.
- 모든 신규 기능은 Activity에 비즈니스 규칙을 직접 추가하지 않고 기존 `Policy`, `Parser`, `Controller`, `Repository` 경계를 사용하거나 작은 새 클래스로 추출한다.
- 미서명 `app-release-unsigned.apk`는 설치·배포용 Release로 표기하지 않는다.

---

## File Map

- Copy: `docs/superpowers/specs/2026-08-05-development-baseline-design.md` — 승인된 지속 개발 기준선 설계.
- Copy: `docs/superpowers/plans/2026-08-05-development-baseline.md` — 이 구현 계획.
- Create: `scripts/check-development-baseline.sh` — 필수 문서·템플릿·명령·브랜치 CI 패턴을 검사한다.
- Modify: `.github/workflows/build-v2.yml` — 표준 작업 브랜치 push와 개발 기준선 검사를 실행한다.
- Create: `CONTRIBUTING.md` — 개발 환경, 브랜치, 테스트, 코드 배치, PR 절차.
- Modify: `README.md` — 개발 기준 문서의 진입점 링크를 제공한다.
- Create: `docs/ARCHITECTURE_KO.md` — 현재 패키지 책임, 데이터 흐름, 오류 처리, 점진적 리팩터링 규칙.
- Create: `docs/DEVELOPMENT_ROADMAP_KO.md` — P0~P3 개발 우선순위와 완료 기준.
- Create: `docs/RELEASE_CHECKLIST_KO.md` — 준비, 빌드, 서명, 해시, 설치, 백업, 배포 후 확인 절차.
- Create: `.github/ISSUE_TEMPLATE/feature.yml` — 기능 이슈 폼.
- Create: `.github/ISSUE_TEMPLATE/bug.yml` — 버그 이슈 폼.
- Create: `.github/pull_request_template.md` — 영향 범위와 검증 근거를 요구하는 PR 템플릿.
- Existing verification: `scripts/check-repository-hygiene.sh`, `app/src/test/java/**`, `.github/workflows/build-v2.yml`.
- GitHub issues after merge: P0 3개, P1~P3 추적 이슈 3개.

---

### Task 1: PR #91 병합과 `v2` 기본 브랜치 전환 완료

**Files:**
- No code changes.
- GitHub PR #91 and repository settings.

**Interfaces:**
- Consumes: PR #91 HEAD `c1273365f27809554e70cc7cee343279141d3030`, green `Build MyBrain AI V2` run #466, owner/admin access.
- Produces: merged and green `v2`, repository `default_branch=v2`, preserved `main` and backup refs.

- [ ] **Step 1: PR #91의 최신 상태와 SHA를 다시 읽는다**

Run:

```bash
gh pr view 91 --repo KANG77556/MyBrain-v3 \
  --json state,isDraft,mergeable,baseRefName,headRefName,headRefOid,statusCheckRollup
```

Expected:

```text
state = OPEN
isDraft = false
mergeable = MERGEABLE
baseRefName = v2
headRefName = cleanup/v2-default-branch-transition
headRefOid = c1273365f27809554e70cc7cee343279141d3030
Build MyBrain AI V2 / build = SUCCESS
```

값이 다르면 병합하지 않고 변경 원인을 확인한다.

- [ ] **Step 2: 보존 브랜치가 움직이지 않았는지 확인한다**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

Expected: exit code `0`, no output.

- [ ] **Step 3: 사용자에게 받은 명시적 `병합` 승인 후 PR #91을 squash merge한다**

Run:

```bash
gh pr merge 91 --repo KANG77556/MyBrain-v3 --squash \
  --subject 'chore: prepare v2 as the default branch (#91)' \
  --body 'Add v2 workflow coverage and document the reversible GitHub default-branch transition while preserving the legacy main ref.' \
  --match-head-commit c1273365f27809554e70cc7cee343279141d3030
```

Expected: PR #91 state `MERGED`, base `v2`.

- [ ] **Step 4: 병합 커밋의 push CI를 끝까지 확인한다**

Run:

```bash
MERGE_SHA=$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')
gh run list --repo KANG77556/MyBrain-v3 --commit "$MERGE_SHA" --workflow build-v2.yml --limit 5
```

Required successful steps:

```text
저장소 위생 검사
V2 단위 테스트
Debug APK 빌드
Debug APK 패키지와 서명 검증
미서명 Release 컴파일 검증 또는 고정 서명 Release APK 빌드
```

Required artifacts:

```text
MyBrainAI-v2-unit-test-report
MyBrainAI-v2-build-log
MyBrainAI-v2-debug
MyBrainAI-v2-unsigned-release-check 또는 MyBrainAI-v2-release
```

- [ ] **Step 5: 저장소 소유자가 기본 브랜치를 `v2`로 변경한다**

GitHub web path:

```text
Settings → General → Default branch → Switch to another branch → v2 → Update
```

이 단계는 관리자 권한이 필요하다.

- [ ] **Step 6: `v2` Ruleset을 설정한다**

Configure:

```text
Require a pull request before merging
Require status check: Build MyBrain AI V2 / build
Require conversation resolution before merging
Block force pushes
Restrict deletions
```

`main`과 `backup/main-pre-v2-20260805`에도 force push와 삭제 금지를 적용한다.

- [ ] **Step 7: 기본 브랜치와 보존 refs를 검증한다**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch')" = 'v2'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Expected: exit code `0`.

---

### Task 2: 격리된 개발 기준선 브랜치와 승인 문서 준비

**Files:**
- Create on implementation branch: `docs/superpowers/specs/2026-08-05-development-baseline-design.md`
- Create on implementation branch: `docs/superpowers/plans/2026-08-05-development-baseline.md`

**Interfaces:**
- Consumes: merged `v2`, approved files from `docs/development-baseline-design`.
- Produces: `chore/development-baseline` containing the approved spec and plan before implementation changes.

- [ ] **Step 1: `v2`에서 구현 브랜치를 만든다**

Run:

```bash
git fetch origin v2
git switch v2
git pull --ff-only origin v2
git switch -c chore/development-baseline
```

Expected: branch `chore/development-baseline`, parent equals current `origin/v2`.

- [ ] **Step 2: 설계 문서를 설계 브랜치에서 복사한다**

Run:

```bash
git show origin/docs/development-baseline-design:docs/superpowers/specs/2026-08-05-development-baseline-design.md \
  > docs/superpowers/specs/2026-08-05-development-baseline-design.md
```

- [ ] **Step 3: 구현 계획을 설계 브랜치에서 복사한다**

Run:

```bash
git show origin/docs/development-baseline-design:docs/superpowers/plans/2026-08-05-development-baseline.md \
  > docs/superpowers/plans/2026-08-05-development-baseline.md
```

- [ ] **Step 4: 승인 문서의 핵심 조건을 확인한다**

Run:

```bash
grep -F '구현 대상 브랜치: `chore/development-baseline`' \
  docs/superpowers/specs/2026-08-05-development-baseline-design.md
grep -F '장기 통합 브랜치는 `v2` 하나만 사용한다' \
  docs/superpowers/specs/2026-08-05-development-baseline-design.md
grep -F 'Activity에 비즈니스 규칙을 직접 추가하지 않고' \
  docs/superpowers/plans/2026-08-05-development-baseline.md
```

Expected: 세 명령 모두 matching line 출력.

- [ ] **Step 5: 승인 문서만 먼저 커밋한다**

```bash
git add docs/superpowers/specs/2026-08-05-development-baseline-design.md \
  docs/superpowers/plans/2026-08-05-development-baseline.md
git commit -m "docs: add continuous development baseline design"
```

---

### Task 3: 개발 기준선 검사를 실패하는 상태로 추가

**Files:**
- Create: `scripts/check-development-baseline.sh`
- Modify: `.github/workflows/build-v2.yml`

**Interfaces:**
- Consumes: repository root, README, planned docs/templates, representative workflow.
- Produces: required baseline files and content contracts; CI failure until Tasks 4~6 provide them.

- [ ] **Step 1: 개발 기준선 검사 스크립트를 작성한다**

Create `scripts/check-development-baseline.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

failures=0

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  failures=$((failures + 1))
}

required_files=(
  "CONTRIBUTING.md"
  "docs/ARCHITECTURE_KO.md"
  "docs/DEVELOPMENT_ROADMAP_KO.md"
  "docs/RELEASE_CHECKLIST_KO.md"
  ".github/ISSUE_TEMPLATE/feature.yml"
  ".github/ISSUE_TEMPLATE/bug.yml"
  ".github/pull_request_template.md"
)

for path in "${required_files[@]}"; do
  if [[ ! -f "$path" ]]; then
    fail "필수 개발 기준선 파일이 없습니다: $path"
  fi
done

require_fixed() {
  local path="$1"
  local pattern="$2"
  local message="$3"
  if [[ ! -f "$path" ]] || ! grep -Fq "$pattern" "$path"; then
    fail "$message"
  fi
}

require_fixed README.md '[기여 방법](CONTRIBUTING.md)' \
  'README에 CONTRIBUTING.md 링크가 없습니다.'
require_fixed README.md '[아키텍처](docs/ARCHITECTURE_KO.md)' \
  'README에 아키텍처 문서 링크가 없습니다.'
require_fixed README.md '[개발 로드맵](docs/DEVELOPMENT_ROADMAP_KO.md)' \
  'README에 개발 로드맵 링크가 없습니다.'
require_fixed README.md '[Release 체크리스트](docs/RELEASE_CHECKLIST_KO.md)' \
  'README에 Release 체크리스트 링크가 없습니다.'

for command in \
  'bash scripts/check-repository-hygiene.sh' \
  'bash scripts/check-development-baseline.sh' \
  'gradle --stacktrace testDebugUnitTest' \
  'gradle --stacktrace assembleDebug' \
  'gradle --stacktrace assembleRelease'; do
  require_fixed CONTRIBUTING.md "$command" \
    "CONTRIBUTING.md에 필수 검증 명령이 없습니다: $command"
done

for branch_pattern in \
  'feature/<짧은-기능명>' \
  'fix/<짧은-문제명>' \
  'chore/<짧은-정리명>' \
  'docs/<짧은-문서명>' \
  'test/<짧은-검증명>'; do
  require_fixed CONTRIBUTING.md "$branch_pattern" \
    "CONTRIBUTING.md에 작업 브랜치 규칙이 없습니다: $branch_pattern"
done

for package_name in assistant data reminder settings share transfer ui voice widget; do
  require_fixed docs/ARCHITECTURE_KO.md "\`$package_name\`" \
    "아키텍처 문서에 패키지 책임이 없습니다: $package_name"
done

for boundary_name in Activity Policy Parser Controller Repository; do
  require_fixed docs/ARCHITECTURE_KO.md "$boundary_name" \
    "아키텍처 문서에 책임 경계가 없습니다: $boundary_name"
done

for priority in 'P0 — 개발·배포 안정성' 'P1 — 핵심 입력·검토·저장 흐름' \
  'P2 — 알림과 위젯 신뢰성' 'P3 — 데이터 보호와 정식 배포'; do
  require_fixed docs/DEVELOPMENT_ROADMAP_KO.md "$priority" \
    "개발 로드맵에 우선순위가 없습니다: $priority"
done

require_fixed docs/RELEASE_CHECKLIST_KO.md 'EXPECTED_CERT_SHA256' \
  'Release 체크리스트에 인증서 지문 검증 기준이 없습니다.'
require_fixed docs/RELEASE_CHECKLIST_KO.md 'app-release-unsigned.apk' \
  'Release 체크리스트에 미서명 APK 경고가 없습니다.'
require_fixed docs/RELEASE_CHECKLIST_KO.md '업데이트 설치' \
  'Release 체크리스트에 업데이트 설치 검증이 없습니다.'
require_fixed docs/RELEASE_CHECKLIST_KO.md '백업' \
  'Release 체크리스트에 백업·복구 검증이 없습니다.'

require_fixed .github/ISSUE_TEMPLATE/feature.yml 'name: 기능 제안' \
  '기능 이슈 템플릿 이름이 없습니다.'
require_fixed .github/ISSUE_TEMPLATE/feature.yml 'id: acceptance_criteria' \
  '기능 이슈 템플릿에 완료 조건 입력이 없습니다.'
require_fixed .github/ISSUE_TEMPLATE/feature.yml 'id: impact' \
  '기능 이슈 템플릿에 영향 범위 입력이 없습니다.'
require_fixed .github/ISSUE_TEMPLATE/bug.yml 'name: 버그 신고' \
  '버그 이슈 템플릿 이름이 없습니다.'
require_fixed .github/ISSUE_TEMPLATE/bug.yml 'id: reproduction' \
  '버그 이슈 템플릿에 재현 절차 입력이 없습니다.'
require_fixed .github/ISSUE_TEMPLATE/bug.yml 'id: versions' \
  '버그 이슈 템플릿에 버전 입력이 없습니다.'

for heading in '## 목적' '## 변경 범위' '## 사용자 검증 시나리오' \
  '## 데이터·권한·AI 영향' '## 테스트 결과' '## 롤백'; do
  require_fixed .github/pull_request_template.md "$heading" \
    "PR 템플릿에 필수 섹션이 없습니다: $heading"
done

for branch_pattern in \
  '- "feature/**"' '- "fix/**"' '- "chore/**"' '- "docs/**"' '- "test/**"'; do
  require_fixed .github/workflows/build-v2.yml "$branch_pattern" \
    "대표 CI가 표준 작업 브랜치를 감시하지 않습니다: $branch_pattern"
done

require_fixed .github/workflows/build-v2.yml \
  'run: bash scripts/check-development-baseline.sh' \
  '대표 CI가 개발 기준선 검사를 실행하지 않습니다.'

baseline_files=(
  "CONTRIBUTING.md"
  "README.md"
  "docs/ARCHITECTURE_KO.md"
  "docs/DEVELOPMENT_ROADMAP_KO.md"
  "docs/RELEASE_CHECKLIST_KO.md"
  ".github/ISSUE_TEMPLATE/feature.yml"
  ".github/ISSUE_TEMPLATE/bug.yml"
  ".github/pull_request_template.md"
)

existing_baseline_files=()
for path in "${baseline_files[@]}"; do
  [[ -f "$path" ]] && existing_baseline_files+=("$path")
done

if ((${#existing_baseline_files[@]} > 0)); then
  placeholder_hits="$(grep -nE '\b(TBD|TODO|FIXME)\b|implement later|fill in details' \
    "${existing_baseline_files[@]}" || true)"
  if [[ -n "$placeholder_hits" ]]; then
    printf '%s\n' "$placeholder_hits" >&2
    fail '개발 기준선 문서 또는 템플릿에 미완성 표기가 남아 있습니다.'
  fi
fi

if ((failures > 0)); then
  printf 'Development baseline checks failed: %d issue(s).\n' "$failures" >&2
  exit 1
fi

printf 'Development baseline checks passed.\n'
```

- [ ] **Step 2: 대표 workflow의 push 대상을 표준 작업 브랜치로 확장한다**

Replace the `push.branches` list with:

```yaml
  push:
    branches:
      - "v2"
      - "feature/**"
      - "fix/**"
      - "chore/**"
      - "docs/**"
      - "test/**"
      - "rebuild/v2"
      - "rebuild/v2-*"
      - "feature/personal-1.1-widgets"
      - "cleanup/**"
```

기존 관찰 대상 브랜치는 제거하지 않는다.

- [ ] **Step 3: 저장소 위생 검사 다음에 개발 기준선 검사 단계를 추가한다**

Insert immediately after `저장소 위생 검사`:

```yaml
      - name: 개발 기준선 검사
        shell: bash
        run: bash scripts/check-development-baseline.sh
```

- [ ] **Step 4: 현재 상태에서 검사가 예상대로 실패하는지 확인한다**

Run:

```bash
bash scripts/check-development-baseline.sh
```

Expected: exit code `1`. 최소한 `CONTRIBUTING.md`, `docs/ARCHITECTURE_KO.md`, `docs/DEVELOPMENT_ROADMAP_KO.md`, `docs/RELEASE_CHECKLIST_KO.md`, 두 이슈 템플릿, PR 템플릿 누락 오류가 출력된다.

- [ ] **Step 5: 검사와 workflow 변경만 커밋한다**

```bash
git add scripts/check-development-baseline.sh .github/workflows/build-v2.yml
git commit -m "test: add continuous development baseline guard"
```

Expected: `chore/**` push workflow가 실행되고 `개발 기준선 검사` 단계에서 예상한 누락 오류로 실패한다.

---

### Task 4: 기여 절차와 README 진입점 작성

**Files:**
- Create: `CONTRIBUTING.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: branch policy, current Gradle commands, repository hygiene and development-baseline scripts.
- Produces: clone-to-PR workflow and links to all long-form development documents.

- [ ] **Step 1: `CONTRIBUTING.md`를 작성한다**

Create:

```markdown
# MyBrain V2 기여 가이드

## 기본 원칙

MyBrain V2의 장기 통합 브랜치는 `v2`입니다. `v2`에 직접 push하지 않고 하나의 사용자 가치 또는 하나의 기술 문제마다 짧은 작업 브랜치와 Pull Request를 사용합니다.

## 개발 환경

- Java 17
- Android SDK 35
- Android Build Tools 35.0.0
- Gradle 8.9
- Android 최소 SDK 26, 대상 SDK 35

## 저장소 준비

```bash
git clone https://github.com/KANG77556/MyBrain-v3.git
cd MyBrain-v3
git switch v2
git pull --ff-only origin v2
```

`local.properties`, 키스토어, 인증서, API 키, 비밀번호, `.env` 파일은 커밋하지 않습니다.

## 작업 브랜치

변경 목적에 맞는 이름을 사용합니다.

```text
feature/<짧은-기능명>
fix/<짧은-문제명>
chore/<짧은-정리명>
docs/<짧은-문서명>
test/<짧은-검증명>
```

예:

```bash
git switch -c feature/quick-entry-review
git switch -c fix/reminder-timezone-reschedule
git switch -c chore/room-schema-export
```

한 브랜치에서 서로 독립적인 기능, 대규모 리팩터링, 데이터 스키마 변경을 함께 처리하지 않습니다.

## 구현 순서

1. 기능 제안 또는 버그 이슈에 사용자 문제와 완료 조건을 작성합니다.
2. 재현 가능한 버그는 실패하는 테스트 또는 명확한 재현 시나리오를 먼저 만듭니다.
3. 새 비즈니스 규칙은 Activity에 직접 넣지 않고 `Policy`, `Parser`, `Controller`, `Repository` 중 적합한 경계로 분리합니다.
4. 요구사항을 만족하는 최소 변경을 구현합니다.
5. 관련 테스트와 전체 검증을 실행합니다.
6. PR 템플릿에 영향 범위와 검증 근거를 기록합니다.

## 코드 배치

- Android 생명주기, Intent, View 연결: 최상위 Activity, Receiver, Provider
- 자연어·AI 분석, 개인정보 필터, 결과 검증: `assistant`
- Room Entity, DAO, Database, Repository: `data`
- 알림 예약, 반복 계산, 재부팅·시간대 재예약: `reminder`
- 사용자 설정, AI 모델·예산, 암호화된 값 저장: `settings`
- 외부 공유 입력: `share`
- 백업·복구·업데이트 APK 검증: `transfer`
- 화면 조정 Controller와 순수 UI Policy: `ui`
- 음성 인식 세션: `voice`
- 위젯 표시와 선택 Policy: `widget`

Activity와 Widget에서 DAO를 직접 호출하지 않습니다. 날짜 계산, 일정 충돌, AI JSON 복구, 저장 무결성, 반복 일정, 백업 충돌, 가격 계산은 가능한 한 Android 의존성이 없는 작은 클래스와 단위 테스트로 분리합니다.

## 점진적 리팩터링

기존 큰 파일을 한 번에 다시 작성하지 않습니다. 큰 Activity 또는 Analyzer를 수정할 때 새 규칙을 파일 안에 더 추가하지 말고, 실제 수정 대상 책임 하나를 테스트 가능한 클래스로 추출합니다.

새 production 파일이 500줄을 넘으면 PR 본문에 분리하지 못한 이유와 후속 분리 계획을 적습니다. 이미 500줄을 넘는 파일은 기능 추가로 더 키우지 않는 것을 기본 원칙으로 합니다.

## 로컬 필수 검증

```bash
bash scripts/check-repository-hygiene.sh
bash scripts/check-development-baseline.sh
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Release Secrets가 없는 환경에서 생성되는 `app/build/outputs/apk/release/app-release-unsigned.apk`는 컴파일 검증용이며 설치·배포용이 아닙니다.

## PR 전 확인

- 사용자 관점 완료 조건을 만족했는지 확인합니다.
- 새 로직의 테스트와 기존 전체 단위 테스트가 성공했는지 확인합니다.
- Debug APK가 `kr.co.mybrain.v2.debug`, `MyBrain AI Debug`로 빌드되는지 확인합니다.
- Room 스키마, 백업 형식, Android 권한, Manifest, AI 요청·비용·개인정보, 알림, 위젯, Release 서명 영향 여부를 기록합니다.
- 변경을 되돌리는 방법을 적습니다.
- 화면 변경은 에뮬레이터 또는 기기 검증 결과를 첨부합니다.

## 보안

API 키, OAuth 토큰, 서명키, 인증서, 비밀번호, 개인정보가 포함된 로그를 Issue, PR, Discussion, Actions 로그에 올리지 않습니다. 보안 문제는 공개 이슈 대신 `SECURITY.md`의 비공개 신고 절차를 사용합니다.
```

- [ ] **Step 2: README에 개발 문서 진입점을 추가한다**

Insert after `## 개발 환경` section:

```markdown
## 개발 문서

- [기여 방법](CONTRIBUTING.md)
- [아키텍처](docs/ARCHITECTURE_KO.md)
- [개발 로드맵](docs/DEVELOPMENT_ROADMAP_KO.md)
- [Release 체크리스트](docs/RELEASE_CHECKLIST_KO.md)
- [브랜치 운영 정책](docs/BRANCH_POLICY_KO.md)
```

기존 빌드, 서명, Actions 아티팩트 안내는 그대로 유지한다.

- [ ] **Step 3: 기여 문서와 README 링크를 정적으로 확인한다**

Run:

```bash
grep -F 'feature/<짧은-기능명>' CONTRIBUTING.md
grep -F 'bash scripts/check-development-baseline.sh' CONTRIBUTING.md
grep -F '[기여 방법](CONTRIBUTING.md)' README.md
grep -F '[아키텍처](docs/ARCHITECTURE_KO.md)' README.md
grep -F '[개발 로드맵](docs/DEVELOPMENT_ROADMAP_KO.md)' README.md
grep -F '[Release 체크리스트](docs/RELEASE_CHECKLIST_KO.md)' README.md
```

Expected: 모든 명령이 matching line 출력.

- [ ] **Step 4: 기여 문서 커밋을 만든다**

```bash
git add CONTRIBUTING.md README.md
git commit -m "docs: add V2 contribution workflow"
```

Development baseline guard는 아직 아키텍처·로드맵·Release·템플릿 누락으로 실패해야 한다.

---

### Task 5: 현재 코드에 맞는 아키텍처와 개발 로드맵 작성

**Files:**
- Create: `docs/ARCHITECTURE_KO.md`
- Create: `docs/DEVELOPMENT_ROADMAP_KO.md`

**Interfaces:**
- Consumes: current packages `assistant`, `data`, `reminder`, `settings`, `share`, `transfer`, `ui`, `voice`, `widget` and existing Policy/Controller/Repository patterns.
- Produces: code-placement contract, standard data flow, error taxonomy, incremental-refactoring rules, P0~P3 priorities.

- [ ] **Step 1: 아키텍처 문서를 작성한다**

Create `docs/ARCHITECTURE_KO.md`:

```markdown
# MyBrain V2 아키텍처 기준

## 목적

이 문서는 새 프레임워크를 정의하지 않습니다. 현재 Java 코드가 사용하는 Activity, Policy, Parser, Controller, Repository와 기능별 패키지의 책임을 명확하게 해 이후 기능이 큰 화면 클래스에 다시 집중되지 않도록 합니다.

## 기본 원칙

1. Android 진입점은 생명주기, Intent, 권한, View 연결을 담당합니다.
2. 계산과 판정은 가능한 한 Android 의존성이 없는 작은 클래스로 둡니다.
3. Activity와 Widget은 DAO를 직접 호출하지 않고 Repository 또는 Controller를 사용합니다.
4. AI 결과는 검증과 사용자 검토를 거친 뒤 저장합니다.
5. 저장, 알림, 위젯 갱신은 각 성공 여부를 구분합니다.
6. 큰 기존 파일은 전면 재작성하지 않고 기능 수정 시 관련 책임만 점진적으로 추출합니다.

## Android 진입점과 Activity

대상:

```text
kr.co.mybrain.v2.*Activity
MyBrainApplication
BroadcastReceiver
AppWidgetProvider
```

담당:

- Android 생명주기
- Intent와 외부 입력 수신
- View 생성과 이벤트 연결
- 권한 요청과 시스템 설정 이동
- Controller 또는 Repository 호출
- 결과 렌더링과 사용자 메시지

직접 구현하지 않는 규칙:

- 날짜·시간 계산
- 일정 충돌 판단
- AI JSON 복구와 결과 검증
- 저장 무결성 판단
- 반복 일정 계산
- 백업 충돌 판단
- 네트워크 재시도와 비용 정책

## 패키지별 책임

### `assistant`

자연어 입력 파싱, 선택적 클라우드 AI 요청, 개인정보 필터, 응답 복구·검증, 재시도·캐시·표시 정책을 담당합니다.

- 네트워크 요청, JSON 복구, 결과 검증, 비용 정책을 한 클래스에 모두 추가하지 않습니다.
- 문자열·날짜·결과 판정은 Parser 또는 Policy로 분리합니다.
- API 키와 원문 개인정보를 로그에 남기지 않습니다.
- `CloudAiWorkItemAnalyzer`를 수정할 때 네트워크, 파싱, 검증 중 실제 변경 대상 책임 하나만 추출합니다.

### `data`

Room Entity, DAO, Database, Repository와 저장 무결성 경계를 담당합니다.

- Activity, Receiver, Widget에서 DAO를 직접 호출하지 않습니다.
- 데이터 접근은 `WorkItemRepository` 같은 Repository를 통합니다.
- 스키마 변경은 migration, schema export, 백업 호환성, 롤백을 함께 설계합니다.
- transaction과 중복 저장 방지 규칙을 테스트합니다.

### `reminder`

알림 예약·취소, 반복 일정 계산, 재부팅·앱 업데이트·시간·시간대 변경 후 재예약을 담당합니다.

- Receiver는 입력 검증과 호출 연결만 담당합니다.
- 반복과 시간 계산은 `RecurrenceCalculator` 같은 순수 클래스로 둡니다.
- 같은 작업의 중복 알람이 생기지 않도록 식별자 정책을 테스트합니다.
- 정확한 알람 권한이 없을 때의 제한 동작을 명시합니다.

### `settings`

사용자 설정 화면, AI 제공자·모델·예산·사용량, 접근성·알림 설정과 암호화된 민감값 저장을 담당합니다.

- API 키는 `EncryptedValueStore`를 통해서만 저장합니다.
- 가격·예산 계산은 Activity가 아닌 Catalog, Settings, Store에 둡니다.
- 설정 변경이 실제 분석·알림 동작에 반영되는 경로를 테스트합니다.

### `share`

외부 공유 Intent에서 텍스트, 이미지, PDF와 문서 입력을 안전하게 추출합니다.

- MIME type, null URI, 접근 권한, 입력 크기를 경계에서 검증합니다.
- 추출 원문을 바로 저장하지 않고 기존 입력 검토 흐름으로 전달합니다.

### `transfer`

백업 생성·암호화·복호화, 복구 계획·충돌·진단과 업데이트 APK 검증·설치 연결을 담당합니다.

- 백업 형식 변경은 버전 필드와 이전 형식 복구 테스트를 동반합니다.
- 복구 전 미리보기와 검증을 제공합니다.
- 손상 파일, 잘못된 암호, 일부 실패에서 기존 데이터를 훼손하지 않습니다.
- APK 설치 전 패키지, 서명, 해시, 버전 관계를 확인합니다.

### `ui`

화면 동작을 조정하는 Controller, 순수 표시·레이아웃·충돌·선택 Policy와 공통 UI 도우미를 담당합니다.

- Policy는 가능한 한 Context 없는 입력과 출력으로 만듭니다.
- Controller는 View와 Repository·Policy를 연결하지만 장기 저장 규칙을 소유하지 않습니다.
- 하나의 Controller가 AI, 저장, 알림, 위젯을 모두 직접 조정하면 별도의 흐름 조정 클래스를 검토합니다.

### `voice`

음성 인식 세션, 중단, 오류와 텍스트 결과 전달을 담당합니다.

- 시스템 인식 실패와 사용자 취소를 구분합니다.
- 음성 원문을 자동 저장하지 않고 입력 검토 흐름으로 전달합니다.

### `widget`

오늘, 일정, 할 일, 빠른 메모 위젯의 데이터 조회, RemoteViews 구성과 표시 Policy를 담당합니다.

- 문구, 정렬, 항목 수, 잘라내기 규칙은 Policy에서 테스트합니다.
- AppWidgetProvider에 복잡한 날짜·선택 규칙을 직접 추가하지 않습니다.
- 저장 성공 후 위젯 갱신 경로를 명시합니다.

## 책임 단위

### Policy

입력값으로 판정 결과를 반환하는 순수 규칙입니다. Android Context, View, DB, 네트워크를 직접 사용하지 않습니다.

### Parser

문자열이나 외부 응답을 구조화된 값으로 변환합니다. 파싱 실패는 호출자가 처리할 수 있는 명시적 결과로 반환합니다.

### Controller

화면 이벤트와 Policy·Repository 호출을 조정합니다. 영구 데이터 모델이나 스키마를 소유하지 않습니다.

### Repository

DAO와 저장 단위를 감싸고 조회·저장·수정·삭제의 일관된 API를 제공합니다.

### Activity

생명주기, View, Intent, 권한과 Controller 호출만 담당합니다.

## 표준 입력·저장 흐름

```text
사용자 입력
→ 입력 정규화
→ 로컬 Parser 또는 선택적 클라우드 AI 분석
→ 개인정보 필터와 결과 검증
→ 사용자 검토와 수정
→ 저장 무결성 확인
→ Repository 저장
→ 알림 재계산
→ 위젯·목록 갱신
→ 성공 또는 복구 가능한 오류 표시
```

- AI 결과를 자동으로 최종 저장하지 않습니다.
- 저장 성공 전에 알림 성공으로 표시하지 않습니다.
- 저장 성공 후 알림만 실패하면 데이터를 유지하고 재시도 가능한 상태를 알립니다.
- 위젯 갱신 실패는 저장을 되돌리지 않습니다.

## 오류 처리

### 사용자 수정 가능 오류

제목 없음, 날짜 모호, 일정 충돌, 잘못된 백업 암호는 원본 입력을 보존하고 수정 방법을 표시합니다.

### 일시적 외부 오류

네트워크, AI 시간 초과, 음성 인식 중단은 제한된 재시도와 로컬 또는 수동 대체 경로를 제공합니다.

### 권한·시스템 제약

알림 권한, 정확한 알람 권한, 파일 URI 접근 실패는 제한된 기능을 설명하고 설정 변경 후 다시 검사합니다.

### 데이터 무결성 오류

Room 저장, migration, 중복 저장, 손상 백업 오류는 부분 성공을 숨기지 않고 기존 데이터를 덮어쓰기 전에 검증과 백업을 수행합니다.

## 테스트 경계

- Policy, Parser, 날짜·시간 계산, AI 복구·검증, 반복 일정, 충돌, 위젯 선택, 예산 계산, 백업 계획은 JUnit 단위 테스트를 사용합니다.
- Repository와 Room migration은 Android 계측 테스트를 사용합니다.
- 런처 Activity 설치·실행과 프로세스 생존은 Android 15 smoke test로 검증합니다.
- 화면 변경은 PR에서 기기 또는 에뮬레이터 결과를 기록합니다.

## 점진적 리팩터링

1. 큰 파일을 이유 없이 한 번에 분해하지 않습니다.
2. 큰 파일에 새 비즈니스 규칙을 직접 추가하지 않습니다.
3. 수정 대상 규칙을 테스트 가능한 Policy, Parser, Controller, Repository로 먼저 추출합니다.
4. 추출과 기능 변경이 독립적으로 리뷰 가능하면 두 PR로 나눕니다.
5. 새 production 파일이 500줄을 넘으면 PR에 이유와 후속 분리 계획을 기록합니다.
6. 클래스 수가 아니라 테스트 가능한 책임 경계가 생겼는지 평가합니다.
```

- [ ] **Step 2: 개발 로드맵을 작성한다**

Create `docs/DEVELOPMENT_ROADMAP_KO.md`:

```markdown
# MyBrain V2 개발 로드맵

## 운영 원칙

- 장기 통합 기준선은 `v2`입니다.
- 하나의 이슈와 PR은 하나의 사용자 가치 또는 기술 문제를 해결합니다.
- P0 안정성 작업이 끝나기 전 대형 신규 기능보다 데이터·빌드·배포 신뢰성을 우선합니다.
- 각 항목은 사용자 완료 조건, 자동 테스트, 롤백 방법을 갖습니다.

## P0 — 개발·배포 안정성

### 1. 고정 서명 Release 경로 검증

완료 조건:

- GitHub Secrets가 있는 실행에서 `MyBrainAI-v2-release`가 생성됩니다.
- APK Signature Scheme v2와 v3가 성공합니다.
- signer 인증서 SHA-256이 workflow의 `EXPECTED_CERT_SHA256`과 일치합니다.
- 신규 설치와 기존 정식 앱 위 업데이트 설치를 검증합니다.

### 2. Room schema export와 migration 기준선

완료 조건:

- Room schema JSON의 저장 경로와 버전 관리 정책이 정해집니다.
- 현재 DB 버전의 schema가 저장됩니다.
- migration 누락 시 CI가 실패합니다.
- CRUD, transaction, 중복 저장과 migration 계측 테스트가 성공합니다.
- 백업·복구 호환성 영향이 문서화됩니다.

### 3. Android 15 실행 smoke test

완료 조건:

- Debug APK와 AndroidTest APK가 빌드됩니다.
- Android 15 규격 에뮬레이터에 설치됩니다.
- 런처 Activity가 실행되고 프로세스가 생존합니다.
- 초기 Room Database 생성에서 치명적 예외가 없습니다.
- 실패 시 logcat과 테스트 보고서가 아티팩트로 남습니다.

## P1 — 핵심 입력·검토·저장 흐름

기준 사용자 흐름:

```text
빠른 입력
→ 로컬 또는 AI 분석
→ 결과 검토와 수정
→ 저장
→ 할 일·일정·메모에서 일관된 조회
```

완료 조건:

- 오프라인에서 로컬 입력과 저장이 가능합니다.
- AI 실패 시 원본 입력을 보존하고 로컬 또는 수동 경로를 제공합니다.
- 날짜가 모호하면 자동 확정하지 않고 검토를 요구합니다.
- 저장 버튼 중복 탭으로 중복 레코드가 생기지 않습니다.
- 일정 충돌을 사용자에게 설명하고 저장 선택권을 제공합니다.
- 저장 성공 후 알림과 위젯이 갱신됩니다.

추적 이슈 제목:

```text
P1: Define quick-entry review and save acceptance scenarios
```

## P2 — 알림과 위젯 신뢰성

완료 조건:

- 재부팅과 앱 업데이트 후 알림이 복원됩니다.
- 시스템 시간과 시간대 변경 후 예약 시간이 일관됩니다.
- 반복 일정의 다음 실행 시간이 경계값 테스트를 통과합니다.
- 알림과 정확한 알람 권한 거부 시 제한 동작을 설명합니다.
- 오늘·일정·할 일·빠른 메모 위젯이 저장 변경을 반영합니다.
- Android 15 기기 또는 동등 에뮬레이터에서 검증합니다.

추적 이슈 제목:

```text
P2: Verify reminder rescheduling across reboot and timezone changes
```

## P3 — 데이터 보호와 정식 배포

완료 조건:

- 암호화 백업 생성과 복호화 검증이 성공합니다.
- 복구 전 변경 예정 항목을 미리 확인할 수 있습니다.
- 잘못된 암호, 손상 파일, 일부 충돌이 기존 데이터를 훼손하지 않습니다.
- 이전 백업 형식의 호환성 정책과 테스트가 있습니다.
- 서명된 Release APK의 신규 설치와 업데이트 설치가 성공합니다.
- APK, SHA-256, 인증서 지문, Release 노트와 롤백 APK가 보관됩니다.

추적 이슈 제목:

```text
P3: Validate encrypted backup restore compatibility
```

## 우선 등록 이슈

```text
P0: Verify signed Release path with GitHub Secrets
P0: Export Room schemas and define migration baseline
P0: Add Android 15 launch smoke test
P1: Define quick-entry review and save acceptance scenarios
P2: Verify reminder rescheduling across reboot and timezone changes
P3: Validate encrypted backup restore compatibility
```

## 완료 판정

각 로드맵 항목은 다음을 모두 만족할 때 완료로 표시합니다.

- 사용자 관점 완료 조건 충족
- 관련 테스트와 전체 필수 CI 성공
- 데이터, 권한, AI, 알림, 위젯, Release 영향 기록
- 실패 시 롤백 또는 복구 경로 확인
- 문서와 실제 동작 일치
```

- [ ] **Step 3: 패키지와 우선순위의 누락을 확인한다**

Run:

```bash
for package_name in assistant data reminder settings share transfer ui voice widget; do
  grep -F "\`$package_name\`" docs/ARCHITECTURE_KO.md
done
for priority in 'P0 — 개발·배포 안정성' 'P1 — 핵심 입력·검토·저장 흐름' \
  'P2 — 알림과 위젯 신뢰성' 'P3 — 데이터 보호와 정식 배포'; do
  grep -F "$priority" docs/DEVELOPMENT_ROADMAP_KO.md
done
```

Expected: 모든 package와 priority가 출력된다.

- [ ] **Step 4: 아키텍처와 로드맵을 커밋한다**

```bash
git add docs/ARCHITECTURE_KO.md docs/DEVELOPMENT_ROADMAP_KO.md
git commit -m "docs: define V2 architecture and roadmap"
```

Development baseline guard는 Release 체크리스트와 GitHub 템플릿 누락으로 계속 실패해야 한다.

---

### Task 6: Release 체크리스트와 GitHub 이슈·PR 템플릿 작성

**Files:**
- Create: `docs/RELEASE_CHECKLIST_KO.md`
- Create: `.github/ISSUE_TEMPLATE/feature.yml`
- Create: `.github/ISSUE_TEMPLATE/bug.yml`
- Create: `.github/pull_request_template.md`

**Interfaces:**
- Consumes: current signing workflow, package names, architecture impact areas, roadmap acceptance criteria.
- Produces: executable release gate and structured feature/bug/PR intake.

- [ ] **Step 1: Release 체크리스트를 작성한다**

Create `docs/RELEASE_CHECKLIST_KO.md`:

```markdown
# MyBrain V2 Release 체크리스트

## 1. Release 준비

- [ ] 기준 브랜치가 `v2`인지 확인합니다.
- [ ] `v2`의 최신 CI가 성공했는지 확인합니다.
- [ ] 작업 트리에 커밋되지 않은 파일이 없는지 확인합니다.
- [ ] `versionCode`와 `versionName`을 증가시킵니다.
- [ ] Release 노트에 사용자 변경, 데이터·권한·알림·백업 영향을 기록합니다.
- [ ] Room schema 또는 백업 형식 변경이 있으면 migration과 이전 형식 호환성을 확인합니다.
- [ ] 롤백에 사용할 이전 고정 서명 APK와 해시를 확보합니다.

## 2. 정적 검사와 테스트

```bash
bash scripts/check-repository-hygiene.sh
bash scripts/check-development-baseline.sh
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
```

- [ ] 저장소 위생 검사 성공
- [ ] 개발 기준선 검사 성공
- [ ] 전체 단위 테스트 성공
- [ ] Room migration 또는 계측 테스트 성공
- [ ] Android 15 smoke test 성공
- [ ] Debug 패키지 `kr.co.mybrain.v2.debug` 확인
- [ ] Debug 앱 이름 `MyBrain AI Debug` 확인
- [ ] Debug APK Signature Scheme v2 확인

## 3. 고정 서명 Release 빌드

Release 빌드는 다음 환경변수를 외부 환경 또는 GitHub Secrets에서만 읽습니다.

```text
MYBRAIN_KEYSTORE_FILE
MYBRAIN_KEYSTORE_PASSWORD
MYBRAIN_KEY_ALIAS
MYBRAIN_KEY_PASSWORD
```

```bash
gradle --stacktrace assembleRelease
```

- [ ] `app/build/outputs/apk/release/app-release.apk` 생성
- [ ] APK Signature Scheme v2와 v3 성공
- [ ] signer가 1개인지 확인
- [ ] signer SHA-256이 `.github/workflows/build-v2.yml`의 `EXPECTED_CERT_SHA256`과 일치
- [ ] 현재 workflow의 예상 인증서 값 `ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520`과 일치

Release 키를 교체할 때는 키 회전 설계, 업데이트 호환성, workflow와 이 문서의 인증서 값을 같은 PR에서 변경합니다.

## 4. 미서명 APK 경고

`app/build/outputs/apk/release/app-release-unsigned.apk`는 Release 컴파일 검증용입니다.

- 설치용 Release로 배포하지 않습니다.
- 기존 앱 업데이트에 사용하지 않습니다.
- GitHub Release나 사용자 안내에 정식 APK로 첨부하지 않습니다.

## 5. 해시와 파일 이름

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

- [ ] 배포 APK SHA-256 생성
- [ ] 업로드 후 다시 다운로드한 APK의 SHA-256 재검증
- [ ] 파일 이름에 앱 버전과 `release` 포함
- [ ] APK, `.sha256`, 서명 보고서를 함께 보관

## 6. 설치 검증

### 신규 설치

- [ ] 지원 최소 Android 버전 또는 대표 하위 버전에 신규 설치
- [ ] Android 15에 신규 설치
- [ ] 런처 Activity 실행
- [ ] 초기 Room Database 생성
- [ ] 빠른 입력, 저장, 목록 조회 확인
- [ ] 알림 권한 거부와 허용 경로 확인
- [ ] 위젯 추가와 갱신 확인

### 업데이트 설치

- [ ] 현재 배포 버전 위에 업데이트 설치 성공
- [ ] 기존 메모·할 일·일정 유지
- [ ] Room migration 성공
- [ ] 기존 알림 재예약 확인
- [ ] 기존 위젯 갱신 확인
- [ ] 설정과 암호화된 API 키 유지

## 7. 백업·복구 검증

- [ ] 업데이트 전 암호화 백업 생성
- [ ] 백업 해시 또는 내부 검증 성공
- [ ] Release 후보에서 백업 복구 미리보기 확인
- [ ] 정상 암호로 복구 성공
- [ ] 잘못된 암호에서 기존 데이터 유지
- [ ] 손상 파일에서 기존 데이터 유지
- [ ] 이전 백업 형식 호환성 확인

## 8. 배포

- [ ] GitHub Release 또는 승인된 배포 위치에 고정 서명 APK 업로드
- [ ] SHA-256과 서명 인증서 지문 게시
- [ ] Release 노트 게시
- [ ] 최소 Android 버전과 알려진 제한 사항 기록
- [ ] 롤백 APK와 복구 절차 내부 기록

## 9. 배포 후 확인

- [ ] 치명적 crash 증가 없음
- [ ] 데이터 손실 신고 없음
- [ ] 저장 실패 증가 없음
- [ ] 알림 누락 또는 중복 증가 없음
- [ ] AI 요청 실패·비용 이상 증가 없음
- [ ] 백업·복구 실패 증가 없음

치명적인 데이터 손실, 실행 불가, 서명 불일치가 확인되면 배포를 중단하고 이전 고정 서명 APK와 백업 절차로 롤백합니다.
```

- [ ] **Step 2: 기능 이슈 폼을 작성한다**

Create `.github/ISSUE_TEMPLATE/feature.yml`:

```yaml
name: 기능 제안
description: 사용자 문제와 완료 조건이 명확한 기능을 제안합니다.
title: "[Feature] "
labels: []
body:
  - type: markdown
    attributes:
      value: |
        하나의 이슈에는 하나의 사용자 가치만 작성해 주세요. 민감정보, API 키, 개인 데이터는 포함하지 마세요.

  - type: textarea
    id: user_problem
    attributes:
      label: 사용자 문제
      description: 현재 사용자가 어떤 상황에서 무엇을 하지 못하는지 설명합니다.
      placeholder: 사용자는 ...할 때 ... 때문에 작업을 완료하지 못합니다.
    validations:
      required: true

  - type: textarea
    id: user_flow
    attributes:
      label: 원하는 사용자 흐름
      description: 시작부터 완료까지 사용자가 보게 될 순서를 작성합니다.
      placeholder: 입력 → 검토 → 저장 → 결과 확인
    validations:
      required: true

  - type: textarea
    id: in_scope
    attributes:
      label: 포함 범위
      description: 이번 기능에서 반드시 제공할 항목을 작성합니다.
    validations:
      required: true

  - type: textarea
    id: out_of_scope
    attributes:
      label: 제외 범위
      description: 이번 이슈에서 다루지 않을 항목을 작성합니다.
    validations:
      required: true

  - type: textarea
    id: acceptance_criteria
    attributes:
      label: 완료 조건
      description: 사용자가 확인할 수 있는 문장으로 작성합니다.
      placeholder: |
        - 사용자가 ...하면 ...이 표시됩니다.
        - 실패하면 원본 입력이 유지됩니다.
    validations:
      required: true

  - type: checkboxes
    id: impact
    attributes:
      label: 영향 범위
      description: 해당하는 항목을 모두 선택합니다. 영향이 없으면 마지막 항목을 선택합니다.
      options:
        - label: Room 데이터 또는 migration
        - label: 알림 또는 정확한 알람
        - label: Android 권한 또는 Manifest
        - label: AI 요청, 비용 또는 개인정보
        - label: 백업 또는 복구 형식
        - label: 위젯
        - label: Release 또는 서명
        - label: 영향 없음
    validations:
      required: true

  - type: dropdown
    id: screen_change
    attributes:
      label: 화면 변경
      options:
        - 없음
        - 기존 화면 변경
        - 새 화면 추가
    validations:
      required: true

  - type: textarea
    id: test_scenarios
    attributes:
      label: 테스트 시나리오
      description: 정상, 실패, 경계 상황을 작성합니다.
    validations:
      required: true
```

- [ ] **Step 3: 버그 이슈 폼을 작성한다**

Create `.github/ISSUE_TEMPLATE/bug.yml`:

```yaml
name: 버그 신고
description: 재현 가능한 앱 문제를 신고합니다.
title: "[Bug] "
labels: []
body:
  - type: markdown
    attributes:
      value: |
        API 키, 개인 일정, 연락처, 원문 음성, 전체 백업 파일은 첨부하지 마세요. 로그와 스크린샷에서 개인정보를 제거해 주세요.

  - type: textarea
    id: problem
    attributes:
      label: 발생한 문제
      description: 실제로 관찰한 결과를 작성합니다.
    validations:
      required: true

  - type: textarea
    id: expected
    attributes:
      label: 기대 결과
      description: 정상이라면 어떤 결과가 나와야 하는지 작성합니다.
    validations:
      required: true

  - type: textarea
    id: reproduction
    attributes:
      label: 재현 절차
      description: 앱 실행부터 문제 발생까지 번호 순서로 작성합니다.
      placeholder: |
        1. 앱을 실행합니다.
        2. ... 화면으로 이동합니다.
        3. ...을 누릅니다.
        4. 문제가 발생합니다.
    validations:
      required: true

  - type: dropdown
    id: frequency
    attributes:
      label: 발생 빈도
      options:
        - 항상
        - 자주
        - 가끔
        - 한 번만 발생
    validations:
      required: true

  - type: input
    id: versions
    attributes:
      label: 앱과 Android 버전
      placeholder: MyBrain 2.0.0-alpha46, Android 15
    validations:
      required: true

  - type: checkboxes
    id: area
    attributes:
      label: 관련 영역
      options:
        - label: 빠른 입력 또는 저장
        - label: 일정 또는 할 일
        - label: AI 분석
        - label: 알림
        - label: 설정
        - label: 공유 또는 음성 입력
        - label: 백업 또는 복구
        - label: 위젯
        - label: 설치 또는 업데이트
    validations:
      required: true

  - type: checkboxes
    id: severity
    attributes:
      label: 영향
      options:
        - label: 앱 실행 불가 또는 crash
        - label: 데이터 손실 또는 중복 저장
        - label: 알림 누락 또는 중복
        - label: 개인정보 또는 보안 영향
        - label: 핵심 기능 사용 불가
        - label: 표시 또는 사용성 문제
    validations:
      required: true

  - type: textarea
    id: evidence
    attributes:
      label: 개인정보를 제거한 증거
      description: 필요한 로그, 스크린샷, 동영상, 관련 Actions run을 첨부합니다.
    validations:
      required: false
```

- [ ] **Step 4: PR 템플릿을 작성한다**

Create `.github/pull_request_template.md`:

```markdown
## 목적

이 변경이 해결하는 사용자 문제 또는 기술 문제를 한 문장으로 설명합니다.

## 변경 범위

- 

## 범위 밖 항목

- 

## 사용자 검증 시나리오

1. 
2. 
3. 

## 데이터·권한·AI 영향

해당하지 않으면 `해당 없음`과 이유를 적습니다.

- Room schema 또는 migration:
- 백업·복구 형식:
- Android 권한 또는 Manifest:
- AI 요청·비용·개인정보:
- 알림 또는 정확한 알람:
- 위젯:
- Release 또는 서명:

## 테스트 결과

- [ ] `bash scripts/check-repository-hygiene.sh`
- [ ] `bash scripts/check-development-baseline.sh`
- [ ] `gradle --stacktrace testDebugUnitTest`
- [ ] `gradle --stacktrace assembleDebug`
- [ ] `gradle --stacktrace assembleRelease` 또는 고정 서명 Release 빌드
- [ ] Debug 패키지 `kr.co.mybrain.v2.debug`
- [ ] Debug 앱 이름 `MyBrain AI Debug`
- [ ] APK 서명 검증

실행한 테스트 수, 실패 수, Actions run과 아티팩트를 기록합니다.

## 화면·기기 확인

화면 변경이 없으면 `해당 없음`이라고 적습니다.

- 확인한 Android 버전:
- 에뮬레이터 또는 기기:
- 스크린샷 또는 UI 결과:

## 롤백

문제가 발생했을 때 되돌릴 commit, 설정, 데이터 또는 APK 절차를 설명합니다.

## 체크리스트

- [ ] 하나의 사용자 가치 또는 기술 문제만 포함합니다.
- [ ] 새 비즈니스 규칙을 Activity에 직접 추가하지 않았습니다.
- [ ] 민감정보와 개인 데이터가 코드, 로그, 문서에 없습니다.
- [ ] 문서와 실제 동작이 일치합니다.
- [ ] 관련 이슈를 연결했습니다.
```

- [ ] **Step 5: 개발 기준선 검사를 다시 실행한다**

Run:

```bash
bash scripts/check-development-baseline.sh
```

Expected:

```text
Development baseline checks passed.
```

- [ ] **Step 6: Release와 템플릿 변경을 커밋한다**

```bash
git add docs/RELEASE_CHECKLIST_KO.md \
  .github/ISSUE_TEMPLATE/feature.yml \
  .github/ISSUE_TEMPLATE/bug.yml \
  .github/pull_request_template.md
git commit -m "docs: add release and contribution templates"
```

---

### Task 7: 전체 검증과 `v2` 대상 개발 기준선 PR 생성

**Files:**
- No planned file changes unless verification finds a concrete defect.
- GitHub PR: `chore/development-baseline` → `v2`.

**Interfaces:**
- Consumes: complete baseline files, existing Android tests and build workflow.
- Produces: green, reviewable, non-product-code baseline PR.

- [ ] **Step 1: 변경 파일이 허용 범위에만 있는지 확인한다**

Run:

```bash
git diff --name-only v2...HEAD | tee development-baseline-files.txt
if grep -E '^app/|^build\.gradle$|^settings\.gradle$|^gradle\.properties$' development-baseline-files.txt; then
  echo '제품 코드 또는 빌드 설정이 변경되었습니다.' >&2
  exit 1
fi
```

Expected: product paths 출력 없음.

Allowed paths:

```text
.github/ISSUE_TEMPLATE/bug.yml
.github/ISSUE_TEMPLATE/feature.yml
.github/pull_request_template.md
.github/workflows/build-v2.yml
CONTRIBUTING.md
README.md
docs/ARCHITECTURE_KO.md
docs/DEVELOPMENT_ROADMAP_KO.md
docs/RELEASE_CHECKLIST_KO.md
docs/superpowers/plans/2026-08-05-development-baseline.md
docs/superpowers/specs/2026-08-05-development-baseline-design.md
scripts/check-development-baseline.sh
```

- [ ] **Step 2: 정적 검사와 diff 검사를 실행한다**

Run:

```bash
bash scripts/check-repository-hygiene.sh
bash scripts/check-development-baseline.sh
git diff --check v2...HEAD
```

Expected: 두 script 성공, `git diff --check` 출력 없음.

- [ ] **Step 3: 기존 Android 검증을 실행한다**

Run:

```bash
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Expected:

```text
기존 단위 테스트 59개 이상
failures = 0
Debug APK 생성
Release Secrets가 없으면 app-release-unsigned.apk 생성
```

- [ ] **Step 4: Debug APK 메타데이터와 서명을 확인한다**

Run:

```bash
APK="app/build/outputs/apk/debug/app-debug.apk"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
"$AAPT2" dump badging "$APK" | tee debug-apk-badging.txt
grep -F "package: name='kr.co.mybrain.v2.debug'" debug-apk-badging.txt
grep -F "application-label:'MyBrain AI Debug'" debug-apk-badging.txt
"$APKSIGNER" verify --verbose --print-certs "$APK" | tee debug-apk-signature-report.txt
grep -F 'Verified using v2 scheme (APK Signature Scheme v2): true' debug-apk-signature-report.txt
sha256sum "$APK" > debug-apk.sha256
```

- [ ] **Step 5: Draft PR을 생성한다**

Title:

```text
chore: add the V2 continuous development baseline
```

Body:

```markdown
## 목적

`v2`에서 앱 개발을 계속할 수 있도록 기여 절차, 현재 코드에 맞는 아키텍처 규칙, P0~P3 로드맵, Release 체크리스트와 GitHub 이슈·PR 템플릿을 추가합니다.

## 변경 사항

- 표준 작업 브랜치 `feature/**`, `fix/**`, `chore/**`, `docs/**`, `test/**` CI 지원
- 개발 기준선 자동 검사 추가
- `CONTRIBUTING.md`와 README 문서 진입점 추가
- 현재 `assistant`, `data`, `reminder`, `settings`, `share`, `transfer`, `ui`, `voice`, `widget` 책임 문서화
- Activity, Policy, Parser, Controller, Repository 경계와 점진적 리팩터링 규칙 정의
- P0~P3 개발 로드맵 추가
- Release 서명·해시·설치·백업 체크리스트 추가
- 기능·버그 이슈 폼과 PR 템플릿 추가

## 검증

- [ ] 저장소 위생 검사
- [ ] 개발 기준선 검사
- [ ] 기존 전체 단위 테스트
- [ ] Debug APK 빌드와 패키지·앱 이름·서명 검사
- [ ] 미서명 또는 고정 서명 Release 컴파일
- [ ] 제품 코드·Room schema·권한·버전 변경 없음

## 롤백

이 PR은 문서, 템플릿, Bash 검사, CI 브랜치 패턴만 변경합니다. 문제가 있으면 squash merge commit을 revert합니다.
```

Create as draft with base `v2` and head `chore/development-baseline`.

- [ ] **Step 6: PR workflow와 아티팩트를 확인한다**

Required successful steps:

```text
저장소 위생 검사
개발 기준선 검사
V2 단위 테스트
Debug APK 빌드
Debug APK 패키지와 서명 검증
미서명 Release 컴파일 검증 또는 고정 서명 Release APK 빌드
```

Required artifacts:

```text
MyBrainAI-v2-unit-test-report
MyBrainAI-v2-build-log
MyBrainAI-v2-debug
MyBrainAI-v2-unsigned-release-check 또는 MyBrainAI-v2-release
```

- [ ] **Step 7: PR 본문에 실제 증거를 기록하고 ready로 전환한다**

Record:

```text
workflow run number and ID
unit test count and zero failures
Debug APK SHA-256
Debug signer SHA-256
unsigned or signed Release APK SHA-256
changed-file scope check
```

Request review from `KANG77556`. Do not merge automatically.

---

### Task 8: 개발 기준선 PR 병합과 병합 후 확인

**Files:**
- No planned file changes beyond merge result.

**Interfaces:**
- Consumes: explicit user command `병합`, green baseline PR, exact latest head SHA.
- Produces: `v2` containing development baseline and green post-merge workflow.

- [ ] **Step 1: 병합 직전 PR을 다시 검증한다**

Verify:

```text
state = open
isDraft = false
mergeable = true
base = v2
head = chore/development-baseline
all required checks = success
unresolved review threads = 0
```

- [ ] **Step 2: 사용자 `병합` 승인 후 expected head SHA로 squash merge한다**

Commit title:

```text
chore: add the V2 continuous development baseline
```

Commit body:

```text
Document the contribution workflow, package responsibilities, development roadmap, release verification, and GitHub issue/PR intake while preserving application behavior.
```

- [ ] **Step 3: 병합 commit의 push workflow를 확인한다**

Required: Task 7과 동일한 checks and artifacts.

- [ ] **Step 4: 기본 브랜치와 보존 refs가 유지되는지 확인한다**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch')" = 'v2'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

---

### Task 9: 로드맵 후속 이슈 등록

**Files:**
- No repository file changes.
- Create six GitHub issues.

**Interfaces:**
- Consumes: merged roadmap and issue forms.
- Produces: actionable P0 work queue and P1~P3 tracking issues.

- [ ] **Step 1: 고정 서명 Release 검증 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P0: Verify signed Release path with GitHub Secrets' \
  --body-file - <<'EOF'
## 사용자 문제

현재 Secrets가 없는 CI에서 미서명 Release 컴파일만 검증됐기 때문에 정식 업데이트 가능한 APK의 고정 서명 경로를 별도로 증명해야 합니다.

## 완료 조건

- GitHub Secrets가 있는 실행에서 `MyBrainAI-v2-release` 생성
- APK Signature Scheme v2와 v3 성공
- signer SHA-256이 `EXPECTED_CERT_SHA256`과 일치
- 신규 설치와 기존 정식 앱 위 업데이트 설치 성공
- APK SHA-256과 서명 보고서 보관

## 테스트

- representative V2 workflow
- Android 15 신규 설치
- 이전 정식 APK에서 업데이트 설치
EOF
```

- [ ] **Step 2: Room schema와 migration 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P0: Export Room schemas and define migration baseline' \
  --body-file - <<'EOF'
## 사용자 문제

DB 스키마 변경 시 migration 누락과 백업 호환성 회귀를 자동으로 발견할 기준선이 필요합니다.

## 완료 조건

- Room schema export 경로와 버전 관리 정책 확정
- 현재 schema JSON 저장
- migration 누락 시 CI 실패
- CRUD, transaction, 중복 저장, migration 계측 테스트 성공
- 백업·복구 호환성과 롤백 문서화

## 범위 밖

사용자 기능과 화면 변경은 포함하지 않습니다.
EOF
```

- [ ] **Step 3: Android 15 smoke test 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P0: Add Android 15 launch smoke test' \
  --body-file - <<'EOF'
## 사용자 문제

JVM 테스트와 APK 빌드 성공만으로는 실제 Android 15 설치·실행과 초기 DB 생성 실패를 발견할 수 없습니다.

## 완료 조건

- Debug APK와 AndroidTest APK 빌드
- Android 15 규격 에뮬레이터 설치
- 런처 Activity 실행
- 프로세스 생존과 치명적 예외 없음
- 초기 Room Database 생성 확인
- logcat과 테스트 보고서 아티팩트 보관
EOF
```

- [ ] **Step 4: 핵심 입력 흐름 추적 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P1: Define quick-entry review and save acceptance scenarios' \
  --body-file - <<'EOF'
## 사용자 흐름

빠른 입력 → 로컬 또는 AI 분석 → 결과 검토·수정 → 저장 → 할 일·일정·메모 조회

## 완료 조건

- 오프라인 로컬 입력
- AI 실패 시 원본 보존과 대체 경로
- 모호한 날짜 검토
- 중복 탭 저장 방지
- 일정 충돌 설명과 선택권
- 저장 후 알림·위젯 반영
EOF
```

- [ ] **Step 5: 알림·시간대 추적 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P2: Verify reminder rescheduling across reboot and timezone changes' \
  --body-file - <<'EOF'
## 사용자 문제

재부팅, 앱 업데이트, 시간·시간대 변경 후 알림이 누락되거나 중복되지 않아야 합니다.

## 완료 조건

- 재부팅과 앱 업데이트 후 재예약
- 시간·시간대 변경 후 일관된 실행 시각
- 반복 일정 경계값 테스트
- 권한 거부 제한 동작
- Android 15 검증
- 중복 알림 없음
EOF
```

- [ ] **Step 6: 암호화 백업 호환성 추적 이슈를 만든다**

```bash
gh issue create --repo KANG77556/MyBrain-v3 \
  --title 'P3: Validate encrypted backup restore compatibility' \
  --body-file - <<'EOF'
## 사용자 문제

업데이트와 데이터 형식 변경 후에도 암호화 백업이 기존 데이터를 훼손하지 않고 복구되어야 합니다.

## 완료 조건

- 정상 백업 생성·복호화
- 복구 전 미리보기
- 잘못된 암호에서 기존 데이터 유지
- 손상 파일에서 기존 데이터 유지
- 일부 충돌 처리
- 이전 백업 형식 호환성 정책과 테스트
EOF
```

- [ ] **Step 7: 생성된 이슈를 우선순위별로 확인한다**

Run:

```bash
gh issue list --repo KANG77556/MyBrain-v3 --state open --limit 50 \
  --search 'P0: OR P1: OR P2: OR P3:'
```

Expected: 위 여섯 제목이 모두 표시된다.

---

## Plan Self-Review

- 설계의 브랜치 흐름, 문서 4종, GitHub 템플릿 3종, 코드 배치 규칙, 오류 처리, 테스트 전략, P0~P3 로드맵, Release 체크리스트, 롤백을 모두 작업에 연결했다.
- PR #91 병합과 관리자 기본 브랜치 변경을 개발 기준선 구현보다 앞선 독립 게이트로 두었다.
- `chore/development-baseline` push가 실제 CI를 실행하도록 `feature/**`, `fix/**`, `chore/**`, `docs/**`, `test/**` 패턴을 구체적으로 추가했다.
- 제품 코드, Room 스키마, 권한, 앱 버전, Release Secrets를 변경하는 단계가 없다.
- Bash 검사에서 필수 파일, README 링크, 명령, 패키지 책임, P0~P3, Release 서명·해시·설치·백업, 이슈·PR 템플릿과 workflow 연결을 확인한다.
- 모든 생성 파일의 실제 내용을 계획에 포함했고 미정 값이나 구현을 뒤로 미루는 표기를 두지 않았다.
- Activity, Policy, Parser, Controller, Repository 용어가 설계와 모든 문서에서 동일하다.
- 대규모 리팩터링 대신 큰 파일 수정 시 관련 책임만 추출하는 점진적 원칙을 유지했다.
- 개발 기준선 PR 병합 후 등록할 여섯 이슈의 제목, 본문, 완료 조건을 실제 명령으로 정의했다.
- 삭제 작업은 기본 브랜치 전환 후 7일 관찰 기간 밖의 별도 범위로 유지했다.
