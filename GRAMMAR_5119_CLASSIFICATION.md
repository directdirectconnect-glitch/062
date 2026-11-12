# English Words — grammatical classification of 5,119 entries

## Precise definitions

- **Существительное / noun** and **глагол / verb** are *lexical parts of speech*.
- **Подлежащее / subject** and **сказуемое / predicate** are *syntactic roles of a word or group in a sentence*. They cannot be unambiguously assigned to a bare dictionary headword.
- An English word may belong to multiple POS depending on sense/context: *work*, *light*, *book*, *watch*. This project **retains all attested possible POS** and sets \`primaryPartOfSpeech=null\` when ambiguous.
- \`possibleSentenceRoles.subjectNounPhraseHead\`: a noun/pronoun **may** head a subject NP, but the actual occurrence may be object or complement. \`verbalPredicateHead\`: verb/auxiliary **may** form a predicate. Sentence-level syntactic parsing is a separate feature.
- \`nounCandidate\` is a preliminary flag for the visual-vocabulary queue; it does **not** mean every noun must have an image. Abstract nouns need separate checks.
- \`partOfSpeechStatus\` marks ambiguity, unknown lexemes, and multiword entries needing human review.

## Files and reproducibility

Source of truth: \`app/src/main/assets/words.json\` (5,119 IDs) and curated \`word_families.json\` POS hints. A deterministic classifier in \`scripts/classify_grammar_5119.py\` uses NLTK WordNet 3.9.1 as a **candidate-sense lexicon**, with exact function-word overrides and curated family POS. WordNet's catalog does not know which sense matches a particular Russian translation. Treat output as candidate grammatical labels until editorial review.

Run \`python -m pip install nltk==3.9.1\`, \`python -m nltk.downloader wordnet omw-1.4\`, then \`python scripts/classify_grammar_5119.py\`.

Automated CI workflow \`.github/workflows/grammar-5119.yml\` creates:
- \`app/src/main/assets/word_grammar_5119.json\` — every entry with possible POS (RU labels), candidate noun and sentence-role affordances, confidence/review status and unchanged meanings;
- \`reports/word_grammar_5119_report.json\` — counts of possible nouns, verbs, other POS, review-needed entries, exact source hash.

**Important:** Counts by POS are non-exclusive, e.g., \`work\` contributes both noun and verb. Do not add disjoint counts expecting 5,119. \`unresolvedIds\` are not falsely classified. The bot commits generated data to \`develop/lexical-pos-5119\`; merging to the Android release branch requires separate testing and authorization.

## Examples

- \`the\` → determiner; neither a noun-subject head nor a lexical-verb predicate head.
- \`book\` → noun and verb candidates; whether the occurrence means a book or to book is determined by sentence context and translation.
- \`he\` → pronoun; potentially heads a subject NP, but subject assignment is contextual.
- \`run\` → possible noun and verb; verbal-predicate possibility not an assertion about an isolated word.

## Further work before release

1. Review \`REVIEW_REQUIRED\` entries and all multiword phrases with Russian glosses; fix remaining translation/provenance concerns separately.
2. For actual educational examples, annotate *sentences* with subject/predicate spans.
3. For picture selection, prioritize sense-resolved **concrete noun** words; do not manufacture images for abstract words or grammatical particles.
4. Add app UX teaching POS if desired; this classification file alone does not change APK behavior.
