# MyBrain V2 지속 개발 기준선 설계

- 작성일: 2026-08-05
- 저장소: `KANG77556/MyBrain-v3`
- 설계 브랜치: `docs/development-baseline-design`
- 선행 PR: `#91 chore: prepare v2 as the default branch`
- 구현 대상 브랜치: `chore/development-baseline`
- 구현 PR 대상: `v2`

## 1. 배경

MyBrain V2에는 메모·할 일·일정 저장, AI 분석, 알림, 설정, 공유, 백업·복구, 음성 입력, 위젯과 화면 제어 코드가 이미 기능별 패키지로 분리되어 있다. `assistant`, `data`, `reminder`, `settings`, `share`, `transfer`, `ui`, `voice`, `widget` 패키지와 각 영역의 단위 테스트가 존재하므로, 새로운 아키텍처를 다시 만드는 것보다 현재 구조의 경계를 명확하게 정하고 반복 가능한 개발 절차를 추가하는 편이 안전하다.

반면 `MainActivity`, `AdaptiveMainActivity`, `CloudAiWorkItemAnalyzer`, 일부 UI Controller처럼 책임과 코드량이 큰 파일도 남아 있다. 앞으로 신규 기능을 이 파일들에 직접 계속 추가하면 화면, 비즈니스 규칙, 저장, AI, 권한 처리의 결합이 다시 커질 수 있다.

저장소 운영 측면에서는 V2 장기 브랜치 `v2`와 대표 CI가 준비되고 있지만, 신규 개발자가 다음 내용을 한 곳에서 확인하기 어렵다.

- 어떤 브랜치에서 작업을 시작하는가
- 새 로직을 어느 패키지와 클래스 유형에 넣는가
- 기능 완료 조건과 필수 테스트는 무엇인가
- 이슈와 PR에 어떤 근거를 남기는가
- 정식 APK를 만들기 전 무엇을 검증하는가
- 다음 개발 우선순위는 무엇인가

이 설계는 제품 기능을 바꾸거나 대규모 리팩터링을 수행하지 않고, 이후 앱 개발을 같은 방식으로 반복할 수 있는 문서·템플릿·로드맵 기준선을 만드는 것을 목적으로 한다.

## 2. 목표

1. `v2`를 단일 장기 통합 기준선으로 사용한다.
2. 기능·버그·정리 작업의 브랜치와 PR 흐름을 표준화한다.
3. 기존 패키지별 책임과 새 코드 배치 규칙을 명문화한다.
4. Activity에 비즈니스 규칙이 다시 집중되지 않도록 점진적 추출 원칙을 정한다.
5. 기능 이슈와 PR에서 요구사항, 검증, 데이터·권한 영향, 롤백 방법을 빠뜨리지 않게 한다.
6. 앱의 다음 개발 순서를 P0~P3 로드맵으로 고정한다.
7. Release APK를 만들기 전 필요한 서명·해시·설치·복구 검증 절차를 체크리스트로 만든다.
8. 기존 CI와 59개 단위 테스트를 유지하면서 문서 기준선을 추가한다.

## 3. 비목표

이번 기준선 작업에서는 다음을 수행하지 않는다.

- Activity 또는 AI 분석 클래스의 대규모 분해
- Java에서 Kotlin 또는 Jetpack Compose로 전환
- 새 DI 프레임워크, 네트워크 라이브러리, 테스트 프레임워크 도입
- Room 스키마 변경 또는 마이그레이션 구현
- 앱 기능, 화면, 문구, 권한, 버전 번호 변경
- Release 키 또는 Secrets 변경
- 과거 브랜치·워크플로·QA PR 삭제
- `develop` 같은 추가 장기 브랜치 생성
- 모든 기존 기술 부채를 한 PR에서 해결

대규모 코드 분해는 해당 화면이나 기능을 실제로 수정하는 PR에서 관련 로직만 작게 추출하는 방식으로 진행한다.

## 4. 선택한 운영 방식

### 4.1 단일 장기 브랜치

