# MyBrain-v3 저장소 보존형 정리 설계

- 작성일: 2026-08-05
- 작업 브랜치: `cleanup/repository-hygiene`
- 기준 브랜치: `feature/personal-1.1-widgets`
- 1차 병합 대상: `feature/personal-1.1-widgets`
- 최종 `main` 승격: 1차 정리와 CI 검증 완료 후 별도 결정

## 1. 배경

현재 `main`은 기존 MyBrain 계보를 유지하고 있고, V2 개발은 `feature/personal-1.1-widgets`를 포함한 별도 계보에서 진행됐다. 두 브랜치는 단순한 선후 관계가 아니라 서로 갈라져 있으므로, 즉시 병합하거나 `main`을 강제로 이동하면 정상 소스와 이력을 잃을 위험이 있다.

또한 저장소에는 다수의 과거 GitHub Actions 워크플로와 실험용 브랜치가 남아 있으며, 최신 V2 브랜치의 자동 빌드 트리거가 충분하지 않다. 공개 저장소 안에 고정 디버그 서명 자료가 포함되어 있고 Android 프로젝트에 맞는 `.gitignore`도 최신 V2 계보에서 빠져 있다.

이 설계는 기존 이력을 삭제하지 않고, 별도 정리 브랜치에서 보안·CI·문서를 먼저 개선한 뒤 검증 결과를 근거로 후속 정리를 수행한다.

## 2. 목표

1. V2 개발 소스를 손실 없이 보존한다.
2. 공개 저장소에서 디버그 서명 자료와 고정 비밀번호를 제거한다.
3. 디버그 앱과 정식 앱의 패키지를 분리한다.
4. 최신 작업 브랜치와 정리 브랜치에서 자동 테스트와 APK 컴파일 검증이 실행되도록 한다.
5. Android 프로젝트에 필요한 제외 규칙과 개발·배포 문서를 갖춘다.
6. 기존 브랜치, 워크플로, PR을 삭제하기 전에 검증 가능한 기준을 만든다.

## 3. 비목표

1차 정리에서는 다음 작업을 하지 않는다.

- `main` 강제 이동 또는 기본 브랜치 변경
- 기존 기능 브랜치·백업 브랜치 삭제
- 기존 워크플로 파일의 대량 삭제
- 열린 QA PR의 종료
- 애플리케이션 기능 변경 또는 UI 개편
- 데이터베이스 스키마 변경
- Release 키 교체
- 저장소 분리

이 항목들은 1차 CI가 성공하고 정리 PR이 검토된 뒤 별도 작업으로 다룬다.

## 4. 브랜치 및 변경 흐름

1. `feature/personal-1.1-widgets`에서 `cleanup/repository-hygiene`를 분기한다.
2. 모든 1차 변경은 `cleanup/repository-hygiene`에만 커밋한다.
3. 단위 테스트, Debug APK, 미서명 Release 컴파일 검증이 모두 성공하면 `feature/personal-1.1-widgets`를 대상으로 PR을 연다.
4. 1차 PR에서는 `main`을 대상으로 하지 않는다. 현재 계보 차이가 크기 때문에 저장소 정리와 제품 계보 승격을 분리한다.
5. 1차 PR 병합 이후 `main` 승격 방법은 별도 설계로 결정한다. 후보는 신규 V2 기준 브랜치 지정, 보존 태그 생성 후 `main` 교체, 또는 저장소 분리다.

## 5. 변경 설계

### 5.1 Android 전용 `.gitignore`

저장소 루트에 Android/Gradle 중심의 `.gitignore`를 추가한다. 최소한 다음 항목을 제외한다.

- `.gradle/`
- `.idea/`
- `*.iml`
- `local.properties`
- 모든 모듈의 `build/`
- `.externalNativeBuild/`, `.cxx/`
- `captures/`
- `*.apk`, `*.aab`, `*.apks`
- `*.jks`, `*.keystore`, `*.p12`, `*.pfx`
- `*.env`, `.env.*`
- 로그 및 임시 파일
- OS별 메타데이터 파일

문서·샘플 설정처럼 의도적으로 버전 관리해야 하는 파일은 예외 규칙을 명시적으로 추가한다.

### 5.2 디버그 서명 보안

