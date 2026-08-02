# Brain Assistant — Codex 인수인계

## 1. 작업 위치

- 저장소: `KANG77556/MyBrain-v3`
- 계속 작업할 브랜치: `feature/brain-assistant-mvp`
- Draft PR: `#88`
- 인수인계 기준 커밋: `853f6a5f62844f9b76627ebbf97bf01fc0f93338`

`main`에서 직접 구현하지 말고, 위 브랜치에서 별도 worktree 또는 하위 작업 브랜치를 만들어 진행한다.

## 2. 현재 검증 상태

기준 커밋에서 다음 워크플로가 성공했다.

- `Brain Assistant Extended Features` run `30700177355`
  - JVM 단위 테스트 성공
  - Debug APK 빌드 성공
  - Android 테스트 APK 컴파일 성공
  - API 28 에뮬레이터 데이터베이스 테스트 성공
- `Export Brain Assistant Source` run `30700177329` 성공
- `Build MyBrain AI APK` run `30700177338` 성공

`Brain Assistant MVP Android`와 `Diagnose Brain Assistant Serialization`은 별도의 구형·진단 워크플로라 실패 상태가 남아 있다. 현재 기능 검증 기준은 `Brain Assistant Extended Features`다. 단, 릴리스 전에 중복 워크플로를 정리하거나 실패 원인을 문서화한다.

## 3. 완료된 기능

- 모바일 중심 대시보드와 태블릿 반응형 배치
- 일정·할 일·메모 기본 저장 흐름
- D-Day Room 저장·관찰·대표 항목 조회
- 홈 대시보드 대표 D-Day 카드
- 앱 내부 캘린더
  - 월간 42일 그리드
  - 주간 보기
  - 일정 목록 보기
  - 이전·다음·오늘 이동
  - 선택일 일정 필터
  - 스마트폰 1열 / 태블릿 2열
- 자연어 분석 안전 정책
  - 신뢰도 높은 로컬 분석은 원격 AI 미호출
  - 애매한 경우만 원격 분석 시도
  - 원격 결과가 더 좋을 때만 채택
  - 일정 날짜·시간 누락 시 검토
  - D-Day 제목·목표일 분리
  - 날짜 없는 D-Day는 저장하지 않고 검토
- 현재 자동 저장 기준: 신뢰도 `0.85` 이상이며 필수 필드가 모두 존재할 때

## 4. 소스 구조 주의사항

저장소는 원본 전체 소스를 일반적인 형태로 직접 보관하지 않고, 분할 아카이브와 CI 오버레이로 완성 소스를 구성한다.

주요 위치:

- `.github/workflows/brain-assistant-extended-features.yml`
- `android-build/brain-assistant-source.part*`
- `android-build/dashboard-overlay/`
- `android-build/feature-overlay/`
- `android-build/device-calendar.patch`

실제 빌드되는 소스를 판단할 때는 워크플로의 적용 순서를 기준으로 한다.

1. 분할 소스 복원
2. 기준 수정 적용
3. `device-calendar.patch` 적용
4. dashboard overlay 복사
5. feature overlay 복사

새 구현은 우선 `android-build/feature-overlay/`에 반영하고, 완성 소스 내보내기 워크플로 결과로 실제 적용 상태를 확인한다.

## 5. 다음 구현 목표

다음 우선순위는 **한 문장의 복합 자연어 입력을 여러 항목으로 안전하게 분리·검토·저장하는 배치 흐름**이다.

예시 입력:

> 다음 주 월요일 10시 교무회의 넣고, 금요일까지 보고서 제출 할 일로 추가하고, 제주 여행 준비물은 메모해줘.

기대 결과:

1. 일정: 다음 주 월요일 10시 교무회의
2. 할 일: 금요일까지 보고서 제출
3. 메모: 제주 여행 준비물
4. 원문은 배치 기록에 보존
5. 한 항목이라도 애매하면 전체 배치를 검토 화면으로 이동
6. 사용자가 확인하면 하나의 트랜잭션으로 전부 저장
7. 저장 도중 하나라도 실패하면 전체 롤백
8. 저장 직후 한 번에 취소할 수 있는 Undo 제공

## 6. 구현 순서 — 반드시 테스트 우선

### Task A. 배치 분석 도메인 계약

RED 테스트부터 작성한다.

- 문장부호와 연결어로 복합 문장을 분리한다.
- 각 조각을 `ParsedItem`으로 분석한다.
- 원문, 순서, 배치 ID를 보존한다.
- 단일 입력은 기존 동작과 호환된다.
- 한 항목이 애매하면 `requiresBatchReview = true`가 된다.

