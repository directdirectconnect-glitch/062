# Visual vocabulary: first-pass grouping

This folder is a **planning catalog**, NOT a photo dataset.

## What actually exists
- Original app source: `app/src/main/assets/words.json`, 5,119 unique entries, including 5,000 individual English spellings and 119 expressions.
- Existing illustration database: `app/src/main/assets/visual_vocabulary.json` is `{"schemaVersion":1,"images":[]}`: **zero attached images**.
- Existing thematic practice data: 3 scenes (`park`, `city`, `metro`) with **43 word links**. They are not image files.
- `visual_classification_draft.json`: one first-pass record per original word ID. Classification is **not yet editorially reviewed**. Null image paths are intentional.

## First-pass classification results

| Suggested visual treatment | Records | Production idea |
|---|---:|---|
| Object photo | 201 | Real, uncluttered photograph showing referent |
| Person photo | 62 | Contextual photo showing role, without relying on identity |
| Place photo | 48 | Recognizable scene/location photograph |
| Action scene | 143 | Person or object visibly performing the verb |
| Concept scene | 303 | Two-panel or contextual explanation for abstract meaning/adverbs |
| Diagram | 46 | Minimal explanatory graphic; photo may mislead |
| Grammar card | 106 | Text + context; articles/prepositions cannot be explained by a single isolated photo |
| Needs semantic/manual review | 4,210 | No safe automatic assignment by conservative word-list rule |
| **Total** | **5,119** | |

These numbers describe **machine-suggested categories**, not confirmed word classes. Some candidates are ambiguous or categorized by basic string rules, so all are `reviewStatus: pending`.

## Editorial workflow
1. Prioritize original 5,000 single-token spellings; handle 119 expressions separately.
2. For each word, check actual meaning and choose `object_photo`, `person_photo`, `place_photo`, `action_scene`, `concept_scene`, `diagram` or `grammar_card`. Correct false guesses rather than keeping an arbitrary category.
3. Design visual brief specifying the exact meaning in Russian, recognizable visible scene, whether text is needed, and any risks of confusing another sense.
4. Add only legally cleared/licensed or original image assets, record credit/license/provenance and image filename. Confirm the image explains the word at lesson size.
5. Use the existing visual-vocabulary schema and Android tests only once visual content is verified. Do not substitute the original word dictionary.

Run `python3 scripts/validate_visual_classification.py` to verify full coverage and ID alignment.

**Quality and rights:** a portion of the 5,119 text entries also needs source-rights clearance and bilingual proofreading before public distribution.
