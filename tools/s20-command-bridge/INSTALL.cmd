@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-S20-CommandAgent.ps1"
echo.
if errorlevel 1 (
    echo INSTALLATION_NOT_CONFIRMED - copy this error output to Codex.
) else (
    echo INSTALLATION_REQUESTED - check GitHub Issue 3 for seq=1.
)
pause
