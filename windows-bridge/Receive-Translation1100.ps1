#Requires -Version 5.1
# Translation 1100: allowlisted dispatch; no arbitrary GitHub commands.
param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$repo = 'directdirectconnect-glitch/062'
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
$root = 'C:\EnglishWords-Windows-Lab\results\dictionary-translation-remediation'
$out = Join-Path $root 'review-1100'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$lock = $null
try { $lock = [IO.File]::Open((Join-Path $out 'dispatch.lock'), 'OpenOrCreate', 'ReadWrite', 'None') } catch [IO.IOException] { exit 0 }
try {
  $raw = & $gh api --paginate "repos/$repo/issues/19/comments" --jq '.[] | {id,body,user:.user.login,association:.author_association}'
  if ($LASTEXITCODE -ne 0) { throw 'GitHub comments unavailable' }
  $comments = @($raw | ForEach-Object { ConvertFrom-Json $_ })
  $requests = @($comments | Where-Object { $_.user -ceq 'directdirectconnect-glitch' -and $_.association -in @('OWNER','MEMBER') -and $_.body -ceq 'WINDOWS TRANSLATION 1100 REQUEST START V1' })
  if ($CheckOnly) { Write-Output "Eligible translation requests: $($requests.Count)"; return }
  foreach ($req in $requests) {
    $receipt = Join-Path $out ("request-$($req.id).receipt")
    if (Test-Path $receipt) { continue }
    $marker = "WINDOWS_TRANSLATION_1100_"
    if (@($comments | Where-Object { $_.body -match '^WINDOWS_TRANSLATION_1100_(STARTED|BLOCKED|REPORT)' }).Count -gt 0) {
      Set-Content $receipt 'existing status; no duplicate dispatch'; continue
    }
    $source = Join-Path $root 'review-1494.jsonl'
    $summary = Join-Path $root 'summary.json'
    $runner = 'C:\EnglishWords-Windows-Lab\bridge\Verify-Translation1100.ps1'
    $reason = $null
    if (!(Test-Path $source) -or !(Test-Path $summary)) { $reason = 'Missing review-1494.jsonl or summary.json' }
    elseif (!(Test-Path $runner)) { $reason = 'Approved verifier Verify-Translation1100.ps1 not installed; cannot claim STARTED' }
    else {
      $ids = @(Get-Content $source -Encoding UTF8 | ForEach-Object { try { ConvertFrom-Json $_ } catch { throw 'Malformed review JSONL' } } | Where-Object { $_.status -ceq 'STILL_REVIEW' })
      if ($ids.Count -ne 1100) { $reason = "Expected 1100 STILL_REVIEW, found $($ids.Count)" }
    }
    if ($reason) {
      $body = "WINDOWS_TRANSLATION_1100_BLOCKED`nUTC=$([DateTime]::UtcNow.ToString('o'))`nrequest=$($req.id)`nreason=$reason`nsource=$source"
    } else {
      $stdout = Join-Path $out 'verifier.stdout.log'
      $stderr = Join-Path $out 'verifier.stderr.log'
      $args = @('-NoProfile','-NonInteractive','-ExecutionPolicy','RemoteSigned','-File',('"' + $runner + '"'))
      $p = Start-Process -FilePath 'powershell.exe' -ArgumentList $args -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
      $body = "WINDOWS_TRANSLATION_1100_STARTED`nUTC=$([DateTime]::UtcNow.ToString('o'))`nPID=$($p.Id)`ncommand=powershell.exe $($args -join ' ')`nlog=$stdout`nerror_log=$stderr`ncount=1100`nrequest=$($req.id)"
    }
    $bodyPath = Join-Path $out ("request-$($req.id)-response.md")
    [IO.File]::WriteAllText($bodyPath, $body, [Text.UTF8Encoding]::new($false))
    & $gh issue comment 19 -R $repo --body-file $bodyPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not post status to GitHub' }
    Set-Content $receipt $body -Encoding UTF8
  }
} finally { if ($lock) { $lock.Dispose() } }
