# MyBrain AI 고정 서명 비밀값을 GitHub Actions에 안전하게 등록합니다.
# 이 스크립트는 비밀번호와 서명키 내용을 저장소 파일에 기록하지 않습니다.

[CmdletBinding()]
param(
    [string]$Repository = "KANG77556/MyBrain-v3",
    [string]$KeyAlias = "mybrain-release"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Convert-SecureStringToPlainText {
    param([Parameter(Mandatory = $true)][Security.SecureString]$SecureValue)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Set-RepositorySecret {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    # 명령줄 인수에 비밀값을 넣지 않고 표준 입력으로 전달합니다.
    $Value | & gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "$Name 등록에 실패했습니다."
    }
}

Write-Host "MyBrain AI 고정 서명 자동 등록을 시작합니다." -ForegroundColor Cyan
Write-Host "대상 저장소: $Repository"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI(gh)가 설치되어 있지 않습니다. https://cli.github.com 에서 설치한 뒤 다시 실행하세요."
}

& gh auth status
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub 로그인이 필요합니다. 브라우저 로그인 화면을 엽니다." -ForegroundColor Yellow
    & gh auth login --web
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub 로그인에 실패했습니다."
    }
}

Add-Type -AssemblyName System.Windows.Forms
$dialog = New-Object System.Windows.Forms.OpenFileDialog
$dialog.Title = "MyBrain AI 고정 서명키 선택"
$dialog.Filter = "Android 서명키 (*.jks;*.keystore)|*.jks;*.keystore|모든 파일 (*.*)|*.*"
$dialog.Multiselect = $false

if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    throw "서명키 선택이 취소되었습니다."
}

$keyStorePath = $dialog.FileName
if (-not (Test-Path -LiteralPath $keyStorePath -PathType Leaf)) {
    throw "서명키 파일을 찾을 수 없습니다."
}

$storePasswordSecure = Read-Host "서명키 저장소 비밀번호를 입력하세요" -AsSecureString
$keyPasswordSecure = Read-Host "키 비밀번호를 입력하세요" -AsSecureString
$storePassword = Convert-SecureStringToPlainText $storePasswordSecure
$keyPassword = Convert-SecureStringToPlainText $keyPasswordSecure

try {
    if ([string]::IsNullOrWhiteSpace($storePassword) -or [string]::IsNullOrWhiteSpace($keyPassword)) {
        throw "비밀번호는 비워둘 수 없습니다."
    }

    if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
        throw "키 별칭은 비워둘 수 없습니다."
    }

    # 원본 바이너리를 한 줄 Base64 문자열로 변환합니다.
    $keyStoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyStorePath))

    Write-Host "GitHub Actions 비밀값을 등록하고 있습니다." -ForegroundColor Cyan
    Set-RepositorySecret -Name "MYBRAIN_KEYSTORE_BASE64" -Value $keyStoreBase64
    Set-RepositorySecret -Name "MYBRAIN_KEYSTORE_PASSWORD" -Value $storePassword
    Set-RepositorySecret -Name "MYBRAIN_KEY_ALIAS" -Value $KeyAlias
    Set-RepositorySecret -Name "MYBRAIN_KEY_PASSWORD" -Value $keyPassword

    Write-Host "등록된 비밀값 이름을 확인합니다." -ForegroundColor Cyan
    & gh secret list --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "비밀값 목록 확인에 실패했습니다."
    }

    Write-Host "완료: 다음 GitHub Actions 빌드부터 고정 서명 릴리스 APK가 생성됩니다." -ForegroundColor Green
}
finally {
    # 메모리에 남아 있는 평문 비밀번호 참조를 정리합니다.
    $storePassword = $null
    $keyPassword = $null
    $keyStoreBase64 = $null
    [GC]::Collect()
}