장기 통합 브랜치는 `v2` 하나만 사용한다. 별도의 `develop` 브랜치는 두지 않는다. 저장소 소유자가 PR #91 병합 후 GitHub 기본 브랜치를 `v2`로 변경하면, 신규 기능과 정리 PR은 모두 `v2`를 대상으로 한다.

작업 브랜치 이름은 다음 규칙을 사용한다.

```text
feature/<짧은-기능명>
fix/<짧은-문제명>
chore/<짧은-정리명>
docs/<짧은-문서명>
test/<짧은-검증명>
```

예:

```text
feature/quick-entry-review
fix/reminder-timezone-reschedule
chore/room-schema-export
test/android15-launch-smoke
```

하나의 브랜치는 하나의 사용자 가치 또는 하나의 기술 문제만 해결한다.

### 4.2 PR 우선 개발

`v2` 직접 push를 개발 경로로 사용하지 않는다. 모든 변경은 짧은 작업 브랜치와 PR을 통해 통합한다.

기능 PR의 기본 흐름은 다음과 같다.

```text
Issue
→ 작업 브랜치
→ 실패하는 테스트 또는 재현 근거
→ 최소 구현
→ 전체 단위 테스트와 APK 검증
→ Pull Request
→ 리뷰와 CI
→ squash merge
```

긴 기능은 하나의 대형 PR로 만들지 않고 독립적으로 검증 가능한 하위 기능으로 나눈다.

## 5. 추가할 저장소 기준선 파일

구현 PR에서는 다음 파일을 추가하거나 수정한다.

```text
CONTRIBUTING.md
README.md
.github/ISSUE_TEMPLATE/feature.yml
.github/ISSUE_TEMPLATE/bug.yml
.github/pull_request_template.md
docs/ARCHITECTURE_KO.md
docs/DEVELOPMENT_ROADMAP_KO.md
docs/RELEASE_CHECKLIST_KO.md
```

### 5.1 `CONTRIBUTING.md`

다음 내용을 한 문서에서 제공한다.

- Java 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9 요구사항
- 저장소 복제와 `v2` 최신화
- 작업 브랜치 이름 규칙
- 로컬 위생 검사, 단위 테스트, Debug/Release 빌드 명령
- 기능별 코드 배치 규칙
- 테스트 우선 개발 원칙
- PR 생성 전 체크리스트
- 민감정보와 Release 키 금지 규칙
- 작은 PR과 점진적 리팩터링 원칙

### 5.2 `README.md`

기존 프로젝트 소개와 빌드 안내는 유지하고, 다음 링크를 추가한다.

- 기여 방법: `CONTRIBUTING.md`
- 아키텍처: `docs/ARCHITECTURE_KO.md`
- 개발 로드맵: `docs/DEVELOPMENT_ROADMAP_KO.md`
- Release 체크리스트: `docs/RELEASE_CHECKLIST_KO.md`

README는 상세 규칙을 중복하지 않고 각 기준 문서의 진입점 역할만 한다.

### 5.3 기능 이슈 템플릿

`.github/ISSUE_TEMPLATE/feature.yml`은 다음 입력을 요구한다.

- 사용자 문제
- 원하는 사용자 흐름
- 범위에 포함되는 항목
- 범위에 포함되지 않는 항목
- 완료 조건
- 데이터·알림·권한·AI·백업 영향
- 화면 변경 여부
- 테스트 시나리오

완료 조건은 사용자 관점에서 확인 가능한 문장으로 작성한다.

### 5.4 버그 이슈 템플릿

`.github/ISSUE_TEMPLATE/bug.yml`은 다음 입력을 요구한다.

- 발생한 문제
- 기대 결과
- 재현 순서
- 발생 빈도
- 앱 버전과 Android 버전
- 관련 화면 또는 기능
- 데이터 손실·알림 누락·보안 영향
- 로그와 스크린샷에서 제거해야 할 개인정보 안내

### 5.5 PR 템플릿

`.github/pull_request_template.md`에는 다음 섹션을 둔다.

