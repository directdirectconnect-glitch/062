#requires -version 5.1
<#
Publish a minimal, sanitized Windows AutoLab status to GitHub Issue #3.
This companion does not execute commands obtained from GitHub, does not
control the phone, and never uploads raw local diagnostics.
Run by the *locally installed* fixed-action scheduler after the lab run.
#>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-Unattended'
$inputPath = Join-Path $root 'unattended-last.json'
$lastPath = Join-Path $root 'last-published-summary.txt'
$repo = 'directdirectconnect-glitch/062'

function Select-Allowed([string]$value, [string[]]$allowed) {
    if ($allowed -contains $value) { return $value }
    return 'UNCONFIRMED'
}
if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) {
    Write-Output 'STATUS_NOT_AVAILABLE'
    exit 2
}
$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($null -eq $gh) {
    Write-Output 'GITHUB_CLI_NOT_AVAILABLE'
    exit 2
}
try {
    $record = Get-Content -LiteralPath $inputPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $phase = Select-Allowed ([string]$record.lab_status) @(
        'READY_TO_RUN', 'NOT_RUN', 'RUNNING_LEGACY', 'PASS', 'FAIL',
        'SKIPPED', 'BLOCKED', 'NO_NEW_REPORT', 'SKIPPED_CONCURRENT_RUN'
    )
    $heartbeat = Select-Allowed ([string]$record.heartbeat) @(
        'NOT_RUN', 'CHECKING_PHONE', 'PASS_APP_LAUNCH_SCREEN_CAPTURE',
        'NO_ISOLATED_TEST_PACKAGE', 'ALREADY_CHECKED_TODAY'
    )
    $en = Select-Allowed ([string]$record.acoustic_en) @(
        'NOT_CONFIGURED', 'NOT_CHECKED', 'PASS', 'FAIL', 'ALREADY_CHECKED_TODAY'
    )
    $ru = Select-Allowed ([string]$record.acoustic_ru) @(
        'NOT_CONFIGURED', 'NOT_CHECKED', 'PASS', 'FAIL', 'ALREADY_CHECKED_TODAY'
    )
    $verified = if ($record.device_model_verified -eq $true) { 'YES' } else { 'NO' }
    # Copy only strict enumerations, never path, device serial, transcript, audio,
    # exception text, log, screenshot, or an arbitrary field from local JSON.
    $summary = "AutoLab v3: device_verified=$verified; lab=$phase; heartbeat=$heartbeat; acoustic_en=$en; acoustic_ru=$ru"
    if ((Test-Path -LiteralPath $lastPath) -and
        ((Get-Content -LiteralPath $lastPath -Raw -Encoding UTF8).Trim() -eq $summary)) {
        Write-Output 'UNCHANGED_STATUS'
        exit 0
    }
    & $gh.Source issue comment 3 -R $repo --body $summary 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'GitHub report publishing failed' }
    $summary | Set-Content -LiteralPath $lastPath -Encoding UTF8
    Write-Output 'SAFE_STATUS_PUBLISHED'
    exit 0
} catch {
    # Diagnostic detail remains only on local PC; never print sensitive content.
    Write-Output 'STATUS_PUBLISH_FAILED'
    exit 1
}
