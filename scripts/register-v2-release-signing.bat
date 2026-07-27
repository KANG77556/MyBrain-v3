@echo off
chcp 65001 > nul
setlocal

echo MyBrain AI v2 고정 서명 등록을 시작합니다.
echo JKS와 비밀번호는 저장소 파일에 기록되지 않습니다.
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0register-v2-release-signing.ps1"

if errorlevel 1 (
    echo.
    echo [오류] 고정 서명 등록에 실패했습니다.
    echo 위 원인을 확인한 뒤 다시 실행하세요.
) else (
    echo.
    echo [완료] GitHub Actions Release 빌드를 요청했습니다.
)

pause
endlocal
