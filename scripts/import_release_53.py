#!/usr/bin/env python3
"""Import 53 locally approved WebP photos without changing vocabulary IDs.

Run from repository root after extracting EnglishWords_53_Android_WebP.zip:
  python3 scripts/import_release_53.py /path/to/extracted/additional_photos

The tool imports binaries, resolves only exact existing dictionary matches, and
records unmatched multiword concepts separately for editorial review.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
STAGING = ROOT / "build-requests/217-photo-drive-staging-manifest.json"
VOCAB = ASSETS / "words.json"
MAP = ASSETS / "visual_vocabulary.json"
OUTPUT = ROOT / "results/release-53-import.json"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="directory containing the approved 53 WebP files")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    source = args.source.resolve()
    manifest = json.loads(STAGING.read_text(encoding="utf-8"))
    words = json.loads(VOCAB.read_text(encoding="utf-8"))
    if len(words) != 5126 or len({x["id"] for x in words}) != 5126:
        raise SystemExit("BLOCKED: vocabulary not 5126 unique IDs")
    entries = manifest["images"]
    if len(entries) != 53 or len({x["word"] for x in entries}) != 53:
        raise SystemExit("BLOCKED: source manifest not exactly 53 distinct concepts")
    expected = {x["word"] + ".webp" for x in entries}
    actual = {p.name for p in source.glob("*.webp")}
    if actual != expected:
        raise SystemExit("BLOCKED: missing=" + repr(sorted(expected - actual)) +
                         " extra=" + repr(sorted(actual - expected)))
    by_en = {x["en"].strip().lower(): x for x in words}
    loaded = json.loads(MAP.read_text(encoding="utf-8"))
    old_map = loaded["images"]
    updated = {x["wordId"]: x for x in old_map}
    staged = ASSETS / "additional_photos"
    linked, review = [], []
    file_hashes = set()
    for item in entries:
        name = item["word"]
        file = source / (name + ".webp")
        data = file.read_bytes()
        if not data.startswith(b"RIFF") or data[8:12] != b"WEBP":
            raise SystemExit("BLOCKED: corrupt/non-WebP file: " + name)
        digest = hashlib.sha256(data).hexdigest()
        if digest in file_hashes:
            raise SystemExit("BLOCKED: duplicate binary: " + name)
        file_hashes.add(digest)
        word = by_en.get(name.replace("_", " "))
        expected_id = item.get("existingWordId")
        if (word["id"] if word else None) != expected_id:
            raise SystemExit("BLOCKED: changed dictionary mapping for " + name)
        if word is None:
            review.append(name)
        else:
            updated[word["id"]] = {
                "wordId": word["id"],
                "asset": "additional_photos/" + name + ".webp",
                "altEn": word["en"],
                "altRu": word["ru"]
            }
            linked.append({"word": name, "wordId": word["id"]})
    report = {
        "status": "REVIEW_REQUIRED" if review else "PASS",
        "source_count": len(entries),
        "linked_exact_word_ids": len(linked),
        "unmapped_phrase_concepts": review,
        "note": "Unmatched names are not assigned arbitrary vocabulary IDs.",
        "dry_run": args.dry_run
    }
    if not args.dry_run:
        staged.mkdir(parents=True, exist_ok=True)
        for item in entries:
            name = item["word"]
            shutil.copyfile(source / (name + ".webp"), staged / (name + ".webp"))
            # Older PNG duplicates of the same concept are superseded by the WebP.
            old_png = staged / (name + ".png")
            if old_png.exists():
                old_png.unlink()
        loaded["images"] = sorted(updated.values(), key=lambda x: x["wordId"])
        MAP.write_text(json.dumps(loaded, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if not review else 2


if __name__ == "__main__":
    raise SystemExit(main())
