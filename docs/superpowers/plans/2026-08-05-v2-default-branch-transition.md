# MyBrain-v3 V2 Default Branch Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the current `main` exactly, establish the verified V2 tree as long-lived branch `v2`, prepare CI and documentation for `v2`, and leave the administrator-only default-branch switch as a documented, reversible final action.

**Architecture:** Branch references are created from immutable, pre-verified commit SHAs before any file changes. All transition code and documentation changes live on `cleanup/v2-default-branch-transition`, which starts from `v2` and is reviewed through a pull request back to `v2`. The current `main` ref never moves, so rollback after the administrative default-branch switch is a settings-only operation.

**Tech Stack:** GitHub branches and pull requests, GitHub Actions, Bash, Android Gradle Plugin, Java 17, Android SDK 35, Gradle 8.9, Markdown.

## Global Constraints

- Repository: `KANG77556/MyBrain-v3`.
- Current default branch remains `main` until the transition PR is merged and its CI is green.
- Current `main` must remain at `55066aab295f7434a7ed413166fa5cfb1a377ede` throughout this implementation.
- `backup/main-pre-v2-20260805` must start at exactly `55066aab295f7434a7ed413166fa5cfb1a377ede`.
- `v2` must start at exactly `3bf2e75b7e5f0d62da7139db40e24b9e67b8df32`.
- Transition work branch: `cleanup/v2-default-branch-transition`, starting from `v2`.
- Do not delete or rename `main`, `feature/personal-1.1-widgets`, backup branches, historical workflows, or QA PR #89.
- Do not change application features, UI behavior, Room schema, version code, version name, package names, or signing secrets.
- Preserve Java 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9, `compileSdk 35`, `targetSdk 35`, and `minSdk 26`.
- The GitHub connector does not expose an administrator-level repository-default-branch mutation; the final `main` → `v2` settings change is performed by the repository owner in GitHub Settings.
- The transition PR targets `v2`, not `main` and not `feature/personal-1.1-widgets`.
- No branch or workflow cleanup occurs until at least seven days after the administrator changes the default branch.

---

## File Map

- Create: `docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md` — approved transition architecture, exact SHAs, rollback, and observation-period rules.
- Create: `docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md` — this task-by-task implementation plan.
- Modify: `scripts/check-repository-hygiene.sh` — require the representative V2 workflow to include `v2` in both push and pull-request target lists.
- Modify: `.github/workflows/build-v2.yml` — add `v2` to push and pull-request branch filters while retaining existing compatibility filters.
- Modify: `README.md` — identify `v2` as the long-lived integration/default-branch candidate and explain legacy preservation.
- Modify: `docs/BRANCH_POLICY_KO.md` — define `v2`, freeze `main`, and retain the former feature branch during observation.
- Create: `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md` — owner checklist for default-branch switch, ruleset setup, verification, rollback, and seven-day observation.
- GitHub refs: `backup/main-pre-v2-20260805`, `v2`, `cleanup/v2-default-branch-transition`.
- GitHub PR: `cleanup/v2-default-branch-transition` → `v2`.

---

### Task 1: Verify immutable source refs and create preservation branches

**Files:**
- No repository file changes.
- Create GitHub refs: `backup/main-pre-v2-20260805`, `v2`, `cleanup/v2-default-branch-transition`.

**Interfaces:**
- Consumes: repository metadata, `main` HEAD, `feature/personal-1.1-widgets` HEAD.
- Produces: three refs with exact, verified starting SHAs used by every later task.

- [ ] **Step 1: Read the repository and source branch heads**

Run:

```bash
gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch'
gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha'
gh api repos/KANG77556/MyBrain-v3/branches/feature/personal-1.1-widgets --jq '.commit.sha'
```

Expected output, in order:

```text
main
55066aab295f7434a7ed413166fa5cfb1a377ede
3bf2e75b7e5f0d62da7139db40e24b9e67b8df32
```

Stop without creating refs if any value differs.

- [ ] **Step 2: Confirm the target branch names do not already point elsewhere**

Run:

