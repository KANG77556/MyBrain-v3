# MyBrain AI V2 릴리스 체크리스트

## 1. 변경 범위
- [ ] 릴리스 대상 커밋과 PR이 `v2`에 포함됨
- [ ] 미해결 리뷰/중요 Issue 없음
- [ ] Room 스키마 또는 백업 포맷 변경 여부 확인
- [ ] Android 권한/패키지명 변경 여부 확인

## 2. 정적 검사와 테스트
- [ ] `bash scripts/check-repository-hygiene.sh`
- [ ] `bash scripts/check-development-baseline.sh`
- [ ] `gradle --stacktrace testDebugUnitTest`
- [ ] 테스트 실패 0건

## 3. Debug 검증
- [ ] `gradle --stacktrace assembleDebug`
- [ ] 패키지 `kr.co.mybrain.v2.debug`
- [ ] 앱 이름 `MyBrain AI Debug`
- [ ] APK Signature Scheme v2 검증
- [ ] 주요 화면/저장/일정/위젯/알림 기본 동작 확인

## 4. Release 검증
- [ ] `gradle --stacktrace assembleRelease`
- [ ] Release Secrets 사용 시 고정 인증서 SHA-256 일치
- [ ] v2/v3 서명 검증
- [ ] APK SHA-256 생성
- [ ] Secrets가 없으면 산출물을 '미서명 컴파일 검증용'으로만 취급

## 5. 설치/업데이트
- [ ] 깨끗한 설치 확인
- [ ] 직전 배포 버전에서 업데이트 확인
- [ ] 기존 사용자 데이터 보존 확인
- [ ] 백업 생성 및 복원 확인
- [ ] 앱 내 업데이트 경로 사용 시 진단/복구 확인

## 6. 배포 후
- [ ] 배포 버전과 commit SHA 기록
- [ ] CI run과 산출물 링크 기록
- [ ] 치명적 문제 발생 시 즉시 롤백/수정 PR 생성
