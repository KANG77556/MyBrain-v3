# MyBrain AI V2

AI 기반 메모·할 일·일정 관리 Android 애플리케이션입니다. 장기 V2 통합 기준선과 기본 브랜치 전환 대상은 `v2`입니다. 전환 전 레거시 기준선은 `main`과 `backup/main-pre-v2-20260805`에 보존하며, 이전 통합 브랜치 `feature/personal-1.1-widgets`는 전환 후 7일 관찰 기간 동안 호환 참조로 유지합니다. 기존 v1.10.1 소스는 `backup/legacy-v1.10.1`에 별도로 보관되어 있습니다.

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

브랜치 역할과 병합 기준은 `docs/BRANCH_POLICY_KO.md`를 따릅니다. 신규 기능과 저장소 정리 PR은 `v2`를 대상으로 합니다. `main`은 전환 전 레거시 기준선으로 동결하며, GitHub 기본 브랜치 변경과 롤백 절차는 `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`에 기록합니다.

보안 문제는 공개 이슈에 올리지 말고 `SECURITY.md`의 비공개 신고 절차를 사용합니다.