```bash
for branch in backup/main-pre-v2-20260805 v2 cleanup/v2-default-branch-transition; do
  gh api "repos/KANG77556/MyBrain-v3/branches/$branch" --silent 2>/dev/null \
    && { echo "Branch already exists: $branch"; exit 1; } \
    || true
done
```

Expected: no output and exit code `0`.

If a branch exists, read its SHA. Continue only when it already matches the required SHA; never force-update an unexpected ref.

- [ ] **Step 3: Create the immutable `main` backup ref**

Run:

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/backup/main-pre-v2-20260805' \
  -f sha='55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Expected: the response contains `refs/heads/backup/main-pre-v2-20260805` and object SHA `55066aab295f7434a7ed413166fa5cfb1a377ede`.

- [ ] **Step 4: Create the long-lived `v2` ref**

Run:

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/v2' \
  -f sha='3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

Expected: the response contains `refs/heads/v2` and object SHA `3bf2e75b7e5f0d62da7139db40e24b9e67b8df32`.

- [ ] **Step 5: Verify both preservation refs before creating a work branch**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Expected: exit code `0` and no output.

- [ ] **Step 6: Create the transition work branch from `v2`**

Run:

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/cleanup/v2-default-branch-transition' \
  -f sha='3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

Expected: the response contains `refs/heads/cleanup/v2-default-branch-transition` with the same starting SHA as `v2`.

---

### Task 2: Commit the approved design and implementation plan

**Files:**
- Create: `docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md`
- Create: `docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md`

**Interfaces:**
- Consumes: the approved design and this implementation plan.
- Produces: auditable transition requirements and execution steps on the work branch.

- [ ] **Step 1: Add the approved design document unchanged**

Create `docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md` with the approved content beginning:

```markdown
# MyBrain-v3 V2 기본 브랜치 단계적 전환 설계

- 작성일: 2026-08-05
- 저장소: `KANG77556/MyBrain-v3`
- 현재 기본 브랜치: `main`
- 현재 `main` 기준 커밋: `55066aab295f7434a7ed413166fa5cfb1a377ede`
- 현재 V2 통합 기준 커밋: `3bf2e75b7e5f0d62da7139db40e24b9e67b8df32`
- 목표 기본 브랜치: `v2`
- 기존 `main` 보존 브랜치: `backup/main-pre-v2-20260805`
- 전환 작업 브랜치: `cleanup/v2-default-branch-transition`
```

Include all approved sections through `## 11. 설계 자체 점검` without changing SHA values, branch names, or the seven-day observation rule.

- [ ] **Step 2: Add this complete implementation plan**

Create `docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md` with this document in full.

- [ ] **Step 3: Verify the two documents contain exact transition identifiers**

Run:

```bash
grep -F '55066aab295f7434a7ed413166fa5cfb1a377ede' \
  docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md \
  docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
grep -F '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32' \
  docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md \
  docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
grep -F 'backup/main-pre-v2-20260805' \
  docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md \
  docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
```

Expected: each command prints matches from both files.

- [ ] **Step 4: Commit the transition documentation**

```bash
git add docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md \
  docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
git commit -m "docs: plan V2 default branch transition"
```

---

### Task 3: Add a failing guard for missing `v2` workflow triggers

**Files:**
- Modify: `scripts/check-repository-hygiene.sh`

**Interfaces:**
- Consumes: `.github/workflows/build-v2.yml`.
- Produces: a deterministic failure until `v2` appears once in the push filter and once in the pull-request filter.

- [ ] **Step 1: Add a count-based `v2` trigger assertion**

Insert immediately after the existing `cleanup/**` workflow assertion:

```bash
v2_trigger_count="$(grep -Fc '      - "v2"' .github/workflows/build-v2.yml || true)"
if ((v2_trigger_count < 2)); then
  fail '대표 V2 워크플로가 v2 push와 pull request 대상을 모두 감시하지 않습니다.'
fi
```

The count threshold is `2`: one list item under `push.branches` and one under `pull_request.branches`.

- [ ] **Step 2: Run the guard and verify the expected failure**

Run:

```bash
bash scripts/check-repository-hygiene.sh
```

Expected: exit code `1` and this message:

