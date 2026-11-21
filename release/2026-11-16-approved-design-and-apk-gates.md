# English Words — release 2026-11-16: approved design and APK gate

Status: **RELEASE CANDIDATE PREPARATION — NOT READY / NOT BUILT**.
Base: `build/0.9.8-217-photos-legacy-dictionary`, originally derived from `test/0.9.8-5119-118-images-s20fe-fold`.
Date: 2026-11-16. Source of truth: currently approved Google Drive PNGs (not historic screenshots or generated alternatives).

## Mandatory current assets
| Screen | Design source (Google Drive) | Status | Expected app behavior |
|---|---|---|---|
| P01 Home | [P01_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/1LJn2czaUMEqxkYebbbhGtCL98U3XbPS_/view) | APPROVED / CODE NOT VERIFIED | Navy and gold; English Words + LANGUAGE LEARNING; Welcome! and «Что вы хотите делать сегодня?»; three actions (Перевести слово / Учить слова / Проверка знаний); no progress/translation card on home |
| P02 Translate | [P02_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/11E6V5eno46P_9f1sxXKsNc-B7v6IAxKA/view) | APPROVED / CODE NOT VERIFIED | Text/voice Russian input, optional examples checkbox, translation, history |
| P03 Learn | [P03_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/1LxrZmuQnCQkR41ER1my5NVgQMHDDwI7o/view) | APPROVED / CODE NOT VERIFIED | Enter directly, no ExampleModeSetup modal; settings on page, progress, back/pause/next, persistent audio switch |
| P04 Knowledge test | [P04_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/1E373zSAOgU-bBD48qgIGem6NEVCv_hLa/view) | APPROVED / CODE NOT VERIFIED | On-page settings, timer, speech/text input, skipped != wrong, feedback and navigation |
| P05 Settings | [P05_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/1RwfXvvCMQp9D4M0deVOfgUnvmYB3Zb6h/view) | APPROVED / CODE NOT VERIFIED | Theme/language/text/voice/reminders, persistence, protected reset |
| P06 Dictionary & history | [P06_2026-11-16_v1.0_CURRENT.png](https://drive.google.com/file/d/1gTgXh2yq97zhnMF5ew0NCLRsqvb6uCQQ/view) | APPROVED / CODE NOT VERIFIED | Search, history, errors, favorites, saved progress; real statistics not mock values |

## Global UI acceptance
- Shared header English Words + LANGUAGE LEARNING is clickable as a whole from all pages and returns to P01 without losing lesson progress.
- Use current v1.0 files ONLY; all v0.x versions are archive. Approved PNG is a visual reference, not proof of implementation.
- No generic image overlay replacing functional controls; screens must be native functional Android layout, preserving accessibility and offline behavior.
- Actual Android UI must be compared to screenshots from the new build on Samsung Galaxy S20 FE, and Fold as user acceptance.

## Dictionary gate
- Source base APK has 5,126 EN–RU records, structurally checked 5,126/5,126. First 3,632 automatically verified; second 389 verified; five errors patched only in a separate copy. 1,100 translations remained REVIEW at last confirmed report. Do not claim 100% semantic verification.
- User authorized preview APK using legacy structurally checked dictionary without waiting for 1,100; label dictionary source SHA, manifest version and outstanding review clearly.

## Image gate
- Intended target 217 photographic assets = existing 164 + 53 new PNGs listed in `build-requests/217-photo-drive-staging-manifest.json`.
- Current staging manifest status `ASSET_IMPORT_PENDING`; `assetStaged=false` for new photos.
- 29/53 new images overlap old concepts and would replace existing associations; 24/53 new concepts; only 36 exact dictionary lemma matches and 17 compound labels need mapping. **Do not claim 217 unique word mappings** without reconciliation.
- Before CI build: download/validate all 53 originals; decode and check unique hashes, dimensions, format and mapping IDs; stage binaries; reconcile desired 217 files vs 188 distinct concepts and 17 unresolved map keys. No silent placeholders.

## Release completion criteria (all must be documented)
1. Approved P01–P06 implemented in the app, reviewed by code diff and functional navigation.
2. 53 new images actually present in Android assets, no corrupt files, correct word↔image relations; manifest updated with actual counts.
3. GitHub Actions build SUCCESS, real APK artifact present (no placeholder); record build run, commit SHA, SHA-256, versionName, versionCode, package name.
4. APK install and screenshot/functional verification on physical S20 FE. State voice and 1,100 translation coverage honestly (NOT_RUN/REVIEW where applicable).
5. Only then call this release's APK READY. Do not silently rebrand the existing 164-image APK as 217.

## Immediate next implementation steps
1. Implement P01/P02/P03/P04/P05/P06 from the six approved Drive designs; explicitly compare new code with old `MainActivity.kt` and `ExampleModeSetup` flow.
2. Windows agent: download 53 image binaries from approved folder, stage in repo and update mapping, verifying SHA/size.
3. Run Android CI and inspect artifact, then physical S20 FE smoke tests. Preserve installed app data and separate QA package.

## Build attempt — 2026-11-16
User requested immediate build. Triggering CI by commit to release branch. The strict asset validation is intentionally fail-closed: it must report missing 15 PNGs and missing image mappings until actually fixed. This is a build/diagnostic attempt, NOT a release artifact or evidence that P01–P06 were implemented. Never label an old 164-image artifact as the final APK. The run's actual outcome must be checked independently.
