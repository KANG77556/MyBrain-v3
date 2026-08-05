# MyBrain-v3 저장소 보존형 정리 설계

- 작성일: 2026-08-05
- 작업 브랜치: `cleanup/repository-hygiene`
- 기준 브랜치: `feature/personal-1.1-widgets`
- 1차 병합 대상: `feature/personal-1.1-widgets`
- 최종 `main` 승격: 1차 정리와 CI 검증 완료 후 별도 결정

## 1. 배경

현재 `main`은 기존 MyBrain 계보를 유지하고 있고, V2 개발은 별도 계보에서 진행됐다. 두 브랜치는 단순한 선후 관계가 아니므로 즉시 병합하거나 `main`을 강제로 이동하면 정상 소스와 이력을 잃을 위험이 있다.

저장소에는 다수의 과거 GitHub Actions 워크플로와 실험용 브랜치가 남아 있다. 최신 V2 브랜치의 자동 빌드 트리거도 충분하지 않았고, 공개 저장소 트리에 고정 Debug 서명 자료와 비밀번호가 포함돼 있었다. 최신 V2 계보에는 Android 프로젝트에 맞는 `.gitignore`도 없었다.

이 설계는 기존 이력을 삭제하지 않고 별도 정리 브랜치에서 보안·CI·문서를 먼저 개선한 뒤, 검증 결과를 근거로 후속 정리를 수행한다.

## 2. 목표

1. V2 개발 소스를 손실 없이 보존한다.
2. 현재 브랜치 트리에서 Debug 서명 자료와 폐기된 공개 비밀번호를 제거한다.
3. Debug 앱과 Release 앱의 패키지 및 표시명을 분리한다.
4. 최신 V2 브랜치와 정리 브랜치에서 자동 테스트와 APK 컴파일 검증이 실행되도록 한다.
5. Android 프로젝트에 필요한 제외 규칙과 개발·배포 문서를 갖춘다.
6. 기존 브랜치, 워크플로, PR을 삭제하기 전에 검증 가능한 기준을 만든다.

## 3. 비목표

1차 정리에서는 다음 작업을 하지 않는다.

- `main` 강제 이동 또는 기본 브랜치 변경
- 기존 기능 브랜치·백업 브랜치 삭제
- 기존 워크플로 파일의 대량 삭제
- 열린 QA PR 종료
- 애플리케이션 기능 또는 UI 변경
- 데이터베이스 스키마 변경
- Release 키 교체
- Git 기록 재작성
- 저장소 분리

이 항목들은 1차 CI와 정리 PR 검토가 끝난 뒤 별도 작업으로 다룬다.

## 4. 브랜치 및 변경 흐름

1. `feature/personal-1.1-widgets`에서 `cleanup/repository-hygiene`를 분기한다.
2. 모든 1차 변경은 정리 브랜치에만 커밋한다.
3. 단위 테스트, Debug APK, 미서명 Release 컴파일 검증이 성공하면 V2 통합 기준선을 대상으로 Draft PR을 연다.
4. 1차 PR에서는 `main`을 대상으로 하지 않는다.
5. 1차 PR 병합 이후 `main` 승격 방법은 별도 설계로 결정한다.

## 5. 변경 설계

### 5.1 Android 전용 `.gitignore`

저장소 루트에 Android/Gradle 중심의 `.gitignore`를 추가한다. 최소한 다음 항목을 제외한다.

- `.gradle/`, `.idea/`, `*.iml`, `local.properties`
- 모든 모듈의 `build/`
- `.externalNativeBuild/`, `.cxx/`, `captures/`
- `*.apk`, `*.aab`, `*.apks`, `*.aar`
- `*.jks`, `*.keystore`, `*.p12`, `*.pfx`와 Base64 변형
- `.env`, `.env.*`, 로그, 임시 파일, OS 메타데이터

문서나 샘플 설정처럼 의도적으로 버전 관리해야 하는 파일은 명시적 예외 규칙을 사용한다.

### 5.2 Debug 서명 보안

현재 트리에서 공개된 Debug PKCS#12 Base64 파일을 삭제하고, `app/build.gradle`에 있던 고정 Debug 키 복원 코드와 공개 비밀번호를 제거한다.

공개된 Debug 키는 영구 노출된 것으로 간주해 어떤 빌드에도 재사용하거나 복원하지 않는다. 1차 정리에서는 보존형 원칙 때문에 Git 기록을 재작성하지 않지만, 현재 트리에서 제거하고 패키지를 분리해 새 빌드와 정식 앱에 영향을 주지 못하게 한다.

