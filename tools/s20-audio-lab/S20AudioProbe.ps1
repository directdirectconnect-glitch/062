#requires -version 5.1
<#
  English Words S20 Audio Probe - Windows 10/11, local acoustic test.
  Example (after installing a speaker and providing a real PCM WAV file):
    .\S20AudioProbe.ps1 -Language en -Expected hello -WavPath .\samples\hello.wav

  No network connection, cloud ASR, PC microphone or private telemetry.
  An isolated debug APK with VoiceProbeActivity must already be installed.
  This module is not yet wired into the scheduled v6 lab.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet("en", "ru")][string]$Language,
    [Parameter(Mandatory=$true)][string]$Expected,
    [Parameter(Mandatory=$true)][string]$WavPath,
    [string]$AndroidSerial = "",
    [string]$AdbPath = "",
    [ValidateRange(30, 240)][int]$ReadyTimeoutSeconds = 150,
    [ValidateRange(5, 120)][int]$ResultTimeoutSeconds = 45
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$package = "com.example.english1000.s20test"

function Assert-Condition([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}
function Resolve-Adb {
    if ($AdbPath) {
        Assert-Condition (Test-Path -LiteralPath $AdbPath -PathType Leaf) "ADB executable not found"
        return (Resolve-Path -LiteralPath $AdbPath).Path
    }
    $fixed = "C:\platform-tools\adb.exe"
    if (Test-Path -LiteralPath $fixed -PathType Leaf) { return $fixed }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    Assert-Condition ($null -ne $command) "ADB is not installed"
    return $command.Source
}
function Invoke-Adb([string[]]$Arguments) {
    $output = & $script:adbExe @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed (exit $LASTEXITCODE)" }
    return [string]($output -join "`n")
}
function Get-State {
    try {
        $xml = Invoke-Adb @("-s", $script:serial, "exec-out", "uiautomator", "dump", "/dev/tty")
        foreach($marker in @("VOICE_PROBE_PASS", "VOICE_PROBE_FAIL", "VOICE_PROBE_ERROR",
                            "VOICE_PROBE_READY", "VOICE_PROBE_LOADING")) {
            if ($xml.Contains($marker)) { return $marker }
        }
    } catch { }
    return "VOICE_PROBE_UNKNOWN"
}
function Wait-ForState([string[]]$StopAt, [int]$TimeoutSec) {
    $end = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $end) {
        $state = Get-State
        if ($StopAt -contains $state) { return $state }
        Start-Sleep -Milliseconds 850
    }
    return "VOICE_PROBE_TIMEOUT"
}

try {
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($Expected)) "Expected word is empty"
    Assert-Condition ($Expected.Length -le 80) "Expected word too long"
    Assert-Condition (Test-Path -LiteralPath $WavPath -PathType Leaf) "PCM WAV sample missing"
    $wav = (Resolve-Path -LiteralPath $WavPath).Path
    $stream = [System.IO.File]::OpenRead($wav)
    try {
        Assert-Condition ($stream.Length -ge 44) "WAV sample too small"
        $buffer = New-Object byte[] 12
        [void]$stream.Read($buffer, 0, 12)
        $riff = [System.Text.Encoding]::ASCII.GetString($buffer, 0, 4)
        $wave = [System.Text.Encoding]::ASCII.GetString($buffer, 8, 4)
        Assert-Condition ($riff -eq "RIFF" -and $wave -eq "WAVE") "Not a WAV file"
    } finally { $stream.Close() }

    $script:adbExe = Resolve-Adb
    if (-not $AndroidSerial) {
        $deviceListing = Invoke-Adb @("devices")
        $devices = @($deviceListing -split "`r?`n" |
                     Where-Object { $_ -match "^\S+\s+device(\s|$)" } |
                     ForEach-Object { ($_ -split "\s+")[0] })
        Assert-Condition ($devices.Count -eq 1) "Exactly one authorized Android device required"
        $script:serial = $devices[0]
    } else { $script:serial = $AndroidSerial }

    $installed = Invoke-Adb @("-s", $script:serial, "shell", "pm", "path", $package)
    Assert-Condition ($installed -match "package:") "Isolated S20 test package not installed"
    [void](Invoke-Adb @("-s", $script:serial, "shell", "pm", "grant", $package,
                         "android.permission.RECORD_AUDIO"))

    # Base64 ensures UTF-8 RU words survive Windows PowerShell 5.1 -> ADB quoting.
    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Expected))
    [void](Invoke-Adb @("-s", $script:serial, "shell", "am", "start",
         "-n", "$package/com.example.english1000.VoiceProbeActivity",
         "--es", "lang", $Language, "--es", "expected_b64", $encoded))

    $ready = Wait-ForState @("VOICE_PROBE_READY", "VOICE_PROBE_ERROR") $ReadyTimeoutSeconds
    Assert-Condition ($ready -eq "VOICE_PROBE_READY") "Vosk did not become ready: $ready"

    Add-Type -AssemblyName System
    $player = New-Object System.Media.SoundPlayer($wav)
    $player.Load()
    $player.PlaySync()

    $result = Wait-ForState @("VOICE_PROBE_PASS", "VOICE_PROBE_FAIL",
                               "VOICE_PROBE_ERROR") $ResultTimeoutSeconds
    # Only emit fixed result markers. Transcript, device IDs and paths stay local.
    Write-Output $result
    if ($result -ne "VOICE_PROBE_PASS") { exit 1 }
    exit 0
} catch {
    Write-Output "VOICE_PROBE_ERROR"
    # Keep potentially sensitive device and transcript details out of stdout.
    Write-Error "Local acoustic probe failed; inspect the Windows local diagnostic log"
    exit 1
}
