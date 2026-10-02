# MyBrain AI V2 아키텍처

## 개요
MyBrain AI V2는 Java 17 기반 단일 Android 애플리케이션 모듈입니다. `app/build.gradle` 기준 namespace와 Release applicationId는 `kr.co.mybrain.v2`이며 Debug는 `.debug` 접미사를 사용합니다.

## 주요 계층
- 화면/진입점: `MainActivity`, `AdaptiveMainActivity`, `OptimizedMainActivity`, `CalendarActivity`, `WorkItemListActivity`, `ShareActivity`
- 데이터: `data/`의 Room `MyBrainDatabase`, `WorkItemDao`, `WorkItemEntity`, `WorkItemRepository`
- AI 보조: `assistant/`의 자연어 파싱, 개인정보 필터, 클라우드 분석, 결과 검증, 재시도/캐시
- 알림: `reminder/`의 예약, 재예약, 수신기, 알림, 반복 계산
- 설정: `settings/`의 AI 설정/예산/사용량, 암호화 값 저장, 접근성 및 알림 설정
- 백업/업데이트: `transfer/`의 백업 암호화·복원, 업데이트 설치, 릴리스 진단
- UI 정책: `ui/`의 대시보드, 일정 충돌, 저장 무결성, 하단 안전영역, 빠른 입력 정책/컨트롤러
- 음성: `voice/`의 연속 음성 인식
- 위젯: `widget/`의 오늘/할 일/일정/빠른 메모 AppWidget

## 데이터 흐름
사용자 입력 → UI/Activity → 정책 또는 Controller → Repository/AI/Reminder 계층 → Room 또는 Android 시스템 → UI/Widget 갱신 순서로 유지합니다.

## 의존성 경계
- Activity가 SQL을 직접 실행하지 않습니다.
- 반복 일정 계산과 저장 무결성처럼 검증 가능한 규칙은 UI에서 분리된 Policy/Calculator에 둡니다.
- 클라우드 AI 전송 전 `CloudPrivacyFilter` 경계를 유지합니다.
- 민감값은 저장소에 하드코딩하지 않고 `EncryptedValueStore` 및 외부 환경/Secrets 정책을 사용합니다.

## 테스트 기준
순수 정책·계산·파싱 로직은 `app/src/test`에서 JUnit으로 검증합니다. UI/플랫폼 종속 로직을 변경할 때는 가능한 핵심 판단을 순수 Policy로 분리해 테스트 가능하게 유지합니다.

## 변경 주의 구역
Room 스키마, Android 권한, 패키지명, 서명, 백업 포맷, 알림 재예약, 업데이트 설치는 호환성 영향이 크므로 별도 검증과 릴리스 체크리스트가 필요합니다.
