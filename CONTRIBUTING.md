# MyBrain AI V2 기여 가이드

## 기준 브랜치
- 통합 기준선은 `v2`입니다.
- 신규 작업은 `feature/**`, 버그 수정은 `fix/**`, 저장소/빌드 작업은 `chore/**`, 문서는 `docs/**`, 테스트 전용 변경은 `test/**`를 사용합니다.
- 레거시 `main`과 보존 브랜치는 기능 개발 대상으로 사용하지 않습니다.

## 작업 원칙
1. 한 PR은 하나의 목적만 다룹니다.
2. 제품 동작 변경에는 관련 단위 테스트를 추가하거나 기존 테스트 근거를 명시합니다.
3. Room 스키마, 패키지명, 권한, Release 서명 정책 변경은 별도 PR로 분리합니다.
4. 비밀값, 키스토어, 인증서, API 키, 사용자 개인정보를 커밋하지 않습니다.
5. 앱 버전 변경은 릴리스 목적일 때만 수행합니다.

## 로컬 검증
```bash
bash scripts/check-repository-hygiene.sh
bash scripts/check-development-baseline.sh
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## PR 작성
- PR 대상 브랜치는 `v2`입니다.
- 변경 이유, 사용자 영향, 테스트 결과, 미검증 항목을 구분합니다.
- 실행하지 않은 검증을 완료로 표시하지 않습니다.
- CI가 모두 성공하고 리뷰 대화가 해결된 뒤 병합합니다.

## 릴리스
릴리스 전 `docs/RELEASE_CHECKLIST_KO.md`를 따릅니다.
