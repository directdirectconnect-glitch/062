#!/usr/bin/env python3
"""Restore approved 53-photo PNG sources staged as small base64 Git objects.

Only the seven absent original PNG files should be chunked. Existing checked-in
image binaries are preserved. The subsequent release integrity gate validates
PNG chunk CRCs, duplicates, mappings and total resource counts.
"""
import base64
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "build-requests/217-photo-drive-staging-manifest.json"
PARTS = ROOT / "release-assets/png-base64"
TARGET = ROOT / "app/src/main/assets/additional_photos"


def main():
    metadata = json.loads(MANIFEST.read_text(encoding="utf-8"))
    TARGET.mkdir(parents=True, exist_ok=True)
    restored = []
    missing = []
    for entry in metadata["images"]:
        name = entry["word"]
        dest = TARGET / (name + ".png")
        if dest.is_file():
            continue
        source = PARTS / name
        chunks = sorted(source.glob("part-*.txt"))
        if not chunks:
            missing.append(name)
            continue
        if [f.name for f in chunks] != [f"part-{i:03d}.txt" for i in range(len(chunks))]:
            raise RuntimeError("Bad chunk sequence for " + name)
        raw = "".join(chunk.read_text(encoding="ascii").strip() for chunk in chunks)
        data = base64.b64decode(raw, validate=True)
        if len(data) != entry["sourceBytes"] or not data.startswith(b"\\x89PNG\\r\\n\\x1a\\n"):
            raise RuntimeError("PNG size/signature mismatch for " + name)
        dest.write_bytes(data)
        restored.append(name)
    print(json.dumps({"restored": restored, "unavailable": missing}, ensure_ascii=False))
    return 0 if not missing else 2


if __name__ == "__main__":
    sys.exit(main())
