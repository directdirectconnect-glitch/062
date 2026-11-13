#!/usr/bin/env python3
"""Check production queue integrity and hard-gate visual assets before APK packaging.

Planning is not production: missing queued images are allowed, but an accepted
image must have evidence and explicit rights approval.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app/src/main/assets/words.json"
QUEUE = ROOT / "visual/image_production_queue_2101_3100.json"
ASSETS = ROOT / "app/src/main/assets"
ALLOWED_METHODS = {"original_ai", "original_photography", "original_illustration"}

def main():
    words = json.loads(WORDS.read_text(encoding="utf-8"))
    data = json.loads(QUEUE.read_text(encoding="utf-8"))
    items = data["items"]
    errors = []
    assert data["range"] == {"first": 2101, "last": 3100, "count": 1000}
    if len(items) != 1000:
        errors.append("Expected exactly 1000 records")
    if [v["wordId"] for v in items] != list(range(2101, 3101)):
        errors.append("IDs must be 2101..3100 with no duplicates")
    approved = 0
    files_found = 0
    for item in items:
        ident = item["wordId"]
        if not 1 <= ident <= len(words):
            errors.append(f"Out-of-range ID: {ident}")
            continue
        source = words[ident - 1]
        if (item["en"], item["ru"]) != (source["en"], source["ru"]):
            errors.append(f"Stale original words.json mapping: {ident}")
        expected = f"images/word_{ident:04d}.webp"
        if item["targetAsset"] != expected:
            errors.append(f"Non-standard asset path: {ident}")
        path = ASSETS / expected
        if path.exists():
            files_found += 1
            if path.read_bytes()[:4] != b"RIFF" or path.read_bytes()[8:12] != b"WEBP":
                errors.append(f"Not a WebP image: {ident}")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if item["assetSha256"] != digest:
                errors.append(f"Asset digest mismatch: {ident}")
            if not item["assetCreated"]:
                errors.append(f"File present but not recorded as created: {ident}")
        if item["assetCreated"] and not path.is_file():
            errors.append(f"Claimed asset missing: {ident}")
        if item["licenseDistribution"] == "approved":
            approved += 1
            if not path.is_file():
                errors.append(f"Rights-approved asset file absent: {ident}")
            if item["creationMethod"] not in ALLOWED_METHODS:
                errors.append(f"Source provenance invalid: {ident}")
            if not item["originEvidence"]:
                errors.append(f"Missing creation evidence: {ident}")
            if item["rightsReview"] != "approved":
                errors.append(f"Missing rights review: {ident}")
            if item["editorialApproval"] != "approved":
                errors.append(f"Missing visual editorial review: {ident}")
        elif item["status"] in {"published", "approved"}:
            errors.append(f"Unapproved image marked ready: {ident}")
    print(json.dumps({
        "queue_size": len(items),
        "real_webp_files_present": files_found,
        "images_approved_for_distribution": approved,
        "errors": errors,
    }, ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