- 목적
- 변경 범위
- 범위 밖 항목
- 사용자 검증 시나리오
- 수행한 테스트와 결과
- Room 스키마·백업 형식 영향
- Android 권한·Manifest 영향
- AI 요청·비용·개인정보 영향
- 알림·위젯 영향
- Release·서명 영향
- 롤백 방법
- 스크린샷 또는 기기 검증 결과

체크박스는 실제로 해당하지 않는 경우 `해당 없음` 근거를 적게 한다.

## 6. 아키텍처 기준

`docs/ARCHITECTURE_KO.md`는 현재 구조를 새로운 프레임워크 이름으로 포장하지 않고, 코드가 실제로 사용하는 책임 단위에 맞춰 작성한다.

### 6.1 최상위 화면과 Android 진입점

대상:

```text
kr.co.mybrain.v2.*Activity
MyBrainApplication
BroadcastReceiver
AppWidgetProvider
```

책임:

- Android 생명주기와 Intent 수신
- 화면 생성과 View 연결
- 권한 요청과 시스템 설정 이동
- Controller 또는 Repository 호출
- 결과 렌더링과 사용자 메시지 표시

금지:

- 날짜·시간 계산 규칙 직접 구현
- 일정 충돌 판단 직접 구현
- AI 응답 JSON 복구와 검증 직접 구현
- 저장 무결성 판단 직접 구현
- 반복 일정 계산 직접 구현
- 백업 충돌 정책 직접 구현
- 네트워크 재시도 정책 직접 구현

Activity가 위 규칙을 필요로 하면 기존 Policy·Controller·Parser·Repository를 사용하거나 작은 새 클래스로 추출한다.

### 6.2 `assistant`

책임:

- 자연어 입력 파싱
- 클라우드 AI 요청과 응답 처리
- 개인정보 필터
- AI 결과 복구와 검증
- 재시도·캐시·표시 정책

규칙:

- 네트워크 통신, JSON 복구, 결과 검증, 비용·재시도 정책을 한 클래스에 모두 추가하지 않는다.
- 순수 문자열·시간·결과 판정은 Android 의존성이 없는 Policy 또는 Parser로 둔다.
- AI 결과는 저장 전에 사용자가 검토할 수 있는 구조를 유지한다.
- API 키와 원문 개인정보를 로그에 기록하지 않는다.

`CloudAiWorkItemAnalyzer`와 같이 큰 기존 클래스는 기능 수정 시 네트워크 요청, 응답 파싱, 결과 검증 중 수정 대상 책임 하나만 별도 클래스로 추출한다. 단순 코드 이동만 하는 대규모 PR은 만들지 않는다.

### 6.3 `data`

책임:

- Room Entity, DAO, Database
- 데이터 조회·저장·갱신·삭제
- 저장 단위와 무결성 경계

규칙:

- Activity와 Widget이 DAO를 직접 호출하지 않는다.
- 데이터 접근은 `WorkItemRepository` 같은 Repository를 통한다.
- 스키마가 바뀌면 migration, schema export, 백업 호환성, 롤백 경로를 같은 설계에서 다룬다.
- 저장 완료 후 알림과 위젯 갱신이 필요한 경우 Controller 또는 조정 계층에서 순서를 명시한다.

### 6.4 `reminder`

책임:

- 알림 예약·취소
- 반복 일정 계산
- 재부팅·시간·시간대 변경 후 재예약
- 권한 상태와 알림 동작 기록

규칙:

- 시간 계산은 `RecurrenceCalculator`와 같은 순수 로직으로 둔다.
- Receiver는 입력 검증과 호출 연결만 담당한다.
- 정확한 알람 권한이 없을 때의 대체 동작을 명시한다.
- 같은 작업의 중복 알람이 생기지 않도록 식별자 정책을 테스트한다.

### 6.5 `settings`

책임:

- 사용자 설정 화면
- AI 제공자·모델·예산·사용량
- 암호화된 민감값 저장
- 접근성·알림 설정

규칙:

