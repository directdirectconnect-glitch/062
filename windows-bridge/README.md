# English Words — GitHub → Windows → ADB bridge

## Scope
This bridge closes a previously missing delivery link for [issue #19](https://github.com/directdirectconnect-glitch/062/issues/19), but **is not itself a full 1,000-word regression**. It checks whether the Windows worker can see GitHub and the physical S20 FE, comments with receipt and calls only a pre-approved existing local .ps1 regression runner inside the lab folder. It never executes GitHub issue body text. It does not install or delete APKs, tap UI, or claim acoustic/physical tests passed.

## One-time Windows setup
Prerequisites: the Windows PC is on; Samsung S20 FE connected by USB and authorized for debugging; `gh` authenticated with permission to read/write GitHub issues for `directdirectconnect-glitch/062`; `adb` available in PATH; PowerShell 5.1+. Do not put tokens in scripts.

From an already cloned checkout of this repository, update checkout to the intended branch and copy the bridge script from `windows-bridge\Invoke-Issue19.ps1` to `C:\EnglishWords-Windows-Lab\bridge\Invoke-Issue19.ps1` (make folder if absent). Copying is intentional: scheduled execution is from a stable local file.

Manual preflight, no phone changes:

```powershell
powershell.exe -NoProfile -File 'C:\EnglishWords-Windows-Lab\bridge\Invoke-Issue19.ps1' -NoPost
```

Review the latest log in `C:\EnglishWords-Windows-Lab\results\github-bridge-19\`. The run must show `CONNECTED` with Galaxy S20 FE model. Absence of a local approved runner is a **BLOCKED** status and is not an ADB failure.

## Explicitly approve a local test runner
Only after inspecting a real, locally installed regression entry point can the operator configure its fully qualified .ps1 path within the lab folder. Never guess the path from a GitHub issue. Example placeholder:

```powershell
powershell.exe -NoProfile -File 'C:\EnglishWords-Windows-Lab\bridge\Invoke-Issue19.ps1' -ApprovedRunner 'C:\EnglishWords-Windows-Lab\REPLACE-WITH-VERIFIED-LOCAL-QA-RUNNER.ps1'
```

The runner itself is responsible for APK sha/signature, safe package `com.example.english1000.visual164qa`, 20×50 unique word IDs, on-device screenshots and checkpoints. Bridge creates `attempted-approved-runner.txt` as a deliberate once-only guard; do not delete it to rerun without inspecting results and verifying safe resume semantics.

## Schedule the bridge (requires a single authorized action on Windows)
For an automatic heartbeat without executing regression yet, register a per-user scheduled task that only calls this fixed preflight and checks issue #19:

```powershell
$script = 'C:\EnglishWords-Windows-Lab\bridge\Invoke-Issue19.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoLogo -NoProfile -File "' + $script + '" -NoPost')
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 5) -RepetitionDuration (New-TimeSpan -Days 365)
Register-ScheduledTask -TaskName 'EnglishWords-Issue19-Bridge' -Action $action -Trigger $trigger -Description 'Check GitHub issue 19 and S20 FE ADB; no phone mutation' -Force
```

This heartbeat stays read-only and does not post every five minutes. **To launch tests autonomously** the task action must be changed once, by the computer operator, to include `-ApprovedRunner 'verified path'` and remove `-NoPost` after they have verified the local script and package-safety controls. Do not enable unattended execution on a shared Windows user without considering account and secret exposure.

## Verify
1. `Get-ScheduledTask -TaskName EnglishWords-Issue19-Bridge` shows Ready/Running.
2. `Get-ScheduledTaskInfo -TaskName EnglishWords-Issue19-Bridge` shows a last run time, task result and upcoming run.
3. Read `results\github-bridge-19\bridge-*.log` and validate the model and serial.
4. Once write mode is enabled, issue #19 should have an ACK comment reporting ADB and that the approved runner was invoked. Do not interpret this as completion of 1,000 words.
5. Only the runner's own physical CSV/logcat/screenshots can establish PASS/FAIL/NOT_RUN and 250/500/750/1000 milestones.

## Known bottleneck
The existing GitHub repository contains a test plan but not a confirmed Windows-native, fully automated 20×50 UI regression implementation. The one-time missing piece is identifying or developing an approved **local runner**. Neither ChatGPT GitHub connector nor a Google Doc can start a Windows executable by themselves.

## Recovery and safety
Logs: `C:\EnglishWords-Windows-Lab\results\github-bridge-19\`. Bridge uses a file lock to prevent concurrent instances and a marker to guard against duplicate regression runs.
Do not remove user apps, clear app data, fake audio verification, or count repeat word IDs as new. If connected device is not S20 FE, stop. The bridge's issue scope is hardcoded to exactly repository `directdirectconnect-glitch/062`, issue #19.
