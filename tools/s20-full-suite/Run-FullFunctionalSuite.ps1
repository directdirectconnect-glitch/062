#requires -version 5.1
<#
English Words S20 physical acceptance harness (initial suite).
Runs against an isolated .s20qa package only; never uninstalls/replaces apps
or modifies the user's existing .s20test / Galaxy Fold programs.
This script must be copied and SHA-256 approved LOCALLY before CommandBridge
is allowed to invoke it. It cannot be executed from a GitHub issue comment.
#>
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$OutputJson)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Repo = 'directdirectconnect-glitch/062'
$RunId = '43087170540'
$Artifact = 'EnglishWords-S20QA-5119-Voice-APK'
$ExpectedSha256 = 'C1CC6F3E797E176C99A5C5AE1DCB57B706E698CAD540AD0575D8A63E46AF7C71'
$ExpectedPackage = 'com.example.english1000.s20qa'
$ExistingTestPackage = 'com.example.english1000.s20test'
$MainActivity = 'com.example.english1000.MainActivity'
$Results = New-Object 'System.Collections.Generic.List[object]'
$StartedUtc = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$serial = ''
$adb = ''
$installedQa = $false
$root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-CommandAgent'
$cache = Join-Path $root 'qa-apk-43087170540'
$apk = Join-Path $cache 'app-s20qa.apk'

