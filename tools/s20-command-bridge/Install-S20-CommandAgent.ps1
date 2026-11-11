#requires -version 5.1
<# Create a separate non-elevated scheduled task; no existing tasks are modified. #>
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskName = 'EnglishWords-S20-CommandBridge'
$root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-CommandAgent'
$source = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'Run-S20-CommandAgent.ps1'
$destination = Join-Path $root 'Run-S20-CommandAgent.ps1'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw 'Agent script missing' }
if (-not (Get-ScheduledTask -TaskName 'EnglishWords-S20-Unattended' -ErrorAction SilentlyContinue)) {
    throw 'Expected S20 unattended task is missing'
}
$old = Get-ScheduledTask -TaskName 'EnglishWords-S20-AutoTest' -ErrorAction SilentlyContinue
if ($old -and $old.State -ne 'Disabled') { throw 'Old test task must remain disabled' }
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) { throw 'GitHub CLI (gh) is required for safe queue and reporting' }
if (-not (Test-Path -LiteralPath $root -PathType Container)) {
    New-Item -ItemType Directory -Path $root -Force | Out-Null
}
if (Test-Path -LiteralPath $destination -PathType Leaf) {
    Copy-Item -LiteralPath $destination -Destination ($destination + '.backup') -Force
}
Copy-Item -LiteralPath $source -Destination $destination -Force
$localExe = Join-Path $PSHOME 'powershell.exe'
& $localExe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $destination -DryRun
if ($LASTEXITCODE -ne 0) { throw 'Dry-run queue check failed. Check gh authentication.' }
$exe = Join-Path $PSHOME 'powershell.exe'
$arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $destination + '"'
$action = New-ScheduledTaskAction -Execute $exe -Argument $arguments -WorkingDirectory $root
$userName = $env:USERDOMAIN + [char]92 + $env:USERNAME
$principal = New-ScheduledTaskPrincipal -UserId $userName -LogonType Interactive -RunLevel Limited
$once = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 15) -RepetitionDuration (New-TimeSpan -Days 3650)
$logon = New-ScheduledTaskTrigger -AtLogOn -User $userName
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Minutes 20) -MultipleInstances IgnoreNew -StartWhenAvailable
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing) {
    if (@($existing.Actions).Count -ne 1 -or
        $existing.Actions[0].Arguments -notlike ('*' + $destination + '*')) {
        throw 'Unexpected existing command task; refusing to replace'
    }
    Set-ScheduledTask -TaskName $taskName -Action $action -Trigger @($once, $logon) -Settings $settings -ErrorAction Stop | Out-Null
} else {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($once, $logon) -Principal $principal -Settings $settings -Description 'Allow-listed GitHub -> S20 test commands only; no remote shell.' -ErrorAction Stop | Out-Null
}
Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
Write-Output 'S20_COMMAND_BRIDGE_TASK_READY'
Write-Output 'Initial agent run requested. It may take a short period to report to Issue 3.'
Write-Output 'Existing S20 test and status publisher tasks were not changed.'
