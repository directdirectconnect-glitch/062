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
    $requests = @($raw | ForEach-Object { ConvertFrom-Json -InputObject $_ } | Where-Object {
        $_.user -ceq $owner -and $_.association -in @('OWNER','MEMBER') -and
        ($_.body -ceq 'QA2 REQUEST STATUS V1' -or $_.body -ceq 'QA2 REQUEST RESUME V1') -and [long]$_.id -gt 6874222810
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
            if ((Get-FileHash -LiteralPath $script).Hash -ceq '3D08DD8B4283D7BC9C9D1B229FB811D643A108698D3E5E9F5AD52044C17FCC06' -and
                (Get-FileHash -LiteralPath $apk).Hash -ceq '8F87E5BDA23E7F861AF1306FE5D75105D413DF98A82AAB844E05097416FB9EB2') {
                $python = (Get-Command python.exe -CommandType Application | Select-Object -First 1).Source
                $spawn = Start-Process -FilePath $python -ArgumentList @('-u', $script, '1000', $run, $apk) -WindowStyle Hidden -PassThru -RedirectStandardOutput "$recoveryReceipt.stdout" -RedirectStandardError "$recoveryReceipt.stderr"
                Set-Content -LiteralPath $recoveryReceipt -Value $spawn.Id -Encoding UTF8
                return
            }
        }
        if ($active.Count -gt 0) { return }
        $summary = Get-Content -LiteralPath $statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $marker = 'QA2 FINAL PHYSICAL V1 run=43572281441'
        if ($raw | Select-String -SimpleMatch $marker -Quiet) {
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
    }
    foreach ($request in $requests) {
        $id = ([long]$request.id).ToString()
        $receipt = Join-Path $inbox "$id.receipt"
        if (Test-Path -LiteralPath $receipt) { continue }
        $command = if ($request.body -ceq 'QA2 REQUEST RESUME V1') { 'RESUME' } else { 'STATUS' }
        $marker = "QA2 RESPONSE $command V1 request=$id"
        if ($raw | Select-String -SimpleMatch $marker -Quiet) {
            Set-Content -LiteralPath $receipt -Value 'already published' -Encoding UTF8
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
            $approvedRunnerHash = '3D08DD8B4283D7BC9C9D1B229FB811D643A108698D3E5E9F5AD52044C17FCC06'
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
