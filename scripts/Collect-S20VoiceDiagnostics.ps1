# Samsung S20 FE voice crash / recognition diagnostics
# Run from a trusted Windows PowerShell terminal with Android platform-tools adb on PATH.
# Read-only: does not install APKs, change app data, grant permissions, or upload private logs.
param(
  [string]$OutputDir = ".\voice-diagnostics",
  [string]$Package = "com.example.english1000",
  [int]$Seconds = 90
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "ADB_NOT_FOUND: install Android platform-tools or add adb to PATH" }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$devices = @(adb devices -l)
$ready = @($devices | Where-Object { $_ -match "^\S+\s+device\b" })
if ($ready.Count -ne 1) { Write-Host ($devices -join "`n"); throw "ADB_DEVICE_NOT_READY_OR_MULTIPLE: connect and authorize exactly one Samsung" }
$serial = ($ready[0] -split "\s+")[0]
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$base = Join-Path $OutputDir "s20fe-voice-$stamp"
New-Item -ItemType Directory -Force -Path $base | Out-Null
adb -s $serial shell getprop ro.product.model | Out-File -Encoding utf8 (Join-Path $base "device-model.txt")
adb -s $serial shell getprop ro.build.version.release | Out-File -Encoding utf8 (Join-Path $base "android-version.txt")
adb -s $serial shell dumpsys package $Package | Select-String "versionName|versionCode|userId|granted=true|RECORD_AUDIO" | Out-File -Encoding utf8 (Join-Path $base "package-permissions.txt")
# Clear only volatile logcat buffers to isolate this run; do not modify application data.
adb -s $serial logcat -c
Write-Host "Reproduce: open English Words -> Learn Words, then test EN/RU speech and voice input. Capturing for $Seconds seconds."
Start-Sleep -Seconds $Seconds
adb -s $serial logcat -d -v threadtime -b main -b system -b crash | Out-File -Encoding utf8 (Join-Path $base "logcat-full.txt")
$pattern = "FATAL EXCEPTION|AndroidRuntime|SIGSEGV|SIGABRT|OutOfMemoryError|EnglishWordsVoice|ASR_MODEL_READY|ASR_RESULT|Vosk|AudioRecord|AudioTrack|sherpa|onnx|lowmemorykiller|lmkd"
Select-String -Path (Join-Path $base "logcat-full.txt") -Pattern $pattern | Out-File -Encoding utf8 (Join-Path $base "voice-events.txt")
Write-Host "Saved diagnostics: $base"
Write-Host "IMPORTANT: full logcat can contain personal data. Review and redact before sharing; do not post raw logs to public GitHub issues."
