from pathlib import Path

path = Path("app/src/main/java/kr/co/mybrain/ai/AiInputActivity.java")
text = path.read_text(encoding="utf-8")

# Python 적용 스크립트에서 \n이 실제 줄바꿈으로 변환된 경우 Java 문자열 이스케이프로 복구합니다.
bad = '''                    .setMessage(decision.reason + "

입력 문장을 " + settings.providerLabel()'''
good = '''                    .setMessage(decision.reason + "\\n\\n입력 문장을 " + settings.providerLabel()'''

if bad in text:
    text = text.replace(bad, good, 1)
    path.write_text(text, encoding="utf-8")
    print("Java 안내 문자열 줄바꿈 복구 완료")
elif good in text:
    print("Java 안내 문자열은 이미 정상입니다")
else:
    raise RuntimeError("복구할 안내 문자열을 찾지 못했습니다")
