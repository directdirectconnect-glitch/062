# English Words visual pipeline — 5,000 candidates

This branch contains **10 real data queues, 500 word IDs each** under `visual-pipeline/jobs/batch_001.jsonl` through `batch_010.jsonl`, derived from the existing 5,119-entry `app/src/main/assets/words.json`. The entries are **review candidates, not generated images**.

Each record includes `wordId`, `en`, `ru`, `category`, `state: REVIEW`, and an original photorealistic prompt. The priority is concrete nouns, then other senses needing editorial review. Current categories: 58 `concrete`, 4,328 `sense_review`, 614 `abstract_review`. Do **not** blindly generate all 5,000: function words and abstract concepts must not yield misleading pictures.

## Production runner

A self-contained Windows/Python runner and setup guide were produced as `EnglishWords-Visual-Pipeline-5000.zip` in the ChatGPT conversation. The runner generates its own SQLite queue from the **same** checked-out `words.json`, and supports:

`plan` → `approve` (CSV) → `generate --execute --budget-usd ...` → `preview` → `review-images` → `export`.

The authoritative **data queues committed here** are inspectable/validated separately; the ZIP's CLI uses the dictionary directly, not these queue files. This avoids executing untrusted remote data or letting GitHub comments trigger expensive calls. Explicit review is mandatory before each paid image generation and before shipping assets. Provider requests use the current documented image API model `gpt-image-2.5-flare`, with local `OPENAI_API_KEY` only. Actual provider spending must also be monitored in the API billing dashboard; the CLI --budget-usd cap is a conservative estimate, not guaranteed invoicing.

The generated manifest uses the existing Android schema:

```json
{
  "schemaVersion": 1,
  "images": [{
    "wordId": 3357,
    "asset": "images/word_3357.webp",
    "altEn": "apple",
    "altRu": "яблоко"
  }]
}
```

Do not commit the SQLite database, raw images, credentials, or sensitive logs. Host approved compressed WebP assets in controlled offline packs as appropriate; a 5,000-photo monolithic APK may become too large.

## Acceptance status

- **PASS:** deterministic 5,000-entry review queue checked in; 10×500.
- **PASS:** local pipeline runner and 4 dry-run tests, as conversation ZIP.
- **NOT_RUN:** external paid generation; no provider API key or spend authorization supplied.
- **NOT_RUN:** image semantic QA and Android offline package integration.
- The pre-existing `visual_vocabulary.json` in the app is still empty; this branch does not pretend images are already included.

Associated tracking: https://github.com/directdirectconnect-glitch/062/issues/15
