#requires -version 5.1
<#
English Words S20 FE command bridge, v1.
The only remote input is a schema-checked, argument-free, allow-listed
operation from one GitHub repository branch. No remote code or shell.
Local reports never include raw phone data, paths, serials, or media.
#>
[CmdletBinding()]
param([switch]$DryRun)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Repo = 'directdirectconnect-glitch/062'
$QueueBranch = 'automation/s20-commands'
$QueuePath = 's20/queue.json'
$Root = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-CommandAgent'
$StatePath = Join-Path $Root 'state.json'
$LogPath = Join-Path $Root 'agent.log'
$LockPath = Join-Path $Root 'bridge.lock'
$TargetPackage = 'com.example.english1000.s20test'
$Allowed = @('inventory', 'smoke', 'full_regression')

function Write-Log([string]$Message) {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        New-Item -ItemType Directory -Path $Root -Force | Out-Null
    }
    $line = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ') + ' ' + $Message
    Add-Content -LiteralPath $LogPath -Value $line -Encoding ASCII
}
function Read-Prop([object]$Obj, [string]$Name) {
    if ($null -eq $Obj) { return '' }
    $prop = $Obj.PSObject.Properties[$Name]
    if ($null -eq $prop -or $null -eq $prop.Value) { return '' }
    return [string]$prop.Value
}
function Write-State([object]$Obj) {
    $tmp = $StatePath + '.tmp'
    $Obj | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $tmp -Encoding UTF8
    Move-Item -LiteralPath $tmp -Destination $StatePath -Force
}
function Read-State {
    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        return [pscustomobject]@{ last_sequence = 0; pending = $false; report = '' }
    }
    $obj = Get-Content -LiteralPath $StatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $obj.PSObject.Properties['last_sequence'] -or
        -not $obj.PSObject.Properties['pending'] -or
        -not $obj.PSObject.Properties['report']) { throw 'Invalid local state schema' }
    return $obj
}
function Find-Gh {
    $gh = Get-Command gh.exe -ErrorAction SilentlyContinue
    if ($gh) { return $gh.Source }
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($gh) { return $gh.Source }
    throw 'GITHUB_CLI_NOT_AVAILABLE'
}
function Load-Queue([string]$Gh) {
    $branch = [uri]::EscapeDataString($QueueBranch)
    $path = "repos/$Repo/contents/$QueuePath" + '?ref=' + $branch
    $payload = & $Gh api --method GET $path 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'QUEUE_FETCH_FAILED' }
    $wrapper = ($payload -join [Environment]::NewLine) | ConvertFrom-Json
    if ([int]$wrapper.size -gt 4096) { throw 'QUEUE_TOO_LARGE' }
    $raw = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((([string]$wrapper.content) -replace '\s', '')))
    $job = $raw | ConvertFrom-Json
    if (@($job.PSObject.Properties).Count -ne 5) { throw 'UNEXPECTED_QUEUE_FIELDS' }
    foreach ($name in @('schema_version','sequence','target','operation','expires_utc')) {
        if (-not $job.PSObject.Properties[$name]) { throw 'INVALID_QUEUE_SCHEMA' }
    }
    $schemaNumber = ($job.schema_version -is [int]) -or ($job.schema_version -is [long])
    $sequenceNumber = ($job.sequence -is [int]) -or ($job.sequence -is [long])
    if (-not $schemaNumber -or [long]$job.schema_version -ne 1 -or
        -not $sequenceNumber -or [long]$job.sequence -lt 1 -or
        $job.sequence -gt 2147483647 -or
        [string]$job.target -cne 's20fe' -or
        $Allowed -cnotcontains [string]$job.operation) {
        throw 'INVALID_OR_UNAPPROVED_OPERATION'
    }
    $deadline = [datetimeoffset]::MinValue
    if (-not [datetimeoffset]::TryParse([string]$job.expires_utc, [ref]$deadline)) {
        throw 'INVALID_DEADLINE'
    }
    $now = [datetimeoffset]::UtcNow
    if ($deadline -le $now -or $deadline -gt $now.AddDays(7)) {
        throw 'EXPIRED_OR_OVERLONG_DEADLINE'
    }
    return $job
}
function Find-Adb {
    $fixed = 'C:\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $fixed -PathType Leaf) { return $fixed }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'ADB_NOT_INSTALLED'
}
function Confirm-S20 {
    $adb = Find-Adb
    $list = @(& $adb devices 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_ENUMERATION_FAILED' }
    $devices = @($list | Where-Object { $_ -match '^\S+\s+device\s*$' })
    if ($devices.Count -ne 1) { throw 'EXPECTED_ONE_AUTHORIZED_DEVICE' }
    $serial = (($devices[0] -split '\s+')[0]).Trim()
    $model = ((& $adb -s $serial shell getprop ro.product.model 2>$null) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $model -cne 'SM-G780G') { throw 'WRONG_DEVICE_MODEL' }
    $packages = @(& $adb -s $serial shell pm path $TargetPackage 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not ($packages -match '^package:')) {
        throw 'ISOLATED_TEST_APP_MISSING'
    }
    return [pscustomobject]@{ adb = $adb; serial = $serial }
}
function Invoke-Smoke {
    $taskName = 'EnglishWords-S20-Unattended'
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction Stop
    if ($task.State -ne 'Ready') { throw 'TEST_TASK_NOT_READY' }
    $before = Get-Date
    Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
    $deadline = (Get-Date).AddSeconds(150)
    $finished = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 4
        $info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction Stop
        $state = (Get-ScheduledTask -TaskName $taskName).State
        if ($info.LastRunTime -ge $before.AddSeconds(-3) -and $state -eq 'Ready') {
            if ($info.LastTaskResult -ne 0) { throw 'TEST_TASK_NONZERO_EXIT' }
            $finished = $true
            break
        }
    }
    if (-not $finished) { throw 'SMOKE_TASK_TIMEOUT_OR_NO_RUN' }
    $report = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-Unattended\unattended-last.json'
    if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw 'SMOKE_REPORT_MISSING' }
    $info = Get-Item -LiteralPath $report
    if ($info.LastWriteTime -lt $before.AddSeconds(-3)) { throw 'SMOKE_REPORT_STALE' }
    $verdict = Get-Content -LiteralPath $report -Encoding UTF8 -Raw | ConvertFrom-Json
    $result = Read-Prop $verdict 'status'
    if (-not $result) { $result = Read-Prop $verdict 'lab_status' }
    if (@('PASS','PASSED') -notcontains $result.ToUpperInvariant()) { throw 'SMOKE_NOT_PASSED' }
    return 'SMOKE_PASS_ONLY'
}
function Invoke-FullRegression {
    # A future local Codex-reviewed suite is allowed at this fixed path only.
    # Approval is a local hash file, never a GitHub issue/comment or remote string.
    $suite = 'C:\EnglishWords-Windows-Lab\Run-FullFunctionalSuite.ps1'
    $approval = Join-Path $Root 'approved-full-suite.sha256'
    if (-not (Test-Path -LiteralPath $suite -PathType Leaf) -or
        -not (Test-Path -LiteralPath $approval -PathType Leaf)) {
        return 'BLOCKED_APPROVED_SUITE_MISSING'
    }
    $expected = (Get-Content -LiteralPath $approval -Raw -Encoding ASCII).Trim()
    if ($expected -notmatch '^[A-Fa-f0-9]{64}$') { return 'BLOCKED_SUITE_NOT_APPROVED' }
    $actual = (Get-FileHash -LiteralPath $suite -Algorithm SHA256).Hash
    if ($actual -ine $expected) { return 'BLOCKED_SUITE_HASH_CHANGED' }
    $outPath = Join-Path $Root 'last-full-regression.json'
    $ps = Join-Path $PSHOME 'powershell.exe'
    $started = Get-Date
    & $ps -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $suite -OutputJson $outPath *> $null
    if ($LASTEXITCODE -ne 0) { return 'FULL_REGRESSION_FAILED' }
    if (-not (Test-Path -LiteralPath $outPath -PathType Leaf)) {
        return 'FULL_REGRESSION_REPORT_MISSING'
    }
    if ((Get-Item -LiteralPath $outPath).LastWriteTime -lt $started.AddSeconds(-2)) {
        return 'FULL_REGRESSION_REPORT_STALE'
    }
    $result = Get-Content -LiteralPath $outPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ((Read-Prop $result 'suite_id') -cne 'englishwords-s20-full') {
        return 'FULL_REGRESSION_INVALID_REPORT'
    }
    $status = (Read-Prop $result 'status').ToUpperInvariant()
    switch ($status) {
        'PASS' { return 'FULL_SUITE_PASS_REVIEW_REQUIRED' }
        'FAIL' { return 'FULL_SUITE_FAIL' }
        default { return 'FULL_SUITE_INCOMPLETE' }
    }
}
function Post-Report([string]$Gh, [string]$Report) {
    $safeFormat = '^S20 CommandBridge v1: seq=[0-9]+; operation=(inventory|smoke|full_regression); result=[A-Z_]+; device=SM-G780G; package=ISOLATED_S20TEST; scope=(FUNCTIONAL|INFRASTRUCTURE_ONLY)$'
    if ($Report -cnotmatch $safeFormat) { throw 'LOCAL_REPORT_FORMAT_NOT_ALLOWED' }
    & $Gh issue comment 3 -R $Repo --body $Report 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'REPORT_PUBLISH_FAILED' }
}

