# One-time, explicit local deployment of the read-only APK preflight inbox.
# Does not modify or disable the existing QA2 inbox or any existing scheduled task.
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
$repo='directdirectconnect-glitch/062'
$ref='feature/safe-s20-apk-preflight-20261121'
$root='C:\EnglishWords-Windows-Lab\bridge'
$task='EnglishWords-APK-Preflight-Issue19'
$files=@('Invoke-SafeS20Apk.ps1','Receive-SafeS20ApkPreflight.ps1')
if (!(Get-Command gh -ErrorAction SilentlyContinue)) { throw 'BLOCKED: gh CLI unavailable' }
if (!(Test-Path -LiteralPath $root)) { throw 'BLOCKED: Windows lab bridge directory unavailable' }
if (Get-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue) { throw 'BLOCKED: task already exists; manual review required' }
$tmp=Join-Path $env:TEMP 'EnglishWords-ApkPreflight-Staging'
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
foreach($name in $files) {
  $source="windows-bridge/$name"
  $dest=Join-Path $tmp $name
  $raw=& gh api "repos/$repo/contents/$source?ref=$ref" --jq '.content'
  if ($LASTEXITCODE -ne 0 -or !$raw) { throw "BLOCKED: cannot fetch $source" }
  $bytes=[Convert]::FromBase64String(($raw -join '') -replace '\s','')
  [IO.File]::WriteAllBytes($dest,$bytes)
  $errors=$null;$tokens=$null
  [void][System.Management.Automation.Language.Parser]::ParseFile($dest,[ref]$tokens,[ref]$errors)
  if ($errors.Count -ne 0) { throw "BLOCKED: PowerShell parse errors in $name" }
  Write-Output "$name SHA256=$((Get-FileHash $dest -Algorithm SHA256).Hash)"
}
if (!$Apply) { Write-Output 'DRY RUN PASS: no Windows files or tasks changed. Use -Apply only after reviewing staged files.'; return }
foreach($name in $files) {
  $dest=Join-Path $root $name
  if (Test-Path -LiteralPath $dest) { throw "BLOCKED: existing file $dest" }
}
foreach($name in $files) { Copy-Item -LiteralPath (Join-Path $tmp $name) -Destination (Join-Path $root $name) -ErrorAction Stop }
& powershell.exe -NoProfile -NonInteractive -File (Join-Path $root 'Receive-SafeS20ApkPreflight.ps1') -CheckOnly
if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: local inbox precheck failed; no task registered' }
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $root 'Receive-SafeS20ApkPreflight.ps1')+'"')
$trigger=New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger -Description 'English Words APK preflight only; never installs' -ErrorAction Stop | Out-Null
Write-Output "DEPLOYED: task=$task (preflight-only). Existing QA2 tasks untouched."