- API 키는 `EncryptedValueStore`를 통해서만 저장한다.
- 가격과 예산 계산은 화면 코드가 아닌 Catalog·Settings·Store 단위로 둔다.
- 설정 변경이 런타임 동작에 반영되는 경로를 테스트한다.

### 6.6 `share`와 `voice`

책임:

- 외부 공유 Intent에서 텍스트·문서 입력 추출
- 음성 인식 세션과 결과 전달

규칙:

- 입력 크기, MIME type, null URI, 권한 실패를 경계에서 처리한다.
- 추출된 원문을 바로 저장하지 않고 기존 입력 검토 흐름으로 전달한다.

### 6.7 `transfer`

책임:

- 백업 생성·암호화·복호화
- 복구 계획, 충돌 판단, 진단
- 앱 업데이트 APK 검증과 설치 연결

규칙:

- 백업 형식 변경은 버전 필드와 이전 버전 복구 테스트를 동반한다.
- 복구는 적용 전 미리보기와 검증을 거친다.
- 손상 파일, 잘못된 암호, 일부 데이터 실패에서 기존 데이터를 훼손하지 않는다.
- APK 설치 전 패키지명, 서명, 해시, 버전 관계를 확인한다.

### 6.8 `ui`

책임:

- 화면 동작을 조정하는 Controller
- 순수 표시·레이아웃·충돌·선택 Policy
- 공통 UI 도우미

규칙:

- Policy는 가능한 한 Android Context 없이 입력과 출력이 명확한 순수 함수로 만든다.
- Controller는 View와 Repository·Policy를 연결하지만 장기 데이터 저장 규칙을 소유하지 않는다.
- 하나의 Controller가 AI, 저장, 알림, 위젯을 모두 직접 처리하기 시작하면 흐름 조정 클래스를 별도로 둔다.

### 6.9 `widget`

책임:

- 위젯 데이터 조회와 RemoteViews 구성
- 오늘·일정·할 일·빠른 메모 표시 정책

규칙:

- 위젯 문구와 항목 선택 규칙은 Policy에서 테스트한다.
- AppWidgetProvider에서 복잡한 날짜·정렬·잘라내기 규칙을 직접 구현하지 않는다.
- 데이터 변경 후 위젯 갱신 경로를 명시한다.

## 7. 표준 사용자 데이터 흐름

핵심 입력 흐름은 다음 순서로 통일한다.

```text
사용자 입력
→ 입력 정규화
→ 로컬 Parser 또는 선택적 클라우드 AI 분석
→ 개인정보 필터와 결과 검증
→ 사용자가 결과 검토·수정
→ 저장 무결성 확인
→ Repository 저장
→ 알림 재계산
→ 위젯·목록 화면 갱신
→ 성공 또는 복구 가능한 오류 표시
```

원칙:

1. AI 결과를 자동으로 최종 저장하지 않는다.
2. 저장 성공 전에 알림 성공으로 표시하지 않는다.
3. 저장이 성공하고 알림 예약만 실패한 경우 데이터는 유지하고 재시도 가능한 상태를 사용자에게 알린다.
4. 위젯 갱신 실패가 저장을 되돌리지는 않는다.
5. 각 단계는 독립적으로 테스트 가능한 입력과 출력을 가져야 한다.

## 8. 오류 처리 기준

오류를 다음 네 종류로 구분한다.

### 사용자 수정 가능 오류

예: 제목 없음, 날짜 불명확, 겹치는 일정, 잘못된 백업 암호.

- 입력 필드 또는 검토 화면에서 구체적인 수정 방법을 보여준다.
- 원본 입력을 잃지 않는다.

### 일시적 외부 오류

예: 네트워크 실패, AI 시간 초과, 시스템 음성 인식 중단.

- 안전한 횟수 내에서만 재시도한다.
- 로컬 파서 또는 수동 입력으로 계속할 경로를 제공한다.
- API 키, 전체 원문, 응답 본문을 일반 로그에 남기지 않는다.

### 권한·시스템 제약

