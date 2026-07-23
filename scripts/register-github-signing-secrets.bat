@echo off
chcp 65001 > nul
setlocal

rem MyBrain AI 고정 서명 GitHub 등록 스크립트를 실행합니다.
rem 비밀번호와 서명키는 이 배치 파일에 저장되지 않습니다.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0register-github-signing-secrets.ps1"

if errorlevel 1 (
    echo.
    echo [오류] 고정 서명 등록에 실패했습니다.
    echo 화면에 표시된 원인을 확인한 뒤 다시 실행하세요.
) else (
    echo.
    echo [완료] GitHub Actions 고정 서명 등록이 끝났습니다.
)

pause
endlocal
