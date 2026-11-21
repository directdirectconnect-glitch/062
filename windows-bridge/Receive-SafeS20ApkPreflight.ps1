# Separate, read-only Issue #19 APK preflight inbox. Never edits existing QA2 handler.
# A deployment administrator must pin exact verified APK metadata below before enabling.
[CmdletBinding()]
param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
$repo='directdirectconnect-glitch/062'
$owner='directdirectconnect-glitch'
$approvedRun=44902725944
$approvedSha=''       # REQUIRED: verified app-debug.apk SHA256 (not ZIP artifact digest)
$approvedPackage=''   # REQUIRED: verified APK package from aapt
$helper='C:\EnglishWords-Windows-Lab\bridge\Invoke-SafeS20Apk.ps1'
$gh='C:\Program Files\GitHub CLI\gh.exe'
if ($approvedSha -notmatch '^[0-9a-fA-F]{64}$' -or $approvedPackage -notmatch '^com\.example\.english1000(\.[A-Za-z0-9_]+)*$') {
  throw 'BLOCKED: verified APK metadata has not been pinned; do not enable task'
}
if (!(Test-Path -LiteralPath $helper)) { throw 'BLOCKED: helper not installed' }
$dir='C:\EnglishWords-Windows-Lab\results\apk-preflight-inbox'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$lock=[IO.File]::Open((Join-Path $dir 'poll.lock'),'OpenOrCreate','ReadWrite','None')
try {
  $raw=@(& $gh api --paginate "repos/$repo/issues/19/comments" --jq '.[] | {id,body,user:.user.login,association:.author_association}')
  if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: GitHub API unavailable' }
  $comments=@($raw | ForEach-Object { $_ | ConvertFrom-Json })
  $exact="APK REQUEST PREFLIGHT V1 run=$approvedRun sha=$($approvedSha.ToLowerInvariant()) package=$approvedPackage"
  $requests=@($comments | Where-Object { $_.user -ceq $owner -and $_.association -in @('OWNER','MEMBER') -and $_.body -ceq $exact })
  if ($CheckOnly) { Write-Output "PRECHECK OK | matching requests=$($requests.Count) | install command not supported"; return }
  foreach ($request in $requests) {
    $id=[long]$request.id
    $receipt=Join-Path $dir "$id.receipt"
    if (Test-Path -LiteralPath $receipt) { continue }
    $response=@()
    try {
      $response=@(& powershell.exe -NoProfile -NonInteractive -File $helper -ExpectedSha256 $approvedSha -ExpectedPackage $approvedPackage -Mode Preflight 2>&1 | Out-String)
      $outcome=if ($LASTEXITCODE -eq 0) { 'PASS' } else { 'BLOCKED' }
    } catch { $outcome='BLOCKED'; $response=@($_.Exception.Message) }
    $body="APK RESPONSE PREFLIGHT V1 request=$id`n`nOutcome=$outcome`n$($response -join "`n")`nInstallation=NOT_RUN."
    $bodyFile=Join-Path $dir "$id-response.md"
    [IO.File]::WriteAllText($bodyFile,$body,[Text.UTF8Encoding]::new($false))
    & $gh issue comment 19 -R $repo --body-file $bodyFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: could not publish response' }
    Set-Content -LiteralPath $receipt -Value $outcome -Encoding UTF8
  }
} finally { $lock.Dispose() }
