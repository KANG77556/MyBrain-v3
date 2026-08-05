# MyBrain-v3 저장소 보존형 정리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** V2 개발 계보를 보존하면서 공개 디버그 서명 자료를 제거하고, Debug 앱 격리·대표 CI·저장소 운영 문서를 갖춘다.

**Architecture:** 모든 변경은 `feature/personal-1.1-widgets`에서 분기한 `cleanup/repository-hygiene`에만 적용한다. 저장소 위생 검사를 독립 셸 스크립트로 두고 GitHub Actions가 이를 단위 테스트와 APK 빌드보다 먼저 실행하도록 하며, 제품 코드는 바꾸지 않고 빌드·보안·문서 경계만 정리한다.

**Tech Stack:** Android Gradle Plugin, Java 17, Android SDK 35, Gradle 8.9, Bash, GitHub Actions, JUnit 4.

## Global Constraints

- 기준 브랜치는 `feature/personal-1.1-widgets`, 작업 브랜치는 `cleanup/repository-hygiene`다.
- 1차 PR 대상은 `feature/personal-1.1-widgets`이며 `main`을 직접 변경하지 않는다.
- 기존 기능 브랜치, 백업 브랜치, 과거 워크플로, 열린 QA PR은 1차 작업에서 삭제하거나 닫지 않는다.
- Java 17, Android SDK 35, Gradle 8.9, `compileSdk 35`, `targetSdk 35`, `minSdk 26`을 유지한다.
- Release 패키지는 `kr.co.mybrain.v2`, Debug 패키지는 `kr.co.mybrain.v2.debug`로 분리한다.
- Release 서명 환경변수와 GitHub Secrets 이름 및 기존 Release 인증서 SHA-256 검증값은 변경하지 않는다.
- 공개된 디버그 키와 비밀번호는 재사용하거나 복원하지 않는다.
- Git 기록은 재작성하지 않고 현재 트리에서 민감 파일을 제거한다.
- 애플리케이션 기능, UI 동작, Room 스키마, 버전 번호는 변경하지 않는다.

---

## File Map

- Create: `.gitignore` — Android/Gradle 산출물, 로컬 설정, 서명 자료를 추적하지 않도록 차단한다.
- Create: `scripts/check-repository-hygiene.sh` — 현재 트리의 민감 파일, 공개 비밀번호, Debug 격리, 대표 CI 설정을 검사한다.
- Modify: `app/build.gradle` — 공개 고정 Debug 서명을 제거하고 Debug 패키지와 앱 이름을 분리한다.
- Delete: `app/debug/mybrain-v2-debug.p12.b64` — 공개된 PKCS#12 Base64 자료를 현재 트리에서 제거한다.
- Modify: `app/src/main/AndroidManifest.xml` — 애플리케이션 라벨을 빌드 변형별 `@string/app_name`으로 전환한다.
- Modify: `.github/workflows/build-v2.yml` — 최신 V2·정리 브랜치 트리거, 위생 검사, 동시 실행 취소, 일반 Debug 서명 검증을 적용한다.
- Modify: `README.md` — 개발·테스트·아티팩트·서명 정책을 설명한다.
- Create: `SECURITY.md` — 비공개 취약점 신고와 민감정보 대응 절차를 설명한다.
- Create: `docs/BRANCH_POLICY_KO.md` — 브랜치 역할과 병합 원칙을 정의한다.
- Create: `docs/REPOSITORY_CLEANUP_BACKLOG_KO.md` — 1차 이후 삭제·비활성화 후보와 판정 기준을 기록한다.
- Existing tests: `app/src/test/java/**` — 제품 기능 회귀 여부를 `testDebugUnitTest`로 확인한다.

---

### Task 1: 저장소 위생 검사를 실패하는 상태로 추가

**Files:**
- Create: `scripts/check-repository-hygiene.sh`

**Interfaces:**
- Consumes: Git 추적 파일 목록, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `.github/workflows/build-v2.yml`.
- Produces: 성공 시 종료 코드 `0`과 `Repository hygiene checks passed.`, 위반 시 종료 코드 `1`과 항목별 오류 메시지.

- [ ] **Step 1: 검사 스크립트를 작성한다**

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