function Add-Result([string]$Name, [string]$Status, [string]$Detail) {
    if (@('PASS', 'FAIL', 'BLOCKED', 'NOT_RUN') -notcontains $Status) {
        throw 'Internal verdict is invalid'
    }
    $Results.Add([pscustomobject]@{
        id = $Name
        status = $Status
        detail = $Detail
    })
}
function Need([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}
function Get-Adb {
    $path = 'C:\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $path -PathType Leaf) { return $path }
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'ADB_MISSING'
}
function Invoke-Adb([string[]]$Arguments) {
    $output = @(& $script:adb -s $script:serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_COMMAND_FAILED' }
    return ($output -join [Environment]::NewLine)
}
function Ui-Document {
    $null = Invoke-Adb -Arguments @('shell', 'uiautomator', 'dump', '/sdcard/ew-s20qa-ui.xml')
    $raw = Invoke-Adb -Arguments @('exec-out','cat','/sdcard/ew-s20qa-ui.xml')
    try { return [xml]$raw } catch { throw 'ANDROID_UI_XML_INVALID' }
}
function Visible-Contains([string[]]$Needles) {
    $xml = Ui-Document
    foreach ($node in @($xml.SelectNodes('//node'))) {
        $t = $node.GetAttribute('text') + ' ' + $node.GetAttribute('content-desc')
        foreach ($needle in $Needles) {
            if ($t.IndexOf($needle,[StringComparison]::OrdinalIgnoreCase) -ge 0) { return $true }
        }
    }
    return $false
}
function Tap-Control([string[]]$Needles, [int]$MaxScroll=3) {
    for ($attempt=0; $attempt -le $MaxScroll; $attempt++) {
        $xml = Ui-Document
        $nodes = @($xml.SelectNodes('//node'))
        # Prefer native clickable controls over headings or nested labels.
        $nodes = @($nodes | Sort-Object { if ($_.GetAttribute('clickable') -eq 'true') { 0 } else { 1 } })
        foreach ($node in $nodes) {
            $t = $node.GetAttribute('text') + ' ' + $node.GetAttribute('content-desc')
            $found = $false
            foreach ($needle in $Needles) {
                if ($t.IndexOf($needle,[StringComparison]::OrdinalIgnoreCase) -ge 0) { $found = $true; break }
            }
            if (-not $found) { continue }
            $b = [regex]::Match($node.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
            if (-not $b.Success) { continue }
            $x = [int](([int]$b.Groups[1].Value + [int]$b.Groups[3].Value)/2)
            $y = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value)/2)
            if ($x -le 0 -or $y -le 20) { continue }
            $null = Invoke-Adb -Arguments @('shell','input','tap',[string]$x,[string]$y)
            Start-Sleep -Milliseconds 900
            return
        }
        if ($attempt -lt $MaxScroll) {
            $null = Invoke-Adb -Arguments @('shell','input','swipe','500','1500','500','500','450')
            Start-Sleep -Milliseconds 700
        }
    }
    throw 'CONTROL_NOT_VISIBLE'
}
function Assert-Screen([string[]]$Needles,[string]$Name) {
    Need (Visible-Contains $Needles) ('SCREEN_CONTROL_MISSING_' + $Name)
}
function Run-Case([string]$Name,[scriptblock]$Body,[string]$Description) {
    try {
        & $Body
        Add-Result $Name 'PASS' $Description
    } catch {
        Add-Result $Name 'FAIL' ('UI or process assertion failed: ' + $Name)
    }
}
function Verify-WordAssets([string]$ApkPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entry = $archive.GetEntry('assets/words.json')
        Need ($null -ne $entry) 'DICTIONARY_ASSET_MISSING'
        $stream = $entry.Open()
        $reader = New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)
        try { $json = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $words = @(ConvertFrom-Json -InputObject $json)
        Need ($words.Count -eq 5119) 'DICTIONARY_COUNT_NOT_5119'
        $ids = New-Object 'System.Collections.Generic.HashSet[int]'
        $names = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($word in $words) {
            Need ($word.id -gt 0 -and $ids.Add([int]$word.id)) 'DUPLICATE_ID'
            Need ($names.Add(([string]$word.en).Trim().ToLowerInvariant())) 'DUPLICATE_ENGLISH'
            Need (-not [string]::IsNullOrWhiteSpace([string]$word.ru)) 'EMPTY_TRANSLATION'
            Need (-not [string]::IsNullOrWhiteSpace([string]$word.pron)) 'EMPTY_PRONUNCIATION'
        }
        foreach ($lang in @('en','ru')) {
            Need ($null -ne $archive.GetEntry("assets/speech/$lang.zip")) 'VOSK_MODEL_MISSING'
        }
    } finally { $archive.Dispose() }
}
function Start-Qa {
    $component = $ExpectedPackage + '/' + $MainActivity
    $null = Invoke-Adb -Arguments @('shell','am','start','-W','-n',$component)
    Start-Sleep -Seconds 3
    $pid = Invoke-Adb -Arguments @('shell','pidof',$ExpectedPackage)
    Need ($pid.Trim().Length -gt 0) 'QA_PROCESS_NOT_RUNNING'
}
function Confirm-Device {
    $devices = @(& $script:adb devices 2>$null)
    Need ($LASTEXITCODE -eq 0) 'ADB_ENUMERATION_FAILED'
    $valid = @($devices | Where-Object { $_ -match '^\S+\s+device\s*$' })
    Need ($valid.Count -eq 1) 'NOT_EXACTLY_ONE_AUTHORIZED_DEVICE'
    $script:serial = (($valid[0] -split '\s+')[0]).Trim()
    $model = Invoke-Adb -Arguments @('shell','getprop','ro.product.model')
    Need ($model.Trim() -ceq 'SM-G780G') 'PHONE_MODEL_NOT_S20FE'
    $installed = Invoke-Adb -Arguments @('shell','pm','path',$ExistingTestPackage')
    Need ($installed -match 'package:') 'EXISTING_TEST_PACKAGE_MISSING'
}
function Get-Artifact {
    $gh = Get-Command gh.exe -ErrorAction SilentlyContinue
    if (-not $gh) { $gh = Get-Command gh -ErrorAction SilentlyContinue }
    Need ($null -ne $gh) 'GITHUB_CLI_NOT_FOUND'
    if (-not (Test-Path -LiteralPath $cache -PathType Container)) {
        New-Item -ItemType Directory -Force -Path $cache | Out-Null
    }
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        & $gh.Source run download $RunId -R $Repo -n $Artifact -D $cache *> $null
        Need ($LASTEXITCODE -eq 0) 'APPROVED_CI_ARTIFACT_UNAVAILABLE'
    }
    Need (Test-Path -LiteralPath $apk -PathType Leaf) 'APK_NOT_DOWNLOADED'
    $sha = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    Need ($sha -ceq $ExpectedSha256) 'APK_SHA256_MISMATCH'
}
function Install-QaSideBySide {
    # Refuse any destructive upgrade/replacement. New test app only.
    $existing = Invoke-Adb -Arguments @('shell','pm','path',$ExpectedPackage)
    Need ($existing -notmatch 'package:') 'QA_APP_ALREADY_INSTALLED_REFUSING_OVERWRITE'
    $res = @(& $script:adb -s $script:serial install $apk 2>&1)
    Need ($LASTEXITCODE -eq 0 -and ($res -join ' ') -match 'Success') 'QA_APK_INSTALL_FAILED'
    $script:installedQa = $true
}

