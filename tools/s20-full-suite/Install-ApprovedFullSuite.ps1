#requires -version 5.1
<#
One-time LOCAL approval of the reviewed full S20 physical QA script.
Will not be called from the remote GitHub queue or by CommandBridge.
Never overwrites the existing S20 app or changes scheduled tasks.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$ApprovedSha256
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$source = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'Run-FullFunctionalSuite.ps1'
$destination = 'C:\EnglishWords-Windows-Lab\Run-FullFunctionalSuite.ps1'
$approvedFile = Join-Path $env:LOCALAPPDATA 'EnglishWords-S20-CommandAgent\approved-full-suite.sha256'
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw 'Missing source script' }
if (-not (Test-Path -LiteralPath 'C:\EnglishWords-Windows-Lab' -PathType Container)) {
    throw 'Expected Windows Lab folder is missing. No files changed.'
}
if (-not (Get-ScheduledTask -TaskName 'EnglishWords-S20-CommandBridge' -ErrorAction SilentlyContinue)) {
    throw 'CommandBridge not installed. No files changed.'
}
$sha = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
if ($sha -ine $ApprovedSha256) { throw 'Approved hash does not match inspected source. No files changed.' }
if (Test-Path -LiteralPath $destination -PathType Leaf) {
    $backup = $destination + '.backup'
    if (Test-Path -LiteralPath $backup -PathType Leaf) {
        throw 'One local backup already exists. Manual inspection required to avoid overwrite.'
    }
    Copy-Item -LiteralPath $destination -Destination $backup
}
Copy-Item -LiteralPath $source -Destination $destination -Force
$installedSha = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
if ($installedSha -ine $sha) { throw 'Installed file differs from approved source' }
$dir = Split-Path -Parent $approvedFile
if (-not (Test-Path -LiteralPath $dir -PathType Container)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$sha | Set-Content -LiteralPath $approvedFile -Encoding ASCII
Write-Output 'FULL_SUITE_LOCALLY_APPROVED'
Write-Output 'Queue number 3 was already consumed and blocked. A NEW incremented queue job is needed.'
Write-Output 'Installed script is pinned; changing its contents blocks all future executions.'
