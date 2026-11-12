# S20 FE 5,119-word physical QA -- local suite

**Purpose:** Make real-device progress without pretending CI/emulator tests are physical microphone acceptance.

## Pinned candidate

- Commit: `b1afa6a94e22830a6e7cc1570a21877904f35b60`
- CI run: `43087170540`, name `EnglishWords-S20QA-5119-Voice-APK`
- APK SHA-256: `c1cc6f3e797e176c99a5c5ae1dcb57b706e698cad540ad0575d8a63e46af7c71`
- Package: `com.example.english1000.s20qa` (**different** from installed `com.example.english1000.s20test` and production `com.example.english1000`)
- Exactly 5,119 records with unique IDs/English headwords, and EN/RU local Vosk archives

## Local installation (one time, requires Codex/operator review)

The installed CommandBridge only executes `C:\EnglishWords-Windows-Lab\Run-FullFunctionalSuite.ps1` when its SHA-256 equals the content in `%LOCALAPPDATA%\EnglishWords-S20-CommandAgent\approved-full-suite.sha256`.

1. Review this repo PR and the full `Run-FullFunctionalSuite.ps1` source, including its allowed side-by-side APK installation and ADB UI interactions. Do not permit any unsigned or unreviewed changes.
2. Place `Run-FullFunctionalSuite.ps1` and `Install-ApprovedFullSuite.ps1` together on the test PC. Get the source SHA using `Get-FileHash .\Run-FullFunctionalSuite.ps1 -Algorithm SHA256`.
3. Run `Install-ApprovedFullSuite.ps1 -ApprovedSha256 <reviewed 64-digit hash>` **locally** using the existing interactive Windows user. The installer never installs an APK; it only pins the script hash. It does not alter existing tasks.
4. Then submit a **new** queue sequence (e.g. 4) with `operation: full_regression`; seq 3 was already consumed and reported `BLOCKED_APPROVED_SUITE_MISSING`. The local agent will run only the now-approved fixed script.
5. Validate the per-case JSON stored locally and sanitized Issue #3 result. The public bridge publishes only `FULL_SUITE_INCOMPLETE`, `FULL_SUITE_FAIL`, or `FULL_SUITE_PASS_REVIEW_REQUIRED`, never raw device data.

## Scope and limitations

- The suite obtains exactly one pinned CI artifact through authenticated `gh run download`, checks its exact SHA-256, then verifies bundled 5,119 dictionary entries and RU/EN Vosk ZIPs before any side-by-side installation.
- Requires exactly one ADB-authorized SM-G780G and a separately installed `.s20test`.
- Installs only a *new* isolated `.s20qa` package; if it already exists, **blocks**, never uninstalls/overwrites it. Does not delete app data or modify Fold.
- Performs real S20 FE UI checks of launch, three modes, Ask Word, learning, manual checking and process restart; logs per-test verdicts. Does not silently claim grading correctness based on a tap alone.
- **NOT_RUN** until additional physical hardware tests are added: 50-word full block, EN/RU real microphone acoustic testing, TTS sound quality, WiFi/airplane mode, UI themes/settings/reminders, endurance, camera view and Fold.
- `status=BLOCKED` is the correct overall response when any mandatory feature is NOT_RUN. Do not claim full functional acceptance on an incomplete suite.
- Captures no photos, WAVs, screenshots or serial numbers into GitHub issues. No remote shell is involved.

**No claim is made that this new local suite is installed or has run on the physical phone.**
