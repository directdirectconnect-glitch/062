#Requires -Version 5.1
<#
Installs a dedicated one-minute read-only watchdog task.
Does not modify the existing EnglishWords-Issue19-Bridge or QA runner task.
Run only from an authorized local Windows interactive session after code review.
#>
[CmdletBinding()]
param(
 [string]$LabRoot = 'C:\EnglishWords-Windows-Lab',
 [string]$TaskName = 'EnglishWords-WindowsHealth-1m'
)
$ErrorActionPreference='Stop'
if ($TaskName -cne 'EnglishWords-WindowsHealth-1m') { throw 'Unapproved task name' }
$source=Join-Path $PSScriptRoot 'Watch-WindowsHealth-1m.ps1'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw ('Missing watchdog source: ' + $source) }
$targetDir=Join-Path $LabRoot 'bridge'
New-Item -Path $targetDir -ItemType Directory -Force | Out-Null
$target=Join-Path $targetDir 'Watch-WindowsHealth-1m.ps1'
$tokens=$null
$errors=$null
[void][System.Management.Automation.Language.Parser]::ParseFile($source,[ref]$tokens,[ref]$errors)
if ($errors.Count -gt 0) { throw ('Watchdog PowerShell parse errors: ' + $errors.Count + ': ' + ($errors[0].Message)) }
Copy-Item -LiteralPath $source -Destination $target -Force
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + $target + '"')
$trigger=New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1) -RepetitionDuration (New-TimeSpan -Days 3650)
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description 'Read-only health check of English Words QA, GitHub and S20 FE once per minute' -Force | Out-Null
# Execute one initial probe in the same Windows user context to validate dependencies.
& powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $target
if ($LASTEXITCODE -ne 0) { throw ('Initial watchdog probe failed, exit=' + $LASTEXITCODE) }
Get-ScheduledTaskInfo -TaskName $TaskName | Select-Object TaskName,LastRunTime,LastTaskResult,NextRunTime
Write-Output ('WATCHDOG_TASK_INSTALLED=' + $TaskName)
Write-Output ('WATCHDOG_STATUS_FILE=' + (Join-Path $LabRoot 'results\autonomy-watchdog\status.json'))
