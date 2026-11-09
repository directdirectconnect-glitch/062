# English Words 0.8.0 — integration preview

This branch integrates the following previously separate development lines:

- Foldable/wide-screen two-column layout from main (>=600dp), with lesson restoration after configuration changes.
- Android on-device-only speech recognition on supported devices (Android 12+, language packages required).
- Offline installed English/Russian text-to-speech voice selection and speech rate controls.
- Capability diagnostics and button-answer fallback when on-device recognition is unavailable.
- Android 8.0 minimum (API 26); phone manufacturer is not restricted.

## Limitations / test plan
- This build does not bundle Vosk or another speech model. Offline speech recognition depends on an installed Android on-device recognition service.
- Voice naturalness depends on installed offline TTS packs.
- Verify both phone and foldable UI, including folding/unfolding mid-lesson.
- Verify on-device recognition with network disconnected, English and Russian separately.
- Verify fallback on Android 8–11 and on devices without on-device recognition.
- Word illustrations are specified separately, not yet implemented.
- Compilation does not establish runtime compatibility; physical device tests are required.

## Baseline
- Original successful 0.6 baseline: branch baseline/0.6
- Previous foldable preview: main
- Integrated source: integrate/0.8.0
