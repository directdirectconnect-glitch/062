# S20 AutoLab: always-on status channel (v3 proposal)

The installed 2026-11-11 AutoLab v2 persists **local** `unattended-last.json`, but its optional GitHub post is conditional on a fresh UI heartbeat or acoustic test. If the ADB stage, original task action, or another early phase fails, the failure stays local and the ChatGPT/GitHub view is stale.

This branch provides `Publish-SafeStatus.ps1`, a fixed-action companion intended to run *after each local agent attempt*, regardless of test outcome, through the **existing local Windows scheduled task**.

- Read only the local agent JSON, do not execute remote shell commands or retrieve scripts from issues.
- Publish **only fixed enumerated** status tokens and a YES/NO device verification bit to [Issue #3](https://github.com/directdirectconnect-glitch/062/issues/3).
- Do not upload phone serials, personal directories, exception messages, transcript, sound recordings, camera frames, screenshots, or unrestricted log lines.
- Deduplicate unchanged statuses locally to avoid flooding the issue.
- Never interpret `READY_TO_RUN`, `SKIPPED`, or `NO_NEW_REPORT` as a real handset PASS.
- Original Windows lab task stays disabled while unattended runner is enabled. Production `com.example.english1000` is untouched; the approved test application is `com.example.english1000.s20test`.

**Not deployed:** Creating this code in GitHub does **not** modify the user's Windows computer, which does not expose a remote control interface to ChatGPT. To activate the companion, a locally approved update to the fixed scheduled action is required. A GitHub build alone cannot remotely install it, and the CI/emulator cannot prove physical microphone performance.

Acceptance gate: confirm a local scheduled run with sanitized GitHub status, followed by a fresh 20-test physical S20 result and separate EN/RU acoustic test. Until then classify the remote test bridge as **unverified**.
