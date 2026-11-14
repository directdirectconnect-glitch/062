"""Read-only verification of APK image manifest against downloaded binary assets.

Usage: python tools/image_factory/verify_manifest.py asset_manifest.json ASSET_DIR --output verification.json
Never treats manifest-declared hashes as evidence of file integrity without reading the files.
"""
import argparse
import hashlib
import json
from pathlib import Path

def verify(manifest_path, asset_dir):
    manifest_path = Path(manifest_path)
    asset_dir = Path(asset_dir)
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not isinstance(data.get("items"), list):
        raise ValueError("Manifest must contain an items list")
    if not asset_dir.is_dir():
        raise ValueError("Asset directory not found")
    entries = data["items"]
    if data.get("count") != len(entries):
        raise ValueError("Manifest count does not match item count")
    seen_names, seen_hashes = set(), {}
    checked, missing, mismatched, unsafe, duplicates, unverified_ids = [], [], [], [], [], []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"Invalid item at index {index}")
        filename = entry.get("filename")
        expected = entry.get("sha256")
        if not isinstance(filename, str) or not filename or not isinstance(expected, str) or len(expected) != 64 or any(c not in "0123456789abcdefABCDEF" for c in expected):
            raise ValueError(f"Invalid filename or sha256 at index {index}")
        rel = Path(filename)
        if rel.is_absolute() or len(rel.parts) != 1 or rel.name != filename or filename in seen_names:
            unsafe.append(filename)
            continue
        seen_names.add(filename)
        if not entry.get("id_verified_against_app_dictionary", False):
            unverified_ids.append(filename)
        file_path = asset_dir / filename
        if not file_path.is_file() or file_path.is_symlink():
            missing.append(filename)
            continue
        actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
        if actual != expected.lower():
            mismatched.append({"file": filename, "expected": expected.lower(), "actual": actual})
        else:
            checked.append(filename)
        if actual in seen_hashes:
            duplicates.append({"file": filename, "matches": seen_hashes[actual]})
        else:
            seen_hashes[actual] = filename
    return {"summary": {"declared": len(entries), "verified_sha256": len(checked),
                        "missing": len(missing), "hash_mismatches": len(mismatched),
                        "unsafe_or_duplicate_names": len(unsafe), "duplicate_binary_files": len(duplicates),
                        "unverified_dictionary_ids": len(unverified_ids)},
            "verified_files": checked, "missing_files": missing, "hash_mismatches": mismatched,
            "unsafe_names": unsafe, "duplicate_files": duplicates, "unverified_dictionary_ids": unverified_ids,
            "status": "PASS" if len(checked) == len(entries) and not (missing or mismatched or unsafe or duplicates or unverified_ids) else "BLOCKED"}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest")
    parser.add_argument("assets")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    report = verify(args.manifest, args.assets)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], **report["summary"]}, ensure_ascii=False))

if __name__ == "__main__":
    main()
