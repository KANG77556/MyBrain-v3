[CmdletBinding()]
param(
    [string]$Repository = "KANG77556/MyBrain-v3",
    [string]$KeyAlias = "mybrain-release",
    [string]$KeystorePath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ExpectedSha256 = "ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520"

function Convert-SecureToPlain([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name 명령을 찾을 수 없습니다. JDK 또는 GitHub CLI 설치를 확인하세요."
    }
}

function Set-Secret([string]$Name, [string]$Value) {
    $Value | & gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "$Name 등록에 실패했습니다." }
}

Require-Command "gh"
Require-Command "keytool"

& gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub 로그인을 시작합니다." -ForegroundColor Yellow
    & gh auth login --web
    if ($LASTEXITCODE -ne 0) { throw "GitHub 로그인에 실패했습니다." }
}

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = "MyBrain AI 고정 서명 JKS 선택"
    $dialog.Filter = "Java Keystore (*.jks;*.keystore)|*.jks;*.keystore|모든 파일 (*.*)|*.*"
    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        throw "서명키 선택이 취소되었습니다."
    }
    $KeystorePath = $dialog.FileName
}

$KeystorePath = (Resolve-Path $KeystorePath).Path
$storeSecure = Read-Host "JKS 저장소 비밀번호" -AsSecureString
$keySecure = Read-Host "키 비밀번호" -AsSecureString
$storePlain = Convert-SecureToPlain $storeSecure
$keyPlain = Convert-SecureToPlain $keySecure
$tempCert = Join-Path $env:TEMP ("mybrain-cert-" + [Guid]::NewGuid().ToString("N") + ".cer")

try {
    & keytool -exportcert -alias $KeyAlias -keystore $KeystorePath -storepass $storePlain -file $tempCert -noprompt *> $null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tempCert)) {
        throw "JKS를 열 수 없거나 별칭 '$KeyAlias'을 찾지 못했습니다."
    }

    $certificate = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($tempCert)
    $actual = $certificate.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant()
    if ($actual -ne $ExpectedSha256) {
        throw "고정 인증서가 아닙니다.`n예상: $ExpectedSha256`n실제: $actual"
    }

    $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath))
    Set-Secret "MYBRAIN_KEYSTORE_BASE64" $base64
    Set-Secret "MYBRAIN_KEYSTORE_PASSWORD" $storePlain
    Set-Secret "MYBRAIN_KEY_ALIAS" $KeyAlias
    Set-Secret "MYBRAIN_KEY_PASSWORD" $keyPlain

    Write-Host "고정 서명 GitHub Secrets 등록 완료" -ForegroundColor Green
    Write-Host "인증서 SHA-256: $actual"
    Write-Host "공식 rebuild/v2 빌드를 실행합니다."
    & gh workflow run build-v2.yml --repo $Repository --ref rebuild/v2
    if ($LASTEXITCODE -ne 0) { throw "워크플로 실행 요청에 실패했습니다." }
    Write-Host "GitHub Actions에서 MyBrainAI-v2-release 아티팩트를 확인하세요." -ForegroundColor Cyan
}
finally {
    if (Test-Path $tempCert) { Remove-Item $tempCert -Force }
    $storePlain = $null
    $keyPlain = $null
    $base64 = $null
    [GC]::Collect()
}
