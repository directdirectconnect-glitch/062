# English Words Image Factory — local queue (initial implementation)

This is an **initial, GPU-independent queue**, not a working image-generation service yet.

## Requirements
Python 3.11+; no third-party packages required for queue commands.

## Input CSV
`word_id,word,translation` — one noun per row. Use stable IDs from the app dictionary. The queue does not classify parts of speech: provide a verified noun export.

## Commands
```powershell
python tools/image_factory/queue.py --db image_factory.sqlite import-nouns nouns.csv
python tools/image_factory/queue.py --db image_factory.sqlite register-existing existing.csv
python tools/image_factory/queue.py --db image_factory.sqlite status
python tools/image_factory/queue.py --db image_factory.sqlite export-pending pending.csv
```

Existing manifest: `word_id,image_path`. Paths must point to actual files accessible on the local Windows computer. Registration verifies SHA-256 and only marks a matching queued word as existing. It does **not** assume the 217 Drive images are downloaded or valid.

Next: GPU inventory from issue #22; SDXL/FLUX backend selection; image validation and perceptual deduplication; safe Google Drive synchronization; Windows scheduled task; integration tests. No model downloads or installations are performed by this code.
