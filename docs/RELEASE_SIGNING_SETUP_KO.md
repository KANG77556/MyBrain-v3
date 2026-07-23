# MyBrain AI 고정 서명 자동 빌드 설정

## 파일 역할

이 문서는 GitHub Actions에 MyBrain AI 고정 서명 정보를 한 번 등록하고, 이후 버전부터 업데이트 가능한 릴리스 APK를 자동 생성하는 방법을 설명합니다.

## 주요 기능

등록되는 GitHub Actions 비밀값은 다음 네 개입니다.

```text
MYBRAIN_KEYSTORE_BASE64
MYBRAIN_KEYSTORE_PASSWORD
MYBRAIN_KEY_ALIAS
MYBRAIN_KEY_PASSWORD
```

비밀값 등록이 끝나면 GitHub Actions가 다음 작업을 자동으로 수행합니다.

```text
앱 빌드
→ 고정 서명 적용
→ APK v2·v3 서명 검증
→ MyBrain 인증서 지문 확인
→ APK와 SHA-256 검증 파일 업로드
```

## 가장 쉬운 등록 방법

### 준비물

1. 개인 PC
2. MyBrain AI 고정 서명키 `mybrain-release.jks`
3. 서명키 비밀번호
4. GitHub CLI

### 실행 순서

1. GitHub CLI를 설치합니다.
2. 이 저장소를 ZIP으로 내려받거나 Git으로 복제합니다.
3. `scripts/register-github-signing-secrets.bat`를 실행합니다.
4. 브라우저 GitHub 로그인이 나오면 로그인합니다.
5. 파일 선택 화면에서 `mybrain-release.jks`를 선택합니다.
6. 저장소 비밀번호와 키 비밀번호를 입력합니다.
7. 완료 메시지가 표시될 때까지 기다립니다.

스크립트는 서명키를 Base64 문자열로 변환한 뒤 GitHub Actions 비밀값으로 전송합니다. 서명키와 비밀번호는 저장소 파일에 기록하지 않습니다.

## GitHub 웹사이트에서 직접 등록하는 방법

저장소에서 다음 경로로 이동합니다.

```text
Settings
→ Secrets and variables
→ Actions
→ Secrets
→ New repository secret
```

네 개의 비밀값을 각각 등록합니다. 서명키 원본 파일은 업로드하지 않고, Base64로 변환한 한 줄 문자열만 `MYBRAIN_KEYSTORE_BASE64`에 입력합니다.

## 빌드 결과 확인

GitHub 저장소에서 다음 경로로 이동합니다.

```text
Actions
→ Build MyBrain AI APK
→ Run workflow
```

성공하면 `MyBrainAI-release-apk` 아티팩트에 다음 파일이 생성됩니다.

```text
MyBrainAI-v버전-release.apk
MyBrainAI-v버전-release.apk.sha256
apk-signature-report.txt
```

## 수정 가능 영역

- 릴리스 APK 표시 이름
- GitHub Actions 아티팩트 이름
- 빌드 도구 버전
- 인증서 검증 보고서 파일명

## 주의 사항

- `applicationId 'kr.co.mybrain.ai'`를 변경하면 기존 앱과 다른 앱으로 인식됩니다.
- 다음 버전은 `versionCode`를 반드시 더 큰 숫자로 올려야 합니다.
- `mybrain-release.jks`와 비밀번호를 공개 저장소에 올리지 마세요.
- 고정 서명키를 분실하거나 다른 키로 바꾸면 기존 설치 앱을 업데이트할 수 없습니다.
- `signing/.gitignore`를 삭제하지 마세요.
- APK를 서명한 뒤 파일 내용을 수정하면 서명이 무효가 됩니다.

## 변경 내역

### 자동 서명 개선

- 비밀값 네 개가 모두 있을 때만 릴리스 서명 실행
- 앱 버전을 `build.gradle`에서 자동 확인
- 릴리스 APK 파일명에 버전 자동 반영
- `apksigner`로 APK v2·v3 서명 확인
- MyBrain AI 고정 인증서 SHA-256 비교
- APK SHA-256 검증 파일 자동 생성

## 예상 오류

### GitHub CLI를 찾을 수 없음

GitHub CLI를 설치한 뒤 PC를 재시작하고 배치 파일을 다시 실행합니다.

### GitHub 로그인이 안 됨

명령 프롬프트에서 다음 명령을 실행합니다.

```powershell
gh auth login --web
```

### 서명키 비밀번호 오류

백업해 둔 고정 서명 설정 문서에서 비밀번호를 확인합니다. 비밀번호를 임의로 새로 만들면 기존 키를 열 수 없습니다.

### 인증서 불일치

다른 서명키를 선택한 경우입니다. MyBrain AI 공식 `mybrain-release.jks`를 다시 선택해야 합니다.

### 고정 서명 단계가 건너뛰어짐

GitHub Actions 비밀값 네 개 중 하나 이상이 등록되지 않은 상태입니다. `gh secret list --repo KANG77556/MyBrain-v3`로 이름을 확인합니다.
