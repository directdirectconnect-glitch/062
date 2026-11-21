#!/usr/bin/env python3
"""Import the 50 approved 19-Nov-2026 WebP pictures into the Android assets.

Invoked after the independently verified 217-photo base pack is restored.
Never invent word IDs or silently replace an earlier illustration.
"""
import hashlib
import json
import pathlib
import re
import shutil
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
EXTRAS = ROOT / "release-media/webp-267-additions"
OUTPUT = ROOT / "results/267-webp-audit.json"
VALID = re.compile(r"word_(\d{4})_([a-z]+)\.webp")


def fail(message):
    raise RuntimeError(message)


def main():
    sources = sorted(EXTRAS.glob("*"))
    if len(sources) != 50:
        fail(f"Exactly 50 approved WebP additions required; found {len(sources)}")
    words = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    if len(words) != 5126 or len({w["id"] for w in words}) != 5126:
        fail("Expected exactly 5126 unique dictionary entries")
    by_id = {w["id"]: w for w in words}
    visual_path = ASSETS / "visual_vocabulary.json"
    visual = json.loads(visual_path.read_text(encoding="utf-8"))
    mapped = {e["wordId"]: e for e in visual["images"]}
    original = [p for p in (ASSETS / "images").glob("*") if p.is_file()]
    supplemental = [p for p in (ASSETS / "additional_photos").glob("*") if p.is_file()]
    if len(original) != 164 or len(supplemental) != 53:
        fail(f"Base pack incomplete: {len(original)} legacy, {len(supplemental)} supplemental")
    digest_paths = {}
    for p in original + supplemental:
        if p.suffix.lower() != ".webp":
            fail("Non-WebP media asset: " + str(p))
        with Image.open(p) as image:
            image.load()
            if image.format != "WEBP":
                fail("Not a decoded WebP: " + str(p))
        digest_paths.setdefault(hashlib.sha256(p.read_bytes()).hexdigest(), []).append(p.name)
    imported = []
    seen_ids = set()
    for path in sources:
        match = VALID.fullmatch(path.name)
        if match is None:
            fail("Unexpected extra photo filename: " + path.name)
        word_id = int(match.group(1))
        spelling = match.group(2)
        if word_id in seen_ids:
            fail(f"Duplicate new word ID {word_id}")
        seen_ids.add(word_id)
        word = by_id.get(word_id)
        if not word or word["en"].strip().lower() != spelling:
            fail(f"Dictionary mismatch for {path.name}: {word}")
        if word_id in mapped:
            fail(f"Existing illustration would be overwritten: {word_id}")
        data = path.read_bytes()
        if len(data) < 20 or data[:4] != b"RIFF" or data[8:12] != b"WEBP" or int.from_bytes(data[4:8], "little") + 8 != len(data):
            fail("Malformed WebP RIFF: " + path.name)
        with Image.open(path) as img:
            img.load()
            if img.format != "WEBP" or img.width == 0 or img.height == 0:
                fail("WebP decode failed: " + path.name)
        digest = hashlib.sha256(data).hexdigest()
        if digest in digest_paths:
            fail("Duplicate binary media: " + path.name + " vs " + ",".join(digest_paths[digest]))
        digest_paths[digest] = [path.name]
        target_name = f"word_{word_id:04d}.webp"
        target = ASSETS / "images" / target_name
        if target.exists():
            fail("Destination asset already exists: " + target_name)
        shutil.copyfile(path, target)
        entry = {"wordId": word_id, "asset": "images/" + target_name,
                 "altEn": word["en"], "altRu": word["ru"]}
        visual["images"].append(entry)
        mapped[word_id] = entry
        imported.append({"id": word_id, "en": spelling, "file": target_name, "sha256": digest})
    visual["images"].sort(key=lambda x: x["wordId"])
    visual_path.write_text(json.dumps(visual, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    all_media = [p for directory in ("images", "additional_photos") for p in (ASSETS / directory).glob("*") if p.is_file()]
    if len(all_media) != 267:
        fail(f"Expected exactly 267 WebP within Android assets, got {len(all_media)}")
    if len(visual["images"]) != 221:
        fail(f"Expected exactly 221 word-ID image mappings, got {len(visual['images'])}")
    report = {"status": "PASS", "words": len(words), "base_images": 217,
              "new_images": len(imported), "total_images": len(all_media),
              "mapped_word_images": len(visual["images"]),
              "supplemental_photos": 17, "new": imported}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: {len(all_media)} WebP, {len(visual['images'])} mapped to dictionary words")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print("FAIL: " + str(exc), file=sys.stderr)
        sys.exit(1)
