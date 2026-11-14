#Requires -Version 5.1
<#
English Words S20 FE - bounded GitHub issue #19 -> Windows dispatch bridge.
No arbitrary GitHub issue body is executed. No APK installation, data deletion, or
interactive device taps in this bridge. It only performs preflight and launches
a locally approved regression runner if explicitly configured.
#>
[CmdletBinding()]
param(
  [string]$LabRoot = 'C:\EnglishWords-Windows-Lab',
  [string]$Repo = 'directdirectconnect-glitch/062',
  [int]$Issue = 19,
  [string]$DeviceSerial = '',
  [string]$ApprovedRunner = '',
  [switch]$NoPost
)
$ErrorActionPreference = 'Stop'
if ($Repo -ne 'directdirectconnect-glitch/062' -or $Issue -ne 19) {
    throw 'Safety check: this bridge only handles directdirectconnect-glitch/062 issue #19.'
}
$stamp = Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK'
$logDir = Join-Path $LabRoot 'results\github-bridge-19'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir ('bridge-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
$lockPath = Join-Path $logDir 'bridge.lock'
$lock = $null
try {
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
    Write-Output 'SKIP: another bridge invocation is active.'
    exit 0
}
function Emit([string]$message) {
    $line = ((Get-Date -Format 's') + ' ' + $message)
    Add-Content -LiteralPath $log -Value $line -Encoding UTF8
    Write-Output $line
}
function Post([string]$body) {
    if ($NoPost) { Emit 'NoPost: GitHub comment intentionally skipped.'; return }
    $file = Join-Path $logDir 'last-comment.txt'
    [System.IO.File]::WriteAllText($file, $body, [System.Text.UTF8Encoding]::new($false))
    & gh issue comment 19 -R $Repo --body-file $file 2>&1 | Out-String | ForEach-Object { Emit $_.Trim() }
    if ($LASTEXITCODE -ne 0) { Emit 'WARNING: GitHub comment submission failed.' }
}
try {
    Emit "START issue=19 timestamp=$stamp"
    # Use the already installed Android Platform Tools even in a fresh scheduled session.
    # Changes only this process PATH, never the machine-wide environment.
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        $adbCandidates = @(
            'C:\platform-tools\adb.exe',
            (Join-Path $LabRoot 'platform-tools\adb.exe'),
            $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' })
        )
        $adbExe = $adbCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
        if ($adbExe) {
            $env:PATH = (Split-Path -Parent $adbExe) + [System.IO.Path]::PathSeparator + $env:PATH
            Emit ('ADB resolved at ' + $adbExe)
        }
    }
    foreach ($command in @('gh','adb')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            Emit "BLOCKED: missing $command in PATH."
            exit 2
        }
    }
    & gh auth status 2>&1 | Out-String | ForEach-Object { Emit $_.Trim() }
    if ($LASTEXITCODE -ne 0) { Emit 'BLOCKED: gh CLI is not authenticated.'; exit 2 }
    $json = & gh issue view 19 -R $Repo --json number,state,title,url 2>&1
    if ($LASTEXITCODE -ne 0) { Emit ('BLOCKED: GitHub issue read: ' + ($json | Out-String)); exit 2 }
    $ticket = ($json | Out-String) | ConvertFrom-Json
    if ($ticket.number -ne 19 -or $ticket.state -ne 'OPEN') {
        Emit "SKIP: issue #19 not open."; exit 0
    }
    $homeProfile = if ($env:USERPROFILE) { $env:USERPROFILE } else { $HOME }
    if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $homeProfile '.android' }
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    Emit ('ANDROID_USER_HOME=' + $env:ANDROID_USER_HOME)
    $deviceText = & adb devices -l 2>&1
    if ($LASTEXITCODE -ne 0) { Emit ('BLOCKED: adb failed: ' + ($deviceText | Out-String)); exit 2 }
    Emit ($deviceText | Out-String)
    $active = @($deviceText | Where-Object { $_ -match '^(\S+)\s+device\b' })
    if ($DeviceSerial) { $active = @($active | Where-Object { $_ -match ('^' + [regex]::Escape($DeviceSerial) + '\s+device\b') }) }
    if ($active.Count -ne 1) {
        Emit "BLOCKED: expected exactly one authorized target device; found $($active.Count)."
        exit 2
    }
    $serial = ($active[0] -split '\s+')[0]
    $model = (& adb -s $serial shell getprop ro.product.model 2>&1 | Out-String).Trim()
    Emit "CONNECTED serial=$serial model=$model"
    if (-not ($model -match 'SM-G780|S20 FE')) {
        Emit 'BLOCKED: attached device is not confirmed as Samsung Galaxy S20 FE.'
        exit 2
    }
    $safeStatus = "Windows bridge received issue #19. ADB=PASS; device=$model; serial=$serial; physical 1000-word test=NOT_RUN. Local log: $log"
    if ([string]::IsNullOrWhiteSpace($ApprovedRunner)) {
        Emit 'BLOCKED: no approved local regression runner supplied. No APK installed or phone UI operated.'
        Post ($safeStatus + "`nBLOCKED: approved regression runner is not configured.")
        exit 3
    }
    $fullRunner = [System.IO.Path]::GetFullPath($ApprovedRunner)
    $rootPrefix = [System.IO.Path]::GetFullPath($LabRoot).TrimEnd('\') + '\'
    if (-not $fullRunner.StartsWith($rootPrefix,[System.StringComparison]::OrdinalIgnoreCase) -or
         [System.IO.Path]::GetExtension($fullRunner) -ne '.ps1' -or
         -not (Test-Path -LiteralPath $fullRunner -PathType Leaf)) {
        Emit 'BLOCKED: approved runner must be an existing .ps1 inside LabRoot.'
        Post ($safeStatus + "`nBLOCKED: approved local regression runner not found.")
        exit 3
    }
    $runMarker = Join-Path $logDir 'attempted-approved-runner.txt'
    if (Test-Path -LiteralPath $runMarker) {
        Emit 'SKIP: runner already attempted. Remove marker manually after reviewing its result.'
        exit 0
    }
    Set-Content -LiteralPath $runMarker -Value ("runner=$fullRunner timestamp=$stamp") -Encoding UTF8
    Emit ('RUNNING approved local runner: ' + $fullRunner)
    Post ($safeStatus + "`nSTATE: approved local test runner has been invoked; no test result claimed yet.")
    $out = & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $fullRunner 2>&1
    $code = $LASTEXITCODE
    $out | Out-File -FilePath (Join-Path $logDir 'approved-runner-output.log') -Encoding UTF8
    Emit "FINISHED approved runner exit_code=$code. Full result requires independent review of runner logs."
    Post ("Issue #19 local approved runner finished. exit_code=$code. Physical test PASS/FAIL/unique ID coverage must be read from saved runner logs; bridge itself does not assert PASS. Log: $log")
    if ($code -ne 0) { exit 4 }
} catch {
    Emit ('ERROR: ' + $_.Exception.Message)
    exit 5
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