```text
ERROR: 대표 V2 워크플로가 v2 push와 pull request 대상을 모두 감시하지 않습니다.
```

No secret, signing-material, package, or application-label checks may newly fail.

- [ ] **Step 3: Commit only the failing guard**

```bash
git add scripts/check-repository-hygiene.sh
git commit -m "test: require V2 workflow coverage for v2"
```

---

### Task 4: Enable `v2` CI and update branch-transition documentation

**Files:**
- Modify: `.github/workflows/build-v2.yml`
- Modify: `README.md`
- Modify: `docs/BRANCH_POLICY_KO.md`
- Create: `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`

**Interfaces:**
- Consumes: the failing guard from Task 3, existing representative workflow, exact preservation refs.
- Produces: green repository hygiene checks, CI coverage for `v2`, and an owner-executable transition/rollback checklist.

- [ ] **Step 1: Add `v2` to the workflow push filter**

Change the push branch list to exactly:

```yaml
  push:
    branches:
      - "v2"
      - "rebuild/v2"
      - "rebuild/v2-*"
      - "feature/personal-1.1-widgets"
      - "cleanup/**"
```

- [ ] **Step 2: Add `v2` to the workflow pull-request filter**

Change the pull-request branch list to exactly:

```yaml
  pull_request:
    branches:
      - "v2"
      - "main"
      - "rebuild/v2"
      - "feature/personal-1.1-widgets"
```

Keep `main`, `rebuild/v2`, and `feature/personal-1.1-widgets` during the observation period.

- [ ] **Step 3: Replace the README project-status paragraph**

Replace the first project-description paragraph with:

```markdown
AI 기반 메모·할 일·일정 관리 Android 애플리케이션입니다. 장기 V2 통합 기준선과 기본 브랜치 전환 대상은 `v2`입니다. 전환 전 레거시 기준선은 `main`과 `backup/main-pre-v2-20260805`에 보존하며, 이전 통합 브랜치 `feature/personal-1.1-widgets`는 전환 후 7일 관찰 기간 동안 호환 참조로 유지합니다. 기존 v1.10.1 소스는 `backup/legacy-v1.10.1`에 별도로 보관되어 있습니다.
```

Replace the `## 브랜치와 기여` paragraph with:

```markdown
브랜치 역할과 병합 기준은 `docs/BRANCH_POLICY_KO.md`를 따릅니다. 신규 기능과 저장소 정리 PR은 `v2`를 대상으로 합니다. `main`은 전환 전 레거시 기준선으로 동결하며, GitHub 기본 브랜치 변경과 롤백 절차는 `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`에 기록합니다.
```

- [ ] **Step 4: Replace `docs/BRANCH_POLICY_KO.md` with the transition policy**

Use:

