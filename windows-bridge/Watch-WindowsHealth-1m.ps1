#Requires -Version 5.1
<#
English Words one-minute Windows QA health probe.
Read-only phone inspection. GitHub issue #19 acts as an asynchronous message bus,
not a real-time connection to the cloud ChatGPT chat.
#>
[CmdletBinding()]
param(
  [string]$LabRoot = 'C:\EnglishWords-Windows-Lab',
  [string]$Repo = 'directdirectconnect-glitch/062',
  [int]$Issue = 19,
  [int]$PostEveryMinutes = 10
)
$ErrorActionPreference = 'Stop'
if ($Repo -cne 'directdirectconnect-glitch/062' -or $Issue -ne 19) { throw 'Unapproved GitHub target' }
$logDir = Join-Path $LabRoot 'results\autonomy-watchdog'
New-Item -Path $logDir -ItemType Directory -Force | Out-Null
$lock = $null
try {
  $lock = [System.IO.File]::Open((Join-Path $logDir 'watchdog.lock'), [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch { Write-Output 'WATCHDOG SKIP: another probe is still running'; exit 0 }
try {
  $now = [DateTime]::UtcNow
  $statusFile = Join-Path $logDir 'status.json'
  $logFile = Join-Path $logDir 'events.log'
  $previous = $null
  if (Test-Path -LiteralPath $statusFile) {
    try { $previous = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json } catch {}
  }
  $adb = 'C:\platform-tools\adb.exe'
  if (-not (Test-Path -LiteralPath $adb)) {
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
  }
  $adbOk = $false
  $packagePresent = $false
  $adbState = 'MISSING_ADB'
  if (Test-Path -LiteralPath $adb -PathType Leaf) {
    try {
      $deviceLines = @(& $adb devices -l 2>&1)
      if ($LASTEXITCODE -eq 0) {
        $active = @($deviceLines | Where-Object { $_ -match '^\S+\s+device\b' })
        if ($active.Count -eq 1) {
          $serial = (($active[0] -split '\s+')[0])
          $model = ((& $adb -s $serial shell getprop ro.product.model 2>&1) | Out-String).Trim()
          if ($LASTEXITCODE -eq 0 -and $model -eq 'SM-G780G') {
            $adbOk = $true; $adbState = 'CONNECTED'
            $pm = ((& $adb -s $serial shell pm path com.example.english1000.visual164qa2 2>&1) | Out-String).Trim()
            $packagePresent = ($LASTEXITCODE -eq 0 -and $pm -match '(?m)^package:')
          } else { $adbState = 'UNEXPECTED_MODEL' }
        } else { $adbState = 'DEVICE_COUNT_' + $active.Count }
      } else { $adbState = 'ADB_ERROR' }
    } catch { $adbState = 'ADB_EXCEPTION' }
  }
  $githubOk = $false
  if (Get-Command gh -ErrorAction SilentlyContinue) {
    try {
      $issueJson = & gh api ('repos/' + $Repo + '/issues/' + $Issue) --jq .number 2>&1
      $githubOk = ($LASTEXITCODE -eq 0 -and (($issueJson | Out-String).Trim() -eq [string]$Issue))
    } catch {}
  }
  # A process name alone is insufficient to prove the QA runner is active.
  # Query visible command lines without terminating or attaching to processes.
  $runners = @()
  try {
    $runners = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe' OR Name='pwsh.exe'" -ErrorAction Stop |
      Where-Object { $_.CommandLine -match '(?i)(Run-S20-1000QA|physical-43572281441|QA2.*Regression)' -and $_.CommandLine -notmatch '(?i)Watch-WindowsHealth-1m' } |
      Select-Object ProcessId,CreationDate,CommandLine)
  } catch {}
  $runnerAlive = ($runners.Count -gt 0)
  $runnerPid = if ($runnerAlive) { [int]$runners[0].ProcessId } else { $null }
  $progressDir = Join-Path $LabRoot 'results\qa2-1000\physical-43572281441'
  $latestProgress = $null
  if (Test-Path -LiteralPath $progressDir) {
    $latestProgress = Get-ChildItem -LiteralPath $progressDir -File -Recurse -ErrorAction SilentlyContinue |
      Where-Object { $_.Extension -in @('.jsonl','.csv','.json') } |
      Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
  }
  $progressTime = if ($latestProgress) { $latestProgress.LastWriteTimeUtc.ToString('o') } else { $null }
  $health = if (-not $adbOk) { 'DISCONNECTED' } elseif (-not $githubOk) { 'GITHUB_UNREACHABLE' } elseif ($runnerAlive) { 'RUNNING' } else { 'RUNNER_NOT_CONFIRMED' }
  $state = [ordered]@{
    timestampUtc = $now.ToString('o')
    intervalSeconds = 60
    adbState = $adbState
    adbConnected = $adbOk
    qaPackagePresent = $packagePresent
    githubReachable = $githubOk
    runnerAlive = $runnerAlive
    runnerPid = $runnerPid
    progressLastWriteUtc = $progressTime
    health = $health
    checkOnly = $true
  }
  $json = $state | ConvertTo-Json -Depth 4
  $tempFile = Join-Path $logDir ('status-' + $PID + '.tmp')
  [System.IO.File]::WriteAllText($tempFile, $json, (New-Object System.Text.UTF8Encoding($false)))
  Move-Item -LiteralPath $tempFile -Destination $statusFile -Force
  Add-Content -LiteralPath $logFile -Encoding UTF8 -Value ($now.ToString('o') + ' ' + $health + ' adb=' + $adbState + ' github=' + $githubOk + ' runner=' + $runnerAlive)
  # Heartbeat in issue #19: only on health transitions or every 10 minutes.
  # Avoid publishing machine identifiers, tokens or Android device serial.
  if ($githubOk) {
    $lastPostPath = Join-Path $logDir 'last-post-utc.txt'
    $lastPost = [DateTime]::MinValue
    if (Test-Path $lastPostPath) {
      $raw = (Get-Content -LiteralPath $lastPostPath -Raw).Trim()
      [void][DateTime]::TryParse($raw, [ref]$lastPost)
    }
    $changed = ($null -eq $previous -or [string]$previous.health -cne [string]$health)
    if ($changed -or ($now - $lastPost).TotalMinutes -ge $PostEveryMinutes) {
      $message = 'WINDOWS_HEARTBEAT_1M V1 | utc=' + $now.ToString('o') +
        ' | health=' + $health + ' | adb=' + $adbState +
        ' | qa2=' + $packagePresent + ' | runnerAlive=' + $runnerAlive +
        ' | progressLastWriteUtc=' + $progressTime +
        ' | checkOnly=true'
      $postFile = Join-Path $logDir 'post.txt'
      [System.IO.File]::WriteAllText($postFile, $message, (New-Object System.Text.UTF8Encoding($false)))
      & gh issue comment $Issue -R $Repo --body-file $postFile *> $null
      if ($LASTEXITCODE -eq 0) { Set-Content -LiteralPath $lastPostPath -Value $now.ToString('o') -Encoding Ascii }
    }
  }
  Write-Output ('WATCHDOG ' + $health + ' timestamp=' + $now.ToString('o'))
} finally {
  if ($null -ne $lock) { $lock.Dispose() }
}
