"""Validate a real three-image FLUX pilot; never infer AI provenance from pixels.

Usage:
  python tools/image_factory/verify_flux_pilot.py --directory ./flux_pilot \
    --metadata ./flux_pilot/provenance.json --output ./flux_pilot/report.json

provenance.json must be recorded by the actual GPU session:
{"engine":"FLUX.1-schnell","gpu":"...", "items":{
  "apple":{"file":"apple.png","inference_seconds":12.3},
  "bridge":{"file":"bridge.png","inference_seconds":12.3},
  "elephant":{"file":"elephant.png","inference_seconds":12.3}}}
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

WORDS = ("apple", "bridge", "elephant")

def verify(directory, metadata):
    folder = Path(directory)
    provenance = json.loads(Path(metadata).read_text(encoding="utf-8"))
    results = {}
    hashes = set()
    issues = []
    for word in WORDS:
        item = provenance.get("items", {}).get(word, {})
        name = item.get("file")
        if name != word + ".png":
            issues.append(word + ": expected exact filename " + word + ".png")
            continue
        path = folder / name
        if not path.is_file():
            issues.append(word + ": missing PNG")
            continue
        try:
            with Image.open(path) as im:
                im.verify()
            with Image.open(path) as im:
                width, height = im.size
                if im.format != "PNG" or min(width, height) < 768:
                    raise ValueError("expected PNG >= 768px on each axis")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest in hashes:
                raise ValueError("duplicate SHA-256")
            hashes.add(digest)
            results[word] = {"file": name, "width": width, "height": height,
                             "sha256": digest, "inference_seconds": item.get("inference_seconds")}
        except Exception as exc:
            issues.append(word + ": " + str(exc))
    if provenance.get("engine") != "FLUX.1-schnell" or not provenance.get("gpu"):
        issues.append("missing claimed engine/GPU provenance")
    return {"status": "BLOCKED" if issues else "FILES_VALID_SEMANTIC_REVIEW_PENDING",
            "provenance_claim": {"engine": provenance.get("engine"), "gpu": provenance.get("gpu")},
            "images": results, "issues": issues,
            "note": "Image validity and metadata do not independently prove FLUX inference or semantic correctness."}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--directory", required=True)
    p.add_argument("--metadata", required=True)
    p.add_argument("--output", required=True)
    args = p.parse_args()
    result = verify(args.directory, args.metadata)
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(result["status"])
    return 0 if result["status"] == "FILES_VALID_SEMANTIC_REVIEW_PENDING" else 2

if __name__ == "__main__":
    raise SystemExit(main())
