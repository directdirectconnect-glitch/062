#!/usr/bin/env python3
"""Fail closed when the 2026-11-16 217-photo APK release has missing PNGs.

Counts files, not unique vocabulary concepts. A separate mapping audit is required.
"""
import json
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MANIFEST = ROOT / "build-requests/217-photo-drive-staging-manifest.json"


def audit_png(file):
    """Check PNG signature, dimensions, and truncated chunk boundaries."""
    data = file.read_bytes()
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return "invalid PNG signature"
    width, height = struct.unpack(">II", data[16:24])
    if not (width and height):
        return "invalid dimensions"
    position = 8
    found_iend = False
    while position + 12 <= len(data):
        size = struct.unpack(">I", data[position:position + 4])[0]
        chunk = data[position + 4:position + 8]
        end = position + 12 + size
        if end > len(data):
            return "truncated chunk"
        position = end
        if chunk == b"IEND":
            found_iend = True
            break
    if not found_iend:
        return "missing IEND"
    return None


def main():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    entries = manifest["images"]
    original = sorted((ASSETS / "images").glob("*.png"))
    additional = sorted((ASSETS / "additional_photos").glob("*.png"))
    all_files = original + additional
    errors = []
    for file in all_files:
        reason = audit_png(file)
        if reason:
            errors.append(f"{file.relative_to(ROOT)}: {reason}")
    report = {
        "expected_original": 164, "expected_additional": 53,
        "actual_original": len(original), "actual_additional": len(additional),
        "manifest_rows": len(entries), "asset_total": len(all_files),
        "mapping_status": "NOT_VERIFIED",
        "errors": errors,
        "release_217_ready": False
    }
    if len(entries) != 53:
        errors.append(f"manifest contains {len(entries)} instead of 53 rows")
    if len(original) != 164:
        errors.append(f"original PNG count {len(original)} != 164")
    if len(additional) != 53:
        errors.append(f"additional PNG count {len(additional)} != 53")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    if errors:
        print("RELEASE BLOCKED: missing/invalid PNG resources; no 217-photo claim", file=sys.stderr)
        return 1
    print("FILE-COUNT GATE PASSED; mapping to 217 word IDs still needs separate verification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
