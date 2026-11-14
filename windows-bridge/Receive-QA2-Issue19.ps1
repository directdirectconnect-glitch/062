#Requires -Version 5.1
param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$repo = 'directdirectconnect-glitch/062'
$endpoint = "repos/$repo/issues/19/comments"
$owner = 'directdirectconnect-glitch'
$root = 'C:\EnglishWords-Windows-Lab\results\qa2-1000'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$adb = 'C:\platform-tools\adb.exe'
$serial = 'RF8T209LFMA'
$pkg = 'com.example.english1000.visual164qa2'
$inbox = Join-Path $root 'issue19-inbox'
New-Item -ItemType Directory -Force -Path $inbox | Out-Null
try {
    $lock = [IO.File]::Open((Join-Path $inbox 'poll.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] { exit 0 }
try {
    $raw = & $gh api --paginate "$endpoint" --jq '.[] | {id,body,user:.user.login,association:.author_association,created:.created_at}'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read issue #19 via GitHub CLI' }
    $comments = @($raw | ForEach-Object { ConvertFrom-Json -InputObject $_ })
    $requests = @($comments | Where-Object {
        $_.user -ceq $owner -and $_.association -in @('OWNER','MEMBER') -and
        ($_.body -ceq 'QA2 REQUEST STATUS V1' -or $_.body -ceq 'QA2 REQUEST RESUME V1' -or $_.body -ceq 'QA2 REQUEST HEALTH V1') -and [long]$_.id -gt 6874222810
    })
    if ($CheckOnly) {
        Write-Output "Issue #19 read OK. Eligible requests: $($requests.Count)"
        return
    }
    $run = Join-Path $root 'physical-43572281441'
    $statusPath = Join-Path $run 'status.json'
    $finalReceipt = Join-Path $inbox 'physical-43572281441-final.receipt'
    if ((Test-Path -LiteralPath $statusPath) -and !(Test-Path -LiteralPath $finalReceipt)) {
        $summary = Get-Content -LiteralPath $statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $recoverable = [int]$summary.completed -in @(5,10,15) -and [int]$summary.unique -eq ([int]$summary.completed * 50)
        $recoveryReceipt = Join-Path $inbox ("auto-recovery-block-" + [int]$summary.completed + ".receipt")
        $active = @(Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object CommandLine -Match 'Run-QA2-Physical\.py')
        if ($recoverable -and $active.Count -eq 0 -and !(Test-Path -LiteralPath $recoveryReceipt)) {
            $script = 'C:\EnglishWords-Windows-Lab\bridge\Run-QA2-Physical.py'
            $apk = 'C:\EnglishWords-Windows-Lab\results\s20fe-qa2\run-43572281441\app-debug.apk'
            if ((Get-FileHash -LiteralPath $script).Hash -ceq 'ADBC01183A4C9FA0482BEDDCFECF8DFCE5DFE09B6FEA7E1E089C419AA00FABB0' -and
                (Get-FileHash -LiteralPath $apk).Hash -ceq '8F87E5BDA23E7F861AF1306FE5D75105D413DF98A82AAB844E05097416FB9EB2') {
                $python = (Get-Command python.exe -CommandType Application | Select-Object -First 1).Source
                $spawn = Start-Process -FilePath $python -ArgumentList @('-u', $script, '1000', $run, $apk) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$recoveryReceipt.stdout" -RedirectStandardError "$recoveryReceipt.stderr"
                Set-Content -LiteralPath $recoveryReceipt -Value $spawn.Id -Encoding UTF8
                return
            }
        }
        if ($active.Count -gt 0) { $skipFinalPosting = $true }
        else {
        $summary = Get-Content -LiteralPath $statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $marker = 'QA2 FINAL PHYSICAL V1 run=43572281441'
        if ($comments | Where-Object { $_.user -ceq $owner -and ($_.body -split "\r?\n", 2)[0] -ceq $marker } | Select-Object -First 1) {
            Set-Content -LiteralPath $finalReceipt -Value 'already published' -Encoding UTF8
        } else {
            $count = [int]$summary.unique
            $blocks = [int]$summary.completed
            $images = @($summary.images).Count
            $wordsVerdict = if ($count -eq 1000 -and $blocks -eq 20) { 'PASS' } else { 'INCOMPLETE' }
            $body = "$marker`n`nPhysical words=$wordsVerdict | unique=$count/1000 | blocks=$blocks/20 | images=$images/164 | acoustic TTS/ASR=NOT_RUN.`n`nWindows logs: C:\EnglishWords-Windows-Lab\results\qa2-1000\physical-43572281441."
            $bodyFile = Join-Path $inbox 'physical-43572281441-final.md'
            [IO.File]::WriteAllText($bodyFile, $body, [Text.UTF8Encoding]::new($false))
            $posted = & $gh issue comment 19 -R $repo --body-file $bodyFile
            if ($LASTEXITCODE -ne 0) { throw 'Could not publish final physical result' }
            Set-Content -LiteralPath $finalReceipt -Value $posted -Encoding UTF8
        }
        } # do not abandon request processing when physical runner is active
    }
    foreach ($request in $requests) {
        $id = ([long]$request.id).ToString()
        $receipt = Join-Path $inbox "$id.receipt"
        if ((Test-Path -LiteralPath $receipt) -and (Get-Content -LiteralPath $receipt -Raw).Trim() -ne 'already published') { continue }
        $command = if ($request.body -ceq 'QA2 REQUEST RESUME V1') { 'RESUME' } elseif ($request.body -ceq 'QA2 REQUEST HEALTH V1') { 'HEALTH' } else { 'STATUS' }
        $marker = "QA2 RESPONSE $command V1 request=$id"
        if ($comments | Where-Object { $_.user -ceq $owner -and ($_.body -split "\r?\n", 2)[0] -ceq $marker } | Select-Object -First 1) {
            Set-Content -LiteralPath $receipt -Value 'verified existing response' -Encoding UTF8
            continue
        }
        if ($command -eq 'HEALTH') {
            # A read-only inventory: hardware presence does not constitute an acoustic/video PASS.
            $timestamp = [DateTime]::UtcNow.ToString('o')
            $av = @()
            try {
                $av = @(Get-PnpDevice -PresentOnly -ErrorAction Stop | Where-Object {
                    $_.Class -in @('Camera','Image','AudioEndpoint','MEDIA') -or $_.FriendlyName -match '(?i)webcamera|microphone|микрофон|realtek|speaker|динамик'
                } | Select-Object -First 32 Class,FriendlyName,Status | ForEach-Object {
                    [pscustomobject]@{ class = $_.Class; name = $_.FriendlyName; status = $_.Status }
                })
                $avState = 'INVENTORY_AVAILABLE'
            } catch { $avState = 'INVENTORY_BLOCKED' }
            $taskName = 'EnglishWords-WindowsHealth-1m'
            $taskState = 'NOT_INSTALLED'
            $taskLast = 'UNKNOWN'
            try {
                $scheduled = Get-ScheduledTask -TaskName $taskName -ErrorAction Stop
                $taskState = [string]$scheduled.State
                $taskInfo = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction Stop
                $taskLast = [string]$taskInfo.LastTaskResult
            } catch {}
            $adbState = 'NOT_AVAILABLE'
            try {
                $model = & $adb -s $serial shell getprop ro.product.model 2>$null
                if ($LASTEXITCODE -eq 0 -and ("$model").Trim() -eq 'SM-G780G') { $adbState = 'CONNECTED' }
            } catch {}
            $qaPath = Join-Path $root 'physical-43572281441\\status.json'
            $qaState = 'NO_STATUS_FILE'
            if (Test-Path -LiteralPath $qaPath) {
                try {
                    $q = Get-Content -LiteralPath $qaPath -Raw -Encoding UTF8 | ConvertFrom-Json
                    $qaState = "$($q.unique)/1000 unique, $($q.completed)/20 blocks"
                } catch { $qaState = 'STATUS_READ_ERROR' }
            }
            $avText = ($av | ConvertTo-Json -Compress -Depth 4)
            $healthBody = "$marker`n`nUTC=$timestamp | GitHub=PASS (reply via local gh) | ADB=$adbState | QA2=$qaState | Watchdog=$taskState | WatchdogLastResult=$taskLast | AV=$avState.`n`nPnP devices JSON (not an acoustic capture test):`n$avText`n`nTTS/ASR acoustic=NOT_RUN. No phone or Windows device settings changed."
            $healthFile = Join-Path $inbox "$id-response.md"
            [IO.File]::WriteAllText($healthFile, $healthBody, [Text.UTF8Encoding]::new($false))
            $posted = & $gh issue comment 19 -R $repo --body-file $healthFile
            if ($LASTEXITCODE -ne 0) { throw 'Could not publish QA2 HEALTH response' }
            Set-Content -LiteralPath $receipt -Value $posted -Encoding UTF8
            continue
        }
        $device = & $adb -s $serial shell getprop ro.product.model 2>$null
        $deviceState = if ($LASTEXITCODE -eq 0 -and "$device".Trim() -eq 'SM-G780G') { 'PASS' } else { 'NOT_AVAILABLE' }
        $installed = if ($deviceState -eq 'PASS') { & $adb -s $serial shell pm path --user 0 $pkg 2>$null } else { '' }
        $installState = if ("$installed" -match '^package:') { 'PRESENT' } else { 'UNCONFIRMED' }
        $run = Join-Path $root 'physical-43572281441'
        $words = Join-Path $run 'words.jsonl'
        $status = Join-Path $run 'status.json'
        $unique = 0
        $blocks = 0
        $verdict = 'NOT_RUN'
        if (Test-Path -LiteralPath $words) {
            $entries = @(Get-Content -LiteralPath $words -Encoding UTF8 | ForEach-Object {
                try { ConvertFrom-Json -InputObject $_ -ErrorAction Stop } catch {}
            })
            $unique = @($entries | Select-Object -ExpandProperty word_id -Unique).Count
            $blocks = @($entries | Group-Object block | Where-Object { @($_.Group | Where-Object { -not $_.repeat }).Count -eq 50 -and $_.Group[-1].repeat -and @($_.Group[-1].pending).Count -eq 0 }).Count
            $verdict = 'IN_PROGRESS'
        }
        if (Test-Path -LiteralPath $status) {
            $summary = Get-Content -LiteralPath $status -Raw -Encoding UTF8 | ConvertFrom-Json
            $unique = [int]$summary.unique
            $blocks = [int]$summary.completed
            $verdict = if ($unique -eq 1000 -and $blocks -eq 20) { 'PASS_PHYSICAL_WORDS' } else { 'INCOMPLETE' }
        }
        $dispatch = ''
        if ($command -eq 'RESUME') {
            $dispatch = ' Dispatch=BLOCKED.'
            $script = 'C:\EnglishWords-Windows-Lab\bridge\Run-QA2-Physical.py'
            $approvedRunnerHash = 'ADBC01183A4C9FA0482BEDDCFECF8DFCE5DFE09B6FEA7E1E089C419AA00FABB0'
            $apk = 'C:\EnglishWords-Windows-Lab\results\s20fe-qa2\run-43572281441\app-debug.apk'
            $approvedApkHash = '8F87E5BDA23E7F861AF1306FE5D75105D413DF98A82AAB844E05097416FB9EB2'
            $active = @(Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object CommandLine -Match 'Run-QA2-Physical\.py')
            if ($active.Count -gt 0) {
                $dispatch = ' Dispatch=ALREADY_RUNNING.'
            } elseif ($unique -eq 1000 -and $blocks -eq 20) {
                $dispatch = ' Dispatch=ALREADY_COMPLETE.'
            } elseif ($installState -eq 'PRESENT' -and $verdict -eq 'INCOMPLETE' -and
                      $blocks -gt 0 -and $unique -ge ($blocks * 50) -and $unique -le (($blocks + 1) * 50) -and
                      (Get-FileHash -LiteralPath $script).Hash -ceq $approvedRunnerHash -and
                      (Get-FileHash -LiteralPath $apk).Hash -ceq $approvedApkHash) {
                $python = (Get-Command python.exe -CommandType Application | Select-Object -First 1).Source
                $spawn = Start-Process -FilePath $python -ArgumentList @('-u', $script, '1000', $run, $apk) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $inbox "$id-runner.log") -RedirectStandardError (Join-Path $inbox "$id-runner-error.log")
                $dispatch = " Dispatch=STARTED pid=$($spawn.Id)."
            }
        }
        $body = "$marker`n`nADB=$deviceState | QA2=$installState | physical=$verdict | unique=$unique/1000 | blocks=$blocks/20.$dispatch"
        $bodyFile = Join-Path $inbox "$id-response.md"
        [IO.File]::WriteAllText($bodyFile, $body, [Text.UTF8Encoding]::new($false))
        $posted = & $gh issue comment 19 -R $repo --body-file $bodyFile
        if ($LASTEXITCODE -ne 0) { throw 'Could not publish QA2 response' }
        Set-Content -LiteralPath $receipt -Value $posted -Encoding UTF8
    }
} finally { $lock.Dispose() }
