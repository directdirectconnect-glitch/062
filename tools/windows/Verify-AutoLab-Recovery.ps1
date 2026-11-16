# Verify AutoLab scheduler and Samsung S20 FE transport.
# Read-only checks. No APK installation, deletion, or test PASS inference.
param([int]$FreshMinutes = 30)
$ErrorActionPreference = 'Stop'
$root = 'C:\EnglishWords-Windows-Lab\results'
New-Item -ItemType Directory -Force -Path $root | Out-Null
$now = Get-Date
$report = [ordered]@{
  timestamp = $now.ToString('o')
  machine = $env:COMPUTERNAME
  scheduler = 'NOT_RUN'
  task = 'NOT_RUN'
  adb = 'NOT_RUN'
  device = 'NOT_RUN'
  application = 'NOT_RUN'
  ui = 'UNCONFIRMED'
  offline = 'UNCONFIRMED'
  voice_en = 'UNCONFIRMED'
  voice_ru = 'UNCONFIRMED'
  overall = 'NOT_RUN'
  evidence = @()
}
try {
  $service = Get-Service Schedule
  $report.scheduler = if ($service.Status -eq 'Running') { 'PASS' } else { 'FAIL' }
  $report.evidence += "Schedule=$($service.Status)"
  $tasks = @(Get-ScheduledTask | Where-Object { $_.TaskName -match '(?i)AutoLab' })
  if ($tasks.Count -ne 1) {
    $report.task = 'BLOCKED'
    $report.evidence += "AutoLabTaskCount=$($tasks.Count)"
  } else {
    $t = $tasks[0]
    $i = Get-ScheduledTaskInfo -TaskName $t.TaskName -TaskPath $t.TaskPath
    $fresh = $i.LastRunTime -gt $now.AddMinutes(-$FreshMinutes) -and $i.LastRunTime -le $now
    $report.task = if ($fresh -and $i.LastTaskResult -eq 0) { 'PASS' } elseif ($fresh) { 'FAIL' } else { 'STALE' }
    $report.evidence += "Task=$($t.TaskPath)$($t.TaskName);State=$($t.State);LastRun=$($i.LastRunTime.ToString('o'));LastResult=$($i.LastTaskResult)"
  }
  $adb = Get-Command adb -ErrorAction SilentlyContinue
  if ($null -eq $adb) {
    $report.adb = 'BLOCKED'
    $report.evidence += 'adb executable not on PATH'
  } else {
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    $devices = @(& $adb.Source devices -l 2>&1)
    $authorized = @($devices | Where-Object { $_ -match '^\S+\s+device\b' })
    $report.adb = if ($LASTEXITCODE -eq 0) { 'PASS' } else { 'FAIL' }
    $report.device = if ($authorized.Count -eq 1) { 'PASS' } else { 'BLOCKED' }
    $report.evidence += "AuthorizedDevices=$($authorized.Count)"
    if ($authorized.Count -eq 1) {
      $serial = ($authorized[0] -split '\s+')[0]
      $model = (& $adb.Source -s $serial shell getprop ro.product.model | Out-String).Trim()
      $report.evidence += "Model=$model"
      if ($model -notmatch '(?i)(SM-G780|S20 FE)') {
        $report.device = 'BLOCKED'
        $report.evidence += 'Connected device is not confirmed S20 FE'
      } else {
        $packages = (& $adb.Source -s $serial shell pm list packages | Out-String)
        $report.application = if ($packages -match 'com\.example\.english1000') { 'PRESENT_ONLY' } else { 'NOT_FOUND' }
      }
    }
  }
} catch {
  $report.overall = 'FAIL'
  $report.evidence += "Exception=$($_.Exception.Message)"
}
if ($report.overall -ne 'FAIL') {
  $report.overall = if ($report.scheduler -eq 'PASS' -and $report.task -eq 'PASS' -and $report.adb -eq 'PASS' -and $report.device -eq 'PASS') { 'INFRA_PASS_ONLY' } else { 'INFRA_NOT_READY' }
}
$path = Join-Path $root ("autolab-verification-" + $now.ToString('yyyyMMdd-HHmmss') + '.json')
$report | ConvertTo-Json -Depth 5 | Set-Content -Path $path -Encoding UTF8
Write-Output "Report: $path"
$report | ConvertTo-Json -Depth 5
if ($report.overall -eq 'INFRA_PASS_ONLY') { exit 0 } else { exit 1 }
