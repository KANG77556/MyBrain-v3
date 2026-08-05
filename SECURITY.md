# Security Policy

## 민감정보 원칙

API 키, OAuth 토큰, 서명키, 인증서, 비밀번호, 개인 데이터는 커밋·Pull Request·Issue·Discussion·Actions 로그에 기록하지 않습니다. Debug APK는 개발 검증용이며 정식 배포물로 취급하지 않습니다.

## 비공개 신고

취약점 또는 민감정보 노출을 발견하면 공개 이슈를 만들지 않습니다. 저장소의 **Security → Advisories → Report a vulnerability**를 사용해 재현 절차, 영향 범위, 관련 커밋 또는 파일 경로를 비공개로 전달합니다.

Private vulnerability reporting 메뉴가 보이지 않으면 저장소 소유자는 GitHub 저장소 설정에서 해당 기능을 활성화한 뒤 신고 경로를 안내해야 합니다. 비공개 경로가 준비되기 전에는 취약점 세부 정보나 비밀값을 공개 채널에 게시하지 않습니다.

## 노출 대응

1. 노출된 키와 토큰을 즉시 폐기하거나 회전합니다.
2. 현재 브랜치와 배포 산출물에서 민감정보를 제거합니다.
3. 영향을 받은 앱 패키지, 서명 인증서, CI 실행, 배포 파일을 식별합니다.
4. 재발 방지 검사를 `scripts/check-repository-hygiene.sh`와 대표 CI에 추가합니다.
5. Git 기록 재작성은 기존 클론과 태그에 미치는 영향을 검토한 별도 보안 작업으로 수행합니다.

## 현재 Debug 키 사건

과거 저장소에 포함된 공개 Debug PKCS#12 자료와 비밀번호는 폐기된 것으로 간주합니다. 해당 키로 서명된 `kr.co.mybrain.v2` 개발 APK는 테스트 기기에서 제거하고, 패키지가 분리된 `kr.co.mybrain.v2.debug` APK를 사용합니다.
