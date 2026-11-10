# English Words — Android APK emulator testing environment

Environment branch: `test/0.9.5-emulator`, derived from immutable beta `beta/0.9.5-unified`.

## How to run
Push changes to `test/0.9.5-emulator` in `app/`, `scripts/`, or the emulator workflow. GitHub Actions **Android APK Emulator Smoke Test** starts automatically. For another identical run, open its existing GitHub Actions run and choose **Re-run jobs**.

The workflow compiles the app's actual debug APK, validates local resources, runs JVM unit tests, then starts a hardware-accelerated Android 13 (API 33) x86_64 AOSP Automated Test Device (ATD) emulator using ReactiveCircus's runner. It installs the APK and launches the app with Wi-Fi and mobile data disabled.

## Smoke checks
- Compact phone-sized screen: main activity launches, three mode controls appear.
- Same examples checkbox is presented before **«Изучать»** and **«Спроси слово»**.
- **«Спроси слово»** activity starts and returns to the lesson.
- Listening mode launches, checking mode exposes manual response controls.
- Force-stop and restart recovers the active checking screen.
- Window dimensions change to a wide unfolded-like format, then back to compact. Manual-check controls remain accessible.
- Each stage captures Android UI hierarchy XML and attempts to capture a PNG screenshot. **AOSP ATD disables hardware rendering; the PNGs from this target are black and must not be used for visual layout verification.** The UI XML is the valid evidence for this CI target.
- On failure, the script captures the last screen image (subject to the ATD limitation) and recent logcat events.

## Artifacts on successful runs
- `EnglishWords-095-Emulator-Report`: screenshots, UI hierarchies, trace, result.json and logcat.
- `EnglishWords-095-Emulator-Tested-APK`: the APK installed and smoke-tested on this virtual device.

## What is not verified
A virtual pixel emulator with a resized screen **does not test a physical Galaxy Fold hinge**. It cannot reliably establish the quality of microphone recognition, correct offline EN/RU speech voices on a user's Android phone, uninterrupted playback with the screen locked, or real-world notification timing. ASR remains dependent on Android's installed on-device language components, not Vosk.

Passing these tests does not imply all functional flows are correct. More end-to-end cases should follow (typed Russian lookup, event history, 50-word correction round, scrolling and accessibility, data upgrade with matching signing key).

## Safety
- No permissions for internet access are requested by the APK.
- This test uses a disposable emulator, never the user's phone data.
- It does not modify `main` or the previously released beta APK.
- Screenshots and logs are retained in GitHub Actions artifacts for 30 days.

## Latest verified run — 10 November 2026

[GitHub Actions #5](https://github.com/directdirectconnect-glitch/062/actions/runs/42772224573) **succeeded**. All seven smoke checks passed on Android 13 with Wi-Fi/data disabled, including main screen, Ask Word, Learn, manual Check, process restart, wide Fold-like resize, and compact resize. JVM tests and APK build also passed. Artifacts: `EnglishWords-095-Emulator-Report` and `EnglishWords-095-Emulator-Tested-APK`.

This run verified the candidate in `test/0.9.5-emulator`, including a fallback to manual checking if an Android offline language pack is unavailable. That fix has **not yet been merged into** `beta/0.9.5-unified`; the two APK binaries are not interchangeable as proof of the same build.