if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
    New-Item -ItemType Directory -Path $Root -Force | Out-Null
}
$lock = $null
try {
    $lock = [System.IO.File]::Open($LockPath, [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
    Write-Log 'BUSY_ANOTHER_AGENT_INSTANCE'
    exit 0
}
try {
    $state = Read-State
    $gh = Find-Gh
    if ([bool]$state.pending) {
        if (-not $DryRun) {
            Post-Report $gh ([string]$state.report)
            $state.pending = $false
            Write-State $state
            Write-Log 'PENDING_REPORT_PUBLISHED'
        } else { Write-Log 'DRY_RUN_PENDING_REPORT' }
    }
    $job = Load-Queue $gh
    $sequence = [int]$job.sequence
    if ($sequence -le [int]$state.last_sequence) {
        Write-Log 'NO_NEW_COMMAND'
        exit 0
    }
    $operation = [string]$job.operation
    if ($DryRun) {
        Write-Log ('DRY_RUN_COMMAND seq=' + $sequence + ' op=' + $operation)
        Write-Output ('DRY_RUN_ACCEPTED seq=' + $sequence + ' op=' + $operation)
        exit 0
    }
    $result = 'UNKNOWN'
    try {
        $null = Confirm-S20
        switch ($operation) {
            'inventory' { $result = 'INVENTORY_PASS' }
            'smoke' { $result = Invoke-Smoke }
            'full_regression' { $result = Invoke-FullRegression }
        }
    } catch {
        # Exception strings may include OS details. Map to enumerated categories only.
        $e = $_.Exception.Message
        $safe = @('ADB_NOT_INSTALLED','ADB_ENUMERATION_FAILED',
            'EXPECTED_ONE_AUTHORIZED_DEVICE','WRONG_DEVICE_MODEL',
            'ISOLATED_TEST_APP_MISSING','TEST_TASK_NOT_READY',
            'TEST_TASK_NONZERO_EXIT','SMOKE_TASK_TIMEOUT_OR_NO_RUN',
            'SMOKE_REPORT_MISSING','SMOKE_REPORT_STALE','SMOKE_NOT_PASSED')
        if ($safe -contains $e) { $result = 'BLOCKED_' + $e }
        else { $result = 'FAILED_LOCAL_CHECK' }
    }
    $message = 'S20 CommandBridge v1: seq=' + $sequence + '; operation=' + $operation +
        '; result=' + $result + '; device=SM-G780G; package=ISOLATED_S20TEST; ' +
        'scope=' + $(if ($operation -eq 'full_regression') { 'FUNCTIONAL' } else { 'INFRASTRUCTURE_ONLY' })
    $state.last_sequence = $sequence
    $state.pending = $true
    $state.report = $message
    Write-State $state
    Write-Log ('COMMAND_COMPLETED seq=' + $sequence + ' result=' + $result)
    Post-Report $gh $message
    $state.pending = $false
    Write-State $state
    Write-Log ('REPORT_PUBLISHED seq=' + $sequence)
    Write-Output $message
} catch {
    Write-Log 'AGENT_TRANSPORT_OR_LOCAL_STATE_ERROR'
    Write-Output 'AGENT_TRANSPORT_OR_LOCAL_STATE_ERROR'
    exit 1
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