sensitive_files="$(git ls-files | grep -E '(^|/)[^/]+\.(jks|keystore|p12|pfx)(\.b64)?$' || true)"
if [[ -n "$sensitive_files" ]]; then
  printf '%s\n' "$sensitive_files" >&2
  fail '서명키 또는 인증서 파일이 Git 추적 대상에 남아 있습니다.'
fi

legacy_debug_password="$(printf '%s%s' 'mybrain-debug-' 'only')"
if git grep -n -F "$legacy_debug_password" -- app .github scripts >/tmp/mybrain-hygiene-secret.txt 2>/dev/null; then
  cat /tmp/mybrain-hygiene-secret.txt >&2
  fail '공개된 개발 서명 비밀번호가 소스 또는 CI에 남아 있습니다.'
fi
rm -f /tmp/mybrain-hygiene-secret.txt

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
```

- [ ] **Step 2: 현재 트리에서 검사가 실패하는지 확인한다**

Run:

```bash
bash scripts/check-repository-hygiene.sh
```

Expected: 종료 코드 `1`. 최소한 `app/debug/mybrain-v2-debug.p12.b64`, 공개 개발 비밀번호, 누락된 `.debug` 패키지, 고정 Debug 인증서 검사 관련 오류가 출력된다.

- [ ] **Step 3: 검사만 별도 커밋한다**

```bash
git add scripts/check-repository-hygiene.sh
git commit -m "test: add repository hygiene guard"
```

---

### Task 2: 공개 Debug 서명 제거와 패키지 격리

**Files:**
- Create: `.gitignore`
- Modify: `app/build.gradle`
- Delete: `app/debug/mybrain-v2-debug.p12.b64`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: 기존 Release 서명 환경변수 `MYBRAIN_KEYSTORE_FILE`, `MYBRAIN_KEYSTORE_PASSWORD`, `MYBRAIN_KEY_ALIAS`, `MYBRAIN_KEY_PASSWORD`.
- Produces: Release `applicationId=kr.co.mybrain.v2`, Debug `applicationId=kr.co.mybrain.v2.debug`, Release 라벨 `MyBrain AI`, Debug 라벨 `MyBrain AI Debug`.

- [ ] **Step 1: Android 전용 `.gitignore`를 작성한다**

```gitignore
# Android Studio
.idea/
*.iml
.navigation/
captures/

# Gradle
.gradle/
**/build/
!gradle/wrapper/gradle-wrapper.jar

# Local SDK and environment configuration
local.properties
.env
.env.*
!.env.example

# Native build output
.externalNativeBuild/
.cxx/
**/.cxx/

# Android build artifacts
*.apk
*.aab
*.apks
*.aar

# Signing and certificate material
*.jks
*.keystore
*.p12
*.pfx
*.jks.b64
*.keystore.b64
*.p12.b64
*.pfx.b64

# Logs and temporary files
*.log
*.tmp
*.temp
*.swp
*~

# Operating system metadata
.DS_Store
Thumbs.db
```

- [ ] **Step 2: `app/build.gradle`에서 고정 Debug 키 복원과 `stableDebug` signingConfig를 제거한다**

Replace the file with:

```groovy
plugins {
    id 'com.android.application'
}

// 정식 Release 서명 정보는 GitHub Actions 또는 로컬 환경변수에서만 읽습니다.
// JKS 파일과 비밀번호는 소스 저장소에 기록하지 않습니다.
def releaseStoreFile = System.getenv('MYBRAIN_KEYSTORE_FILE')
def releaseStorePassword = System.getenv('MYBRAIN_KEYSTORE_PASSWORD')
def releaseKeyAlias = System.getenv('MYBRAIN_KEY_ALIAS')
def releaseKeyPassword = System.getenv('MYBRAIN_KEY_PASSWORD')
def hasReleaseSigning = releaseStoreFile && releaseStorePassword && releaseKeyAlias && releaseKeyPassword

