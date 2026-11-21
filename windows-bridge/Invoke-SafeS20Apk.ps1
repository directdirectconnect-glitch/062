# English Words: isolated Samsung APK preflight and guarded installation.
# This script is NOT an Issue #19 command handler. Never execute issue comment text.
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedSha256,
  [Parameter(Mandatory=$true)][ValidatePattern('^com\.example\.english1000(\.[a-zA-Z0-9_]+)*$')][string]$ExpectedPackage,
  [ValidateSet('Preflight','Install')][string]$Mode='Preflight',
  [string]$Repo='directdirectconnect-glitch/062',
  [long]$RunId=44902725944,
  [string]$Adb='C:\platform-tools\adb.exe',
  [string]$Aapt='',
  [string]$ApkSigner=''
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if ($Repo -ne 'directdirectconnect-glitch/062' -or $RunId -ne 44902725944) { throw 'BLOCKED: unapproved repository or run' }
if (!(Test-Path -LiteralPath $Adb)) { throw 'BLOCKED: adb missing' }
$deviceLines=@(& $Adb devices | Where-Object { $_ -match '^[^\s]+\s+device$' })
if ($deviceLines.Count -ne 1) { throw "BLOCKED: expected exactly one authorized device; found $($deviceLines.Count)" }
$serial=($deviceLines[0] -split '\s+')[0]
$model=(& $Adb -s $serial shell getprop ro.product.model).Trim()
if ($model -notin @('SM-G780G','SM-G780F')) { throw "BLOCKED: unexpected device model $model" }
$dir=Join-Path $env:LOCALAPPDATA 'EnglishWords\SafeApk\44902725944'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$apk=Join-Path $dir 'app-debug.apk'
if (!(Test-Path -LiteralPath $apk)) {
  if (!(Get-Command gh -ErrorAction SilentlyContinue)) { throw 'BLOCKED: gh CLI missing' }
  & gh run download $RunId --repo $Repo --name EnglishWords-1.0.24-APK --dir $dir
  if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: artifact download failed' }
}
if (!(Test-Path -LiteralPath $apk)) { throw 'BLOCKED: APK missing in downloaded artifact' }
$actual=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $ExpectedSha256.ToLowerInvariant()) { throw "BLOCKED: APK SHA256 mismatch: $actual" }
if (!$Aapt) {
  $candidates=@(Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Filter aapt.exe -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
  if ($candidates.Count -gt 0) { $Aapt=$candidates[0].FullName }
}
if (!$ApkSigner) {
  $signers=@(Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
  if ($signers.Count -gt 0) { $ApkSigner=$signers[0].FullName }
}
if (!$Aapt -or !(Test-Path -LiteralPath $Aapt)) { throw 'BLOCKED: aapt.exe required for package verification' }
if (!$ApkSigner -or !(Test-Path -LiteralPath $ApkSigner)) { throw 'BLOCKED: apksigner.bat required for signature verification' }
$badging=@(& $Aapt dump badging $apk)
if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: aapt failed' }
$pkgLine=@($badging | Where-Object { $_ -match '^package: name=' })
if ($pkgLine.Count -ne 1 -or $pkgLine[0] -notmatch "name='([^']+)'") { throw 'BLOCKED: cannot parse package' }
$package=$Matches[1]
if ($package -cne $ExpectedPackage) { throw "BLOCKED: package mismatch: $package" }
& $ApkSigner verify --verbose $apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: APK signature verification failed' }
$installed=(& $Adb -s $serial shell pm list packages $package) -join "`n"
if ($installed -match "(?m)^package:$([regex]::Escape($package))\s*$") { throw 'BLOCKED: package already installed; no overwrite permitted' }
Write-Output "PREFLIGHT PASS | model=$model | package=$package | SHA256=$actual | run=$RunId | mode=$Mode"
if ($Mode -eq 'Install') {
  & $Adb -s $serial install --no-streaming $apk
  if ($LASTEXITCODE -ne 0) { throw 'INSTALL FAILED: adb returned nonzero' }
  Write-Output "INSTALL REQUEST COMPLETED | verify package launch and audio separately"
}
