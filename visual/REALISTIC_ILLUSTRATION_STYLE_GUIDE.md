# English Words — approved realistic illustration style (2026-11-11)

## Decision
User approved **realistic illustrations** as the primary style. This is a visual **art direction approval**, not acceptance of every sample or clearance of copyright ownership.

## Image specification
- Square source artwork; realistic or photorealistic digital illustration, not random stock photographs.
- Natural light, authentic materials and believable poses; clean composition and background; one teachable semantic focus.
- No embedded word text, logos, watermarks, branded UI, recognisable copyrighted characters, or direct copying of identifiable photographs/works.
- For concrete objects: the referent must be easily recognizable without captions.
- For verbs, adjectives, emotions, grammatical concepts: use human action, contextual contrast or a clear diagram where needed; do not rely on facial expressions alone.
- Image may not represent an alternate sense by mistake (e.g., "mouse" computer device vs animal). Record intended sense for review.
- Pipeline output: RGB square 640×640 WebP, target 40–100 KB when quality permits. Preserve image boundaries; inspect at actual lesson-card size.
- Store at `app/src/main/assets/images/word_<ID>.webp`; existing Android catalog maps `wordId` to `images/word_<ID>.webp`.
- The normal word learning flow must still work when the image is missing, and images must not reveal test answers before the user responds.

## Provenance and release safeguards
- Generate artwork specifically for English Words, do not reuse third-party photos.
- Store original output, prompt/generation provenance, the optimized asset's SHA-256, actual image path, and a semantic editorial decision.
- Do **not** claim that AI-generated images are copyright-free or uniquely owned in all jurisdictions. Check resemblance to protected characters/brands and secure rights clearances before publication.
- Source dictionary entries themselves have an unresolved provenance/licensing issue; graphic production is not a waiver of that block.
- `visual_vocabulary.json` must only reference assets physically present in the APK and approved for use.

## Pilot status
Ten original ChatGPT-generated illustrations were prepared as a **local ZIP** (not yet pushed as image binaries into GitHub); they map to the source word IDs 967, 1544, 1546, 1547, 1554, 1747, 2101, 2104, 2108 and 2126.
Individual visuals are **not yet accepted** for commercial release. Do not count them as installed images in GitHub until image files are actually committed and validated.

## Consistency rule
Keep illustrations independent per word rather than reusing one single artwork for multiple meanings. Reusing a character/style is permitted as a visual theme, but each scene must illustrate the actual target sense.
