# Read-only Windows runner for English Words image archive QA.
# Example:
# powershell -NoProfile -ExecutionPolicy Bypass -File tools/image_factory/Run-ImageAudit.ps1 -CuratedDirectory "C:\EnglishWords-Windows-Lab\images\curated53" -ManifestPath "C:\EnglishWords-Windows-Lab\assets\asset_manifest.json" -AssetDirectory "C:\EnglishWords-Windows-Lab\assets\webp"
param(
  [Parameter(Mandatory=$true)][string]$CuratedDirectory,
  [string]$FullArchiveDirectory,
  [string]$ManifestPath,
  [string]$AssetDirectory,
  [string]$ResultsDirectory = "C:\EnglishWords-Windows-Lab\results",
  [string]$Python = "python",
  [int]$ExpectedCuratedFiles = 53
)
$ErrorActionPreference = "Stop"
$runId = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
New-Item -ItemType Directory -Force -Path $ResultsDirectory | Out-Null
$log = Join-Path $ResultsDirectory "image-audit-$runId.log"
$summary = [ordered]@{ utc=$runId; curated="NOT_RUN"; full="NOT_RUN"; manifest="NOT_RUN"; curated_count=0; errors=@(); reports=@() }
function Log([string]$message) {
  $line = "$([DateTime]::UtcNow.ToString('o')) $message"
  Add-Content -LiteralPath $log -Value $line
  Write-Output $line
}
function Run-Check([string]$label,[string[]]$arguments,[string]$reportPath) {
  Log "START $label"
  & $Python @arguments 2>&1 | ForEach-Object { Log "$_" }
  if ($LASTEXITCODE -ne 0) { throw "$label failed: exit code $LASTEXITCODE" }
  if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) { throw "$label did not produce report" }
  $script:summary.reports += $reportPath
  Log "PASS $label -> $reportPath"
}
try {
  & $Python -c "import PIL; print(PIL.__version__)" 2>&1 | ForEach-Object { Log "Python/Pillow $_" }
  if ($LASTEXITCODE -ne 0) { throw "Python/Pillow unavailable" }
  if (-not (Test-Path -LiteralPath $CuratedDirectory -PathType Container)) { throw "Curated directory missing: $CuratedDirectory" }
  $files = @(Get-ChildItem -LiteralPath $CuratedDirectory -File -Recurse | Where-Object { $_.Extension.ToLowerInvariant() -in @(".png",".jpg",".jpeg",".webp") })
  $summary.curated_count = $files.Count
  Log "Curated files found: $($files.Count); expected $ExpectedCuratedFiles"
  if ($files.Count -ne $ExpectedCuratedFiles) { throw "Curated file count mismatch; refusing incomplete audit" }
  $out = Join-Path $ResultsDirectory "image-audit-curated53-$runId.json"
  Run-Check "curated" @("tools/image_factory/audit.py",$CuratedDirectory,"--output",$out,"--threshold","5","--minimum","768") $out
  $audit = Get-Content -LiteralPath $out -Raw | ConvertFrom-Json
  if ($audit.summary.scanned -ne $ExpectedCuratedFiles -or $audit.summary.rejected -gt 0 -or $audit.summary.exact_duplicate_files -gt 0 -or $audit.summary.possible_duplicate_pairs -gt 0) {
    $summary.curated = "BLOCKED"
    throw "Curated archive failed quality gate; inspect $out"
  }
  $summary.curated = "PASS"
  if ($FullArchiveDirectory) {
    if (-not (Test-Path -LiteralPath $FullArchiveDirectory -PathType Container)) { throw "Full archive directory missing" }
    $out = Join-Path $ResultsDirectory "image-audit-full-$runId.json"
    Run-Check "full" @("tools/image_factory/audit.py",$FullArchiveDirectory,"--output",$out,"--threshold","5","--minimum","768") $out
    $fullAudit = Get-Content -LiteralPath $out -Raw | ConvertFrom-Json
    if ($fullAudit.summary.rejected -gt 0 -or $fullAudit.summary.exact_duplicate_files -gt 0 -or $fullAudit.summary.possible_duplicate_pairs -gt 0) {
      $summary.full = "BLOCKED"
      throw "Full archive failed quality gate; inspect $out"
    }
    $summary.full = "PASS"
  }
  if ($ManifestPath -and $AssetDirectory) {
    $out = Join-Path $ResultsDirectory "image-manifest-$runId.json"
    Run-Check "manifest" @("tools/image_factory/verify_manifest.py",$ManifestPath,$AssetDirectory,"--output",$out) $out
    $verification = Get-Content -LiteralPath $out -Raw | ConvertFrom-Json
    $summary.manifest = [string]$verification.status
    if ($summary.manifest -ne "PASS") { throw "Manifest verification BLOCKED; inspect $out" }
  }
} catch {
  $summary.errors += "$_"
  Log "FAIL $_"
} finally {
  $summaryPath = Join-Path $ResultsDirectory "image-audit-summary-$runId.json"
  $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
  Log "SUMMARY $summaryPath"
  Get-FileHash -Algorithm SHA256 -LiteralPath $summaryPath | ForEach-Object { Log "SUMMARY_SHA256 $($_.Hash)" }
}
if ($summary.errors.Count -gt 0) { exit 1 }
