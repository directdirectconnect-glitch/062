# Vocabulary provenance and release review

The Android application loads the **original** `app/src/main/assets/words.json`.
No fabricated translations, blank fields or synthetic test fixtures were added.

## Sources and review status

- Rows 1–3312: prior English Words development vocabulary, with multiple author-written batches. These have not had final bilingual editorial proofreading.
- Rows 3313–5000 and 5001–5119: initial bilingual lexical candidates based on factual word/translation/pronunciation reference data at https://github.com/iuzhakov/English-Russian-vocabulary . The reference repository README does **not** state a redistribution license. **Provenance and permission/legal review is required before distributing any derived dataset publicly or shipping it in an APK.** This branch should be treated as a development-only vocabulary pending source clearance and independent linguistic validation.
- The latest batch corrects a few obvious issues (for example, `beetle` and `waitress`); the rest may still contain translation or pronunciation errors.

## Counts and compatibility

- 5,119 ordered unique English-spelling records.
- 5,000 are single-token English spellings, and 119 are multiword expressions.
- Existing IDs 1–3312 and their content remain stable. New IDs append in order.
- Some single-token entries can be inflected forms or compounds rather than dictionary lemmas. A spelling count is not a claim of 5,000 editorially verified lemmas.

## Required before release

1. Confirm data rights for the 1,807 candidate entries or independently reauthor/replace them from correctly licensed references.
2. Bulk QA: confirm English headwords, Russian meaning accuracy, IPA/transcription and consistency of notation, duplicates by lemma, and appropriateness for A1–B1 travel and everyday usage.
3. Verify app loading, memory, random selection, persistence of progress and 50-word lesson boundaries using this actual `words.json` on Android devices. Do not use temporary load-test corpora.
4. Merge into the release branch only after the relevant checks and make a new APK before Fold and S20 FE device testing.