현재 저장소에 포함된 `app/debug/mybrain-v2-debug.p12.b64`를 삭제하고, `app/build.gradle`에 있는 고정 디버그 키 복원 코드와 공개 비밀번호를 제거한다.

디버그 빌드는 Android Gradle Plugin의 기본 디버그 서명을 사용한다. CI 실행마다 인증서가 달라질 수 있으므로 디버그 APK 간 덮어쓰기 호환성은 보장하지 않는다. 대신 디버그 앱은 정식 앱과 다른 패키지로 분리해 정식 설치를 침범하지 않도록 한다.

`debug` 빌드 타입에는 다음 정책을 적용한다.

- `applicationIdSuffix ".debug"`
- 앱 표시명에 Debug 구분이 가능하도록 리소스 또는 manifest placeholder 사용
- 기본 디버그 서명 사용

Release 서명은 기존처럼 환경변수와 GitHub Secrets에서만 읽는다. Release 키와 비밀번호는 저장소에 추가하지 않는다.

### 5.3 CI 워크플로 정리

우선 V2의 단일 대표 워크플로인 `.github/workflows/build-v2.yml`만 수정한다. 기존 과거 워크플로는 1차 단계에서 삭제하지 않는다.

#### 트리거

- push: `rebuild/v2`, `rebuild/v2-*`, `feature/personal-1.1-widgets`, `cleanup/**`
- pull_request: `main`, `rebuild/v2`, `feature/personal-1.1-widgets`
- 수동 실행: `workflow_dispatch`

#### 필수 검증

1. Java 17과 Android SDK 35 준비
2. `testDebugUnitTest` 실행
3. Debug APK 빌드
4. `apksigner verify`로 APK 서명 형식 검증
5. Debug APK SHA-256 생성
6. Release Secrets가 없으면 `assembleRelease`로 미서명 Release 컴파일 검증
7. Release Secrets가 있으면 고정 Release 인증서 지문 검증
8. 테스트 보고서, 빌드 로그, APK 및 해시를 아티팩트로 업로드

고정 디버그 인증서 지문 비교는 제거한다. 디버그 단계에서는 APK가 정상적으로 서명되었는지만 검증한다. Release 인증서 지문 비교는 유지한다.

워크플로의 동시 실행 낭비를 줄이기 위해 브랜치별 concurrency 그룹과 `cancel-in-progress: true`를 추가한다.

### 5.4 README 개선

루트 README에는 다음을 포함한다.

- 프로젝트 목적과 현재 V2 개발 상태
- 기준 개발 브랜치와 레거시 보관 브랜치
- 요구 도구: Java 17, Android SDK 35, Gradle 8.9
- 로컬 단위 테스트 명령
- Debug 및 Release 컴파일 명령
- Debug와 Release 패키지·서명 정책 차이
- GitHub Actions 아티팩트 사용법
- Release Secrets가 없을 때 생성되는 파일은 설치용 Release가 아니라는 경고
- 기여 시 브랜치와 PR 기준

### 5.5 보안 및 운영 문서

`SECURITY.md`를 추가해 다음 내용을 기록한다.

- API 키, 서명키, 비밀번호를 이슈나 커밋에 올리지 않는 원칙
- 민감정보 발견 시 공개 이슈 대신 사용할 신고 방법
- 유출 의심 시 키 폐기·교체·기록 정리 절차
- Debug APK는 정식 배포물이 아니라는 점

별도의 브랜치 운영 문서에는 다음 역할을 정의한다.

- `main`: 현재 공개 기준선. V2 승격 전까지 직접 변경 최소화
- `feature/personal-1.1-widgets`: 현재 V2 통합 기준선
- `cleanup/repository-hygiene`: 저장소 정리 전용 임시 브랜치
- `backup/*`: 삭제하지 않는 보존 지점
- `feature/*`: 기능 작업 브랜치

### 5.6 후속 정리 목록

1차 PR에는 삭제를 포함하지 않고, 후속 후보를 문서에 목록화한다.

- 기능이 겹치는 Android APK 워크플로
- AllFileHub, ChalkakCoach 등 다른 프로젝트용 워크플로
- 동일 커밋을 가리키는 임시 QA 브랜치
- 병합 목적이 아닌 종료 가능한 Draft PR
- 오래된 Actions 아티팩트와 보존 기간