Debug 빌드는 Android Gradle Plugin의 기본 Debug 서명을 사용한다. CI 실행 환경에 따라 인증서가 달라질 수 있으므로 Debug APK 간 덮어쓰기 호환성은 보장하지 않는다. 대신 정식 앱과 다른 패키지를 사용한다.

`app/build.gradle`의 정책은 다음과 같다.

- Release application ID: `kr.co.mybrain.v2`
- Debug application ID: `kr.co.mybrain.v2.debug`
- Release 표시명: `MyBrain AI`
- Debug 표시명: `MyBrain AI Debug`
- Release 서명 정보는 기존 환경변수와 GitHub Secrets에서만 읽음

`AndroidManifest.xml`의 애플리케이션 라벨은 `@string/app_name`을 참조한다.

기존 공개 Debug 키로 서명되고 패키지명이 `kr.co.mybrain.v2`인 개발 APK는 신뢰하지 않는다. 테스트 기기에서는 이를 제거한 뒤 새 Debug APK를 설치한다.

### 5.3 저장소 위생 검사

`scripts/check-repository-hygiene.sh`를 대표 CI의 첫 번째 검증으로 실행한다. 검사는 Git이 추적하는 전체 텍스트와 파일 목록을 대상으로 한다.

검사 항목은 다음과 같다.

- 키스토어·인증서 확장자 및 Base64 변형이 추적되지 않는지
- 폐기된 공개 Debug 비밀번호가 코드와 문서를 포함한 전체 추적 텍스트에 남아 있지 않은지
- PEM 개인키 헤더가 추적 텍스트에 남아 있지 않은지
- Debug 패키지 suffix와 Debug 표시명 설정이 존재하는지
- Manifest가 빌드 변형별 앱 이름 리소스를 사용하는지
- 대표 워크플로가 `cleanup/**`와 위생 검사 스크립트를 포함하는지
- 폐기 대상 고정 Debug 인증서 지문 검사가 제거됐는지

### 5.4 대표 CI 워크플로

우선 V2의 대표 워크플로 `.github/workflows/build-v2.yml`만 수정한다. 기존 과거 워크플로는 1차 단계에서 삭제하지 않는다.

#### 트리거

- push: `rebuild/v2`, `rebuild/v2-*`, `feature/personal-1.1-widgets`, `cleanup/**`
- pull request: `main`, `rebuild/v2`, `feature/personal-1.1-widgets`
- 수동 실행: `workflow_dispatch`

#### 필수 검증

1. 저장소 위생 검사
2. Java 17, Android SDK 35, Gradle 8.9 준비
3. `testDebugUnitTest`
4. Debug APK 빌드
5. APK Signature Scheme v2 및 signer 정보 검증
6. Debug APK SHA-256 생성
7. Release Secrets가 없으면 미서명 Release 컴파일 검증
8. Release Secrets가 있으면 기존 Release 인증서 지문 검증
9. 테스트 보고서, 빌드 로그, APK와 해시를 아티팩트로 업로드

고정 Debug 인증서 지문 비교는 제거하고 Release 인증서 지문 비교는 유지한다.

브랜치별 중복 실행을 줄이기 위해 concurrency 그룹과 `cancel-in-progress: true`를 사용한다. 워크플로 권한은 `contents: read`로 제한한다.

### 5.5 README와 운영 문서

루트 README에는 다음을 포함한다.

- 프로젝트 목적과 현재 V2 개발 상태
- 기준 개발 브랜치와 레거시 보관 브랜치
- Java 17, Android SDK 35, Gradle 8.9 요구사항
- 로컬 테스트 및 Debug/Release 컴파일 명령
- Debug와 Release 패키지·서명 정책 차이
- GitHub Actions 아티팩트 사용법
- 미서명 Release 파일은 설치·배포용이 아니라는 경고
- 기존 공개 Debug APK 제거와 새 Debug APK 재설치 안내

`SECURITY.md`에는 다음을 기록한다.

- 키, 토큰, 비밀번호를 공개 채널에 올리지 않는 원칙
- GitHub Security Advisories를 통한 비공개 신고 절차
- Private vulnerability reporting 활성화 필요성
- 유출 시 키 폐기·교체, 현재 트리 제거, 영향 범위 기록 절차
- 공개된 과거 Debug 키는 폐기 상태이며 재사용하지 않는다는 점

`docs/BRANCH_POLICY_KO.md`에는 브랜치 역할과 병합 원칙을 정의하고, `docs/REPOSITORY_CLEANUP_BACKLOG_KO.md`에는 2차 정리 후보와 판정 기준을 기록한다.

### 5.6 후속 정리 목록

1차 PR에는 삭제를 포함하지 않고 다음 후보만 목록화한다.