```markdown
# MyBrain-v3 브랜치 운영 정책

## 역할

- `v2`: 장기 V2 통합 기준선이자 기본 브랜치 전환 대상입니다. 신규 기능과 저장소 정리 PR은 `v2`를 대상으로 합니다.
- `main`: 전환 전 레거시 공개 기준선입니다. `55066aab295f7434a7ed413166fa5cfb1a377ede`에서 동결하며 직접 push, force push, 기능 병합을 금지합니다.
- `backup/main-pre-v2-20260805`: 전환 직전 `main`의 추가 보존 지점이며 `55066aab295f7434a7ed413166fa5cfb1a377ede`를 유지합니다.
- `feature/personal-1.1-widgets`: 이전 V2 통합 기준선입니다. 기본 브랜치 전환 후 최소 7일 동안 호환 참조로 유지합니다.
- `backup/*`: 삭제하지 않는 보존 지점입니다.
- `feature/*`: 하나의 기능 또는 검증 목적을 가진 작업 브랜치입니다.
- `cleanup/*`: 기능을 바꾸지 않는 저장소·빌드·문서 정리 브랜치입니다.

## 병합 원칙

1. 신규 PR의 base는 `v2`입니다.
2. 단위 테스트, 저장소 위생 검사, Debug APK 패키지·이름·서명 검사, Release 컴파일 검증이 성공해야 합니다.
3. 정리 PR은 제품 기능과 저장소 구조 변경을 섞지 않습니다.
4. GitHub 기본 브랜치 변경은 `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`의 체크리스트와 롤백 절차를 따릅니다.
5. `main`, 백업 브랜치, 이전 V2 브랜치, 과거 워크플로, QA PR #89는 기본 브랜치 전환 후 최소 7일 동안 삭제하지 않습니다.
6. 관찰 기간 종료 후 정리는 별도 설계와 PR에서 수행합니다.

## 금지 사항

- `main`, `v2`, `backup/*` 강제 push
- `main` 또는 백업 브랜치 삭제
- 공개 저장소에 키스토어·토큰·비밀번호 추가
- 검증되지 않은 APK를 Release로 표기
- 기본 브랜치 전환과 과거 브랜치·워크플로 대량 삭제를 같은 변경으로 수행
```

- [ ] **Step 5: Create the owner transition and rollback guide**

Create `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md` with:

```markdown
# V2 기본 브랜치 전환 및 롤백 절차

## 전환 전 고정값

- 기존 기본 브랜치: `main`
- 기존 `main` SHA: `55066aab295f7434a7ed413166fa5cfb1a377ede`
- 보존 브랜치: `backup/main-pre-v2-20260805`
- 새 장기 브랜치: `v2`
- 초기 `v2` SHA: `3bf2e75b7e5f0d62da7139db40e24b9e67b8df32`
- 대표 상태 검사: `Build MyBrain AI V2 / build`

## 코드 전환 준비 확인

1. `backup/main-pre-v2-20260805`가 기존 `main` SHA를 가리키는지 확인합니다.
2. `main`이 같은 SHA에서 변경되지 않았는지 확인합니다.
3. 전환 준비 PR이 `v2`에 병합됐는지 확인합니다.
4. 병합 커밋의 `Build MyBrain AI V2`가 성공했는지 확인합니다.
5. QA PR #89와 기존 브랜치·워크플로가 삭제되지 않았는지 확인합니다.

## GitHub 기본 브랜치 변경

저장소 소유자가 GitHub 웹에서 수행합니다.

1. **Settings → General → Default branch**로 이동합니다.
2. 기본 브랜치를 `main`에서 `v2`로 변경합니다.
3. 변경 경고를 확인하고 적용합니다.
4. 저장소 첫 화면과 새 PR 화면에서 기본 대상이 `v2`인지 확인합니다.

## v2 Ruleset 권장값

- Pull Request를 통한 변경 요구
- 필수 상태 검사: `Build MyBrain AI V2 / build`
- 미해결 리뷰 대화가 있으면 병합 금지
- force push 금지
- 브랜치 삭제 금지

`main`과 `backup/main-pre-v2-20260805`에도 최소한 force push 및 삭제 금지를 적용합니다.

## 전환 직후 검증

1. 저장소 API의 `default_branch`가 `v2`인지 확인합니다.
2. `main`과 백업 브랜치 SHA가 기존 값인지 확인합니다.
3. 문서 전용 테스트 PR을 열었을 때 base가 자동으로 `v2`인지 확인합니다.
4. `v2`에서 `Build MyBrain AI V2`가 실행되는지 확인합니다.
5. QA PR #89의 base와 상태가 의도치 않게 변경되지 않았는지 확인합니다.

## 롤백

치명적인 장애나 PR 대상 혼선이 발생하면:

1. **Settings → General → Default branch**에서 기본 브랜치를 다시 `main`으로 변경합니다.
2. `main`이 `55066aab295f7434a7ed413166fa5cfb1a377ede`인지 확인합니다.
3. 신규 PR의 기본 대상이 `main`인지 확인합니다.
4. `v2`와 백업 브랜치는 삭제하거나 강제 이동하지 않습니다.
5. 원인 분석과 수정은 별도 `cleanup/*` 브랜치와 PR로 진행합니다.

## 관찰 기간

기본 브랜치 변경 시각부터 최소 7일 동안 `main`, 백업 브랜치, `feature/personal-1.1-widgets`, 과거 워크플로, QA PR #89를 삭제하지 않습니다. 2026-08-05에 전환하면 최소 2026-08-12 KST까지 삭제 작업을 금지합니다.
```

- [ ] **Step 6: Run the repository guard and document consistency checks**

Run:

```bash
bash scripts/check-repository-hygiene.sh
test "$(grep -Fc '      - "v2"' .github/workflows/build-v2.yml)" -ge 2
grep -F 'backup/main-pre-v2-20260805' README.md docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
grep -F '55066aab295f7434a7ed413166fa5cfb1a377ede' docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
grep -F 'Build MyBrain AI V2 / build' docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
```

Expected: hygiene output `Repository hygiene checks passed.` and all grep commands print matching lines.

- [ ] **Step 7: Commit CI and operational documentation**

```bash
git add .github/workflows/build-v2.yml scripts/check-repository-hygiene.sh README.md \
  docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
git commit -m "ci: prepare v2 as default branch"
```

---

### Task 5: Validate the transition branch and open the `v2` PR

**Files:**
- No additional code changes unless verification identifies a specific defect.
- Create GitHub PR: `cleanup/v2-default-branch-transition` → `v2`.

**Interfaces:**
- Consumes: transition work branch, representative V2 workflow, exact backup refs.
- Produces: reviewable PR with branch-integrity and CI evidence, without changing repository default settings.

- [ ] **Step 1: Re-verify branch integrity immediately before opening the PR**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

Expected: exit code `0` and no output.

- [ ] **Step 2: Run static and Android verification**

Run:

```bash
bash scripts/check-repository-hygiene.sh
git diff --check v2...HEAD
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Expected: all commands exit `0`; the unit suite reports at least 59 tests and no failures; Debug and unsigned Release APKs are created.

- [ ] **Step 3: Open a Draft PR to `v2`**

Title:

```text
chore: prepare v2 as the default branch
```

Body:

```markdown
## 목적

현재 `main`을 이동하지 않고 정확히 보존한 상태에서, 검증된 V2 기준선을 장기 브랜치 `v2`로 운영하고 GitHub 기본 브랜치 전환을 준비합니다.

## 보존 지점

- `main`: `55066aab295f7434a7ed413166fa5cfb1a377ede`
- `backup/main-pre-v2-20260805`: `55066aab295f7434a7ed413166fa5cfb1a377ede`
- 초기 `v2`: `3bf2e75b7e5f0d62da7139db40e24b9e67b8df32`

## 변경 사항

- 대표 V2 워크플로의 push 및 pull request 대상에 `v2` 추가
- 저장소 위생 검사에서 `v2` 트리거 누락 방지
- README와 브랜치 정책을 `v2` 장기 기준선에 맞게 갱신
- 소유자용 기본 브랜치 변경, Ruleset, 검증, 롤백 절차 추가
- `main`, 기존 V2 브랜치, 과거 워크플로, QA PR #89는 변경하거나 삭제하지 않음

## 검증

- [ ] 저장소 위생 검사
- [ ] 59개 이상 단위 테스트
- [ ] Debug APK 빌드
- [ ] `kr.co.mybrain.v2.debug` 및 `MyBrain AI Debug` 확인
- [ ] APK Signature Scheme v2 확인
- [ ] 미서명 Release 컴파일 또는 고정 서명 Release 검증
- [ ] `main`과 백업 브랜치 SHA 불변 확인

## 병합 후 수동 단계

이 PR 병합만으로 GitHub 기본 브랜치는 변경되지 않습니다. 저장소 소유자가 `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`에 따라 **Settings → General → Default branch**에서 `v2`로 변경해야 합니다.
```

Create the PR as draft with base `v2` and head `cleanup/v2-default-branch-transition`.

- [ ] **Step 4: Inspect the PR-triggered workflow**

Required successful steps:

- 저장소 위생 검사
- V2 단위 테스트
- Debug APK 빌드
- Debug APK 패키지와 서명 검증
- 미서명 Release 컴파일 검증 or signed Release path

Required artifacts:

- `MyBrainAI-v2-unit-test-report`
- `MyBrainAI-v2-build-log`
- `MyBrainAI-v2-debug`
- `MyBrainAI-v2-unsigned-release-check` or `MyBrainAI-v2-release`

- [ ] **Step 5: Update PR evidence and mark ready for review**

Add the workflow run number, conclusion, test count, Debug package/application label, signing result, artifact names, and the three verified branch SHAs to the PR body. Mark the PR ready only when all required checks are green.

- [ ] **Step 6: Stop before merge**

Do not merge automatically. Present the PR URL and request the explicit command `병합` before merging into `v2`.

---

### Task 6: Merge to `v2` and verify the merge commit

**Files:**
- No planned repository file changes beyond the PR merge result.

**Interfaces:**
- Consumes: explicit user authorization to merge, green transition PR, unchanged preservation refs.
- Produces: `v2` containing transition documentation and workflow coverage, while `main` remains unchanged.

- [ ] **Step 1: Re-read the PR and latest CI immediately before merge**

Verify:

```text
base = v2
head = cleanup/v2-default-branch-transition
mergeable = true
all required workflow steps = success
main SHA = 55066aab295f7434a7ed413166fa5cfb1a377ede
backup SHA = 55066aab295f7434a7ed413166fa5cfb1a377ede
```

- [ ] **Step 2: Merge with an expected head SHA**

Use squash merge with the exact latest PR head SHA to prevent merging stale or moved code.

Commit title:

```text
chore: prepare v2 as the default branch
```

Commit message:

```text
Add v2 workflow coverage and document the reversible GitHub default-branch transition while preserving the legacy main ref.
```

- [ ] **Step 3: Verify the post-merge workflow**

The push to `v2` must start `Build MyBrain AI V2`. Confirm the same required steps and artifacts as Task 5.

- [ ] **Step 4: Verify preservation refs remain unchanged**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Expected: exit code `0`.

---

### Task 7: Perform the administrator-only default-branch switch and observation

**Files:**
- No code changes.
- GitHub repository settings and rulesets.

**Interfaces:**
- Consumes: merged and green `v2`, owner/admin access, `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`.
- Produces: repository `default_branch = v2`, protected long-lived branch, reversible observation period.

- [ ] **Step 1: Repository owner changes the default branch**

In GitHub:

```text
Settings → General → Default branch → Switch to another branch → v2 → Update
```

- [ ] **Step 2: Apply the `v2` ruleset**

Configure:

```text
Require a pull request before merging
Require status check: Build MyBrain AI V2 / build
Require conversation resolution before merging
Block force pushes
Restrict deletions
```

Protect `main` and `backup/main-pre-v2-20260805` from force pushes and deletions.

- [ ] **Step 3: Verify repository metadata and refs**

Run:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch')" = 'v2'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup/main-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Expected: exit code `0`.

- [ ] **Step 4: Verify new PR targeting and unchanged QA state**

Open the new-PR screen and confirm `v2` is preselected as base. Confirm PR #89 remains open/draft with its existing base and has not been retargeted or closed.

- [ ] **Step 5: Start the seven-day observation period**

Record the exact KST change timestamp. Until the timestamp plus seven days:

```text
Do not delete main
Do not delete backup/main-pre-v2-20260805
Do not delete feature/personal-1.1-widgets
Do not delete historical workflows
Do not close QA PR #89 as part of this transition
```

- [ ] **Step 6: Roll back settings if a critical issue occurs**

Change GitHub default branch back to `main`, verify repository metadata, and leave `v2` plus all backup refs intact for diagnosis. Do not force-push or rewrite history.

---

## Plan Self-Review

- Every approved design requirement maps to a task: immutable backup, `v2` creation, transition work branch, CI trigger changes, documentation, PR/CI gate, admin switch, rollback, and observation period.
- Exact SHA values and branch names are consistent throughout the plan.
- The plan contains no placeholder values, deferred implementation wording, or ambiguous merge target.
- The failing guard in Task 3 proves that the workflow lacks `v2` coverage before the YAML change and passes only after Task 4.
- Destructive cleanup remains explicitly outside scope and cannot occur before the seven-day observation period.
- Administrator-only actions are separated from connector-executable branch and PR actions.
