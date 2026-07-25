from pathlib import Path

AI_FILE = Path("app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java")
GRADLE_FILE = Path("app/build.gradle")
WORKFLOW_FILE = Path(".github/workflows/build-stage10.yml")

old = '''        input = new EditText(this);
        input.setHint("예: 내일 오전 9시 교무회의, 금요일까지 보고서 제출, 김 선생님께 전화하기");
        input.setTextSize(16);
        input.setGravity(Gravity.TOP);
        input.setMinLines(8);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));

        noticeText = text("", 13, Color.DKGRAY);
        noticeText.setPadding(0, dp(12), 0, dp(12));
        root.addView(noticeText, fullWrap());

        analyzeButton = primaryButton("AI로 여러 항목 분석");
        analyzeButton.setOnClickListener(v -> requestAnalysis());
        root.addView(analyzeButton, buttonParams());

        Button settingsButton = secondaryButton("AI 설정 열기");
'''

new = '''        input = new EditText(this);
        input.setHint("예: 내일 오전 9시 교무회의, 금요일까지 보고서 제출, 김 선생님께 전화하기");
        input.setTextSize(16);
        input.setGravity(Gravity.TOP);
        input.setMinLines(6);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        inputParams.setMargins(0, 0, 0, dp(10));
        root.addView(input, inputParams);

        // 가장 자주 쓰는 자동 추천 분석을 입력창 바로 아래에 배치합니다.
        analyzeButton = primaryButton("AI로 여러 항목 분석");
        analyzeButton.setOnClickListener(v -> requestAnalysis());
        LinearLayout.LayoutParams analyzeParams = buttonParams();
        analyzeParams.setMargins(0, 0, 0, dp(10));
        root.addView(analyzeButton, analyzeParams);

        // 처리 방식 설명은 실행 버튼 아래에 두어 첫 화면의 행동 순서를 단순화합니다.
        noticeText = text("", 13, Color.DKGRAY);
        noticeText.setPadding(dp(2), dp(2), dp(2), dp(8));
        root.addView(noticeText, fullWrap());

        Button settingsButton = secondaryButton("AI 설정 열기");
'''

text = AI_FILE.read_text(encoding="utf-8")
if old not in text:
    raise SystemExit("AiInputActivity 대상 배치 블록을 찾지 못했습니다.")
text = text.replace(old, new, 1)
text = text.replace("AI 분석 입력 화면을 구성합니다.", "AI 분석 입력 화면을 구성합니다. 실행 버튼은 입력창 바로 아래에 배치합니다.", 1)
AI_FILE.write_text(text, encoding="utf-8")

gradle = GRADLE_FILE.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode 28", "versionCode 29", 1)
gradle = gradle.replace("versionName '1.8.5'", "versionName '1.8.6'", 1)
GRADLE_FILE.write_text(gradle, encoding="utf-8")

workflow = WORKFLOW_FILE.read_text(encoding="utf-8")
workflow = workflow.replace("MyBrain Stage 14 APK", "MyBrain Stage 15 APK")
workflow = workflow.replace("1.8.5", "1.8.6")
WORKFLOW_FILE.write_text(workflow, encoding="utf-8")

print("Stage 15 AI 분석 화면 배치 수정 완료")
