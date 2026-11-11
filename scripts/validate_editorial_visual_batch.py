#!/usr/bin/env python3
"""Validate editorial visual batches against the unchanged original app dictionary."""
import json
from pathlib import Path
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
WORDS = ROOT / "app/src/main/assets/words.json"
BATCHES = (
    (ROOT / "visual/editorial_batch_0001_0300.json", 1, 300),
    (ROOT / "visual/editorial_batch_0301_0800.json", 301, 800),
)
ALLOWED = {"grammar_card", "action_scene", "object_photo", "person_photo",
           "place_photo", "diagram", "concept_scene"}


def main():
    words = json.loads(WORDS.read_text(encoding="utf-8"))
    errors = []
    totals = Counter()
    all_ids = []
    for path, first, last in BATCHES:
        data = json.loads(path.read_text(encoding="utf-8"))
        records = data["items"]
        ids = [row["wordId"] for row in records]
        if ids != list(range(first, last + 1)):
            errors.append(f"{path.name}: incorrect ID coverage")
        for row in records:
            idx = row["wordId"] - 1
            if not 0 <= idx < len(words):
                errors.append(f"Invalid ID {row['wordId']}")
                continue
            word = words[idx]
            if (row["en"], row["ru"]) != (word["en"], word["ru"]):
                errors.append(f"Stale source mapping for ID {row['wordId']}")
            if row["visualType"] not in ALLOWED:
                errors.append(f"Unsupported visual type ID {row['wordId']}")
            if not row["visualBriefRu"].strip():
                errors.append(f"Missing visual brief ID {row['wordId']}")
            if row.get("imageStatus") != "not_created" and not row.get("imagePath"):
                errors.append(f"Image status without actual file for ID {row['wordId']}")
            totals[row["visualType"]] += 1
        all_ids.extend(ids)
    if len(set(all_ids)) != len(all_ids):
        errors.append("Overlapping word IDs across batches")
    if all_ids != list(range(1, 801)):
        errors.append("Combined batches must cover IDs 1–800 without gaps")
    print(json.dumps({"first_pass_editorial_records": len(all_ids),
                      "visual_types": dict(totals),
                      "errors": errors}, ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
