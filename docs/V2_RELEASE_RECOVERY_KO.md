# MyBrain AI v2 정식 배포·복구 절차

## 핵심 원칙

Android 앱을 데이터 손실 없이 업데이트하려면 다음 세 조건이 모두 같아야 합니다.

1. 패키지명 `kr.co.mybrain.v2`
2. 이전 설치본보다 큰 `versionCode`
3. 최초 Release APK와 동일한 고정 서명 인증서

고정 JKS를 분실하면 기존 설치본을 같은 앱으로 업데이트할 수 없습니다. JKS 원본과 비밀번호는 GitHub 저장소가 아닌 별도의 안전한 저장소 두 곳 이상에 백업합니다.

## GitHub 고정 서명 등록

Windows PC에서 다음 파일을 실행합니다.

```text
scripts/register-v2-release-signing.bat
```

필요한 프로그램:

- JDK 17 이상 (`keytool` 포함)
- GitHub CLI (`gh`)
- MyBrain AI 고정 서명 JKS

스크립트가 수행하는 작업:

1. GitHub 로그인 상태 확인
2. JKS 파일 선택
3. 저장소·키 비밀번호를 화면에서 입력
4. 인증서 SHA-256이 MyBrain 고정 인증서와 같은지 검사
5. 다음 GitHub Actions Secrets 등록
6. 공식 `rebuild/v2` 빌드 실행

```text
MYBRAIN_KEYSTORE_BASE64
MYBRAIN_KEYSTORE_PASSWORD
MYBRAIN_KEY_ALIAS
MYBRAIN_KEY_PASSWORD
```

비밀번호와 JKS 내용은 소스 파일에 저장되지 않습니다.

## 최초 Release 설치 순서

현재 기기에 디버그 APK가 설치돼 있다면 고정 서명 Release APK와 서명이 다르므로 바로 덮어쓸 수 없습니다.

1. 앱에서 `백업·복원·업데이트`를 엽니다.
2. 암호화 `.mybrain` 백업을 생성합니다.
3. `배포·복구 진단`에서 해당 백업의 복원 리허설을 실행합니다.
4. 백업 파일과 비밀번호를 별도 보관합니다.
5. 기존 디버그 앱을 삭제합니다.
6. GitHub Actions의 `MyBrainAI-v2-release` APK를 설치합니다.
7. 앱에서 암호화 백업을 전체 교체 방식으로 복원합니다.
8. 일정·할 일·메모·알림을 확인합니다.
9. GPT·Gemini API 키는 보안상 다시 등록합니다.

## 다음 Release 업데이트

최초 고정 서명 Release가 설치된 뒤부터는 앱의 `APK 업데이트 설치`에서 새 Release APK를 선택합니다.

앱이 다음 항목을 검사한 후 Android 설치 화면을 엽니다.

- 패키지명 일치
- 버전 코드 증가
- 현재 앱과 서명 인증서 일치

## 복원 리허설 해석

- `새로 추가`: 현재 기기에 없는 백업 항목
- `갱신 예정`: 외부 ID는 같지만 내용이 다른 항목
- `변경 없음`: 현재 데이터와 같은 항목
- `현재 기기에만 존재`: 병합 시 유지되고 전체 교체 시 삭제되는 항목
- `알림 재등록 대상`: 복원 후 다시 예약할 알림
- `중복 외부 ID`: 0개여야 안전

복원 리허설은 DB와 설정을 수정하지 않습니다.

## 확인해야 할 파일

GitHub Actions 성공 후 `MyBrainAI-v2-release` 아티팩트에는 다음 파일이 생성됩니다.

```text
MyBrainAI-v2.0.0-버전-release.apk
MyBrainAI-v2.0.0-버전-release.apk.sha256
apk-signature-report.txt
```

Release APK의 인증서 SHA-256은 다음 값이어야 합니다.

```text
ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520
```
