# S20 FE command queue (data, never code)

The project owner requested an autonomous, **allow-listed** command bridge between this connected GitHub repository and the locally approved Windows test agent.

## Endpoint

- Repository: `directdirectconnect-glitch/062`
- Branch: `automation/s20-commands`
- Queue: `s20/queue.json`
- Target: exactly `s20fe` / model `SM-G780G` / isolated Android package `com.example.english1000.s20test`
- Reports: [Issue #3](https://github.com/directdirectconnect-glitch/062/issues/3)
- Full release test matrix: [Issue #11](https://github.com/directdirectconnect-glitch/062/issues/11)

The Windows agent **polls the repository file** under its own read/write Github CLI credentials every 15 minutes. It never reads executable instructions from issues or from the command JSON. A local operator must first review/install the bundled fixed-action scripts; merely committing a queue file does not execute anything on Windows.

## Accepted queue schema

```json
{
  "schema_version": 1,
  "sequence": 1,
  "target": "s20fe",
  "operation": "inventory",
  "expires_utc": "2026-11-17T23:59:00Z"
}
```

Exactly five fields. No arguments, file paths, scripts, arbitrary URLs, shell instructions, or device selectors. The UTC expiry must be in the future and within the next seven days. `sequence` is strictly increasing; each value runs no more than once per local state (unless a local operator explicitly resets it). Result publishing is retried from local state on the next scheduled pass if necessary.

## Permitted operations

| Operation | Behavior | Validity |
| --- | --- | --- |
| `inventory` | Verify **exactly one** authorized ADB device, model SM-G780G, isolated test package present | Infrastructure only |
| `smoke` | Start only the already installed `EnglishWords-S20-Unattended` Windows task, verify fresh local result | Smoke only, not full acceptance |
| `full_regression` | Run **only** `C:\\EnglishWords-Windows-Lab\\Run-FullFunctionalSuite.ps1` after the local operator approves its exact SHA-256 hash in `approved-full-suite.sha256`. | `BLOCKED` until reviewed suite is installed; PASS requires manual coverage review |

This queue cannot remotely install APKs, delete data, record media, modify device settings, or execute arbitrary commands. Full functional coverage (including EN/RU real-microphone tests) requires a **separately locally reviewed test suite** with explicit permissions and an evidence-backed per-case report.

## To issue the next task

An authorized GitHub contributor updates only `s20/queue.json`, incrementing `sequence`, selecting one of the three operations, and setting a UTC expiry within seven days. The local agent will post a short allow-listed verdict to Issue #3. It does not send screenshots, microphone audio, camera video, full logs, phone serials, or private file paths.

## Stop / rollback

Disable the Windows task `EnglishWords-S20-CommandBridge`. The existing `EnglishWords-S20-Unattended`, `EnglishWords-S20-StatusPublisher` and old disabled `EnglishWords-S20-AutoTest` tasks are not modified by this bridge. No remote shell is ever opened.

## Verification

For this first integration, use `operation: inventory`, confirm a new Issue #3 comment `S20 CommandBridge v1: seq=1; ...`. Until such a post, deployment and end-to-end connectivity remain **unverified**. Do not claim Codex itself is connected: this is a fixed-scope local test bridge, not an interactive Codex session.
