#!/usr/bin/env python3
"""Validate staged editorial visual planning batches against original words.json."""
import json
from pathlib import Path
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app/src/main/assets/words.json"
BATCH = ROOT / "visual/editorial_batch_0001_0300.json"
ALLOWED = {"grammar_card", "action_scene", "object_photo", "person_photo",
           "place_photo", "diagram", "concept_scene"}

def main():
    words = json.loads(WORDS.read_text(encoding="utf-8"))
    data = json.loads(BATCH.read_text(encoding="utf-8"))
    rows = data["items"]
    errors = []
    if len(rows) != 300:
        errors.append("Batch length is not 300")
    if [x["wordId"] for x in rows] != list(range(1, 301)):
        errors.append("Editorial batch IDs must be ordered 1..300")
    for record, actual in zip(rows, words):
        if (record["en"], record["ru"]) != (actual["en"], actual["ru"]):
            errors.append(f"Stale source mapping id={record['wordId']}")
        if record["visualType"] not in ALLOWED:
            errors.append(f"Unsupported image type id={record['wordId']}")
        if not record["visualBriefRu"].strip():
            errors.append(f"Missing visual brief id={record['wordId']}")
        if record["imageStatus"] != "not_created" and not record["imagePath"]:
            errors.append(f"Image status without path id={record['wordId']}")
    result = {
        "editorial_first_pass_count": len(rows),
        "ambiguous_meanings_flagged": sum(x["semanticAmbiguity"] for x in rows),
        "by_visual_type": dict(Counter(x["visualType"] for x in rows)),
        "images_created_in_batch": sum(x["imagePath"] is not None for x in rows),
        "errors": errors,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
