#!/usr/bin/env python3
"""Strict inventory and linkage validation for the 2026-11-16 photo release.

This is a release *gate*, not a claim of semantic or visual editorial approval.
Checks original 164 + new 53 binaries, 53 inventory names, PNG chunk boundaries and
CRCs, duplicate hashes and whether every new asset is reachable from UI mappings.
"""
import hashlib
import json
import pathlib
import struct
import sys
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ROOT / "build-requests/217-photo-drive-staging-manifest.json"
MAPPINGS = ASSETS / "visual_vocabulary.json"
WORDS = ASSETS / "words.json"
SUPPORTED = {".png", ".jpg", ".jpeg", ".webp"}


def check_png(file):
    data = file.read_bytes()
    if len(data) < 45 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return "invalid PNG signature/size"
    pos, size_bytes, has_ihdr, has_idat, has_iend = 8, len(data), False, False, False
    while pos + 12 <= size_bytes:
        length = struct.unpack_from(">I", data, pos)[0]
        chunk = data[pos + 4:pos + 8]
        end = pos + length + 12
        if end > size_bytes:
            return "truncated PNG chunk"
        stored_crc = struct.unpack_from(">I", data, end - 4)[0]
        actual_crc = zlib.crc32(data[pos + 4:end - 4]) & 0xFFFFFFFF
        if stored_crc != actual_crc:
            return "invalid PNG chunk CRC"
        if not has_ihdr:
            if chunk != b"IHDR" or length != 13:
                return "invalid/missing PNG IHDR"
            width, height = struct.unpack_from(">II", data, pos + 8)
            if not (width and height):
                return "zero PNG dimensions"
            has_ihdr = True
        if chunk == b"IDAT":
            has_idat = True
        if chunk == b"IEND":
            has_iend = True
            if end != size_bytes:
                return "trailing bytes after PNG IEND"
            break
        pos = end
    if not (has_ihdr and has_idat and has_iend):
        return "incomplete PNG structure"
    return None


def main():
    errors = []
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = manifest["images"]
    original_dir = ASSETS / "images"
    photos_dir = ASSETS / "additional_photos"
    original = sorted(p for p in original_dir.iterdir() if p.is_file() and p.suffix.lower() in SUPPORTED)
    additional = sorted(p for p in photos_dir.iterdir() if p.is_file() and p.suffix.lower() in SUPPORTED)
    names = [entry["word"] for entry in entries]
    if len(entries) != 53 or len(set(names)) != 53:
        errors.append(f"manifest must have 53 unique photo names, got {len(entries)} rows / {len(set(names))} names")
    if len(original) != 164:
        errors.append(f"expected 164 baseline image files, found {len(original)}")
    if len(additional) != 53:
        errors.append(f"expected 53 additional image files, found {len(additional)}")
    expected = {f"{word}.png" for word in names}
    actual = {p.name for p in additional}
    for name in sorted(expected - actual):
        errors.append(f"MISSING PHOTO additional_photos/{name}")
    for name in sorted(actual - expected):
        errors.append(f"UNEXPECTED PHOTO additional_photos/{name}")

    hashes = {}
    for file in original + additional:
        if file.stat().st_size <= 0:
            errors.append(f"EMPTY {file.relative_to(ASSETS)}")
            continue
        if file.suffix.lower() == ".png":
            reason = check_png(file)
            if reason:
                errors.append(f"BROKEN PNG {file.relative_to(ASSETS)}: {reason}")
        digest = hashlib.sha256(file.read_bytes()).hexdigest()
        hashes.setdefault(digest, []).append(file.relative_to(ASSETS).as_posix())
    duplicates = [v for v in hashes.values() if len(v) > 1]
    for paths in duplicates:
        errors.append(f"DUPLICATE CONTENT: {', '.join(paths)}")

    mapping_doc = json.loads(MAPPINGS.read_text(encoding="utf-8"))
    mappings = mapping_doc["images"]
    ids = [item["wordId"] for item in mappings]
    if len(ids) != len(set(ids)):
        errors.append("DUPLICATE wordId in visual_vocabulary.json: resolve replacement policy")
    word_rows = json.loads(WORDS.read_text(encoding="utf-8"))
    word_ids = {row["id"] for row in word_rows}
    if len(word_rows) != 5126 or len(word_ids) != 5126:
        errors.append(f"words.json: expected 5126 rows and unique IDs, got {len(word_rows)} / {len(word_ids)}")
    referenced_assets = set()
    for item in mappings:
        asset = item["asset"]
        if asset.startswith("/") or ".." in pathlib.PurePosixPath(asset).parts:
            errors.append(f"unsafe asset path {asset}")
            continue
        file = ASSETS / asset
        if not file.is_file():
            errors.append(f"missing referenced asset {asset} for wordId {item['wordId']}")
        if item["wordId"] not in word_ids:
            errors.append(f"unknown word ID {item['wordId']} in mapping")
        referenced_assets.add(asset)

    for word in names:
        asset = f"additional_photos/{word}.png"
        if asset not in referenced_assets:
            errors.append(f"UNLINKED PHOTO {asset}: add a valid wordId mapping or resolve overlapping concept")

    report = {
        "expected": {"words": 5126, "baseline_images": 164, "new_photos": 53},
        "actual": {"words": len(word_rows), "baseline_images": len(original),
                   "new_photos": len(additional), "unique_mapped_words": len(set(ids)),
                   "linked_new_photos": len(expected & {x.removeprefix("additional_photos/") for x in referenced_assets if x.startswith("additional_photos/")})},
        "duplicate_binary_groups": duplicates,
        "errors": errors,
        "status": "PASS" if not errors else "BLOCKED"
    }
    output = ROOT / "results" / "release-217-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if errors:
        print(f"RELEASE BLOCKED: {len(errors)} integrity or linkage violations", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
