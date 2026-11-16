# English Words — диагностика Windows AutoLab
# Запускать на локальном Windows-компьютере в PowerShell:
# powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\windows\Repair-AutoLab-Scheduler.ps1
# Не удаляет приложения, данные или задания. Запускает только однозначно найденное задание AutoLab.
$ErrorActionPreference = 'Stop'
$root = 'C:\EnglishWords-Windows-Lab'
$logDir = Join-Path $root 'results'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $logDir "autolab-scheduler-repair-$stamp.log"
function Record($msg) { $line = "$(Get-Date -Format o) $msg"; $line | Tee-Object -FilePath $log -Append }
Record 'BEGIN: Windows AutoLab scheduler diagnosis'
try {
  $service = Get-Service -Name Schedule
  Record "Task Scheduler service: $($service.Status)"
  if ($service.Status -ne 'Running') { Record 'FAIL: Schedule service stopped; manual administrator intervention required'; exit 2 }
  $tasks = @(Get-ScheduledTask | Where-Object { ($_.TaskName + ' ' + $_.TaskPath) -match '(?i)(EnglishWords|AutoLab)' })
  foreach ($task in $tasks) {
    $info = Get-ScheduledTaskInfo -TaskName $task.TaskName -TaskPath $task.TaskPath
    Record "TASK $($task.TaskPath)$($task.TaskName) state=$($task.State) last=$($info.LastRunTime) result=$($info.LastTaskResult) next=$($info.NextRunTime)"
    foreach ($action in $task.Actions) { Record "ACTION executable=$($action.Execute) args=$($action.Arguments)" }
  }
  $target = @($tasks | Where-Object { $_.TaskName -match '(?i)AutoLab' })
  if ($target.Count -ne 1) {
    Record "BLOCKED: expected exactly one AutoLab task, found $($target.Count); no task started"
    exit 3
  }
  if ($target[0].State -eq 'Disabled') { Record 'BLOCKED: task disabled; no automatic modification'; exit 4 }
  $adb = Get-Command adb -ErrorAction SilentlyContinue
  if (-not $adb) { Record 'WARN: adb not on PATH; task may have its own SDK path' }
  else {
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    $deviceOutput = & $adb.Source devices -l 2>&1 | Out-String
    Record "ADB devices: $deviceOutput"
    if ($deviceOutput -notmatch '(?m)^\S+\s+device\b') { Record 'BLOCKED: no authorized ADB device; no task started'; exit 5 }
  }
  Record "STARTING $($target[0].TaskPath)$($target[0].TaskName)"
  Start-ScheduledTask -TaskName $target[0].TaskName -TaskPath $target[0].TaskPath
  Start-Sleep -Seconds 8
  $after = Get-ScheduledTaskInfo -TaskName $target[0].TaskName -TaskPath $target[0].TaskPath
  Record "AFTER: last=$($after.LastRunTime) result=$($after.LastTaskResult)"
  Record 'TRIGGER_REQUESTED_ONLY: completion, UI, offline and voice tests remain unverified until fresh device logs are examined'
} catch { Record "FAIL: $($_.Exception.Message)"; exit 1 }