예: 알림 권한 거부, 정확한 알람 권한 없음, 파일 URI 접근 실패.

- 기능 전체가 실패한 것처럼 처리하지 않고 제한된 동작을 설명한다.
- 설정 이동이 필요한 경우 사용자 행동 후 다시 검사한다.

### 데이터 무결성 오류

예: Room 저장 실패, 복구 파일 손상, 중복 저장, migration 실패.

- 부분 성공을 숨기지 않는다.
- 기존 데이터를 삭제하거나 덮어쓰기 전에 검증과 백업을 수행한다.
- 복구 불가능한 경우 진단 정보를 제공하되 개인정보는 제외한다.

## 9. 테스트 전략

### 9.1 순수 단위 테스트

다음 로직은 JUnit 테스트를 필수로 한다.

- Parser와 날짜·시간 계산
- Policy의 분기와 경계값
- AI JSON 복구와 결과 검증
- 저장 무결성 판단
- 반복 일정과 충돌 판단
- 위젯 항목·문구 선택
- 가격·예산 계산
- 백업 계획과 호환성 판단

새 동작은 실패하는 테스트 또는 명확한 재현 케이스를 먼저 추가한다.

### 9.2 Repository와 Room 테스트

P0 작업에서 Android 계측 테스트를 추가해 다음을 검증한다.

- Database 생성과 기본 CRUD
- transaction 경계
- 중복 저장 방지
- migration과 schema export
- 테스트 종료 후 격리

### 9.3 최소 앱 실행 테스트

P0 작업에서 Android 15 규격 에뮬레이터 기준 최소 smoke test를 둔다.

- Debug APK 설치
- 런처 Activity 실행
- 프로세스 생존
- 치명적 예외 없음
- 기본 저장소 초기화

화면 전체 자동화는 이번 기준선 범위에 포함하지 않는다.

### 9.4 PR 필수 검증

모든 PR에서 다음을 실행한다.

```bash
bash scripts/check-repository-hygiene.sh
gradle --stacktrace testDebugUnitTest
gradle --stacktrace assembleDebug
gradle --stacktrace assembleRelease
```

대표 CI는 Debug 패키지, 앱 이름, APK Signature Scheme v2와 조건부 Release 경로를 계속 확인한다.

## 10. 점진적 리팩터링 규칙

1. 기존 큰 파일을 이유 없이 한 번에 분해하지 않는다.
2. 큰 파일을 수정하는 기능 PR은 새 비즈니스 규칙을 그 파일 안에 직접 추가하지 않는다.
3. 수정하려는 규칙을 먼저 테스트 가능한 Policy·Parser·Controller·Repository로 추출한다.
4. 추출과 기능 변경이 서로 독립적으로 리뷰 가능하면 두 PR로 나눈다.
5. 신규 production 파일은 한 가지 책임을 갖도록 설계한다.
6. 500줄을 넘는 새 파일은 PR 본문에 분리하지 못한 이유와 후속 계획을 적는다.
7. 이미 500줄을 넘는 파일은 기능 추가로 더 키우지 않는 것을 기본 원칙으로 한다.
8. 단순 클래스 수 증가가 아니라 테스트 가능한 경계가 생겼는지를 기준으로 평가한다.

## 11. 개발 로드맵

`docs/DEVELOPMENT_ROADMAP_KO.md`는 다음 우선순위를 사용한다.

### P0 — 개발·배포 안정성

완료 전에는 대형 신규 기능보다 기반 안정성을 우선한다.

1. PR #91 병합과 기본 브랜치 `v2` 전환
2. `v2` Ruleset과 필수 CI 설정
3. Release Secrets가 있는 환경에서 고정 서명 Release 경로 검증
4. Room schema export 위치와 보관 정책 확정
5. 현재 DB 버전의 migration 기준선과 계측 테스트 추가
6. Android 15 Debug 설치·실행 smoke test 추가
7. 앱 버전·APK 해시·서명 결과를 포함하는 Release 후보 절차 확정