android {
    namespace 'kr.co.mybrain.v2'
    compileSdk 35

    defaultConfig {
        applicationId 'kr.co.mybrain.v2'
        minSdk 26
        targetSdk 35
        versionCode 46
        versionName '2.0.0-alpha46'
        resValue "string", "app_name", "MyBrain AI"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            release {
                storeFile file(releaseStoreFile)
                storePassword releaseStorePassword
                keyAlias releaseKeyAlias
                keyPassword releaseKeyPassword
                enableV1Signing true
                enableV2Signing true
                enableV3Signing true
                enableV4Signing true
            }
        }
    }

    buildTypes {
        debug {
            debuggable true
            applicationIdSuffix ".debug"
            resValue "string", "app_name", "MyBrain AI Debug"
        }
        release {
            minifyEnabled false
            shrinkResources false
            if (hasReleaseSigning) signingConfig signingConfigs.release
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.core:core:1.15.0'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.room:room-runtime:2.6.1'
    annotationProcessor 'androidx.room:room-compiler:2.6.1'
    implementation 'com.google.mlkit:text-recognition-korean:16.0.1'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 3: Manifest 라벨을 빌드 변형별 리소스로 전환한다**

Change:

```xml
android:label="MyBrain AI"
```

To:

```xml
android:label="@string/app_name"
```

- [ ] **Step 4: 공개된 Debug 키 파일을 삭제한다**

```bash
git rm app/debug/mybrain-v2-debug.p12.b64
```

- [ ] **Step 5: 보안 변경을 정적으로 검증한다**

Run:

```bash
if git ls-files | grep -E '(^|/)[^/]+\.(jks|keystore|p12|pfx)(\.b64)?$'; then exit 1; fi
! git grep -n -F "$(printf '%s%s' 'mybrain-debug-' 'only')" -- app .github scripts
grep -F 'applicationIdSuffix ".debug"' app/build.gradle
grep -F 'resValue "string", "app_name", "MyBrain AI Debug"' app/build.gradle
grep -F 'android:label="@string/app_name"' app/src/main/AndroidManifest.xml
```

Expected: 민감 파일과 공개 비밀번호 검색은 결과 없이 성공하고, 세 가지 설정 검색은 각각 정확히 한 줄 이상을 출력한다.

- [ ] **Step 6: 기존 단위 테스트와 Debug/Release 컴파일을 실행한다**

Run:

```bash
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Expected: 세 명령이 모두 종료 코드 `0`. Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`, 미서명 Release APK는 `app/build/outputs/apk/release/app-release-unsigned.apk`에 생성된다.

- [ ] **Step 7: 보안 변경을 커밋한다**

```bash
git add .gitignore app/build.gradle app/src/main/AndroidManifest.xml
git add -u app/debug/mybrain-v2-debug.p12.b64
git commit -m "security: isolate debug build and remove public key"
```

---

### Task 3: 대표 V2 GitHub Actions 정리

**Files:**
- Modify: `.github/workflows/build-v2.yml`

**Interfaces:**
- Consumes: `scripts/check-repository-hygiene.sh`, Gradle tasks `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, 기존 Release Secrets.
- Produces: 정리 브랜치 자동 검증, 브랜치별 중복 실행 취소, Debug APK·해시·서명 보고서, Unit Test 보고서, 조건부 Release 아티팩트.

- [ ] **Step 1: 워크플로 트리거와 concurrency를 확장한다**

Use:

```yaml
on:
  push:
    branches:
      - "rebuild/v2"
      - "rebuild/v2-*"
      - "feature/personal-1.1-widgets"
      - "cleanup/**"
  pull_request:
    branches:
      - "main"
      - "rebuild/v2"
      - "feature/personal-1.1-widgets"
  workflow_dispatch:

concurrency:
  group: build-v2-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

- [ ] **Step 2: 최소 권한과 저장소 위생 검사 단계를 추가한다**

Add before `jobs`:

```yaml
permissions:
  contents: read
```

Add immediately after checkout:

```yaml
      - name: 저장소 위생 검사
        shell: bash
        run: bash scripts/check-repository-hygiene.sh
```

- [ ] **Step 3: 고정 Debug 인증서 지문 환경변수를 제거한다**

Delete:

```yaml
      EXPECTED_DEBUG_CERT_SHA256: 49c3fde6dff9be797d506eade00bb660288efc83d385b6b8b930c5d8fe771b87
```

Keep the existing Release value:

```yaml
      EXPECTED_CERT_SHA256: ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520
```

- [ ] **Step 4: Debug 서명 검증을 일반 서명 형식 검증으로 교체한다**

Use:

```yaml
      - name: Debug APK 서명 검증
        shell: bash
        run: |
          APK="app/build/outputs/apk/debug/app-debug.apk"
          APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
          test -f "$APK"
          "$APKSIGNER" verify --verbose --print-certs "$APK" | tee debug-apk-signature-report.txt
          grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" debug-apk-signature-report.txt
          grep -q "^Signer #1 certificate SHA-256 digest:" debug-apk-signature-report.txt
          sha256sum "$APK" > debug-apk.sha256
```

- [ ] **Step 5: 전체 위생 검사를 실행한다**

Run:

```bash
bash scripts/check-repository-hygiene.sh
```

Expected:

```text
Repository hygiene checks passed.
```

- [ ] **Step 6: YAML의 핵심 조건을 정적으로 확인한다**

Run:

```bash
grep -F '"cleanup/**"' .github/workflows/build-v2.yml
grep -F 'cancel-in-progress: true' .github/workflows/build-v2.yml
grep -F 'bash scripts/check-repository-hygiene.sh' .github/workflows/build-v2.yml
! grep -F 'EXPECTED_DEBUG_CERT_SHA256' .github/workflows/build-v2.yml
```

Expected: 앞의 세 검색은 설정을 출력하고 마지막 검색은 결과 없이 성공한다.

- [ ] **Step 7: CI 변경을 커밋한다**

```bash
git add .github/workflows/build-v2.yml
git commit -m "ci: validate V2 cleanup branches"
```

---

### Task 4: 개발·보안·브랜치 운영 문서 정리

**Files:**
- Modify: `README.md`
- Create: `SECURITY.md`
- Create: `docs/BRANCH_POLICY_KO.md`
- Create: `docs/REPOSITORY_CLEANUP_BACKLOG_KO.md`

**Interfaces:**
- Consumes: Task 2의 패키지·서명 정책, Task 3의 실제 CI 명령과 아티팩트 이름.
- Produces: 개발자가 별도 설명 없이 로컬 빌드, CI 결과 해석, 비공개 신고, 후속 저장소 정리를 수행할 수 있는 운영 기준.

- [ ] **Step 1: `README.md`를 다음 구조로 교체한다**

```markdown
# MyBrain AI V2

AI 기반 메모·할 일·일정 관리 Android 애플리케이션입니다. 현재 V2 통합 기준선은 `feature/personal-1.1-widgets`이며, 저장소 보존형 정리는 `cleanup/repository-hygiene`에서 검증합니다. 기존 v1.10.1 소스는 `backup/legacy-v1.10.1`에 보관되어 있습니다.

## 개발 환경

- Java 17
- Android SDK 35 및 Build Tools 35.0.0
- Gradle 8.9
- Android 최소 버전 26, 대상 버전 35

## 로컬 검증

```bash
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
bash scripts/check-repository-hygiene.sh
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. Release Secrets가 없을 때 생성되는 `app-release-unsigned.apk`는 컴파일 확인용이며 설치·배포용이 아닙니다.

## 패키지와 서명 정책

- Release: `kr.co.mybrain.v2`, 외부 환경변수 또는 GitHub Secrets의 고정 Release 키로만 서명합니다.
- Debug: `kr.co.mybrain.v2.debug`, Android 기본 Debug 키를 사용하며 앱 이름은 `MyBrain AI Debug`입니다.
- 과거 공개 Debug 키로 서명된 `kr.co.mybrain.v2` 개발 APK는 신뢰하지 않으며 테스트 기기에서 삭제한 뒤 새 Debug APK를 설치합니다.
- 키스토어, 인증서, API 키, 비밀번호를 저장소·이슈·빌드 로그에 기록하지 않습니다.

## GitHub Actions 아티팩트

대표 워크플로는 `Build MyBrain AI V2`입니다.

- `MyBrainAI-v2-unit-test-report`: JUnit 결과와 단위 테스트 로그
- `MyBrainAI-v2-build-log`: Debug 빌드 로그
- `MyBrainAI-v2-debug`: Debug APK, SHA-256, 서명 보고서
- `MyBrainAI-v2-unsigned-release-check`: Release Secrets가 없을 때의 미서명 컴파일 결과
- `MyBrainAI-v2-release`: Release Secrets가 있을 때의 고정 서명 APK와 검증 자료

## 브랜치와 기여

브랜치 역할과 병합 기준은 `docs/BRANCH_POLICY_KO.md`를 따릅니다. 1차 저장소 정리는 `feature/personal-1.1-widgets` 대상 PR로만 병합하며 `main` 승격은 별도 검증과 설계를 거칩니다.

보안 문제는 공개 이슈에 올리지 말고 `SECURITY.md`의 비공개 신고 절차를 사용합니다.
```

- [ ] **Step 2: `SECURITY.md`를 작성한다**

```markdown
# Security Policy

## 민감정보 원칙

API 키, OAuth 토큰, 서명키, 인증서, 비밀번호, 개인 데이터는 커밋·Pull Request·Issue·Discussion·Actions 로그에 기록하지 않습니다. Debug APK는 개발 검증용이며 정식 배포물로 취급하지 않습니다.

## 비공개 신고

취약점 또는 민감정보 노출을 발견하면 공개 이슈를 만들지 않습니다. 저장소의 **Security → Advisories → Report a vulnerability**를 사용해 재현 절차, 영향 범위, 관련 커밋 또는 파일 경로를 비공개로 전달합니다.

Private vulnerability reporting 메뉴가 보이지 않으면 저장소 소유자는 GitHub 저장소 설정에서 해당 기능을 활성화한 뒤 신고 경로를 안내해야 합니다. 비공개 경로가 준비되기 전에는 취약점 세부 정보나 비밀값을 공개 채널에 게시하지 않습니다.

## 노출 대응

1. 노출된 키와 토큰을 즉시 폐기하거나 회전합니다.
2. 현재 브랜치와 배포 산출물에서 민감정보를 제거합니다.
3. 영향을 받은 앱 패키지, 서명 인증서, CI 실행, 배포 파일을 식별합니다.
4. 재발 방지 검사를 `scripts/check-repository-hygiene.sh`와 대표 CI에 추가합니다.
5. Git 기록 재작성은 기존 클론과 태그에 미치는 영향을 검토한 별도 보안 작업으로 수행합니다.

## 현재 Debug 키 사건

과거 저장소에 포함된 공개 Debug PKCS#12 자료와 비밀번호는 폐기된 것으로 간주합니다. 해당 키로 서명된 `kr.co.mybrain.v2` 개발 APK는 테스트 기기에서 제거하고, 패키지가 분리된 `kr.co.mybrain.v2.debug` APK를 사용합니다.
```

- [ ] **Step 3: `docs/BRANCH_POLICY_KO.md`를 작성한다**

```markdown
# MyBrain-v3 브랜치 운영 정책

## 역할

- `main`: 현재 공개 기준선. V2 승격이 결정되기 전까지 직접 push와 기능 병합을 최소화합니다.
- `feature/personal-1.1-widgets`: 현재 V2 통합 기준선입니다.
- `cleanup/repository-hygiene`: 보안·CI·문서 정리 전용 임시 브랜치입니다.
- `backup/*`: 삭제하지 않는 보존 지점입니다.
- `feature/*`: 하나의 기능 또는 검증 목적을 가진 작업 브랜치입니다.
- `cleanup/*`: 기능을 바꾸지 않는 저장소·빌드·문서 정리 브랜치입니다.

## 병합 원칙

1. 기능 브랜치는 현재 통합 기준선을 대상으로 Pull Request를 엽니다.
2. 단위 테스트, 저장소 위생 검사, Debug APK, Release 컴파일 검증이 성공해야 합니다.
3. 정리 PR은 제품 기능과 저장소 구조 변경을 섞지 않습니다.
4. `main` 승격 전 현재 `main` 보존 태그와 롤백 절차를 먼저 확정합니다.
5. 임시 QA 브랜치와 워크플로는 대체 경로와 보존 필요성을 확인한 뒤 별도 PR에서 정리합니다.

## 금지 사항

- `main` 강제 push
- 공개 저장소에 키스토어·토큰·비밀번호 추가
- 검증되지 않은 APK를 Release로 표기
- 백업 브랜치 삭제와 기능 변경을 같은 PR에서 수행
```

- [ ] **Step 4: `docs/REPOSITORY_CLEANUP_BACKLOG_KO.md`를 작성한다**

```markdown
# 저장소 후속 정리 백로그

이 문서는 1차 보존형 정리에서 삭제하지 않은 후보를 기록합니다. 각 항목은 별도 PR에서 마지막 실행, 대상 브랜치, 대체 경로, 보존 필요성을 확인한 뒤 처리합니다.

## 워크플로 후보

- 이름과 목적이 겹치는 Android APK 빌드 워크플로
- `AllFileHub`, `ChalkakCoach` 등 MyBrain V2와 다른 제품용 워크플로
- 특정 과거 버전 또는 일회성 패치를 적용하는 워크플로
- 대표 `Build MyBrain AI V2`로 대체 가능한 Debug/Release 빌드 워크플로

처리 전 다음을 확인합니다.

1. 최근 30일 실행 여부와 마지막 성공 커밋
2. 현재 존재하는 대상 브랜치
3. 생성 아티팩트의 소비자
4. 대표 V2 워크플로로 대체 가능한지
5. 비활성화 후 최소 7일 관찰이 필요한지

## 브랜치 후보

- 동일 커밋을 가리키는 `feature/brain-assistant-room-v3-qa-*` 계열 임시 브랜치
- `build/*`, `*-apk-build`, 과거 버전 검증 브랜치
- 병합 또는 검증이 끝난 일회성 `cleanup/*` 브랜치

브랜치를 삭제하기 전 태그 또는 백업 브랜치가 필요한지 확인하고, 열린 PR과 Actions 트리거 참조가 없는지 검사합니다.

## Pull Request 후보

병합 목적이 아닌 기기 검증용 Draft PR은 로그·스크린샷·결론을 본문에 남긴 뒤 닫습니다. 기능 변경 PR은 통합 기준선과의 충돌 여부를 별도로 검토합니다.

## Actions 보존 정책 후보

- 일반 빌드 아티팩트: 14일
- Release 후보와 서명 보고서: 30일
- 실패 로그: 원인 분석 완료 후 14일
- 장기 보존이 필요한 배포물: GitHub Release로 이동

실제 보존 기간은 사용량과 배포 절차를 확인한 뒤 저장소 설정에서 적용합니다.
```

- [ ] **Step 5: 문서와 실제 설정의 일치를 확인한다**

Run:

```bash
grep -F 'gradle --stacktrace testDebugUnitTest' README.md
grep -F 'kr.co.mybrain.v2.debug' README.md SECURITY.md
grep -F 'feature/personal-1.1-widgets' README.md docs/BRANCH_POLICY_KO.md
grep -F 'Build MyBrain AI V2' README.md .github/workflows/build-v2.yml
bash scripts/check-repository-hygiene.sh
```

Expected: 모든 검색이 관련 줄을 출력하고 위생 검사가 성공한다.

- [ ] **Step 6: 문서를 커밋한다**

```bash
git add README.md SECURITY.md docs/BRANCH_POLICY_KO.md docs/REPOSITORY_CLEANUP_BACKLOG_KO.md
git commit -m "docs: document V2 repository operations"
```

---

### Task 5: 최종 CI 검증과 보존형 PR 생성

**Files:**
- No code changes expected unless verification exposes a defect.
- Create through GitHub: Pull Request from `cleanup/repository-hygiene` to `feature/personal-1.1-widgets`.

**Interfaces:**
- Consumes: 최종 브랜치 HEAD, `Build MyBrain AI V2` workflow run, Actions job logs and artifacts.
- Produces: 검증 근거가 포함된 비병합 상태의 정리 PR.

- [ ] **Step 1: 로컬 또는 격리 환경에서 전체 정적 검사를 실행한다**

```bash
bash scripts/check-repository-hygiene.sh
git diff --check feature/personal-1.1-widgets...HEAD
```

Expected: 위생 검사 성공, `git diff --check` 출력 없음.

- [ ] **Step 2: 전체 Gradle 검증을 실행한다**

```bash
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Expected: 모든 명령 성공. Release Secrets가 없는 환경에서는 미서명 Release가 생성된다.

- [ ] **Step 3: Debug 패키지와 서명을 확인한다**

```bash
APK="app/build/outputs/apk/debug/app-debug.apk"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
"$APKSIGNER" verify --verbose --print-certs "$APK"
"$AAPT2" dump badging "$APK" | grep "package: name='kr.co.mybrain.v2.debug'"
```

Expected: APK 서명 검증 성공 및 Debug 패키지명 출력.

- [ ] **Step 4: GitHub Actions 실행을 확인한다**

Expected workflow: `Build MyBrain AI V2`.

Required successful steps:

- 저장소 위생 검사
- 앱 버전 확인
- 단위 테스트
- Debug APK 빌드
- Debug APK 서명 검증
- 미서명 Release 컴파일 검증 또는 고정 서명 Release APK 빌드

Required artifacts:

- `MyBrainAI-v2-unit-test-report`
- `MyBrainAI-v2-build-log`
- `MyBrainAI-v2-debug`
- Release Secrets 상태에 따라 `MyBrainAI-v2-unsigned-release-check` 또는 `MyBrainAI-v2-release`

- [ ] **Step 5: 실패가 있으면 가장 작은 수정으로 해결하고 재검증한다**

For each failure:

```bash
git show --stat --oneline HEAD
git diff HEAD^ -- affected/path
gradle --stacktrace <failed-task>
bash scripts/check-repository-hygiene.sh
```

Expected: 원인이 확인된 파일만 수정하고 동일 검증을 다시 실행한다. 관련 없는 리팩터링은 포함하지 않는다.

- [ ] **Step 6: PR을 생성한다**

Title:

```text
chore: harden and organize the V2 repository
```

Body:

```markdown
## 목적

V2 개발 계보를 보존하면서 공개 Debug 서명 자료를 제거하고, Debug 앱 격리·대표 CI·운영 문서를 추가합니다.

## 변경 사항

- 공개 `mybrain-v2-debug.p12.b64` 및 고정 Debug 비밀번호 제거
- Debug 패키지를 `kr.co.mybrain.v2.debug`로 분리
- Android/Gradle `.gitignore`와 저장소 위생 검사 추가
- `Build MyBrain AI V2`를 최신 V2 및 `cleanup/**` 브랜치에서 실행
- 고정 Debug 인증서 비교를 일반 APK 서명 검증으로 교체
- README, 보안 정책, 브랜치 정책, 후속 정리 백로그 추가

## 검증

- [ ] `bash scripts/check-repository-hygiene.sh`
- [ ] `testDebugUnitTest`
- [ ] `assembleDebug`
- [ ] Debug APK 서명 및 `kr.co.mybrain.v2.debug` 패키지 확인
- [ ] 미서명 또는 고정 서명 Release 컴파일 확인
- [ ] GitHub Actions 아티팩트 확인

## 보존 범위

이 PR은 `feature/personal-1.1-widgets`만 대상으로 합니다. `main` 이동, 기존 브랜치·과거 워크플로 삭제, QA PR 종료는 포함하지 않습니다.
```

- [ ] **Step 7: 병합하지 않고 검토 가능한 상태로 남긴다**

Expected: PR base가 `feature/personal-1.1-widgets`, head가 `cleanup/repository-hygiene`이며 자동 병합은 설정하지 않는다.

---

## Plan Self-Review

- 설계의 Android `.gitignore`, Debug 키 제거, 패키지 분리, CI 트리거·concurrency, 문서, 후속 백로그, 검증, 롤백 가능한 브랜치 범위를 모두 작업에 연결했다.
- 계획에 미정 값이나 구현을 뒤로 미루는 표현을 두지 않았다.
- `app_name`, `applicationIdSuffix`, 워크플로·아티팩트 이름, 브랜치 이름은 모든 작업에서 동일하게 사용했다.
- 제품 기능과 데이터 스키마 변경은 포함하지 않았다.
