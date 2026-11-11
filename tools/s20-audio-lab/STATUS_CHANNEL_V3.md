# S20 status publisher v3.1 -- local activation and acceptance

## Scope

The Codex-repaired Windows task has been reported as passing one manual launch smoke test. Its JSON may use `status: PASSED`, while the older wrapper uses `lab_status`. Both are recognized. `PASSED` maps to **SMOKE_PASS_ONLY**, never to 20/20 functional or speech acceptance.

This change adds an **independent low-privilege publisher task**. It does not overwrite the Codex-repaired AutoLab task, invoke ADB, uninstall an APK, or modify production Galaxy Fold installs. Raw diagnostic records remain local.

## One-time local activation by Codex (after user approval)

1. Fetch/review `Publish-SafeStatus.ps1` and `Install-StatusPublisher.ps1` from this PR branch.
2. Confirm `gh auth status` works for `directdirectconnect-glitch/062`; do not print or transmit tokens.
3. Verify a dry run: `powershell.exe -NoProfile -File .\Publish-SafeStatus.ps1 -DryRun`.
4. Install the new `EnglishWords-S20-StatusPublisher` task every 15 minutes using `Install-StatusPublisher.ps1`. Existing test tasks remain unchanged.
5. Validate Task Scheduler and a new Issue #3 comment, then observe a **second schedule-triggered run without manual invocation** and record its independent LastRunTime and status file timestamp.
6. Classify the scheduled-trigger gate as unverified until Step 5 completes.

## Status policy

- `lab_status: PASS` -> `LAB_PASS_UNVERIFIED_COVERAGE`. Explicit 20-test coverage must be separately validated.
- `status: PASSED` -> `SMOKE_PASS_ONLY`.
- No state -> `MISSING_STATE`; state age above 45 minutes -> `STALE_STATE`; invalid JSON -> `INVALID_STATE`.
- Only allow-listed fixed verdict tokens are sent to Issue #3. Never upload screenshots, device IDs, file paths, commands, logs, microphone WAVs, transcripts, camera frames, or raw error messages.
- Report `scheduled_trigger=UNVERIFIED` until verified. Exit code zero cannot distinguish manual from scheduled invocation.
- Deduplicate unchanged daily messages but report status changes immediately.

## Remaining work

Functional 20-test suite, new APK installation, EN/RU speech recognition, and calibrated physical camera checks. None follows from smoke success.