### P1 — 핵심 입력·검토·저장 흐름

기준 사용자 시나리오:

```text
빠른 입력
→ 로컬 또는 AI 분석
→ 결과 검토와 수정
→ 저장
→ 할 일·일정·메모에서 일관된 조회
```

검증 항목:

- 오프라인 로컬 입력
- AI 실패와 로컬 대체
- 날짜가 모호한 입력
- 저장 중 중복 탭
- 일정 충돌
- 저장 성공 후 알림·위젯 반영

### P2 — 알림과 위젯 신뢰성

- 재부팅 후 알림 복원
- 앱 업데이트 후 재예약
- 시간과 시간대 변경
- 반복 일정
- 알림 권한과 정확한 알람 권한 거부
- 오늘·할 일·일정·빠른 메모 위젯 갱신
- Android 15 실기기 또는 동등 에뮬레이터 검증

### P3 — 데이터 보호와 정식 배포

- 암호화 백업 생성과 검증
- 복구 전 미리보기
- 일부 충돌과 손상 파일 처리
- 이전 백업 형식 호환성
- 서명된 Release APK 설치·업데이트 검증
- Release 노트, 해시, 인증서 지문과 롤백 기록

## 12. 첫 기준선 이후 등록할 이슈

기준선 PR 병합 후 다음 이슈를 별도로 등록한다.

1. `P0: Verify signed Release path with GitHub Secrets`
2. `P0: Export Room schemas and define migration baseline`
3. `P0: Add Android 15 launch smoke test`
4. `P1: Define quick-entry review and save acceptance scenarios`
5. `P2: Verify reminder rescheduling across reboot and timezone changes`
6. `P3: Validate encrypted backup restore compatibility`

각 이슈는 구현을 바로 시작하기 위한 세부 코드 지시가 아니라 사용자 문제, 완료 조건, 테스트 범위를 담는다.

## 13. Release 체크리스트

`docs/RELEASE_CHECKLIST_KO.md`는 다음 단계로 구성한다.

### 준비

- `v2` 최신화
- 작업 트리와 CI 상태 확인
- 버전 코드와 버전 이름 증가
- 변경 로그 작성
- Room migration·백업 호환성 확인

### 검증

- 저장소 위생 검사
- 전체 단위 테스트
- 계측·smoke test
- Debug APK 검증
- 고정 서명 Release 빌드
- 예상 Release 인증서 지문 확인
- SHA-256 생성과 재검증
- 기존 설치 위 업데이트 테스트
- 신규 설치 테스트
- 백업 생성·복구 확인

### 배포

- APK, 해시, 서명 보고서 보관
- Release 노트 게시
- 설치 대상과 롤백 APK 기록
- 배포 후 치명적 오류·데이터 손실·알림 누락 확인

미서명 Release APK는 설치·배포용 산출물로 표시하지 않는다.

## 14. 구현 순서

1. PR #91을 `v2`에 병합하고 병합 후 CI를 확인한다.
2. 저장소 소유자가 기본 브랜치를 `v2`로 변경하고 Ruleset을 설정한다.
3. `v2`에서 `chore/development-baseline` 브랜치를 만든다.
4. `CONTRIBUTING.md`와 README 링크를 작성한다.
5. 아키텍처, 로드맵, Release 체크리스트를 작성한다.
6. 기능·버그 이슈 템플릿과 PR 템플릿을 추가한다.
7. 문서의 명령과 현재 CI·Gradle 설정이 일치하는지 검사한다.
8. 기존 전체 단위 테스트와 Debug/Release 빌드를 실행한다.
9. `v2` 대상 Draft PR을 열고 문서·템플릿을 검토한다.
10. PR 병합 후 P0 이슈를 등록한다.

## 15. 검증 기준

개발 기준선 완료 조건은 다음과 같다.

