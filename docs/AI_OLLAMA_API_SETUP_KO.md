# MyBrain AI · 휴대전화 Ollama 및 API 키 설정

## 1. 휴대전화에서 Ollama 실행

MyBrain AI는 같은 휴대전화에서 실행 중인 Ollama 서버에 연결합니다.

- 기본 주소: `http://127.0.0.1:11434`
- 외부 PC 주소는 허용하지 않습니다.
- API 키가 필요하지 않습니다.
- 입력 문장은 외부 클라우드로 전송되지 않습니다.

### 설치 순서

1. Ollama Server Android 릴리스 페이지를 엽니다.
2. 최신 APK를 설치합니다.
3. Ollama Server 앱을 실행합니다.
4. Ollama 서비스를 시작합니다.
5. `qwen3:1.7b` 모델을 내려받습니다.
6. MyBrain AI → AI 설정 → Ollama를 선택합니다.
7. `휴대전화 Ollama 연결 테스트`를 누릅니다.

Ollama Server Android 릴리스:

`https://github.com/sunshine0523/OllamaServer/releases`

### 권장 모델

- 기본 권장: `qwen3:1.7b`
- 더 가벼운 대체 모델: `gemma3:1b`

큰 모델은 휴대전화 발열, 배터리 소모, 메모리 부족이 발생할 수 있습니다.

---

## 2. OpenAI API 키 만들기

OpenAI API 키 페이지:

`https://platform.openai.com/api-keys`

1. OpenAI Platform 계정으로 로그인합니다.
2. API keys 페이지에서 새 비밀 키를 만듭니다.
3. 생성 직후 키를 복사합니다.
4. MyBrain AI → AI 설정 → OpenAI API 키 입력란에 붙여넣습니다.
5. GPT 연결 테스트를 누릅니다.
6. 정상 연결 후 설정을 저장합니다.

주의: ChatGPT Plus 구독과 OpenAI API 요금은 별도입니다.

---

## 3. Gemini API 키 만들기

Google AI Studio API 키 페이지:

`https://aistudio.google.com/app/apikey`

1. Google 계정으로 로그인합니다.
2. API 키 만들기를 선택합니다.
3. 생성된 키를 복사합니다.
4. MyBrain AI → AI 설정 → Gemini API 키 입력란에 붙여넣습니다.
5. Gemini 연결 테스트를 누릅니다.
6. 정상 연결 후 설정을 저장합니다.

---

## 4. 개인정보 보호

- Ollama: 같은 휴대전화 내부에서 처리합니다.
- GPT·Gemini: 입력 문장이 각 회사의 클라우드 API로 전송됩니다.
- 학생 이름, 주민등록번호, 건강정보, 상담 내용 등 민감한 정보는 클라우드 AI에 보내지 않는 것을 권장합니다.
- GPT·Gemini API 키는 Android Keystore 기반 암호화 저장소에 보관합니다.
- 앱 삭제 시 저장된 API 키도 삭제됩니다.