각 후보는 마지막 실행, 대상 브랜치, 대체 워크플로, 보존 필요성을 확인한 뒤 별도 PR에서 비활성화하거나 삭제한다.

## 6. 검증 기준

다음 조건을 모두 만족해야 1차 정리가 완료된 것으로 본다.

1. 공개 트리에 `.p12`, `.pfx`, `.jks`, `.keystore` 및 Base64 키 파일이 새로 존재하지 않는다.
2. `app/build.gradle`에 디버그 키 비밀번호가 없다.
3. Debug `applicationId`가 `kr.co.mybrain.v2.debug`로 빌드된다.
4. `testDebugUnitTest`가 성공한다.
5. Debug APK가 생성되고 `apksigner verify`를 통과한다.
6. Release Secrets가 없는 환경에서도 미서명 Release 컴파일이 성공한다.
7. Release Secrets가 있는 환경에서는 기존 Release 인증서 지문 검증이 성공한다.
8. CI가 `cleanup/repository-hygiene` push에서 자동 실행된다.
9. README의 명령과 실제 CI 명령이 일치한다.
10. 1차 변경에서 기존 브랜치, 과거 워크플로, PR이 삭제되지 않는다.

## 7. 롤백

모든 변경은 `cleanup/repository-hygiene`에 한정한다. 문제가 발생하면 브랜치를 삭제하거나 해당 커밋을 되돌리면 기준 브랜치에는 영향이 없다.

정리 PR을 병합한 뒤 문제가 발견되면 다음 순서로 복구한다.

1. 정리 PR의 merge commit을 revert한다.
2. Release Secrets와 인증서 자체는 변경하지 않았으므로 기존 Release 빌드 경로를 복원한다.
3. 삭제한 공개 디버그 키 파일은 복원하지 않는다. 디버그 빌드가 필요하면 기본 디버그 서명을 사용한다.
4. `feature/personal-1.1-widgets`의 병합 직전 커밋을 보존 태그로 남긴다.

## 8. 위험과 대응

### 디버그 APK 업데이트 불가

기본 디버그 키가 환경마다 달라 기존 디버그 앱 위에 업데이트 설치가 안 될 수 있다. 패키지를 `.debug`로 분리하고 개발용 앱은 삭제 후 재설치하는 정책으로 대응한다. 안정적인 내부 테스트 업데이트가 필요해지면 별도의 비공개 Debug keystore Secret 설계를 추가한다.

### CI 사용량 증가

트리거 범위를 넓히면 실행 수가 증가할 수 있다. concurrency 취소와 단일 대표 워크플로 사용으로 중복 실행을 줄인다.

### `main`과 V2 계보 충돌

1차 PR의 대상을 `feature/personal-1.1-widgets`로 제한한다. `main` 승격은 정리 성공 이후 별도 설계와 백업 태그를 거쳐 수행한다.

### 과거 워크플로 혼선 지속

1차 단계에서는 안전을 위해 삭제하지 않으므로 Actions 목록이 당장은 복잡하게 남는다. 대표 V2 워크플로 이름과 README 안내를 명확히 하고, 후속 인벤토리 PR에서 정리한다.

## 9. 구현 순서

1. 설계 문서 커밋
2. 구현 계획 작성
3. Android `.gitignore` 추가
4. 디버그 키 파일과 고정 디버그 서명 코드 제거
5. Debug 패키지 분리
6. V2 CI 트리거·검증·concurrency 수정
7. README, `SECURITY.md`, 브랜치 운영 문서 작성
8. 정적 비밀정보 검사
9. 단위 테스트 및 Debug/Release 빌드 검증
10. 변경사항 자체 검토
11. `feature/personal-1.1-widgets` 대상 Draft PR 생성

## 10. 완료 산출물

- `docs/superpowers/specs/2026-08-05-repository-hygiene-design.md`
- Android용 `.gitignore`
- 안전한 Debug/Release 서명 구성
- 수정된 `.github/workflows/build-v2.yml`
- 확장된 `README.md`
- `SECURITY.md`
- 브랜치 운영 문서
- 테스트·빌드·서명 검증 결과
- V2 통합 기준 브랜치 대상 Draft PR