- 기능이 겹치는 Android APK 워크플로
- AllFileHub, ChalkakCoach 등 다른 제품용 워크플로
- 동일 커밋을 가리키는 임시 QA 브랜치
- 병합 목적이 아닌 종료 가능한 Draft PR
- 오래된 Actions 아티팩트와 보존 기간

각 후보는 마지막 실행, 대상 브랜치, 대체 워크플로, 보존 필요성을 확인한 뒤 별도 PR에서 처리한다.

## 6. 검증 기준

다음 조건을 모두 만족해야 1차 정리가 완료된 것으로 본다.

1. 현재 브랜치 트리에 키스토어·인증서 파일 및 Base64 변형이 존재하지 않는다.
2. 폐기된 공개 Debug 비밀번호가 코드와 문서를 포함한 추적 텍스트에 존재하지 않는다.
3. PEM 개인키 헤더가 추적 텍스트에 존재하지 않는다.
4. Debug application ID가 `kr.co.mybrain.v2.debug`로 빌드된다.
5. Debug 표시명이 `MyBrain AI Debug`다.
6. `testDebugUnitTest`가 성공한다.
7. Debug APK가 생성되고 서명 검증을 통과한다.
8. Release Secrets가 없는 환경에서 미서명 Release 컴파일이 성공한다.
9. Release Secrets가 있는 환경에서 기존 Release 인증서 지문 검증이 성공한다.
10. CI가 정리 브랜치 push와 대상 PR에서 자동 실행된다.
11. README 명령과 실제 CI 명령이 일치한다.
12. 기존 브랜치, 과거 워크플로, PR을 1차 변경에서 삭제하지 않는다.

## 7. 롤백

모든 변경은 정리 브랜치에 한정한다. 문제가 발생하면 해당 커밋을 되돌리거나 브랜치를 삭제하면 기준 브랜치에는 영향이 없다.

정리 PR 병합 뒤 문제가 발견되면 다음 순서로 복구한다.

1. 정리 PR의 merge commit을 revert한다.
2. Release Secrets와 인증서 자체는 변경하지 않았으므로 기존 Release 빌드 경로를 복원한다.
3. 공개됐던 Debug 키 파일과 비밀번호는 보안상 복원하지 않는다.
4. Debug는 기본 Debug 서명과 `.debug` 패키지를 계속 사용한다.
5. 통합 기준선의 병합 직전 커밋을 보존 태그로 남긴다.

## 8. 위험과 대응

### Debug APK 업데이트 호환성

기본 Debug 키가 환경마다 달라 기존 Debug 앱 위에 업데이트 설치가 안 될 수 있다. 패키지를 `.debug`로 분리하고 개발용 앱은 삭제 후 재설치하는 정책으로 대응한다. 안정적인 내부 테스트 업데이트가 필요하면 별도의 비공개 Debug keystore Secret 설계를 추가한다.

### 과거 공개 Debug APK 위험

과거 공개 키로 서명된 정식 패키지명의 개발 APK는 제3자 APK로 업데이트될 수 있다. 테스트 기기에서 기존 개발 APK를 제거하고 이후 Debug는 `.debug` 패키지만 사용한다.

### CI 사용량 증가

트리거 범위를 넓히면 실행 수가 증가할 수 있다. concurrency 취소와 단일 대표 워크플로 사용으로 중복 실행을 줄인다.

### `main`과 V2 계보 충돌

1차 PR 대상을 V2 통합 기준선으로 제한한다. `main` 승격은 별도 설계와 백업 태그를 거쳐 수행한다.

### 과거 워크플로 혼선 지속

1차 단계에서는 안전을 위해 삭제하지 않는다. 대표 V2 워크플로와 README 안내를 명확히 하고 후속 인벤토리 PR에서 정리한다.

## 9. 구현 순서

1. 설계 문서와 구현 계획 커밋
2. 실패하는 저장소 위생 검사 추가
3. Android `.gitignore` 추가
4. Debug 키 파일과 고정 Debug 서명 코드 제거
5. Debug 패키지와 표시명 분리
6. V2 CI 트리거·검증·concurrency 수정
7. README와 운영 문서 작성
8. 전체 추적 텍스트 비밀정보 검사
9. 단위 테스트 및 Debug/Release 빌드 검증
10. PR 전체 diff 자체 검토
11. V2 통합 기준선 대상 Draft PR 생성

## 10. 완료 산출물

- 이 설계 문서와 구현 계획
- Android용 `.gitignore`
- 안전한 Debug/Release 서명 구성
- 저장소 위생 검사 스크립트
- 수정된 대표 V2 워크플로
- 확장된 README와 `SECURITY.md`
- 브랜치 정책 및 후속 정리 백로그
- 테스트·빌드·서명 검증 결과
- V2 통합 기준선 대상 Draft PR
