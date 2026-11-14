#Requires -Version 5.1
<# Read-only S20 FE 1000-word regression prerequisite check.
This script does NOT install APKs, touch the Android UI, alter app data or claim words.
The installed .visual164qa signing certificate differs from the corrected APK, so
the 1000-word regression must not be invoked until the signer conflict is resolved.
#>
[CmdletBinding()]
param(
  [string]$LabRoot = 'C:\EnglishWords-Windows-Lab',
  [string]$AdbPath = 'C:\platform-tools\adb.exe',
  [string]$Package = 'com.example.english1000.visual164qa'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Package -cne 'com.example.english1000.visual164qa') { throw 'Only approved visual164qa package may be inspected.' }
$results = Join-Path $LabRoot 'results\s20fe-1000-preflight'
New-Item -ItemType Directory -Force -Path $results | Out-Null
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss')
$reportPath = Join-Path $results ("preflight-$runId.json")
$report = [ordered]@{
 timestamp_utc = [DateTime]::UtcNow.ToString('o')
 scope = 'read-only ADB + QA package inspection, not 1000-word regression'
 device = 'NOT_RUN'
 device_model = $null
 target_package = $Package
 target_package_installed = 'NOT_RUN'
 corrected_apk_compatibility = 'BLOCKED'
 reason = 'Reported installed and corrected APK signing certificate SHA256 values differ; no overwrite attempted.'
 physical_word_count = 0
 physical_regression = 'NOT_RUN'
 checkpoints_250_500_750_1000 = 'NOT_RUN'
 changes_to_phone = 'NONE'
}
$code = 2
try {
 if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw "adb missing at $AdbPath" }
 $env:ANDROID_USER_HOME = if ($env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME } elseif ($env:USERPROFILE) { Join-Path $env:USERPROFILE '.android' } else { throw 'USERPROFILE missing' }
 New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
 $devices = @(& $AdbPath devices -l)
 if ($LASTEXITCODE -ne 0) { throw 'adb devices -l failed' }
 $authorized = @($devices | Where-Object { $_ -match '^(\S+)\s+device\b' })
 if ($authorized.Count -ne 1) { throw ("Expected 1 authorized device; found " + $authorized.Count) }
 $serial = ($authorized[0] -split '\s+')[0]
 $model = ((& $AdbPath -s $serial shell getprop ro.product.model) | Out-String).Trim()
 if ($LASTEXITCODE -ne 0 -or $model -notmatch 'SM-G780|S20 FE') { throw ("Unexpected device model: " + $model) }
 $report.device = 'PASS'
 $report.device_model = $model
 $packagePath = ((& $AdbPath -s $serial shell pm path $Package) | Out-String).Trim()
 if ($LASTEXITCODE -ne 0) { throw 'Android package check failed' }
 $report.target_package_installed = if ($packagePath -match '(?m)^package:') { 'PASS' } else { 'NOT_INSTALLED' }
 $report.reason = 'Read-only checks passed. The existing QA APK cannot safely be overwritten by a differently signed APK. No approved full-regression UI runner exists yet.'
 $code = 3
} catch {
 $report.reason = $_.Exception.Message
 $report.device = if ($report.device -eq 'PASS') { 'PASS' } else { 'BLOCKED' }
} finally {
 $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding UTF8
 Write-Output "QA_PREFLIGHT_REPORT=$reportPath"
 Write-Output "DEVICE=$($report.device); PACKAGE=$($report.target_package_installed); REGRESSION=$($report.physical_regression); SIGNING=$($report.corrected_apk_compatibility)"
}
exit $code
