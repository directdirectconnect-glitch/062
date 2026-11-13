# English Words — Samsung Galaxy S20 FE / 1000-word physical regression

Date: 2026-11-13. Status: REQUESTED / NOT_RUN on physical device. This document does not claim device test completion.

## Immutable candidate
- Source branch: `test/0.9.8-5119-118-images-s20fe-fold`
- Source commit: `c23f2658f01789d7c0d1fc6cde2471b2c3d61c7e`
- GitHub Actions run: https://github.com/directdirectconnect-glitch/062/actions/runs/43416559130
- Artifact: `EnglishWords-5126-164-Visual-Test-APK` (artifact ID `14032171894`)
- Expected debug applicationId: `com.example.english1000.visual164qa`, base `com.example.english1000`, versionCode 99; verify via `aapt dump badging` before installation. Never confuse with `.s20qa` or `.s20test`.
- Dictionary expected: 5126 entries; offline visual manifest: 164 entries.

## Preflight / safety
1. Confirm exactly one authorized `adb devices -l` target, Samsung Galaxy S20 FE; record serial and model.
2. Download the exact artifact via `gh run download 43416559130 -R directdirectconnect-glitch/062 -n EnglishWords-5126-164-Visual-Test-APK -D <safe-dir>`. Verify APK with `apksigner verify`, `aapt dump badging`, and compute SHA-256. Do not confuse ZIP digest with APK SHA.
3. Check all installed matching packages. Do NOT uninstall, clear data, force-stop another installed package, or install over production/S20 QA package. If target `.visual164qa` is already installed, review its signature and data-preservation policy before any upgrade; stop rather than force reinstall.
4. Use existing approved Windows ADB server and writable `ANDROID_USER_HOME`. Save logs to `C:\EnglishWords-Windows-Lab\results\`.
5. Record original progress of candidate QA package if already installed; isolate test data from normal learner progress. Do not use `pm clear`.

## 1000 distinct words: functional test, NOT a 1000-word single lesson
- Run 20 consecutive learning/check blocks of 50 unique words each (1000 distinct dictionary IDs total); preserve the agreed 50-word UI block limit. Track block index, word IDs, English/Russian text, buttons/actions, and timestamps in CSV/JSONL.
- At the end of each block assert exactly 50 expected words encountered and no unintended repeated IDs. Across all 20 blocks assert exactly 1000 distinct word IDs; if app intentionally schedules repetition, distinguish repeats and do not count them toward the 1000 distinct coverage requirement.
- Exercise `Знаю`, `Не знаю`, and skipped responses with a deterministic pattern in a dedicated QA session; verify the error queue, remedial loop, correction message, and no unlocking next new block before clearing errors.
- Every 5 blocks (250, 500, 750, 1000) perform a genuine cold restart of only the candidate QA app; verify current position, prior scores, and unresolved errors persist. If a restart must occur mid-block, additionally test one midpoint checkpoint.
- Check EN→RU, RU→EN, and any available mixed directions. Mark unsupported directional UI cases NOT_RUN, never PASS by assumption.
- Run a representative 100-word interval with airplane mode/network unavailable and verify continuing navigation and local images. Restore original network state afterward.
- Check illustrated cards across all 164 mapped word IDs separately; count images actually rendered and visibly correct, check no broken paths, decode stalls or text leaking answers. Verify hidden-until-answer behavior in knowledge-check mode. A static manifest check alone is not an image render PASS.
- Check full runtime after 1000-word stress: responsiveness, memory/ANR, crashes, out-of-memory, image load failures, counters and progress integrity. Collect filtered logcat with precise time boundaries; avoid collecting unrelated personal data.
- Run at least one rotation/resizing/background/foreground interruption if safe. Voice EN/RU playback/ASR remain separate physical acoustic test cases; do not mark PASS without audio evidence.

## Non-negotiable results
- Report per-block PASS/FAIL/NOT_RUN for 20 blocks and aggregate actual distinct-word count.
- Report FAIL with first offending word ID, block number, screenshot/log excerpt, and reproduction steps; stop destructive or misleading automated actions.
- Output: artifact SHA-256 and package metadata; 20-block CSV; image-164 coverage; failure notes; before/after installed package list; four checkpoint records; concise summary PASS/FAIL/NOT_RUN.
- Passing GitHub Actions or source-code inspection is NOT evidence that the physical-device regression ran.