1. PR #91이 `v2`에 병합되어 있다.
2. 저장소 기본 브랜치가 `v2`다.
3. `CONTRIBUTING.md`만 읽고 작업 브랜치 생성부터 PR 검증까지 수행할 수 있다.
4. `ARCHITECTURE_KO.md`가 모든 현재 주요 패키지의 책임을 설명한다.
5. Activity와 Policy·Controller·Repository의 경계가 명확하다.
6. `DEVELOPMENT_ROADMAP_KO.md`에 P0~P3 우선순위와 완료 조건이 있다.
7. `RELEASE_CHECKLIST_KO.md`에 서명·해시·업데이트·백업 검증이 포함된다.
8. 기능·버그 이슈 템플릿이 요구사항과 영향 범위를 수집한다.
9. PR 템플릿이 테스트, 데이터, 권한, AI, 알림, 위젯, Release, 롤백 영향을 확인한다.
10. README에서 모든 기준 문서로 이동할 수 있다.
11. 기존 저장소 위생 검사와 59개 단위 테스트가 성공한다.
12. Debug APK와 미서명 또는 고정 서명 Release 컴파일이 성공한다.
13. 제품 코드, 앱 버전, Room 스키마, 권한에는 변경이 없다.

## 16. 롤백

기준선 PR은 문서와 GitHub 템플릿만 변경한다. 문제가 있으면 해당 PR의 squash commit을 revert하면 제품 코드와 데이터에는 영향이 없다.

PR #91 또는 기본 브랜치 전환에 문제가 생긴 경우에는 `docs/V2_DEFAULT_BRANCH_TRANSITION_KO.md`에 따라 기본 브랜치를 `main`으로 되돌리고, 개발 기준선 PR은 병합하지 않는다.

## 17. 위험과 대응

### 문서와 실제 코드 불일치

문서에서 현재 존재하지 않는 클래스나 명령을 표준으로 만들지 않는다. 구현 시 파일 경로, Gradle task, 워크플로 이름을 실제 저장소와 대조한다.

### 절차 과다

모든 작은 변경에 불필요한 문서를 요구하지 않는다. 이슈 템플릿은 사용자 문제와 완료 조건에 집중하고, PR 템플릿의 영향 항목은 `해당 없음`을 허용하되 이유를 적는다.

### 대형 기술 부채 지연

점진적 리팩터링 원칙이 무기한 미루기로 변하지 않도록 P0 이후 큰 파일을 수정하는 PR에서 추출 여부를 반드시 검토한다. 독립적인 전면 재작성은 하지 않는다.

### 기본 브랜치 전환과 기준선 작업 혼합

PR #91과 개발 기준선 PR을 분리한다. 기본 브랜치 전환이 안정되기 전에는 기준선 PR을 `v2`에 병합하지 않는다.

## 18. 완료 산출물

- `CONTRIBUTING.md`
- 업데이트된 `README.md`
- `.github/ISSUE_TEMPLATE/feature.yml`
- `.github/ISSUE_TEMPLATE/bug.yml`
- `.github/pull_request_template.md`
- `docs/ARCHITECTURE_KO.md`
- `docs/DEVELOPMENT_ROADMAP_KO.md`
- `docs/RELEASE_CHECKLIST_KO.md`
- `v2` 대상 개발 기준선 PR
- P0 후속 이슈 3개와 P1~P3 추적 이슈

## 19. 설계 자체 점검

- 미정 값, TODO, 구현을 뒤로 미루는 모호한 표현을 남기지 않았다.
- 새 프레임워크를 도입하지 않고 현재 Java·Gradle·패키지 패턴을 유지했다.
- 제품 코드 변경과 저장소 운영 기준선을 분리했다.
- 브랜치 전환 PR #91과 개발 기준선 PR의 순서를 명확히 했다.
- Activity, Controller, Policy, Repository, Parser의 책임을 구분했다.
- 오류 처리, 테스트, 데이터 흐름, Release, 롤백을 포함했다.
- 대규모 리팩터링 대신 기능 수정 시 점진적 추출을 선택했다.
- 이후 작업자가 이 문서만으로 구현 계획을 작성할 수 있도록 파일, 역할, 검증 기준을 구체화했다.
