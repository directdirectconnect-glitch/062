#!/usr/bin/env python3
"""Strict 267-WebP release gate: real Android assets, not Drive references.

Do not bypass this gate when building a production release. All images must
be present in the checked-out repository, decodable, unique and reachable.
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ROOT / "build-requests/217-photo-drive-staging-manifest.json"
MAPPINGS = ASSETS / "visual_vocabulary.json"
WORDS = ASSETS / "words.json"
SUPPLEMENTAL = ASSETS / "supplemental_photo_catalog.json"
REPORT = ROOT / "results/release-267-audit.json"
VALID_EXT = {".png", ".jpg", ".jpeg", ".webp"}

def valid_webp(path):
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return False, "invalid WebP RIFF signature"
    if int.from_bytes(data[4:8], "little") + 8 != len(data):
        return False, "WebP RIFF file length mismatch"
    chunk = data[12:16]
    if chunk not in (b"VP8 ", b"VP8L", b"VP8X"):
        return False, "missing WebP image chunk"
    try:
        from PIL import Image
        with Image.open(path) as image:
            image.load()
            if image.format != "WEBP" or image.width < 1 or image.height < 1:
                return False, "WebP decode or dimensions invalid"
    except ImportError:
        return False, "Pillow decoder unavailable: cannot certify WebP images"
    except Exception as exc:
        return False, "WebP decoding failed: " + str(exc)
    return True, ""

def main():
    errors = []
    details = {"expected": {"words": 5126, "baseline_webp": 164,
                            "new_webp": 53, "extra_webp": 50, "total_webp": 267}}
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    items = manifest["images"]
    expected_new = {x["word"] + ".webp" for x in items}
    if len(items) != 53 or len(expected_new) != 53:
        errors.append("manifest must list exactly 53 unique photo concepts")
    baseline = ASSETS / "images"
    newdir = ASSETS / "additional_photos"
    originals = sorted(p for p in baseline.glob("*") if p.suffix.lower() in VALID_EXT)
    additional = sorted(p for p in newdir.glob("*") if p.suffix.lower() in VALID_EXT)
    extra_dir = ASSETS / "extra_photos"
    extra = sorted(p for p in extra_dir.glob("*") if p.suffix.lower() in VALID_EXT)
    images = originals + additional + extra
    if len(extra) != 50:
        errors.append(f"expected 50 November photos, found {len(extra)}")
    if len(originals) != 164:
        errors.append(f"expected 164 baseline images, found {len(originals)}")
    if len(additional) != 53:
        errors.append(f"expected 53 additional images, found {len(additional)}")
    for path in images:
        if path.suffix.lower() != ".webp":
            errors.append(f"NON-WEBP FILE: {path.relative_to(ASSETS)}")
    actual_names = {p.name for p in additional}
    for name in sorted(expected_new - actual_names):
        errors.append("MISSING WEBP: additional_photos/" + name)
    for name in sorted(actual_names - expected_new):
        errors.append("UNEXPECTED IMAGE: additional_photos/" + name)
    digest_paths = {}
    for path in images:
        if path.suffix.lower() != ".webp":
            continue
        valid, reason = valid_webp(path)
        if not valid:
            errors.append(f"BROKEN WEBP {path.relative_to(ASSETS)}: {reason}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        digest_paths.setdefault(digest, []).append(path.relative_to(ASSETS).as_posix())
    duplicates = [paths for paths in digest_paths.values() if len(paths) > 1]
    for paths in duplicates:
        errors.append("DUPLICATE WEBP: " + ", ".join(paths))
    words = json.loads(WORDS.read_text(encoding="utf-8"))
    word_ids = {w["id"] for w in words}
    if len(words) != 5126 or len(word_ids) != 5126:
        errors.append(f"dictionary must have 5126 unique IDs, got {len(words)}/{len(word_ids)}")
    mapping = json.loads(MAPPINGS.read_text(encoding="utf-8"))["images"]
    mapped_ids = [item["wordId"] for item in mapping]
    if len(mapped_ids) != len(set(mapped_ids)):
        errors.append("duplicated wordId in visual vocabulary")
    referenced = set()
    for item in mapping:
        rel = item.get("asset", "")
        if not rel or rel.startswith("/") or ".." in pathlib.PurePosixPath(rel).parts:
            errors.append("unsafe mapped asset path: " + rel)
            continue
        if not rel.endswith(".webp"):
            errors.append("mapped asset is not WebP: " + rel)
        if item["wordId"] not in word_ids:
            errors.append("unknown mapped word ID: " + str(item["wordId"]))
        if not (ASSETS / rel).is_file():
            errors.append("missing mapped asset: " + rel)
        referenced.add(rel)
    # Compound words may live outside the 5126-word deck only when the user
    # can reach them in a separately implemented supplemental media screen.
    supplemental = []
    if SUPPLEMENTAL.exists():
        doc = json.loads(SUPPLEMENTAL.read_text(encoding="utf-8"))
        supplemental = doc.get("images", [])
        for item in supplemental:
            asset = item.get("asset", "")
            if item.get("titleEn", "").strip() == "" or not asset.startswith("additional_photos/") or not asset.endswith(".webp"):
                errors.append("invalid supplemental image metadata: " + asset)
            if not (ASSETS / asset).is_file():
                errors.append("missing supplemental WebP: " + asset)
            referenced.add(asset)
        # Presence of metadata alone is not evidence that an Android user can
        # navigate to these resources. This screen must be implemented too.
        if supplemental and not (ROOT / "app/src/main/java/com/example/english1000/SupplementalPhotosActivity.kt").is_file():
            errors.append("supplemental media screen not implemented")
    unlinked = [f"additional_photos/{x['word']}.webp" for x in items
                if f"additional_photos/{x['word']}.webp" not in referenced]
    for path in unlinked:
        errors.append("UNREACHABLE PHOTO: " + path)
    unlinked_extra = [p.relative_to(ASSETS).as_posix() for p in extra
                      if p.relative_to(ASSETS).as_posix() not in referenced]
    for path in unlinked_extra:
        errors.append("UNREACHABLE NOVEMBER PHOTO: " + path)
    if len(images) != 267:
        errors.append(f"expected 267 total images, found {len(images)}")
    details["actual"] = {"dictionary_rows": len(words),
                         "baseline_images": len(originals),
                         "additional_images": len(additional),
                         "extra_images": len(extra),
                         "total_images": len(images),
                         "decoded_unique_webp": len(digest_paths),
                         "mapped_word_ids": len(set(mapped_ids)),
                         "supplemental_entries": len(supplemental),
                         "unlinked_photos": len(unlinked)}
    details["duplicate_content_groups"] = duplicates
    details["errors"] = errors
    details["status"] = "PASS" if not errors else "BLOCKED"
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(details, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(details, ensure_ascii=False, indent=2))
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main())
