#requires -version 5.1
<#
Installs a separate low-privilege fixed-action status publisher.
Does not modify the existing S20 test task, old task, APK, or phone.
Requires explicit local operator/Codex approval for task registration.
#>
[CmdletBinding()]
param([switch]$NoStart)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskName = 'EnglishWords-S20-StatusPublisher'
$testTaskName = 'EnglishWords-S20-Unattended'
$root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-Unattended'
$source = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'Publish-SafeStatus.ps1'
$destination = Join-Path $root 'Publish-SafeStatus.ps1'
if (-not (Test-Path -LiteralPath $root -PathType Container)) {
  throw 'Expected unattended agent folder is missing; aborting.'
}
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
  throw 'Publisher source file missing; aborting.'
}
if (-not (Get-ScheduledTask -TaskName $testTaskName -ErrorAction SilentlyContinue)) {
  throw 'Expected unattended runner task not found; aborting.'
}
$legacy = Get-ScheduledTask -TaskName 'EnglishWords-S20-AutoTest' -ErrorAction SilentlyContinue
if ($legacy -and $legacy.State -ne 'Disabled') {
  throw 'Legacy task is not disabled; resolve test-task concurrency before setup.'
}
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing) {
  if (@($existing.Actions).Count -ne 1 -or
      $existing.Actions[0].Arguments -notlike '*Publish-SafeStatus.ps1*' -or
      $existing.Actions[0].Arguments -notlike ('*' + $destination + '*')) {
    throw 'Existing status task has an unexpected action; refusing to overwrite it.'
  }
}
if (Test-Path -LiteralPath $destination -PathType Leaf) {
  Copy-Item -LiteralPath $destination -Destination ($destination + '.backup') -Force
}
Copy-Item -LiteralPath $source -Destination $destination -Force
$psExe = Join-Path $PSHOME 'powershell.exe'
$arguments = ('-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "{0}"' -f $destination)
$action = New-ScheduledTaskAction -Execute $psExe -Argument $arguments -WorkingDirectory $root
$userName = $env:USERDOMAIN + [char]92 + $env:USERNAME
$principal = New-ScheduledTaskPrincipal -UserId $userName -LogonType Interactive -RunLevel Limited
$once = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval (New-TimeSpan -Minutes 15) -RepetitionDuration (New-TimeSpan -Days 3650)
$logon = New-ScheduledTaskTrigger -AtLogOn -User $userName
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -MultipleInstances IgnoreNew -StartWhenAvailable
if (-not $existing) {
  Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($once,$logon) -Principal $principal -Settings $settings -Description 'Publish sanitized S20 test verdicts to GitHub Issue 3; no remote shell.' -ErrorAction Stop | Out-Null
} else {
  Set-ScheduledTask -TaskName $taskName -Action $action -Trigger @($once,$logon) -Settings $settings -ErrorAction Stop | Out-Null
}
Write-Output ('STATUS_PUBLISH_TASK_READY: ' + $taskName)
if (-not $NoStart) {
  Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
  Write-Output 'FIRST_STATUS_PUBLICATION_REQUESTED'
}
Write-Output 'Existing phone test scheduled task and APK have not been changed.'
