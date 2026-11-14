"""Read-only image archive audit. Reports damage, exact and possible visual duplicates.

Usage: python tools/image_factory/audit.py ARCHIVE --output audit.json
Never deletes, moves, or rewrites input images.
"""
import argparse
import hashlib
import json
from pathlib import Path

try:
    from .similarity import dhash, distance
    from .validate import validate_image
except ImportError:
    import importlib.util
    def _load(name):
        spec = importlib.util.spec_from_file_location("image_factory_" + name, Path(__file__).with_name(name + ".py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    _similarity = _load("similarity")
    dhash, distance = _similarity.dhash, _similarity.distance
    validate_image = _load("validate").validate_image

EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}

def audit(root, threshold=5, minimum=768):
    """Return deterministic JSON-serializable report without modifying inputs."""
    root = Path(root)
    if not root.is_dir():
        raise ValueError("Archive directory does not exist: " + str(root))
    if not isinstance(threshold, int) or not 0 <= threshold <= 64:
        raise ValueError("threshold must be 0..64")
    if not isinstance(minimum, int) or minimum < 1:
        raise ValueError("minimum must be positive")
    paths = sorted((p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in EXTENSIONS),
                   key=lambda p: str(p.relative_to(root)))
    valid = []
    rejected = []
    exact = []
    possible = []
    by_digest = {}
    for path in paths:
        relative = path.relative_to(root).as_posix()
        try:
            metadata = validate_image(path, minimum=minimum)
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            visual = dhash(path)
        except Exception as exc:
            rejected.append({"file": relative, "reason": str(exc) or type(exc).__name__})
            continue
        entry = {"file": relative, "sha256": digest, "dhash": f"{visual:016x}", **metadata}
        if digest in by_digest:
            exact.append({"file": relative, "matches": by_digest[digest]})
        else:
            by_digest[digest] = relative
        for earlier in valid:
            if earlier["sha256"] == digest:
                continue
            score = distance(visual, int(earlier["dhash"], 16))
            if score <= threshold:
                possible.append({"file": relative, "matches": earlier["file"], "distance": score})
        valid.append(entry)
    return {
        "root": str(root.resolve()),
        "threshold": threshold,
        "minimum": minimum,
        "summary": {
            "scanned": len(paths),
            "valid": len(valid),
            "rejected": len(rejected),
            "exact_duplicate_files": len(exact),
            "possible_duplicate_pairs": len(possible),
            "distinct_sha256": len(by_digest)
        },
        "valid_files": valid,
        "rejected_files": rejected,
        "exact_duplicates": exact,
        "possible_duplicates": possible,
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive")
    parser.add_argument("--output", required=True, help="Path for JSON report")
    parser.add_argument("--threshold", type=int, default=5)
    parser.add_argument("--minimum", type=int, default=768)
    args = parser.parse_args()
    report = audit(args.archive, threshold=args.threshold, minimum=args.minimum)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False))

if __name__ == "__main__":
    main()
