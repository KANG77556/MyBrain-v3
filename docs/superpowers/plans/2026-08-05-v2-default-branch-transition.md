# MyBrain-v3 V2 Default Branch Transition Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` and verify every destructive boundary before continuing.

**Goal:** Preserve the current legacy `main` exactly, establish the verified V2 tree as long-lived branch `v2`, prepare CI and documentation for `v2`, and leave the administrator-only default-branch switch reversible.

**Architecture:** Branch references are created from immutable, pre-verified commit SHAs. All code and documentation changes live on `cleanup/v2-default-branch-transition`, which is reviewed through a pull request to `v2`. The `main` ref never moves, so rollback after the settings change is performed by selecting `main` as the default branch again.

**Tech Stack:** GitHub branches and pull requests, GitHub Actions, Bash, Android Gradle Plugin, Java 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9, Markdown.

## Fixed identifiers

```text
Repository: KANG77556/MyBrain-v3
Legacy main SHA: 55066aab295f7434a7ed413166fa5cfb1a377ede
Initial verified V2 SHA: 3bf2e75b7e5f0d62da7139db40e24b9e67b8df32
Legacy backup branch: backup/main-pre-v2-20260805
Long-lived V2 branch: v2
Transition branch: cleanup/v2-default-branch-transition
Transition PR base: v2
```

## Global constraints

- Do not force-push, rename, or delete `main`.
- Do not force-push or delete `backup/main-pre-v2-20260805`.
- Do not delete `feature/personal-1.1-widgets`, historical workflows, or QA PR #89 during this transition.
- Do not change application behavior, UI, Room schema, versions, package names, permissions, Release keys, or Secrets.
- Keep Java 17, SDK 35, Build Tools 35.0.0, Gradle 8.9, `compileSdk 35`, `targetSdk 35`, and `minSdk 26`.
- Keep the transition PR separate from historical branch and workflow cleanup.
- The repository owner performs the final default-branch and ruleset settings changes.
- Observe the new default branch for at least seven days before destructive cleanup.

## GitHub API path rule

Branch names containing `/` must be URL-encoded when they are used as REST path parameters. All `gh api` commands in this plan therefore use:

```text
backup%2Fmain-pre-v2-20260805
```

for the branch named:

```text
backup/main-pre-v2-20260805
```

Do not substitute the unescaped branch name into a `/branches/{branch}` endpoint.

---

## Task 1: Verify source refs and create preservation branches

- [ ] **Read repository and source branch state**

```bash
test "$(gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch')" = 'main'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/feature%2Fpersonal-1.1-widgets --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

Stop if any assertion fails.

- [ ] **Check whether target refs already exist**

```bash
for endpoint in \
  'branches/backup%2Fmain-pre-v2-20260805' \
  'branches/v2' \
  'branches/cleanup%2Fv2-default-branch-transition'; do
  if gh api "repos/KANG77556/MyBrain-v3/$endpoint" --silent 2>/dev/null; then
    echo "Branch already exists: $endpoint"
  fi
done
```

An existing branch is acceptable only when its SHA matches the required fixed identifier. Never force-update an unexpected ref.

- [ ] **Create the legacy backup ref when absent**

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/backup/main-pre-v2-20260805' \
  -f sha='55066aab295f7434a7ed413166fa5cfb1a377ede'
```

- [ ] **Create the long-lived `v2` ref when absent**

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/v2' \
  -f sha='3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

- [ ] **Verify preservation refs before creating the work branch**

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup%2Fmain-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

- [ ] **Create the transition branch from the verified V2 SHA**

```bash
gh api --method POST repos/KANG77556/MyBrain-v3/git/refs \
  -f ref='refs/heads/cleanup/v2-default-branch-transition' \
  -f sha='3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

---

## Task 2: Commit the approved transition design and plan

Files:

```text
docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md
docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
```

- [ ] Confirm both documents contain the exact fixed SHAs, branch names, rollback rules, and seven-day observation period.
- [ ] Scan for incomplete requirements or contradictory branch names.
- [ ] Commit the documents on `cleanup/v2-default-branch-transition`.

```bash
git add docs/superpowers/specs/2026-08-05-v2-default-branch-transition-design.md \
  docs/superpowers/plans/2026-08-05-v2-default-branch-transition.md
git commit -m 'docs: plan V2 default branch transition'
```

---

## Task 3: Add a failing guard for missing `v2` workflow coverage

Modify `scripts/check-repository-hygiene.sh` to require two exact `v2` entries in `.github/workflows/build-v2.yml`: one under `push.branches` and one under `pull_request.branches`.

```bash
v2_trigger_count="$(grep -Fc '      - "v2"' .github/workflows/build-v2.yml || true)"
if ((v2_trigger_count < 2)); then
  fail '대표 V2 워크플로가 v2 push와 pull request 대상을 모두 감시하지 않습니다.'
fi
```

- [ ] Run the guard before modifying the workflow.
- [ ] Verify it fails only because `v2` coverage is missing.
- [ ] Commit the failing guard separately.

```bash
git add scripts/check-repository-hygiene.sh
git commit -m 'test: require V2 workflow coverage for v2'
```

---

## Task 4: Enable `v2` CI and update operating documentation

Modify `.github/workflows/build-v2.yml` so its branch filters include:

```yaml
on:
  push:
    branches:
      - "v2"
      - "rebuild/v2"
      - "rebuild/v2-*"
      - "feature/personal-1.1-widgets"
      - "cleanup/**"
  pull_request:
    branches:
      - "v2"
      - "main"
      - "rebuild/v2"
      - "feature/personal-1.1-widgets"
