from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"교체할 원문을 찾지 못했습니다: {path}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java",
    '''/**
 * Ollama·GPT·Gemini로 문장을 분석하고 여러 결과를 확인한 뒤 한꺼번에 저장하는 화면입니다.
 * Ollama는 같은 휴대전화의 로컬 서버를 사용하며 기본 규칙 분석은 기존 MainActivity를 재사용합니다.
 */''',
    '''/**
 * 기기 내부 규칙과 GPT·Gemini를 조합해 문장을 분석하고 여러 결과를 한꺼번에 저장하는 화면입니다.
 * 자동 추천은 단순 문장을 기기에서 처리하고 여러 행동이 섞인 문장만 클라우드 AI로 보냅니다.
 */'''
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java",
    '''    private void refreshProviderInfo() {
        AiSettings settings = AiSettings.load(this);
        providerText.setText("현재 방식: " + settings.providerLabel() + " · " + settings.selectedModel());
        analyzeButton.setText(settings.isModelProvider()
                ? settings.providerLabel() + "로 여러 항목 분석" : "기본 규칙으로 분석");

        if (settings.isOllamaProvider()) {
            noticeText.setText("Ollama는 이 휴대전화에서 실행됩니다. 한 문장에서 최대 8개의 일정·할 일·메모를 분리합니다.");
        } else if (settings.isCloudProvider()) {
            noticeText.setText("GPT 또는 Gemini를 선택하면 입력 문장이 클라우드 API로 전송됩니다. 분석 결과는 저장 전에 선택하고 수정할 수 있습니다.");
        } else {
            noticeText.setText("기본 규칙 분석은 한 번에 한 항목을 처리합니다. 여러 항목 분리는 Ollama·GPT·Gemini에서 사용할 수 있습니다.");
        }
    }''',
    '''    private void refreshProviderInfo() {
        AiSettings settings = AiSettings.load(this);
        providerText.setText("현재 방식: " + settings.providerLabel() + " · " + settings.selectedModel());

        if (settings.isAutoProvider()) {
            analyzeButton.setText("자동 추천으로 분석");
            noticeText.setText("날짜·시간 중심 문장은 휴대전화에서 처리하고, 여러 행동이 섞인 문장만 "
                    + settings.preferredCloudLabel() + "를 우선 사용합니다. API 키가 없으면 기본 규칙으로 처리합니다.");
        } else if (settings.isCloudProvider()) {
            analyzeButton.setText(settings.providerLabel() + "로 여러 항목 분석");
            noticeText.setText("입력 문장이 클라우드 API로 전송됩니다. 분석 결과는 저장 전에 선택하고 수정할 수 있습니다.");
        } else {
            analyzeButton.setText("기본 규칙으로 분석");
            noticeText.setText("인터넷이나 AI 서버 없이 휴대전화 내부 규칙으로 날짜·시간과 한 개 항목을 정리합니다.");
        }
    }'''
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java",
    '''        // 날짜·요일·시간 범위는 Ollama나 클라우드 호출 전에 기기 안에서 즉시 분석합니다.
        List<AiAnalysisResult> rangeResults = KoreanScheduleRangeParser.parse(value, new java.util.Date());
        if (!rangeResults.isEmpty()) {
            showResultEditor(rangeResults);
            return;
        }

        AiSettings settings = AiSettings.load(this);
        if (!settings.isModelProvider()) {
            openLocalAnalyzer(value);
            return;
        }

        if (settings.isCloudProvider()) {
            String keyName = AiSettings.PROVIDER_OPENAI.equals(settings.provider)
                    ? SecureApiKeyStore.KEY_OPENAI : SecureApiKeyStore.KEY_GEMINI;
            if (!SecureApiKeyStore.has(this, keyName)) {
                new AlertDialog.Builder(this)
                        .setTitle(settings.providerLabel() + " API 키 필요")
                        .setMessage("AI 설정에서 " + settings.providerLabel() + " API 키를 먼저 등록하세요.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("설정 열기", (dialog, which) -> startActivityForResult(
                                new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS))
                        .show();
                return;
            }

            if (settings.confirmBeforeCloud) {
                new AlertDialog.Builder(this)
                        .setTitle(settings.providerLabel() + "로 전송")
                        .setMessage("입력 문장을 " + settings.providerLabel()
                                + " 클라우드 API로 보내 여러 일정·할 일·메모로 분리할까요? 개인정보가 포함됐는지 확인하세요.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton("전송 및 분석", (dialog, which) -> performAiAnalysis(settings, value))
                        .show();
                return;
            }
        }

        performAiAnalysis(settings, value);''',
    '''        // 날짜·요일·시간 범위는 AI 호출 전에 기기 안에서 즉시 분석합니다.
        List<AiAnalysisResult> rangeResults = KoreanScheduleRangeParser.parse(value, new java.util.Date());
        if (!rangeResults.isEmpty()) {
            showResultEditor(rangeResults);
            return;
        }

        AiSettings settings = AiSettings.load(this);
        HybridAnalysisRouter.Decision decision = HybridAnalysisRouter.resolve(this, settings, value);
        if (!decision.usesCloud()) {
            if (settings.isAutoProvider()) {
                Toast.makeText(this, decision.reason, Toast.LENGTH_SHORT).show();
            }
            openLocalAnalyzer(value);
            return;
        }

        // 자동 추천이 선택한 공급자는 현재 분석 한 번에만 사용하고 저장된 설정은 바꾸지 않습니다.
        settings.provider = decision.provider;
        String keyName = HybridAnalysisRouter.keyName(settings.provider);
        if (!SecureApiKeyStore.has(this, keyName)) {
            new AlertDialog.Builder(this)
                    .setTitle(settings.providerLabel() + " API 키 필요")
                    .setMessage("AI 설정에서 " + settings.providerLabel() + " API 키를 먼저 등록하세요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("설정 열기", (dialog, which) -> startActivityForResult(
                            new Intent(this, AiSettingsActivity.class), REQUEST_SETTINGS))
                    .show();
            return;
        }

        if (settings.confirmBeforeCloud) {
            new AlertDialog.Builder(this)
                    .setTitle(settings.providerLabel() + "로 전송")
                    .setMessage(decision.reason + "\n\n입력 문장을 " + settings.providerLabel()
                            + " 클라우드 API로 보내 여러 일정·할 일·메모로 분리할까요? 개인정보가 포함됐는지 확인하세요.")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("전송 및 분석", (dialog, which) -> performAiAnalysis(settings, value))
                    .show();
            return;
        }

        performAiAnalysis(settings, value);'''
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java",
    "    /** 네트워크 또는 로컬 Ollama 호출은 별도 작업 스레드에서 수행합니다. */",
    "    /** GPT 또는 Gemini 네트워크 호출은 별도 작업 스레드에서 수행합니다. */"
)

replace_once(
    "app/src/main/java/kr/co/mybrain/ai/IntegratedMainActivity.java",
    'versionText.setText("v1.7.2 · 날짜·시간 범위 자동 분리");',
    'versionText.setText("v1.8.0 · 규칙 우선 하이브리드 AI");'
)

replace_once(
    "app/build.gradle",
    """        versionCode 22
        versionName '1.7.2'""",
    """        versionCode 23
        versionName '1.8.0'"""
)

print("v1.8.0 하이브리드 분석 흐름 적용 완료")
