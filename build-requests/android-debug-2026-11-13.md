# English Words Android debug APK — build request

Requested: 2026-11-13.
Branch: release/0.9.8-vocabulary-5000-apk.
Task: run existing GitHub Actions workflow, validate integration, unit tests and assembleDebug, upload EnglishWords-APK artifact.

**Known gaps before release:** bundled words.json currently contains 5119 entries (target 5200); visual_vocabulary.json currently has an empty images array (target 164). The resulting APK, if compilation succeeds, is **technical preview only**, not an approved 5200-word/164-image release.

Do not replace the currently installed Fold build without separate smoke and data-retention checks.
