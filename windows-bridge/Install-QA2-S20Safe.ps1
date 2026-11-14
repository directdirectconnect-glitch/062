#Requires -Version 5.1
<#
QA2 safe deploy for a single Samsung Galaxy S20 FE.
Never uninstalls, clears data, replaces packages or claims word coverage.
Downloads only the successful visual-test build for the approved QA2 source commit.
#>
[CmdletBinding()]
param(
 [string]$LabRoot = 'C:\EnglishWords-Windows-Lab',
 [string]$Repo = 'directdirectconnect-glitch/062',
 [string]$Branch = 'test/0.9.8-5119-118-images-s20fe-fold',
 [string]$SourceCommit = '557b542ec30ffd805fa375c720089c992f90c02b',
 [string]$ExpectedPackage = 'com.example.english1000.visual164qa2',
 [string]$Adb = 'C:\platform-tools\adb.exe'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Repo -cne 'directdirectconnect-glitch/062' -or
    $Branch -cne 'test/0.9.8-5119-118-images-s20fe-fold' -or
    $SourceCommit -cne '557b542ec30ffd805fa375c720089c992f90c02b' -or
    $ExpectedPackage -cne 'com.example.english1000.visual164qa2') { throw 'Fixed QA2 safety scope cannot be overridden.' }
$root = Join-Path $LabRoot 'results\s20fe-qa2'
New-Item -ItemType Directory -Path $root -Force | Out-Null
$log = Join-Path $root ('deploy-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
function Say([string]$s) { $s | Tee-Object -FilePath $log -Append }
function Assert([bool]$p,[string]$msg) { if (-not $p) { throw $msg } }
try {
 Say 'START read-only checks'
 Assert (Test-Path -LiteralPath $Adb -PathType Leaf) 'Missing adb.exe'
 Assert ($null -ne (Get-Command gh -ErrorAction SilentlyContinue)) 'Missing gh CLI'
 & gh auth status *> $null
 Assert ($LASTEXITCODE -eq 0) 'GitHub CLI not authorized'
 $env:ANDROID_USER_HOME = if ($env:USERPROFILE) { Join-Path $env:USERPROFILE '.android' } else { throw 'USERPROFILE required' }
 New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
 $devices = @(& $Adb devices -l)
 Assert ($LASTEXITCODE -eq 0) 'adb devices failed'
 $authorized = @($devices | Where-Object { $_ -match '^\S+\s+device\b' })
 Assert ($authorized.Count -eq 1) ('Expected exactly one authorized device; got ' + $authorized.Count)
 $serial = ($authorized[0] -split '\s+')[0]
 $model = ((& $Adb -s $serial shell getprop ro.product.model) | Out-String).Trim()
 Assert ($LASTEXITCODE -eq 0 -and $model -eq 'SM-G780G') ('Unexpected device model: ' + $model)
 Say ('DEVICE=PASS; model=' + $model)
 $runsText = & gh run list -R $Repo -b $Branch -w visual-test-apk.yml -L 30 --json databaseId,headSha,status,conclusion,event
 Assert ($LASTEXITCODE -eq 0) 'Cannot query GitHub Actions'
 $runs = @($runsText | ConvertFrom-Json)
 $approved = @($runs | Where-Object {
    $_.headSha -eq $SourceCommit -and $_.status -eq 'completed' -and
    $_.conclusion -eq 'success' -and $_.event -eq 'push'
 })
 Assert ($approved.Count -gt 0) 'No confirmed successful QA2 build for exact approved commit; nothing installed'
 $runId = [string]$approved[0].databaseId
 Say ('CI=PASS; run=' + $runId)
 $output = Join-Path $root ('run-' + $runId)
 New-Item -ItemType Directory -Force -Path $output | Out-Null
 & gh run download $runId -R $Repo --name 'EnglishWords-5126-164-Visual-Test-APK' --dir $output
 Assert ($LASTEXITCODE -eq 0) 'Cannot download approved APK'
 $apkFiles = @(Get-ChildItem -LiteralPath $output -File -Filter '*.apk')
 Assert ($apkFiles.Count -eq 1) ('Expected exactly 1 downloaded APK, got ' + $apkFiles.Count)
 $apk = $apkFiles[0].FullName
 $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
 Say ('APK_SHA256=' + $hash)
 $sdkTools = @('C:\platform-tools\aapt.exe')
 if ($env:LOCALAPPDATA) { $sdkTools += @(Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Filter aapt.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 if ($env:ANDROID_HOME) { $sdkTools += @(Get-ChildItem -Path (Join-Path $env:ANDROID_HOME 'build-tools') -Filter aapt.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 if ($env:ANDROID_SDK_ROOT) { $sdkTools += @(Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT 'build-tools') -Filter aapt.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 $aapt = @($sdkTools | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1)
 Assert ($aapt.Count -eq 1) 'aapt.exe unavailable: refuse installation until APK manifest is verified'
 $badging = (& $aapt[0] dump badging $apk | Select-Object -First 1)
 Assert ($LASTEXITCODE -eq 0) 'aapt inspection failed'
 Assert ($badging -match "^package:\s+name='([^']+)'") 'Could not parse package name'
 Assert ($Matches[1] -ceq $ExpectedPackage) ('Unexpected APK package: ' + $Matches[1])
 Say ('APK_PACKAGE=PASS; ' + $ExpectedPackage)
 $signerCandidates = @()
 if ($env:LOCALAPPDATA) { $signerCandidates += @(Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 if ($env:ANDROID_HOME) { $signerCandidates += @(Get-ChildItem -Path (Join-Path $env:ANDROID_HOME 'build-tools') -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 if ($env:ANDROID_SDK_ROOT) { $signerCandidates += @(Get-ChildItem -Path (Join-Path $env:ANDROID_SDK_ROOT 'build-tools') -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
 $signer = @($signerCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1)
 Assert ($signer.Count -eq 1) 'apksigner.bat unavailable: refuse to install unsigned/unverified artifact'
 $signatureInfo = (& $signer[0] verify --print-certs $apk 2>&1 | Out-String)
 Assert ($LASTEXITCODE -eq 0) 'APK digital signature verification failed'
 $shaLine = @($signatureInfo -split "[\r\n]+" | Where-Object { $_ -match '(?i)certificate SHA-256 digest:' })
 Assert ($shaLine.Count -gt 0) 'Could not read signing certificate digest'
 Say ('APK_SIGNATURE=PASS; ' + $shaLine[0].Trim())

 $installed = ((& $Adb -s $serial shell pm path $ExpectedPackage) | Out-String).Trim()
 Assert ($LASTEXITCODE -eq 0) 'pm path failed'
 if ($installed -match '(?m)^package:') {
  Say 'ALREADY_INSTALLED=YES; no overwrite attempted'
  exit 0
 }
 Say 'INSTALL_START: new QA2 package only'
 $result = & $Adb -s $serial install $apk 2>&1 | Out-String
 Assert ($LASTEXITCODE -eq 0 -and $result -match '\bSuccess\b') ('Installation failed: ' + $result.Trim())
 $installedAfter = ((& $Adb -s $serial shell pm path $ExpectedPackage) | Out-String).Trim()
 Assert ($LASTEXITCODE -eq 0 -and $installedAfter -match '(?m)^package:') 'QA2 package not visible after installation'
 Say 'INSTALL=PASS; no existing package removed or overwritten; regression NOT_RUN'
 exit 0
} catch {
 Say ('BLOCKED: ' + $_.Exception.Message)
 exit 2
}