try {
    $adb = Get-Adb
    Confirm-Device
    Add-Result 'S20_AUTHORIZED_ADB' 'PASS' 'Exactly one SM-G780G authorized, existing test app retained.'
    Get-Artifact
    Add-Result 'APK_SHA256_VERIFIED' 'PASS' 'Exact published CI build hash matches pinned SHA256.'
    Verify-WordAssets $apk
    Add-Result 'APK_5119_WORDS_VOSK' 'PASS' '5,119 unique EN/ID entries; RU/EN bundled model archives present.'
    Install-QaSideBySide
    Add-Result 'ISOLATED_QA_INSTALL' 'PASS' 'Installed com.example.english1000.s20qa side by side, without replacing other apps.'

    Run-Case 'QA_LAUNCH' { Start-Qa } 'Isolated QA app opens on physical S20 FE.'
    Run-Case 'QA_HOME_MODES' {
        Assert-Screen -Needles @('Изучать','Learn words') -Name 'LEARN'
        Assert-Screen -Needles @('Проверять','Check words') -Name 'CHECK'
        Assert-Screen -Needles @('Спроси слово','Ask a word') -Name 'ASK'
    } 'All three main menu modes visible.'
    Run-Case 'QA_ASK_WORD' {
        Tap-Control -Needles @('Спроси слово','Ask a word')
        Assert-Screen -Needles @('Добавлять примеры','Include usage examples') -Name 'ASK_OPTIONS'
        Tap-Control -Needles @('Начать','Start') -MaxScroll 0
        Assert-Screen -Needles @('СПРОСИ СЛОВО','ASK A WORD') -Name 'ASK_STARTED'
        $null = Invoke-Adb -Arguments @('shell','input','keyevent','4')
        Start-Sleep -Seconds 1
    } 'Ask Word mode launches with examples options.'
    Run-Case 'QA_LEARNING_MODE' {
        Tap-Control -Needles @('Изучать','Learn words')
        Assert-Screen -Needles @('Добавлять примеры','Include usage examples') -Name 'LEARN_OPTIONS'
        Tap-Control -Needles @('Начать','Start') 0
        Need (Visible-Contains -Needles @('Пауза','Pause listening')) 'LEARNING_PAUSE_MISSING'
    } 'Learning mode starts and exposes pause control.'
    Run-Case 'QA_CHECK_MODE' {
        Tap-Control -Needles @('Проверять','Check words')
        Need (Visible-Contains -Needles @('Не знаю',"Don't know")) 'CHECK_CONTROL_NOT_VISIBLE'
    } 'Manual answer control available.'
    Run-Case 'QA_FORCE_STOP_RECOVERY' {
        $null = Invoke-Adb -Arguments @('shell','am','force-stop',$ExpectedPackage)
        Start-Qa
        Need (Visible-Contains -Needles @('Не знаю',"Don't know")) 'CHECK_SESSION_NOT_RESTORED'
    } 'Current check session survives force-stop and restart.'
} catch {
    Add-Result 'SETUP_OR_INSTALL' 'BLOCKED' 'Candidate verification, device, isolated install or prerequisite blocked.'
} finally {
    # The following are outside this deterministic UI smoke sub-suite.
    # Never claim microphone/camera acceptance without physical evidence.
    foreach ($id in @(
        'FULL_50_WORD_BLOCK_AND_ERROR_LOOP',
        'EN_RU_REAL_MICROPHONE_ACOUSTIC',
        'EN_RU_OFFLINE_TTS_AND_SPEAKER',
        'AIRPLANE_MODE_OFFLINE_EN_RU',
        'THEMES_FONT_SETTINGS_REMINDERS',
        '30_MIN_ENDURANCE_AND_DATA_INTEGRITY',
        'USB_CAMERA_PHYSICAL_CORROBORATION',
        'GALAXY_FOLD_OWNER_REGRESSION'
    )) { Add-Result $id 'NOT_RUN' 'Additional physical scenario and explicit hardware approval required.' }
    $all = @($Results)
    $failed = @($all | Where-Object { $_.status -eq 'FAIL' }).Count
    $blocked = @($all | Where-Object { $_.status -in @('BLOCKED','NOT_RUN') }).Count
    $status = if ($failed -gt 0) { 'FAIL' } elseif ($blocked -gt 0) { 'BLOCKED' } else { 'PASS' }
    $summary = [pscustomobject]@{
        suite_id = 'englishwords-s20-full'
        status = $status
        executed_at_utc = $StartedUtc
        candidate_commit = 'b1afa6a94e22830a6e7cc1570a21877904f35b60'
        ci_run = [int64]43087170540
        package_id = $ExpectedPackage
        expected_dictionary_count = 5119
        isolated_qa_installed = $installedQa
        cases = $all
        passed_count = @($all | Where-Object { $_.status -eq 'PASS' }).Count
        failed_count = $failed
        blocked_or_not_run_count = $blocked
        overall_release_acceptance = 'NOT_CLAIMED'
    }
    $parent = Split-Path -Parent $OutputJson
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $OutputJson -Encoding UTF8
    Write-Output ('S20_QA_VERDICT=' + $status + ' PASS=' + $summary.passed_count +
        ' FAIL=' + $failed + ' BLOCKED_OR_NOT_RUN=' + $blocked)
}
exit 0
