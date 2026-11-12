#!/usr/bin/env python3
"""Install and verify the audited, offline visual-vocabulary package before building APK."""
import argparse
import json
import hashlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--expected-images", type=int, default=118)
    args = parser.parse_args()
    if not args.archive.is_file():
        parser.error("Missing image archive. The APK must not be labeled visual-enabled.")
    words = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    assert len(words) == 5121, f"Expected all 5121 dictionary records; got {len(words)}"
    known = {w["id"]: w for w in words}
    assert len(known) == len(words), "Duplicate dictionary IDs"
    with zipfile.ZipFile(args.archive) as archive:
        names = set(archive.namelist())
        key = "app/src/main/assets/visual_vocabulary.json"
        assert key in names, "Package has no visual_vocabulary.json"
        manifest = json.loads(archive.read(key).decode("utf-8"))
        images = manifest["images"]
        assert len(images) == args.expected_images, f"Expected {args.expected_images} entries, got {len(images)}"
        ids = set()
        for entry in images:
            wid = entry["wordId"]
            assert wid in known, f"Unknown word ID {wid}"
            assert wid not in ids, f"Duplicate image mapping for word ID {wid}"
            ids.add(wid)
            rel = entry["asset"]
            assert rel.startswith("images/") and rel.endswith(".webp") and ".." not in rel
            member = "app/src/main/assets/" + rel
            assert member in names, f"Missing image {member}"
            data = archive.read(member)
            assert data[:4] == b"RIFF" and data[8:12] == b"WEBP", f"Not WebP: {member}"
            assert entry["altEn"].casefold() == known[wid]["en"].casefold(), f"Wrong word at {wid}"
        for entry in images:
            member = "app/src/main/assets/" + entry["asset"]
            dst = ASSETS / entry["asset"]
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_bytes(archive.read(member))
        (ASSETS / "visual_vocabulary.json").write_bytes(archive.read(key))
    print(f"PASS: {len(words)} words, {len(images)} unique offline image mappings and files")

if __name__ == "__main__":
    main()
