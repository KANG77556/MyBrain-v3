# MyBrain AI 릴리스 APK의 서명 방식과 인증서 지문을 검사합니다.
# Android SDK Build Tools 35.0.0의 apksigner가 설치되어 있어야 합니다.

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$ExpectedCertificateSha256 = "ee9b89627074c2708f7d91ae1a9fcf5ebd8f9611b4df0719e8aa4eef63765520"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    throw "APK 파일을 찾을 수 없습니다: $ApkPath"
}

$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
    $androidHome = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($androidHome)) {
    throw "ANDROID_HOME 또는 ANDROID_SDK_ROOT 환경변수가 필요합니다."
}

$apkSigner = Join-Path $androidHome "build-tools\35.0.0\apksigner.bat"
if (-not (Test-Path -LiteralPath $apkSigner -PathType Leaf)) {
    throw "apksigner를 찾을 수 없습니다: $apkSigner"
}

$report = & $apkSigner verify --verbose --print-certs $ApkPath 2>&1
if ($LASTEXITCODE -ne 0) {
    $report | ForEach-Object { Write-Host $_ }
    throw "APK 전자서명 검증에 실패했습니다."
}

$report | ForEach-Object { Write-Host $_ }
$text = $report -join "`n"

if ($text -notmatch "Verified using v2 scheme \(APK Signature Scheme v2\): true") {
    throw "APK v2 서명이 확인되지 않았습니다."
}
if ($text -notmatch "Verified using v3 scheme \(APK Signature Scheme v3\): true") {
    throw "APK v3 서명이 확인되지 않았습니다."
}

$certificateLine = $report | Where-Object {
    $_ -match "^Signer #1 certificate SHA-256 digest:"
} | Select-Object -First 1

if (-not $certificateLine) {
    throw "서명 인증서 SHA-256 값을 찾지 못했습니다."
}

$actualCertificate = ($certificateLine -replace "^Signer #1 certificate SHA-256 digest:\s*", "")
$actualCertificate = ($actualCertificate -replace "[:\s]", "").ToLowerInvariant()
$expectedCertificate = ($ExpectedCertificateSha256 -replace "[:\s]", "").ToLowerInvariant()

if ($actualCertificate -ne $expectedCertificate) {
    throw "고정 서명 인증서가 아닙니다. 예상=$expectedCertificate, 실제=$actualCertificate"
}

$hash = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "검증 성공" -ForegroundColor Green
Write-Host "APK SHA-256: $hash"
Write-Host "인증서 SHA-256: $actualCertificate"