```

Keep the compatibility targets during the observation period.

Update:

```text
README.md
docs/BRANCH_POLICY_KO.md
docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
```

The documents must state:

- `v2` is the long-lived integration/default-branch target.
- `main` and `backup/main-pre-v2-20260805` preserve the legacy state.
- `feature/personal-1.1-widgets` remains during observation.
- The owner changes the default branch through GitHub Settings.
- Rollback selects `main` again without moving refs.
- No historical branch, workflow, or QA PR cleanup occurs for at least seven days.

Verification:

```bash
bash scripts/check-repository-hygiene.sh
test "$(grep -Fc '      - "v2"' .github/workflows/build-v2.yml)" -ge 2
grep -F 'backup/main-pre-v2-20260805' README.md docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
grep -F '55066aab295f7434a7ed413166fa5cfb1a377ede' docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
grep -F 'Build MyBrain AI V2 / build' docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
```

Commit:

```bash
git add .github/workflows/build-v2.yml scripts/check-repository-hygiene.sh README.md \
  docs/BRANCH_POLICY_KO.md docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md
git commit -m 'ci: prepare v2 as default branch'
```

---

## Task 5: Validate the transition branch and open the PR

- [ ] Verify the three fixed refs immediately before opening the PR.

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup%2Fmain-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/v2 --jq '.commit.sha')" = \
  '3bf2e75b7e5f0d62da7139db40e24b9e67b8df32'
```

- [ ] Run the complete static and Android verification.

```bash
bash scripts/check-repository-hygiene.sh
git diff --check v2...HEAD
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

- [ ] Verify Debug package metadata and signing.

```bash
APK='app/build/outputs/apk/debug/app-debug.apk'
AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$AAPT2" dump badging "$APK" | grep "package: name='kr.co.mybrain.v2.debug'"
"$AAPT2" dump badging "$APK" | grep "application-label:'MyBrain AI Debug'"
"$APKSIGNER" verify --verbose --print-certs "$APK"
```

- [ ] Open a Draft PR with base `v2` and head `cleanup/v2-default-branch-transition`.
- [ ] Record the exact branch SHAs, test count, workflow run, artifact names, APK hashes, and signing result.
- [ ] Mark ready only after the PR-triggered workflow is green.
- [ ] Request review and do not merge automatically.

Required workflow steps:

```text
저장소 위생 검사
V2 단위 테스트
Debug APK 빌드
Debug APK 패키지와 서명 검증
미서명 Release 컴파일 검증 or signed Release path
```

Required artifacts:

```text
MyBrainAI-v2-unit-test-report
MyBrainAI-v2-build-log
MyBrainAI-v2-debug
MyBrainAI-v2-unsigned-release-check or MyBrainAI-v2-release
```

---

## Task 6: Address review and merge only with explicit approval

- [ ] Read all review submissions, inline threads, and PR comments.
- [ ] Fix every valid unresolved thread and rerun CI.
- [ ] Confirm the PR is open, ready, mergeable, based on `v2`, and at the expected head SHA.
- [ ] Confirm all required checks are successful.
- [ ] Merge only after the user sends the explicit command `병합`.

Use squash merge with an expected head SHA.

```text
Commit title: chore: prepare v2 as the default branch
Commit body: Add v2 workflow coverage and document the reversible GitHub default-branch transition while preserving the legacy main ref.
```

---

## Task 7: Verify the merged `v2` branch

- [ ] Wait for the push workflow on the merge commit to complete.
- [ ] Verify all required steps and artifacts again.
- [ ] Verify legacy refs remain unchanged.

```bash
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup%2Fmain-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

---

## Task 8: Administrator default-branch switch and rulesets

The repository owner performs:

```text
Settings → General → Default branch → v2 → Update
```

Recommended `v2` ruleset:

```text
Require a pull request before merging
Require status check: Build MyBrain AI V2 / build
Require conversation resolution before merging
Block force pushes
Restrict deletions
```

Protect `main` and `backup/main-pre-v2-20260805` from force pushes and deletion.

Verify:

```bash
test "$(gh api repos/KANG77556/MyBrain-v3 --jq '.default_branch')" = 'v2'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/main --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
test "$(gh api repos/KANG77556/MyBrain-v3/branches/backup%2Fmain-pre-v2-20260805 --jq '.commit.sha')" = \
  '55066aab295f7434a7ed413166fa5cfb1a377ede'
```

Confirm a new PR defaults to `v2` and QA PR #89 remains unchanged.

---

## Task 9: Seven-day observation and rollback

Record the exact KST default-branch change time. Until seven full days have elapsed:

```text
Do not delete main
Do not delete backup/main-pre-v2-20260805
Do not delete feature/personal-1.1-widgets
Do not delete historical workflows
Do not close QA PR #89 as part of this transition
```

If a critical issue occurs:

1. Change the GitHub default branch back to `main`.
2. Verify `main` still points to the fixed legacy SHA.
3. Leave `v2` and all backup refs intact.
4. Diagnose and fix through a new `cleanup/**` branch and PR.
5. Do not force-push or rewrite history.

## Completion criteria

- The backup branch and legacy `main` remain at the fixed legacy SHA.
- `v2` contains the transition changes and has a green post-merge workflow.
- The repository default branch is `v2` after owner action.
- New PRs default to `v2`.
- Required rulesets protect `v2`, `main`, and the backup ref.
- QA PR #89 and historical branches/workflows remain during observation.
- Rollback is a documented settings-only operation.
