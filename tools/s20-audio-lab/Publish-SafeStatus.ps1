#requires -version 5.1
<#
English Words S20 status publisher v3.1.
Reads local status JSON, sends only fixed allow-listed verdicts to GitHub.
It cannot run remote commands and never uploads raw diagnostics or phone data.
ASCII-only source to remain compatible with Windows PowerShell 5.1.
#>
[CmdletBinding()]
param(
  [switch]$DryRun
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-Unattended'
$inputPath = Join-Path $root 'unattended-last.json'
$dedupPath = Join-Path $root 'last-published-summary-v3.txt'
$repo = 'directdirectconnect-glitch/062'
$issue = '3'
$taskName = 'EnglishWords-S20-Unattended'

function Read-Property([object]$Object, [string]$Name) {
  if ($null -eq $Object) { return '' }
  $prop = $Object.PSObject.Properties[$Name]
  if ($null -eq $prop -or $null -eq $prop.Value) { return '' }
  return [string]$prop.Value
}
function From-Allowed([string]$Raw, [string[]]$Allowed, [string]$Fallback) {
  $v = $Raw.ToUpperInvariant().Trim()
  if ($Allowed -contains $v) { return $v }
  return $Fallback
}
function Map-Result([object]$Record) {
  $lab = (Read-Property $Record 'lab_status').ToUpperInvariant()
  if ($lab) {
    switch ($lab) {
      'PASS'                   { return 'LAB_PASS_UNVERIFIED_COVERAGE' }
      'PASSED'                 { return 'SMOKE_PASS_ONLY' }
      'FAIL'                   { return 'FAIL' }
      'FAILED'                 { return 'FAIL' }
      'BLOCKED'                { return 'BLOCKED' }
      'SKIPPED'                { return 'SKIPPED_NO_NEW_BUILD' }
      'SKIPPED_CONCURRENT_RUN' { return 'SKIPPED_CONCURRENT_RUN' }
      'NO_NEW_REPORT'          { return 'NO_NEW_REPORT' }
      'READY_TO_RUN'           { return 'READY_TO_RUN' }
      'RUNNING_LEGACY'         { return 'IN_PROGRESS' }
      'NOT_RUN'                { return 'NOT_RUN' }
      default                  { return 'UNCONFIRMED' }
    }
  }
  # The locally repaired Codex runner uses status=PASSED for a start-only smoke test.
  $status = (Read-Property $Record 'status').ToUpperInvariant()
  switch ($status) {
    'PASSED' { return 'SMOKE_PASS_ONLY' }
    'PASS'   { return 'SMOKE_PASS_ONLY' }
    'FAILED' { return 'FAIL' }
    'FAIL'   { return 'FAIL' }
    'ERROR'  { return 'FAIL' }
    'SKIPPED' { return 'SKIPPED' }
    'RUNNING'{ return 'IN_PROGRESS' }
    default  { return 'UNCONFIRMED' }
  }
}

$result = 'MISSING_STATE'
$heartbeat = 'UNCONFIRMED'
$acousticEn = 'UNCONFIRMED'
$acousticRu = 'UNCONFIRMED'
$device = 'UNCONFIRMED'
if (Test-Path -LiteralPath $inputPath -PathType Leaf) {
  $age = ((Get-Date).ToUniversalTime() - (Get-Item -LiteralPath $inputPath).LastWriteTimeUtc).TotalMinutes
  if ($age -gt 45 -or $age -lt -5) {
    $result = 'STALE_STATE'
  } else {
    try {
      $record = Get-Content -LiteralPath $inputPath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
      if ($null -eq $record) { throw 'Empty JSON' }
      $result = Map-Result $record
      $heartbeat = From-Allowed (Read-Property $record 'heartbeat') @(
        'NOT_RUN','CHECKING_PHONE','PASS_APP_LAUNCH_SCREEN_CAPTURE',
        'NO_ISOLATED_TEST_PACKAGE','ALREADY_CHECKED_TODAY'
      ) 'UNCONFIRMED'
      $acousticEn = From-Allowed (Read-Property $record 'acoustic_en') @(
        'NOT_CONFIGURED','NOT_CHECKED','PASS','FAIL','ALREADY_CHECKED_TODAY'
      ) 'UNCONFIRMED'
      $acousticRu = From-Allowed (Read-Property $record 'acoustic_ru') @(
        'NOT_CONFIGURED','NOT_CHECKED','PASS','FAIL','ALREADY_CHECKED_TODAY'
      ) 'UNCONFIRMED'
      $deviceVerified = Read-Property $record 'device_model_verified'
      if ($deviceVerified -eq 'True') { $device = 'YES' }
      elseif ($deviceVerified -eq 'False') { $device = 'NO' }
    } catch {
      $result = 'INVALID_STATE'
    }
  }
}
$task = 'TASK_UNAVAILABLE'
try {
  $info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction Stop
  $minsAgo = ((Get-Date) - $info.LastRunTime).TotalMinutes
  if ($minsAgo -ge 0 -and $minsAgo -lt 45) {
    if ($info.LastTaskResult -eq 0) { $task = 'RECENT_EXIT_ZERO' }
    else { $task = 'RECENT_NONZERO_EXIT' }
  } else {
    $task = 'NOT_RUN_RECENTLY'
  }
} catch {
  $task = 'TASK_UNAVAILABLE'
}
$summary = "AutoLab v3.1: result=$result; task=$task; device_verified=$device; ui=$heartbeat; voice_en=$acousticEn; voice_ru=$acousticRu; scheduled_trigger=UNVERIFIED; test_coverage=UNVERIFIED"
# No local path, phone identifier, exception, transcript, screenshot, or audio
# is included in summary. No private fields are copied to GitHub.
if ($DryRun) {
  Write-Output $summary
  exit 0
}
if (-not (Test-Path -LiteralPath $root -PathType Container)) {
  Write-Output 'AGENT_ROOT_NOT_FOUND'
  exit 2
}
$today = Get-Date -Format 'yyyy-MM-dd'
$dedupKey = "$today|$summary"
if (Test-Path -LiteralPath $dedupPath -PathType Leaf) {
  if ((Get-Content -LiteralPath $dedupPath -Raw -Encoding UTF8).Trim() -eq $dedupKey) {
    Write-Output 'UNCHANGED_TODAY'
    exit 0
  }
}
$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($null -eq $gh) {
  Write-Output 'GITHUB_CLI_MISSING'
  exit 2
}
try {
  & $gh.Source issue comment $issue -R $repo --body $summary 1>$null 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'Publish failed' }
  $dedupKey | Set-Content -LiteralPath $dedupPath -Encoding ASCII
  Write-Output 'SAFE_STATUS_PUBLISHED'
  exit 0
} catch {
  Write-Output 'STATUS_PUBLISH_FAILED'
  exit 1
}
