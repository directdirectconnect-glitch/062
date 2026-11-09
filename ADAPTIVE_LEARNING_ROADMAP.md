# English Words — adaptive learning and forgetting-pattern analytics

Status: approved design proposal for a future post-0.8.7 phase (target 0.9.x). NOT implemented in 0.8.7.

## Goal
Identify which words a learner repeatedly struggles with, whether they share meaningful properties, and suggest specific practice methods. Never claim to know a psychological cause from error counts alone. Show hypotheses and supporting observations.

## Existing teaching rules to preserve
- Units: 50 words per block; target capacity up to 500 words per day.
- If a block has unresolved errors, remediate those words before passing to the next new block. Provide focused short practice within the current block, not a forced advance.
- Both English-to-Russian and Russian-to-English matter.
- Offline-first, responsive for phones and unfolded Fold displays.
- Current example phrases may be opened before answering; mark the attempt as assisted if the learner viewed example sentences or translation before response.

## Data model
Store local-only append-only learning events with safe schema migrations; start with a minimal event ledger in app-private SQLite/Room or JSONL plus robust local backup. Do not retain audio recordings by default.
LearningEvent:
- word_id and canonical word/en translation
- timestamp (UTC), session_id, block_id, direction, mode (manual/ASR)
- answer outcome: correct, incorrect, skipped, uncertain, or ASR_unavailable
- optional response latency only when instrumentation is reliable
- whether answer/examples/hints were seen before responding
- number of repeats listened to, phrase examples opened, examples listened to
- spoken recognition confidence/error when provided; label separately from memory errors
- self-rated confidence when enabled (easy/hard/unsure)
Per-word state: n_attempts_unassisted, n_correct_unassisted, last_seen, streak, due_review_at, recent_history, direction-specific mastery, tags.

## Tags and pattern discovery
Build local metadata gradually: part of speech, everyday topic (airport/transport/hotel/restaurant/shopping), morphological family, multiword expression, visual/phonetic resemblance, spelling-vs-pronunciation mismatch, irregular verb, function word.
Assess:
1. Repeated confusion between similar forms/meanings (e.g. look/watch/see, say/tell, in/on/at).
2. Direction asymmetry: learner understands EN→RU but cannot recall RU→EN.
3. Pronunciation vs spelling mismatch; ASR failures cannot be presumed knowledge failures.
4. Interference: words from same topic or language family mixed up.
5. Context gaps: isolated word fails but succeeds in examples, or vice versa.
6. Time-dependent forgetting measured by days since last unassisted success.
7. Session-level fatigue correlation only with adequate evidence, cautiously; don't infer diagnoses.

## Rules for confidence and explanations
- At least 3 unassisted valid attempts per word before marking a repeated difficulty.
- At least 5 relevant attempts and more than one problematic word before suggesting a group-level pattern.
- Small samples: say “пока недостаточно данных”.
- Distinguish learner-selected “Не знаю” from voice recognition engine failures.
- Assisted responses are practice, not independent proof of recall.
- Explain recommendations in plain Russian or English according to UI language; show short relevant evidence, not pseudo-diagnostic statements.

## Adaptive actions
- On-block errors: create mini-loop with the failed words and compare with nearest confusing words, then retest without hints.
- EN→RU success, RU→EN weakness: increase active recall from Russian to English.
- Phonetic confusion: add slow playback, repetition, contrast pairs and listening discrimination.
- Semantic confusion: show side-by-side minimal contrasts and 5–6 contextual example phrases.
- Context insufficiency: sentence-level checks from the curated offline example bank.
- Forgetting: per-word spaced repetition after successful retrieval, tentative due windows 1d/3d/7d/14d/30d, adjust to behavior rather than rigidly applying intervals.
- Avoid repetitive drilling of one item; cap successive repeats, offer rest and alternative example.
- Propose a personalized next 50-word block that prioritizes unresolved mistakes before new words, consistent with progression rule.

## UI
Add “Почему эти слова сложны?” or “Что повторить” panel in progress/statistics, not in main question UI.
Suggested card:
"Вы 4 раза путали in / on / at, чаще в направлении Русский → English. Возможная причина — выбор предлога по контексту. Практика: 6 коротких примеров и 3 задания на различение."
One-tap "Повторить сейчас", and pause/ignore recommendation options.
Never suggest unproven causes as certainties.

## Privacy and control
- No cloud requirement, no audio retention by default, no export without user action.
- Allow analytics toggle, reset progress/analytics, and export locally.
- Store only data necessary to improve learning; protect app data and preserve it on app upgrade.

## Implementation sequence
Phase A: event instrumentation & local persistence; automated tests for migration, data correctness, ASR-error exclusion, and assisted-status.
Phase B: rules-engine for repeated mistakes, direction bias, spaced repetition, and grounded explanations.
Phase C: simple progress insights and manual follow-up exercises, offline.
Phase D: content tags, confusion pairs, evaluation against test datasets, calibration, and optional richer recommendations.

## Acceptance scenarios
- Three separate “Не знаю” attempts on the word *ticket* create a due review; one noise-related ASR error does not.
- Looking at phrase translations before answering records assisted practice, not independent correct recall.
- Four genuine mix-ups across in/on/at produce a cautious grammar/context recommendation.
- After unresolved mistakes in a 50-word block, new words are not incorrectly unlocked.
- Changing device layout, fonts, app language, or background preserves progress and history.
- App operates without network across Android 8+ devices.