권장 새 모델 예시:

```kotlin
data class ParsedBatch(
    val originalText: String,
    val items: List<ParsedItem>,
    val requiresReview: Boolean,
)
```

기존 공개 API와 테스트를 불필요하게 깨지 말고 최소 확장한다.

### Task B. 저장 원자성

RED 테스트:

- 배치 전체가 하나의 Room transaction으로 저장된다.
- 중간 실패 시 일정·할 일·메모·D-Day가 부분 저장되지 않는다.
- 저장된 ID 목록 또는 배치 ID로 전체 Undo가 가능하다.

Repository와 DAO 계층에 transaction 경계를 둔다. UI 계층에서 여러 insert를 순차 호출하는 방식은 금지한다.

### Task C. 배치 검토 화면

RED Compose 테스트:

- 여러 분석 결과를 카드 목록으로 표시한다.
- 각 카드에서 유형·제목·날짜·시간·우선순위를 수정할 수 있다.
- 필수 필드 누락 카드에 오류 안내를 표시한다.
- 전체 저장 버튼은 모든 항목이 유효할 때만 활성화한다.
- 항목 삭제와 원문 확인을 제공한다.
- 한 손 조작을 위해 저장 버튼은 하단 고정 영역에 둔다.

스마트폰은 1열, 태블릿은 왼쪽 원문·오른쪽 분석 결과의 2열 구성을 권장한다.

### Task D. 캘린더·대시보드 반영

- 배치 저장 직후 일정은 캘린더에 즉시 보인다.
- 할 일·메모·D-Day는 대시보드 요약에 즉시 반영된다.
- 부분 갱신이나 중복 표시가 없어야 한다.

### Task E. 검증과 산출물

최소 검증:

```bash
./gradlew --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest \
  :app:assembleDebug
```

그 다음 `Brain Assistant Extended Features` 워크플로를 통과시킨다.

완료 보고에는 반드시 포함한다.

- 테스트 개수와 실패 0건
- 성공한 workflow run ID
- 최종 commit SHA
- Debug APK artifact 위치와 SHA-256
- 실제 Galaxy 기기 테스트 여부

실제 기기에서 확인하지 않았다면 확인했다고 표현하지 않는다.

## 7. 테스트 사례

다음 사례를 고정 테스트로 추가한다.

- `내일 3시 치과 예약`
- `다음 주 월요일 회의` → 시간 누락으로 검토
- `오후 4시 상담` → 날짜 누락으로 검토
- `8월 20일 개학 디데이`
- `개학 디데이` → 날짜 누락으로 검토
- `내일 3시 회의하고 금요일까지 보고서 제출해`
- `엄마 생일은 8월 12일 디데이로, 선물 사기는 할 일로 추가해`
- 원격 AI 장애 시 로컬 결과 유지
- 원격 결과 신뢰도가 낮으면 로컬 결과 유지
- 배치 중 두 번째 항목 저장 실패 시 전체 롤백

## 8. Codex 시작 지시문

Codex에서 아래 내용을 그대로 사용한다.

```text
KANG77556/MyBrain-v3 저장소의 feature/brain-assistant-mvp 브랜치에서 계속 작업해.
먼저 docs/CODEX_HANDOFF.md와 저장소의 AGENTS.md/CLAUDE.md가 있으면 읽어.
Superpowers의 using-git-worktrees, executing-plans 또는 subagent-driven-development, test-driven-development, verification-before-completion 절차를 적용해.
main에는 직접 커밋하지 말고 격리된 worktree/작업 브랜치를 사용해.

현재 기준 커밋은 853f6a5f62844f9b76627ebbf97bf01fc0f93338이고, 다음 목표는 복합 자연어 입력을 여러 일정·할 일·메모·D-Day 항목으로 분리한 뒤 전체 배치 검토와 원자적 저장·Undo를 구현하는 것이다.

반드시 RED 테스트부터 작성하고, feature-overlay 중심으로 최소 구현해. 워크플로의 소스 복원·patch·overlay 적용 순서를 보존해.
각 단계마다 테스트를 실행하고, 실패 원인을 확인한 뒤 수정해. 최종적으로 Brain Assistant Extended Features 워크플로 성공, Debug APK 생성, SHA-256 계산까지 완료해. 실제 Galaxy 기기 검증을 하지 않았으면 했다고 말하지 마.
```
