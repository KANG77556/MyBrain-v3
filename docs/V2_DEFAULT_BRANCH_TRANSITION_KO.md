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
